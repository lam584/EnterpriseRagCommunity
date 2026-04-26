param(
    [string]$PidFile,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

function Get-KafkaProcessIds {
    $ids = New-Object System.Collections.Generic.HashSet[int]

    $javaProcesses = @(Get-CimInstance Win32_Process | Where-Object {
            $_.Name -match '^java(.exe)?$' -and $_.CommandLine -match 'kafka\.Kafka'
        })
    foreach ($proc in $javaProcesses) {
        [void]$ids.Add([int]$proc.ProcessId)
    }

    $portOwners = @(Get-NetTCPConnection -LocalPort 9092, 9093 -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique)
    foreach ($owner in $portOwners) {
        [void]$ids.Add([int]$owner)
    }

    return @($ids)
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($PidFile)) {
    $PidFile = Join-Path $repoRoot 'logs\kafka\kafka.pid'
}

$targets = New-Object System.Collections.Generic.HashSet[int]
if (Test-Path $PidFile) {
    $pidText = (Get-Content $PidFile -Raw).Trim()
    if ($pidText -match '^\d+$') {
        [void]$targets.Add([int]$pidText)
    }
}
foreach ($id in (Get-KafkaProcessIds)) {
    [void]$targets.Add([int]$id)
}

if ($targets.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'Kafka is not running.'
    }
    if (Test-Path $PidFile) {
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }
    exit 0
}

foreach ($id in $targets) {
    try {
        Stop-Process -Id $id -Force -ErrorAction Stop -Confirm:$false
        if (-not $Quiet) {
            Write-Host "Stopped Kafka process PID=$id"
        }
    } catch {
        if (-not $Quiet) {
            Write-Host "Skipped PID=${id}: $($_.Exception.Message)"
        }
    }
}

if (Test-Path $PidFile) {
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue -Confirm:$false
}

Start-Sleep -Seconds 2
$remaining = @(Get-NetTCPConnection -LocalPort 9092, 9093 -State Listen -ErrorAction SilentlyContinue)
if ($remaining.Count -gt 0) {
    throw 'Kafka ports are still in use after stop.'
}

if (-not $Quiet) {
    Write-Host 'Kafka stopped.'
}