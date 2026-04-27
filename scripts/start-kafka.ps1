param(
    [string]$KafkaHome = $env:KAFKA_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ConfigFile,
    [string]$LogDir,
    [string]$PidFile,
    [switch]$ForceRestart
)

$ErrorActionPreference = 'Stop'

function Get-KafkaJavaProcess {
    $portOwners = @(Get-NetTCPConnection -LocalPort 9092, 9093 -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique)
    $javaProcesses = @(Get-CimInstance Win32_Process | Where-Object {
            $_.Name -match '^java(.exe)?$' -and $_.CommandLine -match 'kafka\.Kafka'
        })
    if ($portOwners.Count -gt 0) {
        $byPort = $javaProcesses | Where-Object { $portOwners -contains $_.ProcessId }
        if ($byPort) {
            return $byPort | Select-Object -First 1
        }
    }
    return $javaProcesses | Select-Object -First 1
}

function Get-LogDirFromConfig {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "Kafka config not found: $Path"
    }

    $line = Get-Content $Path | Where-Object {
        $_ -match '^\s*log\.dirs\s*=' -and $_ -notmatch '^\s*#'
    } | Select-Object -First 1

    if (-not $line) {
        return $null
    }

    $value = ($line -split '=', 2)[1].Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $null
    }
    return $value.Replace('/', '\\')
}

if ([string]::IsNullOrWhiteSpace($KafkaHome)) {
    $KafkaHome = 'E:\kafka_2.13-4.2.0'
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = 'C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot'
}
if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
    $ConfigFile = Join-Path $KafkaHome 'config\server.properties'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($LogDir)) {
    $LogDir = Join-Path $repoRoot 'logs\kafka'
}
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

if ([string]::IsNullOrWhiteSpace($PidFile)) {
    $PidFile = Join-Path $LogDir 'kafka.pid'
}

$stdoutLog = Join-Path $LogDir 'kafka.stdout.log'
$stderrLog = Join-Path $LogDir 'kafka.stderr.log'

if (-not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    throw "JAVA_HOME is invalid: $JavaHome"
}
if (-not (Test-Path (Join-Path $KafkaHome 'bin\windows\kafka-server-start.bat'))) {
    throw "KAFKA_HOME is invalid: $KafkaHome"
}

$existing = Get-KafkaJavaProcess
if ($existing -and -not $ForceRestart) {
    Set-Content -Path $PidFile -Value $existing.ProcessId -Encoding ASCII
    Write-Host "Kafka is already running. PID=$($existing.ProcessId)"
    exit 0
}
if ($existing -and $ForceRestart) {
    Stop-Process -Id $existing.ProcessId -Force -Confirm:$false
    Start-Sleep -Seconds 2
}

$dataDir = Get-LogDirFromConfig -Path $ConfigFile
if (-not [string]::IsNullOrWhiteSpace($dataDir)) {
    $metaFile = Join-Path $dataDir 'meta.properties'
    if (-not (Test-Path $metaFile)) {
        New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
        $env:JAVA_HOME = $JavaHome
        $env:KAFKA_HOME = $KafkaHome
        $env:Path = "$JavaHome\bin;$KafkaHome\bin\windows;" + $env:Path

        # Kafka 4.0+ removed 'kafka-storage.bat random-uuid'; generate UUID via PowerShell instead.
        $clusterId = [guid]::NewGuid().ToString()
        if ([string]::IsNullOrWhiteSpace($clusterId)) {
            throw 'Unable to generate Kafka cluster ID.'
        }

        # Preserve stderr for troubleshooting instead of swallowing it with Out-Null.
        $formatStdoutLog = Join-Path $LogDir 'kafka-storage.stdout.log'
        $formatStderrLog = Join-Path $LogDir 'kafka-storage.stderr.log'
        $formatProc = Start-Process -FilePath (Join-Path $KafkaHome 'bin\windows\kafka-storage.bat') `
            -ArgumentList @('format', '-t', $clusterId, '-c', $ConfigFile) `
            -NoNewWindow `
            -Wait `
            -PassThru `
            -RedirectStandardOutput $formatStdoutLog `
            -RedirectStandardError $formatStderrLog
        if ($formatProc.ExitCode -ne 0) {
            $errTail = @()
            if (Test-Path $formatStderrLog) {
                $errTail += Get-Content $formatStderrLog -Tail 20 -ErrorAction SilentlyContinue
            }
            if ($errTail.Count -eq 0 -and (Test-Path $formatStdoutLog)) {
                $errTail += Get-Content $formatStdoutLog -Tail 20 -ErrorAction SilentlyContinue
            }
            throw ("Kafka metadata format failed (exit=$($formatProc.ExitCode)).`n" + ($errTail -join [Environment]::NewLine))
        }
    }
}

# Escape $env:* with backticks so they are expanded inside the child shell, not the parent.
# Otherwise $env:JAVA_HOME would be replaced by its current value here and produce invalid syntax.
$command = "`$env:JAVA_HOME='$JavaHome'; " +
    "`$env:KAFKA_HOME='$KafkaHome'; " +
    "`$env:Path='$JavaHome\bin;$KafkaHome\bin\windows;' + `$env:Path; " +
    "& '$KafkaHome\bin\windows\kafka-server-start.bat' '$ConfigFile'"

$launcher = Start-Process -FilePath 'powershell.exe' `
    -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $command) `
    -WindowStyle Hidden `
    -PassThru `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog

$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 1
    $running = Get-KafkaJavaProcess
    if ($running) {
        Set-Content -Path $PidFile -Value $running.ProcessId -Encoding ASCII
        Write-Host "Kafka started in background. PID=$($running.ProcessId)"
        Write-Host "stdout: $stdoutLog"
        Write-Host "stderr: $stderrLog"
        exit 0
    }
    if ($launcher.HasExited) {
        $tail = @()
        if (Test-Path $stderrLog) {
            $tail += Get-Content $stderrLog -Tail 20 -ErrorAction SilentlyContinue
        }
        if ($tail.Count -eq 0 -and (Test-Path $stdoutLog)) {
            $tail += Get-Content $stdoutLog -Tail 20 -ErrorAction SilentlyContinue
        }
        throw ("Kafka start failed.`n" + ($tail -join [Environment]::NewLine))
    }
}

throw "Kafka start timed out. Check logs: $stdoutLog / $stderrLog"