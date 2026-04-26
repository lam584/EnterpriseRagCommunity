param(
  [string]$KeyPath = "E:\DownLoads\test123.pem",
  [string]$ControllerHost = "root@8.166.143.72",
  [string]$SutHost = "root@8.138.17.175",
  [string]$SutTargetIp = "172.20.170.194",
  [int]$SutTargetPort = 80,
  [string[]]$WorkerIps = @("172.20.170.195", "172.20.170.196"),
  [int[]]$Tiers = @(110000, 160000, 210000, 260000, 300000),
  [int]$RampSeconds = 30,
  [int]$DurationSeconds = 60,
  [int]$ThinkTimeMs = 1000,
  [int]$ClientRmiLocalPort = 50001,
  [int]$ObservationSeconds = 60,
  [int]$MonitorExtraSeconds = 20,
  [int]$MaxMonitorWaitSeconds = 15,
  [int]$ForceStopAfterSeconds = 180,
  [int]$ControllerTimeoutSeconds = 0,
  [int]$MaxAttemptsPerTier = 1,
  [int]$RetryDelaySeconds = 30,
  [switch]$ContinueOnTierIssue,
  [string]$WorkerBHost = "root@8.166.141.78",
  [string]$WorkerCHost = "root@8.166.115.79",
  [string]$WorkerHeap = "-Xms64g -Xmx64g",
  [string]$RunPrefix = "quick",
  [string]$RemoteProjectDir = "/root/EnterpriseRagCommunity",
  [string]$RemoteResultsRoot = "/root/EnterpriseRagCommunity/perf/jmeter/results",
  [string]$LocalResultsRoot = "",
  [string]$SummaryCsv = "",
  [switch]$DownloadRunFiles,
  [switch]$ShowLiveSummary = $true
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($LocalResultsRoot)) {
  $LocalResultsRoot = Join-Path $PSScriptRoot "results\quick-scan"
}
if ([string]::IsNullOrWhiteSpace($SummaryCsv)) {
  $SummaryCsv = Join-Path $LocalResultsRoot "quick-scan-summary.csv"
}

if ($DownloadRunFiles) {
  New-Item -ItemType Directory -Force -Path $LocalResultsRoot | Out-Null
}

if ($MaxAttemptsPerTier -ne 1) {
  Write-Warning "Auto retry is disabled by policy. MaxAttemptsPerTier is forced to 1."
  $MaxAttemptsPerTier = 1
}

$workerList = $WorkerIps -join ","
$workerCount = [Math]::Max(1, $WorkerIps.Count)
$summarizer = Join-Path $PSScriptRoot "summarize-jmeter-result.ps1"
$remotePlan = "perf/jmeter/EnterpriseRagCommunity_basic_load.jmx"

function Invoke-RemoteCommand {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RemoteHost,
    [Parameter(Mandatory = $true)]
    [string]$Command
  )

  & ssh -i $KeyPath -o IdentitiesOnly=yes -o PubkeyAuthentication=yes $RemoteHost $Command
}

