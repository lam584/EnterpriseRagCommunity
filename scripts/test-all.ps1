param(
  [switch]$SkipSecurity,
  [switch]$SkipPerf,
  [string]$BaseUrl = "http://localhost:8099"
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$outDir = Join-Path $root "test-reports"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Copy-Dir($src, $dst) {
  if (Test-Path $src) {
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Copy-Item -Path (Join-Path $src "*") -Destination $dst -Recurse -Force
  }
}

$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$crashDirRelative = "test-reports/jvm-crash"
$crashDir = Join-Path $root $crashDirRelative
New-Item -ItemType Directory -Force -Path $crashDir | Out-Null
$crashOpts = "-XX:ErrorFile=$crashDirRelative/hs_err_pid%p.log -XX:ReplayDataFile=$crashDirRelative/replay_pid%p.log"
if ([string]::IsNullOrWhiteSpace($oldJavaToolOptions)) {
  $env:JAVA_TOOL_OPTIONS = $crashOpts
} else {
  $env:JAVA_TOOL_OPTIONS = ($oldJavaToolOptions + " " + $crashOpts)
}

try {
  Write-Host "1/5 Backend: JUnit + integration + coverage"
  Push-Location $root
  try {
    & .\gradlew.bat test integrationTest jacocoTestReport jacocoIntegrationTestReport --no-daemon
  } finally {
    Pop-Location
  }

  Copy-Dir (Join-Path $root "build/reports/tests/test") (Join-Path $outDir "backend-tests")
  Copy-Dir (Join-Path $root "build/reports/tests/integrationTest") (Join-Path $outDir "backend-integration-tests")
  Copy-Dir (Join-Path $root "build/reports/jacoco/test/html") (Join-Path $outDir "backend-jacoco-unit")
  Copy-Dir (Join-Path $root "build/reports/jacocoIntegrationTestReport/html") (Join-Path $outDir "backend-jacoco-integration")

  Write-Host "2/5 Frontend: Vitest + coverage"
  Push-Location (Join-Path $root "my-vite-app")
  try {
    & npm.cmd run test:ci
  } finally {
    Pop-Location
  }
  Copy-Dir (Join-Path $root "my-vite-app/test-reports") (Join-Path $outDir "frontend-vitest")

  if (-not $SkipSecurity) {
    Write-Host "3/5 Security: SCA(Dependency-Check) + npm audit"
    Push-Location $root
    try {
      & .\gradlew.bat dependencyCheckAnalyze --no-daemon
    } finally {
      Pop-Location
    }
    Copy-Dir (Join-Path $root "build/reports/dependency-check") (Join-Path $outDir "security-dependency-check")

    & powershell -ExecutionPolicy Bypass -File (Join-Path $root "security/npm-audit.ps1")
    Copy-Dir (Join-Path $root "security/reports") (Join-Path $outDir "security-reports")
  } else {
    Write-Host "3/5 Security: skipped"
  }

  if (-not $SkipPerf) {
    Write-Host "4/5 Performance: start server(perf) + JMeter"
    Push-Location $root
    $proc = $null
    try {
      $proc = Start-Process -FilePath (Join-Path $root "gradlew.bat") -ArgumentList @("bootRun", "--args=--spring.profiles.active=perf") -PassThru -WindowStyle Hidden
      $ready = $false
      for ($i = 0; $i -lt 120; $i++) {
        try {
          $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 -Uri ($BaseUrl + "/api/public/site-config")
          if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) {
            $ready = $true
            break
          }
        } catch {
        }
        Start-Sleep -Seconds 1
      }
      if (-not $ready) {
        throw "Server not ready: $BaseUrl"
      }

      & powershell -ExecutionPolicy Bypass -File (Join-Path $root "perf/jmeter/run-jmeter.ps1")
      Copy-Dir (Join-Path $root "perf/jmeter/results") (Join-Path $outDir "perf-jmeter")

      if (Get-Command docker -ErrorAction SilentlyContinue) {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $root "security/zap-baseline-docker.ps1") -TargetUrl $BaseUrl
        Copy-Dir (Join-Path $root "security/reports") (Join-Path $outDir "security-reports")
      }
    } finally {
      if ($proc -ne $null -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
      }
      Pop-Location
    }
  } else {
    Write-Host "4/5 Performance: skipped"
  }

  $summary = Join-Path $outDir "SUMMARY.md"
  $lines = @(
    "# Test Summary",
    "",
    "## Functional",
    "- Backend unit tests: backend-tests/",
    "- Backend integration tests: backend-integration-tests/",
    "",
    "## Coverage",
    "- Backend unit coverage: backend-jacoco-unit/",
    "- Backend integration coverage: backend-jacoco-integration/",
    "- Frontend coverage: frontend-vitest/vitest-coverage/",
    "",
    "## Security",
    "- Dependency-Check (SCA): security-dependency-check/",
    "- npm audit: security-reports/npm-audit.json",
    "- ZAP baseline (DAST, optional): security-reports/zap-baseline.html",
    "",
    "## Performance",
    "- JMeter report: perf-jmeter/"
  )
  $lines | Out-File -FilePath $summary -Encoding utf8

  Write-Host "5/5 Summary: $summary"
  Write-Host "Artifacts: $outDir"
} finally {
  $env:JAVA_TOOL_OPTIONS = $oldJavaToolOptions
}
