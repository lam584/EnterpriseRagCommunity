param(
  [string]$ProjectDir = "my-vite-app",
  [string]$OutDir = "security/reports"
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$proj = Join-Path $root $ProjectDir
if (-not (Test-Path $proj)) {
  throw "找不到前端目录：$proj"
}

$out = Join-Path $root $OutDir
New-Item -ItemType Directory -Force -Path $out | Out-Null

$json = Join-Path $out "npm-audit.json"

Push-Location $proj
try {
  $result = & npm.cmd audit --json
  $result | Out-File -FilePath $json -Encoding utf8
} finally {
  Pop-Location
}

Write-Host "npm audit JSON: $json"