function Wait-RemoteResult {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RunName,
    [Parameter(Mandatory = $true)]
    [int]$MonitorSeconds,
    [Parameter(Mandatory = $true)]
    [int]$ControllerWaitSeconds
  )

  $remoteDir = "$RemoteResultsRoot/$RunName"
  $controllerDeadline = (Get-Date).AddSeconds([Math]::Max(10, $ControllerWaitSeconds))
  $forceStopDeadline = (Get-Date).AddSeconds([Math]::Max(30, $ForceStopAfterSeconds))
  $monitorDeadline = (Get-Date).AddSeconds([Math]::Max(5, [Math]::Min($MonitorSeconds, $MaxMonitorWaitSeconds)))
  $metricsReady = $false
  $forcedStop = $false
  $lastSummaryText = ""
  $lastMetricText = ""

  while ($true) {
    $status = (Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "if ps -ef | grep '[A]pacheJMeter.jar' | grep -q $RunName; then echo RUNNING; else echo DONE; fi").Trim()
    Write-Host "[$RunName] controller=$status"
    if ($ShowLiveSummary) {
      $summaryText = Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "grep -E 'summary \+|summary =|Err:' $remoteDir/jmeter.log 2>/dev/null | tail -n 3 || true"
      if (-not [string]::IsNullOrWhiteSpace($summaryText) -and $summaryText -ne $lastSummaryText) {
        Write-Host $summaryText
        $lastSummaryText = $summaryText
      }

      $metricText = (Invoke-RemoteCommand -RemoteHost $SutHost -Command "tail -n 1 /tmp/$RunName.csv 2>/dev/null || true").Trim()
      if (-not [string]::IsNullOrWhiteSpace($metricText) -and $metricText -ne $lastMetricText) {
        $metricParts = $metricText.Split(',')
        if ($metricParts.Count -ge 2) {
          Write-Host "[$RunName] sut cpu=$($metricParts[0])% memAvail=$($metricParts[1])%"
        }
        else {
          Write-Host "[$RunName] sut metrics raw=$metricText"
        }
        $lastMetricText = $metricText
      }
    }
    if ($status -eq "DONE") {
      break
    }
    if ((Get-Date) -ge $forceStopDeadline) {
      Write-Warning "[$RunName] controller still running after ${ForceStopAfterSeconds}s, force killing remote jmeter process."
      Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "pkill -9 -f ApacheJMeter.jar || true; sleep 2; if ps -ef | grep '[A]pacheJMeter.jar' >/dev/null; then echo FORCE_STOP_REMAINING; else echo FORCE_STOP_DONE; fi"
      $forcedStop = $true
      Start-Sleep -Seconds 5
      break
    }
    if ((Get-Date) -ge $controllerDeadline) {
      throw "[$RunName] controller exceeded wait timeout ${ControllerWaitSeconds}s."
    }
    Start-Sleep -Seconds 5
  }

  $monitorWaitSeconds = [Math]::Max(5, [Math]::Min($MonitorSeconds, $MaxMonitorWaitSeconds))
  while ($true) {
    $csvState = (Invoke-RemoteCommand -RemoteHost $SutHost -Command "if [ -s /tmp/$RunName.csv ]; then echo READY; else echo WAITING; fi").Trim()
    Write-Host "[$RunName] monitor=$csvState"
    if ($csvState -eq "READY") {
      $metricsReady = $true
      break
    }
    if ((Get-Date) -ge $monitorDeadline) {
      Write-Warning "[$RunName] monitor CSV not ready within ${monitorWaitSeconds}s, fallback to whatever samples are currently available."
      break
    }
    Start-Sleep -Seconds 5
  }

  $csvText = Invoke-RemoteCommand -RemoteHost $SutHost -Command "cat /tmp/$RunName.csv 2>/dev/null || true"
  $samples = @()
  $numStyle = [System.Globalization.NumberStyles]::Float
  $inv = [System.Globalization.CultureInfo]::InvariantCulture
  foreach ($line in ($csvText -split "`r?`n")) {
    if ([string]::IsNullOrWhiteSpace($line)) {
      continue
    }
    $parts = $line.Trim().Split(',')
    if ($parts.Count -lt 2) {
      continue
    }

    $cpuVal = 0.0
    $memVal = 0.0
    if ([double]::TryParse($parts[0], $numStyle, $inv, [ref]$cpuVal) -and [double]::TryParse($parts[1], $numStyle, $inv, [ref]$memVal)) {
      $samples += [PSCustomObject]@{
        cpu = $cpuVal
        mem = $memVal
      }
    }
  }

  if ($samples.Count -gt 0) {
    $cpuSorted = @($samples | Select-Object -ExpandProperty cpu | Sort-Object)
    $p95Index = [Math]::Floor(($cpuSorted.Count - 1) * 0.95)
    $cpuP95 = $cpuSorted[[int]$p95Index]
    $memMin = ($samples | Measure-Object -Property mem -Minimum).Minimum

    # Use CPU P95 and memory minimum availability to avoid under-reporting during bursty load.
    $metrics = [PSCustomObject]@{
      cpuAvg = [Math]::Round([double]$cpuP95, 2)
      memAvailAvg = [Math]::Round([double]$memMin, 2)
    }
  }
  else {
    $metrics = [PSCustomObject]@{
      cpuAvg = [double]::NaN
      memAvailAvg = [double]::NaN
    }
  }

  $tail = Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "tail -n 8 $remoteDir/jmeter.log 2>/dev/null || true"

  return [PSCustomObject]@{
    Metrics = $metrics
    TailLog = $tail
    ForcedStop = $forcedStop
  }
}

