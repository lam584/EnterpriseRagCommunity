param(
  [Parameter(Mandatory = $true)]
  [string]$ResultsDir,
  [Parameter(Mandatory = $true)]
  [string]$TestId,
  [Parameter(Mandatory = $true)]
  [int]$Threads,
  [Parameter(Mandatory = $true)]
  [int]$DurationSeconds,
  [string]$OutputCsv = "",
  [double]$CpuUsageOverride = [double]::NaN,
  [double]$MemoryAvailableOverride = [double]::NaN
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutputCsv)) {
  $OutputCsv = Join-Path $PSScriptRoot "results\longtest-summary.csv"
}

$jtl = Join-Path $ResultsDir "results.jtl"
$sysMetrics = Join-Path $ResultsDir "system-metrics.json"
if (-not (Test-Path $jtl)) {
  throw "Missing results.jtl: $jtl"
}

$rows = Import-Csv -Path $jtl
if ($rows.Count -le 0) {
  throw "results.jtl has no samples: $jtl"
}

$elapsed = @($rows | ForEach-Object { [double]$_.elapsed } | Sort-Object)
$p90Index = [Math]::Floor(($elapsed.Count - 1) * 0.90)
$p90Seconds = [Math]::Round($elapsed[[int]$p90Index] / 1000.0, 4)
$avgElapsed = ($rows | Measure-Object -Property elapsed -Average).Average
$avgSeconds = [Math]::Round(([double]$avgElapsed) / 1000.0, 4)

$ok = ($rows | Where-Object { $_.success -eq 'true' }).Count
$total = $rows.Count
$successRate = [Math]::Round(($ok * 100.0) / [Math]::Max(1, $total), 4)

$firstTs = [double]$rows[0].timeStamp
$lastTs = [double]$rows[$rows.Count - 1].timeStamp
$wallSeconds = [Math]::Max(1.0, ($lastTs - $firstTs) / 1000.0)
$tps = [Math]::Round($total / $wallSeconds, 4)

$cpuUsage = $null
$memoryAvailable = $null
if (Test-Path $sysMetrics) {
  $m = Get-Content -Path $sysMetrics -Raw | ConvertFrom-Json
  $cpuUsage = $m.cpuPercentAvg
  $memoryAvailable = $m.memoryAvailablePercentAvg
}
if (-not [double]::IsNaN($CpuUsageOverride)) {
  $cpuUsage = $CpuUsageOverride
}
if (-not [double]::IsNaN($MemoryAvailableOverride)) {
  $memoryAvailable = $MemoryAvailableOverride
}

$durationLabel = if ($DurationSeconds % 3600 -eq 0) {
  "{0}h" -f ($DurationSeconds / 3600)
} elseif ($DurationSeconds % 60 -eq 0) {
  "{0}m" -f ($DurationSeconds / 60)
} else {
  "{0}s" -f $DurationSeconds
}

$headers = ConvertFrom-Json '{"testId":"\u6d4b\u8bd5\u7f16\u53f7","users":"\u5e76\u53d1\u7528\u6237\u6570","duration":"\u538b\u6d4b\u65f6\u957f","p90":"90%\u7684\u7528\u6237\u54cd\u5e94\u65f6\u95f4(s)","avg":"\u5e73\u5747\u54cd\u5e94\u65f6\u95f4(s)","success":"\u4e8b\u52a1\u6210\u529f\u7387","tps":"\u6bcf\u79d2\u5904\u7406\u4e8b\u52a1","cpu":"CPU\u5360\u7528\u7387","mem":"\u5185\u5b58\u53ef\u7528\u7387"}'

$cpuUsageText = if ($null -eq $cpuUsage) { "N/A" } else { ("{0:N2}%" -f [double]$cpuUsage) }
$memoryAvailableText = if ($null -eq $memoryAvailable) { "N/A" } else { ("{0:N2}%" -f [double]$memoryAvailable) }

$row = [ordered]@{
  $headers.testId = $TestId
  $headers.users = $Threads
  $headers.duration = $durationLabel
  $headers.p90 = $p90Seconds
  $headers.avg = $avgSeconds
  $headers.success = ("{0:N2}%" -f $successRate)
  $headers.tps = $tps
  $headers.cpu = $cpuUsageText
  $headers.mem = $memoryAvailableText
}

$outDir = Split-Path -Parent $OutputCsv
if (-not (Test-Path $outDir)) {
  New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$obj = [PSCustomObject]$row
if (-not (Test-Path $OutputCsv)) {
  $obj | Export-Csv -Path $OutputCsv -NoTypeInformation -Encoding UTF8
} else {
  $obj | Export-Csv -Path $OutputCsv -NoTypeInformation -Encoding UTF8 -Append
}

Write-Host "Record appended: $OutputCsv"
$obj | Format-Table -AutoSize