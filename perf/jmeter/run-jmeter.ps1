param(
  [string]$HostName = "localhost",
  [int]$Port = 8099,
  [int]$Threads = 200,
  [int]$RampSeconds = 30,
  [int]$Loops = 50,
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

& $JMeterBat `
  -n `
  -t $testPlan `
  -l $jtl `
  -e `
  -o $reportDir `
  -Jhost=$HostName `
  -Jport=$Port `
  -Jthreads=$Threads `
  -JrampSeconds=$RampSeconds `
  -Jloops=$Loops

Write-Host "JTL: $jtl"
Write-Host "HTML: $reportDir"
