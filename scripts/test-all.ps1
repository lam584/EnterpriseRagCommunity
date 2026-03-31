param(
    [switch]$SkipSecurity,
    [switch]$SkipPerf
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

$reportRoot = Join-Path $root 'test-reports'
$logRoot = Join-Path $reportRoot 'logs'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$results = New-Object System.Collections.Generic.List[object]

function Copy-ReportArtifacts {
    param(
        [string]$TargetName,
        [string[]]$Sources
    )
    $target = Join-Path $reportRoot $TargetName
    if (Test-Path $target) {
        Remove-Item -Recurse -Force $target
    }
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    foreach ($src in $Sources) {
        $abs = Join-Path $root $src
        if (Test-Path $abs) {
            Copy-Item -Recurse -Force -Path $abs -Destination $target
        }
    }
}

function Invoke-Step {
    param(
        [string]$Name,
        [string]$Command,
        [string]$TargetName,
        [string[]]$Sources
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $status = 'PASSED'
    $exitCode = 0
    try {
        Write-Host "==> $Name"
        Invoke-Expression $Command
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
        if ($exitCode -ne 0) {
            $status = 'FAILED'
        }
    } catch {
        $status = 'FAILED'
        $exitCode = 1
        Write-Host $_
    } finally {
        $sw.Stop()
        if ($TargetName -and $Sources.Count -gt 0) {
            Copy-ReportArtifacts -TargetName $TargetName -Sources $Sources
        }
        $results.Add([pscustomobject]@{
                Name      = $Name
                Status    = $status
                ExitCode  = $exitCode
                DurationS = [math]::Round($sw.Elapsed.TotalSeconds, 2)
            })
    }
}

function Test-EndpointReady {
    param([string]$Url)
    try {
        $resp = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
        return $resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500
    } catch {
        return $false
    }
}

function Ensure-PerfService {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 180
    )
    if (Test-EndpointReady -Url $Url) {
        return $null
    }

    $bootOut = Join-Path $logRoot 'bootrun.out.log'
    $bootErr = Join-Path $logRoot 'bootrun.err.log'
    $cmd = "Set-Location '$root'; .\gradlew.bat bootRun --no-daemon --args='--server.port=8099 --spring.profiles.active=perf'"
    $proc = Start-Process -FilePath 'powershell' -ArgumentList "-NoProfile -ExecutionPolicy Bypass -Command `$ErrorActionPreference='Stop'; $cmd" -PassThru -WindowStyle Hidden -RedirectStandardOutput $bootOut -RedirectStandardError $bootErr

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if ($proc.HasExited) {
            throw "Service startup failed. bootRun exited. Logs: $bootOut / $bootErr"
        }
        if (Test-EndpointReady -Url $Url) {
            return $proc
        }
    }
    throw "Service not ready within $TimeoutSeconds seconds: $Url"
}

function Stop-PerfService {
    param([System.Diagnostics.Process]$Process)
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    }
}

$startedAt = Get-Date
$perfProc = $null
$perfHealthUrl = 'http://localhost:8099/api/public/site-config'

Invoke-Step -Name 'Backend Unit Tests' -Command '.\gradlew.bat test --no-daemon --stacktrace' -TargetName 'backend-tests' -Sources @('build/reports/tests/test')
Invoke-Step -Name 'Backend Integration Tests' -Command '.\gradlew.bat integrationTest --no-daemon --stacktrace' -TargetName 'backend-integration-tests' -Sources @('build/reports/tests/integrationTest')
Invoke-Step -Name 'Backend Unit Coverage' -Command '.\gradlew.bat jacocoTestReport --no-daemon --stacktrace' -TargetName 'backend-jacoco-unit' -Sources @('build/reports/jacoco/test/html')
Invoke-Step -Name 'Backend Integration Coverage' -Command '.\gradlew.bat jacocoIntegrationTestReport --no-daemon --stacktrace' -TargetName 'backend-jacoco-integration' -Sources @('build/reports/jacoco/jacocoIntegrationTestReport/html')
Invoke-Step -Name 'Frontend Vitest' -Command 'npm --prefix .\my-vite-app run test:ci' -TargetName 'frontend-vitest' -Sources @('my-vite-app/test-reports/vitest-junit.xml', 'my-vite-app/test-reports/vitest-coverage')

if (-not $SkipPerf) {
    try {
        $perfProc = Ensure-PerfService -Url $perfHealthUrl
        Invoke-Step -Name 'JMeter Perf Test' -Command 'powershell -ExecutionPolicy Bypass -File .\perf\jmeter\run-jmeter.ps1' -TargetName 'perf-jmeter' -Sources @('perf/jmeter/results')
    } finally {
        Stop-PerfService -Process $perfProc
    }
}

if (-not $SkipSecurity) {
    Invoke-Step -Name 'Backend Dependency Scan' -Command '.\gradlew.bat dependencyCheckAnalyze --no-daemon --no-problems-report --warning-mode=none' -TargetName 'security-dependency-check' -Sources @('build/reports/dependency-check')
    Invoke-Step -Name 'Frontend Dependency Audit' -Command 'npm --prefix .\my-vite-app audit --json > .\test-reports\npm-audit.json' -TargetName 'security-reports' -Sources @('test-reports/npm-audit.json')
}

$endedAt = Get-Date
$summaryPath = Join-Path $reportRoot 'SUMMARY.md'
$failedCount = @($results | Where-Object { $_.Status -eq 'FAILED' }).Count
$totalDuration = [math]::Round(($endedAt - $startedAt).TotalMinutes, 2)

$lines = @()
$lines += '# Full Test Summary'
$lines += ''
$lines += "- StartedAt: $($startedAt.ToString('yyyy-MM-dd HH:mm:ss'))"
$lines += "- EndedAt: $($endedAt.ToString('yyyy-MM-dd HH:mm:ss'))"
$lines += "- TotalMinutes: $totalDuration"
$lines += "- FailedTasks: $failedCount"
$lines += ''
$lines += '| Step | Status | ExitCode | DurationSec |'
$lines += '| --- | --- | --- | ---: |'
foreach ($r in $results) {
    $lines += "| $($r.Name) | $($r.Status) | $($r.ExitCode) | $($r.DurationS) |"
}
$lines += ''
$lines += '## Report Directories'
$lines += ''
$lines += '- Backend Unit: `test-reports/backend-tests/`'
$lines += '- Backend Integration: `test-reports/backend-integration-tests/`'
$lines += '- Backend Unit Coverage: `test-reports/backend-jacoco-unit/`'
$lines += '- Backend Integration Coverage: `test-reports/backend-jacoco-integration/`'
$lines += '- Frontend Tests and Coverage: `test-reports/frontend-vitest/`'
$lines += '- Performance: `test-reports/perf-jmeter/`'
$lines += '- Security: `test-reports/security-dependency-check/`, `test-reports/security-reports/`'

Set-Content -Path $summaryPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Summary generated: $summaryPath"

if ($failedCount -gt 0) {
    exit 1
}

exit 0
