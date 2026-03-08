param(
  [string]$HostName = "localhost",
  [int]$Port = 8099,
  [string]$Protocol = "http",
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
$logFile = Join-Path $outDir "jmeter.log"

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
  "-Jloops=$Loops"
)
& $JMeterBat @jmeterArgs

$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
  throw "JMeter 执行失败(exit=$exitCode)，请查看日志：$logFile"
}

if (Test-Path $jtl) {
  $lineCount = (Get-Content -Path $jtl -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
  if ($lineCount -gt 1) {
    & $JMeterBat -g $jtl -o $reportDir
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