function Get-RemoteRunSummary {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RunName
  )

  $remoteJtl = "$RemoteResultsRoot/$RunName/results.jtl"
  $pythonScript = @"
import csv
import json
import math
from collections import Counter

path = r'$remoteJtl'
total = 0
ok = 0
elapsed_sum = 0
first_ts = None
last_ts = None
hist = Counter()

with open(path, newline='', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        total += 1
        elapsed = int(float(row['elapsed']))
        hist[elapsed] += 1
        elapsed_sum += elapsed
        if str(row.get('success', '')).lower() == 'true':
            ok += 1
        ts = float(row['timeStamp'])
        if first_ts is None:
            first_ts = ts
        last_ts = ts

if total <= 0:
    raise SystemExit('results.jtl has no samples')

target_index = int(math.floor((total - 1) * 0.90))
cumulative = 0
p90_ms = 0
for value in sorted(hist):
    cumulative += hist[value]
    if cumulative > target_index:
        p90_ms = value
        break

wall_seconds = max(1.0, (last_ts - first_ts) / 1000.0)
payload = {
    'p90Seconds': round(p90_ms / 1000.0, 4),
    'avgSeconds': round((elapsed_sum / total) / 1000.0, 4),
    'successRate': round((ok * 100.0) / total, 4),
    'tps': round(total / wall_seconds, 4),
    'total': total,
}
print(json.dumps(payload, ensure_ascii=False))
"@

  $remoteCommand = "python3 - <<'PY'`n$pythonScript`nPY"
  $summaryJson = Invoke-RemoteCommand -RemoteHost $ControllerHost -Command $remoteCommand
  if ([string]::IsNullOrWhiteSpace(($summaryJson | Out-String))) {
    throw "[$RunName] remote summary is empty. results.jtl may be missing or unreadable."
  }
  return ($summaryJson | ConvertFrom-Json)
}

function Test-RemoteResultExists {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RunName
  )

  $exists = (Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "if [ -s $RemoteResultsRoot/$RunName/results.jtl ]; then echo YES; else echo NO; fi").Trim()
  return ($exists -eq "YES")
}

