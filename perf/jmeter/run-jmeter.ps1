param(
  [string]$HostName = "localhost",
  [int]$Port = 8099,
  [string]$Protocol = "http",
  [Alias("Concurrency")]
  [int]$Threads = 200,
  [int]$RampSeconds = 30,
  [int]$DurationSeconds = 600,
  [int]$ThinkTimeMs = 1000,
  [int]$Loops = 50,
  [int]$MonitorSampleSeconds = 2,
  [switch]$SkipHtmlReport = $false,
  [string]$JMeterBat = $env:JMETER_BAT
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($JMeterBat)) {
  if (-not [string]::IsNullOrWhiteSpace($env:JMETER_HOME)) {
    $JMeterBat = Join-Path $env:JMETER_HOME "bin\jmeter.bat"
  }
}

if ([string]::IsNullOrWhiteSpace($JMeterBat) -or -not (Test-Path $JMeterBat)) {
  throw "找不到 JMeter，可通过设置 JMETER_HOME 或 JMETER_BAT 指定 jmeter.bat 路径。"
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$testPlan = Join-Path $root "EnterpriseRagCommunity_basic_load.jmx"
if (-not (Test-Path $testPlan)) {
  throw "找不到测试计划文件：$testPlan"
}

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $root ("results\" + $ts)
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$jtl = Join-Path $outDir "results.jtl"
$reportDir = Join-Path $outDir "html-report"
$logFile = Join-Path $outDir "jmeter.log"
$metricsFile = Join-Path $outDir "system-metrics.json"

$jmeterArgs = @(
  "-n",
  "-t", $testPlan,
  "-l", $jtl,
  "-j", $logFile,
  "-Jhost=$HostName",
  "-Jport=$Port",
  "-Jprotocol=$Protocol",
  "-Jthreads=$Threads",
  "-JrampSeconds=$RampSeconds",
  "-JdurationSeconds=$DurationSeconds",
  "-JthinkTimeMs=$ThinkTimeMs",
  "-Jloops=$Loops"
)
$allCpu = @()
$allAvailMb = @()
$totalMb = 0.0
try {
  $os = Get-CimInstance Win32_OperatingSystem
  $totalMb = [math]::Round(([double]$os.TotalVisibleMemorySize) / 1024.0, 2)
} catch {
  $totalMb = 0.0
}

$proc = Start-Process -FilePath $JMeterBat -ArgumentList $jmeterArgs -PassThru -WindowStyle Hidden
while (-not $proc.HasExited) {
  try {
    Wait-Process -Id $proc.Id -Timeout $MonitorSampleSeconds -ErrorAction SilentlyContinue
  } catch {
  }
  try {
    $cpuSample = Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'" | Select-Object -First 1
    $memSample = Get-CimInstance Win32_PerfFormattedData_PerfOS_Memory | Select-Object -First 1
    if ($null -ne $cpuSample -and $null -ne $cpuSample.PercentProcessorTime) {
      $allCpu += [double]$cpuSample.PercentProcessorTime
    }
    if ($null -ne $memSample -and $null -ne $memSample.AvailableMBytes) {
      $allAvailMb += [double]$memSample.AvailableMBytes
    }
  } catch {
    # Ignore counter read failures and continue the pressure run.
  }
  $proc.Refresh()
}

$exitCode = $proc.ExitCode
if ($exitCode -ne 0) {
  throw "JMeter 执行失败(exit=$exitCode)，请查看日志：$logFile"
}

$cpuAvg = if ($allCpu.Count -gt 0) { [math]::Round((($allCpu | Measure-Object -Average).Average), 2) } else { $null }
$cpuMax = if ($allCpu.Count -gt 0) { [math]::Round((($allCpu | Measure-Object -Maximum).Maximum), 2) } else { $null }
$availMbAvg = if ($allAvailMb.Count -gt 0) { [math]::Round((($allAvailMb | Measure-Object -Average).Average), 2) } else { $null }
$availMbMin = if ($allAvailMb.Count -gt 0) { [math]::Round((($allAvailMb | Measure-Object -Minimum).Minimum), 2) } else { $null }
$availPctAvg = if ($totalMb -gt 0 -and $availMbAvg -ne $null) { [math]::Round(($availMbAvg / $totalMb) * 100.0, 2) } else { $null }
$availPctMin = if ($totalMb -gt 0 -and $availMbMin -ne $null) { [math]::Round(($availMbMin / $totalMb) * 100.0, 2) } else { $null }

$sysMetrics = [ordered]@{
  threads = $Threads
  rampSeconds = $RampSeconds
  durationSeconds = $DurationSeconds
  monitorSampleSeconds = $MonitorSampleSeconds
  totalVisibleMemoryMB = $totalMb
  cpuPercentAvg = $cpuAvg
  cpuPercentMax = $cpuMax
  memoryAvailableMBAvg = $availMbAvg
  memoryAvailableMBMin = $availMbMin
  memoryAvailablePercentAvg = $availPctAvg
  memoryAvailablePercentMin = $availPctMin
}
$sysMetrics | ConvertTo-Json -Depth 4 | Set-Content -Path $metricsFile -Encoding UTF8

if (Test-Path $jtl) {
  $lineCount = (Get-Content -Path $jtl -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
  if ($lineCount -gt 1 -and -not $SkipHtmlReport) {
    & $JMeterBat -g $jtl -o $reportDir
  } elseif ($lineCount -gt 1 -and $SkipHtmlReport) {
    Write-Host "Skip HTML report generation by option: -SkipHtmlReport"
  } else {
    Write-Warning "JMeter 未产生采样数据（results.jtl 为空或仅有表头），跳过 HTML 报告生成：$jtl"
    if (Test-Path $logFile) {
      Write-Host "JMeter log (tail): $logFile"
      Get-Content -Path $logFile -Tail 120 -ErrorAction SilentlyContinue
    }
  }
} else {
  Write-Warning "未找到 JMeter 结果文件，跳过 HTML 报告生成：$jtl"
}

Write-Host "JTL: $jtl"
Write-Host "HTML: $reportDir"
Write-Host "LOG: $logFile"
Write-Host "SYS: $metricsFile"
