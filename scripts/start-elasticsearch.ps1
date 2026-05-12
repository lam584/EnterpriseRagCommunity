param(
    [string]$EsHome = $env:ES_HOME,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ElasticsearchArgs
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($EsHome)) {
    $EsHome = 'E:\elasticsearch-9.2.4'
}

$launcher = Join-Path $EsHome 'bin\elasticsearch.bat'
if (-not (Test-Path $launcher)) {
    throw "Elasticsearch launcher not found: $launcher"
}

$escapedLauncher = '"' + $launcher.Replace('"', '""') + '"'
$escapedArgs = @()
foreach ($arg in $ElasticsearchArgs) {
    $escapedArgs += '"' + $arg.Replace('"', '""') + '"'
}

$cmdParts = @(
    'set "CLASSPATH="',
    'set "JAVA_HOME="',
    'set "ES_JAVA_HOME="',
    'call ' + $escapedLauncher
)
if ($escapedArgs.Count -gt 0) {
    $cmdParts[-1] += ' ' + ($escapedArgs -join ' ')
}

Write-Host "Starting Elasticsearch with bundled JDK and sanitized Java environment..."
Write-Host "ES_HOME: $EsHome"

& cmd.exe /d /v:off /c ($cmdParts -join ' & ')
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    exit $exitCode
}