function Restart-Workers {
  $workerHosts = @($WorkerBHost, $WorkerCHost) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  foreach ($workerHost in $workerHosts) {
    Write-Host "Restarting worker server on $workerHost ..."
    try {
      Invoke-RemoteCommand -RemoteHost $workerHost -Command "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; pkill -9 -f ApacheJMeter.jar || true; /usr/sbin/fuser -k 1099/tcp 50000/tcp >/dev/null 2>&1 || true; HEAP='$WorkerHeap' nohup /root/jmeter/bin/jmeter-server >/root/jmeter-server.log 2>&1 </dev/null &"
      Start-Sleep -Seconds 10
      $ready = (Invoke-RemoteCommand -RemoteHost $workerHost -Command "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; if /usr/sbin/ss -ltn | grep -q ':1099' && /usr/sbin/ss -ltn | grep -q ':50000'; then echo READY; else echo NOT_READY; fi").Trim()
      if ($ready -ne "READY") {
        Write-Warning "Worker $workerHost not ready after first wait, retry health check in 10s."
        Start-Sleep -Seconds 10
        $ready = (Invoke-RemoteCommand -RemoteHost $workerHost -Command "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; if /usr/sbin/ss -ltn | grep -q ':1099' && /usr/sbin/ss -ltn | grep -q ':50000'; then echo READY; else echo NOT_READY; fi").Trim()
      }
      $health = Invoke-RemoteCommand -RemoteHost $workerHost -Command "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; /usr/sbin/ss -ltnp | grep -E ':1099|:50000' || true"
      $logTail = Invoke-RemoteCommand -RemoteHost $workerHost -Command "tail -n 20 /root/jmeter-server.log 2>/dev/null || true"
      Write-Host "Worker $workerHost state=$ready"
      Write-Host $health
      Write-Host $logTail
    }
    catch {
      Write-Warning "Failed to restart worker on ${workerHost}: $($_.Exception.Message)"
    }
  }
}

function Copy-RunFiles {
  param(
    [Parameter(Mandatory = $true)]
    [string]$RunName
  )

  $remoteDir = "${ControllerHost}:$RemoteResultsRoot/$RunName"
  $localDir = Join-Path $LocalResultsRoot $RunName
  if (Test-Path $localDir) {
    Remove-Item -Recurse -Force $localDir
  }
  New-Item -ItemType Directory -Force -Path $localDir | Out-Null

  & scp -i $KeyPath "$remoteDir/jmeter.log" "$localDir/jmeter.log" | Out-Null
  & scp -i $KeyPath "${ControllerHost}:/tmp/$RunName.controller.log" "$localDir/$RunName.controller.log" | Out-Null

  $exists = (Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "if [ -s $RemoteResultsRoot/$RunName/results.jtl ]; then echo YES; else echo NO; fi").Trim()
  if ($exists -ne "YES") {
    $remoteTail = Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "tail -n 80 /tmp/$RunName.controller.log 2>/dev/null || true"
    $localTailPath = Join-Path $localDir "controller-tail.txt"
    $remoteTail | Set-Content -Path $localTailPath -Encoding UTF8
    throw "Remote results.jtl is missing for $RunName. controller tail saved to $localTailPath"
  }

  & scp -i $KeyPath "$remoteDir/results.jtl" "$localDir/results.jtl" | Out-Null

  return $localDir
}

