param(
  [string]$TargetUrl = "http://localhost:8099",
  [string]$OutDir = "security/reports",
  [string]$ReportName = "zap-baseline.html"
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$out = Join-Path $root $OutDir
New-Item -ItemType Directory -Force -Path $out | Out-Null

$report = Join-Path $out $ReportName

& docker run --rm `
  -t `
  -v "${out}:/zap/wrk:rw" `
  owasp/zap2docker-stable `
  zap-baseline.py `
  -t $TargetUrl `
  -r $ReportName `
  -I

Write-Host "ZAP report: $report"