foreach ($tier in $Tiers) {
  $threadsPerWorker = [int][Math]::Ceiling($tier / $workerCount)
  $runName = "${RunPrefix}_$tier"
  $monitorSeconds = $RampSeconds + $DurationSeconds + $MonitorExtraSeconds
  $controllerWaitSeconds = if ($ControllerTimeoutSeconds -gt 0) { $ControllerTimeoutSeconds } else { $RampSeconds + $DurationSeconds + $MonitorExtraSeconds + 180 }
  $remoteDir = "$RemoteResultsRoot/$runName"
  $tierCompleted = $false
  $successRate = 0.0

  for ($attempt = 1; $attempt -le $MaxAttemptsPerTier; $attempt++) {
    Write-Host "=== START $runName attempt=$attempt/$MaxAttemptsPerTier total=$tier perWorker=$threadsPerWorker ==="
    $result = $null

    try {
      Invoke-RemoteCommand -RemoteHost $SutHost -Command "rm -f /tmp/$runName.csv /tmp/$runName.json /tmp/$runName.log; nohup /tmp/sut_monitor.sh /tmp/$runName.csv $monitorSeconds >/tmp/$runName.log 2>&1 < /dev/null & echo STARTED" | Out-Host

      $controllerCommand = @(
        "rm -rf $remoteDir",
        "mkdir -p $remoteDir",
        "cd $RemoteProjectDir",
        "nohup ~/jmeter/bin/jmeter -Jclient.rmi.localport=$ClientRmiLocalPort -n -t $remotePlan -R $workerList -l $remoteDir/results.jtl -j $remoteDir/jmeter.log -Ghost=$SutTargetIp -Gport=$SutTargetPort -Gprotocol=http -Gthreads=$threadsPerWorker -GrampSeconds=$RampSeconds -GdurationSeconds=$DurationSeconds -GthinkTimeMs=$ThinkTimeMs >/tmp/$runName.controller.log 2>&1 < /dev/null & echo STARTED"
      ) -join "; "
      Invoke-RemoteCommand -RemoteHost $ControllerHost -Command $controllerCommand | Out-Host
      Write-Host "[$runName] remote results.jtl => ${ControllerHost}:$remoteDir/results.jtl"

      $result = Wait-RemoteResult -RunName $runName -MonitorSeconds $monitorSeconds -ControllerWaitSeconds $controllerWaitSeconds
      if (-not (Test-RemoteResultExists -RunName $runName)) {
        $remoteTail = Invoke-RemoteCommand -RemoteHost $ControllerHost -Command "tail -n 120 /tmp/$runName.controller.log 2>/dev/null || true"
        throw "[$runName] remote results.jtl was not generated. Controller tail:`n$remoteTail"
      }
      $remoteSummary = Get-RemoteRunSummary -RunName $runName

      & powershell -ExecutionPolicy Bypass -File $summarizer -TestId $tier -Threads $tier -DurationSeconds $DurationSeconds -P90Seconds ([double]$remoteSummary.p90Seconds) -AvgSeconds ([double]$remoteSummary.avgSeconds) -SuccessRateOverride ([double]$remoteSummary.successRate) -TpsOverride ([double]$remoteSummary.tps) -CpuUsageOverride ([double]$result.Metrics.cpuAvg) -MemoryAvailableOverride ([double]$result.Metrics.memAvailAvg) -OutputCsv $SummaryCsv | Out-Host

      $successRate = [double]$remoteSummary.successRate

      if ($DownloadRunFiles) {
        $localDir = Copy-RunFiles -RunName $runName
        Write-Host "[$runName] downloaded artifacts => $localDir"
      }

      Write-Host $result.TailLog
      Write-Host "=== DONE $runName attempt=$attempt cpuAvg=$($result.Metrics.cpuAvg) memAvailAvg=$($result.Metrics.memAvailAvg) successRate=$([Math]::Round($successRate,4))% ==="
      if ($result.ForcedStop) {
        Write-Warning "[$runName] execution was force-stopped at ${ForceStopAfterSeconds}s; workers will be restarted before next step."
        Restart-Workers
      }
      $tierCompleted = $true
      break
    }
    catch {
      Write-Warning "Attempt $attempt failed for ${runName}: $($_.Exception.Message)"
      throw "[$runName] failed. Auto retry is disabled. Please perform RCA and relaunch this tier manually."
    }
  }

  if (-not $tierCompleted) {
    if ($ContinueOnTierIssue) {
      Write-Warning "All attempts failed for $runName, continue to next tier because ContinueOnTierIssue is enabled."
      continue
    }
    throw "All attempts failed for $runName. Stop quick scan."
  }

  if ($successRate -lt 100.0) {
    if ($ContinueOnTierIssue) {
      Write-Warning "SuccessRate below 100% at $runName (${successRate}%), continue to next tier because ContinueOnTierIssue is enabled."
    }
    else {
      Write-Warning "Stop escalating after $runName because SuccessRate dropped below 100% (${successRate}%)."
      break
    }
  }

  if ($tier -ne $Tiers[-1] -and $ObservationSeconds -gt 0) {
    Write-Host "=== OBSERVE ${ObservationSeconds}s BEFORE NEXT TIER ==="
    Start-Sleep -Seconds $ObservationSeconds
  }
}

Write-Host "Quick scan summary: $SummaryCsv"