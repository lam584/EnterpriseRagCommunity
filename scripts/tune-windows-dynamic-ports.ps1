param(
    [switch]$Apply,
    [int]$StartPort = 1024,
    [int]$PortCount = 64511,
    [switch]$SetTimedWait,
    [int]$TcpTimedWaitDelay = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run PowerShell as Administrator.'
    }
}

function Show-CurrentDynamicPorts {
    Write-Host '=== Current Dynamic Port Range ==='
    netsh int ipv4 show dynamicport tcp
    netsh int ipv4 show dynamicport udp
    netsh int ipv6 show dynamicport tcp
    netsh int ipv6 show dynamicport udp
}

function Validate-Range {
    param(
        [int]$Start,
        [int]$Count
    )
    if ($Start -lt 1024 -or $Start -gt 65535) {
        throw "Invalid StartPort: $Start. Valid range: 1024~65535."
    }
    if ($Count -lt 255 -or $Count -gt 64511) {
        throw "Invalid PortCount: $Count. Valid range: 255~64511."
    }
    $end = $Start + $Count - 1
    if ($end -gt 65535) {
        throw "Port range overflow: StartPort=$Start, PortCount=$Count, EndPort=$end."
    }
}

function Apply-DynamicPorts {
    param(
        [int]$Start,
        [int]$Count
    )

    Write-Host "Applying dynamic port config: StartPort=$Start, PortCount=$Count"
    netsh int ipv4 set dynamicport tcp start=$Start num=$Count
    netsh int ipv4 set dynamicport udp start=$Start num=$Count
    netsh int ipv6 set dynamicport tcp start=$Start num=$Count
    netsh int ipv6 set dynamicport udp start=$Start num=$Count
}

function Apply-TimedWait {
    param(
        [int]$Delay
    )

    if ($Delay -lt 30 -or $Delay -gt 300) {
        throw "TcpTimedWaitDelay should be 30~300 seconds. Current: $Delay."
    }

    $regPath = 'HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters'
    Write-Host "Setting TcpTimedWaitDelay=$Delay at $regPath"
    New-ItemProperty -Path $regPath -Name 'TcpTimedWaitDelay' -PropertyType DWord -Value $Delay -Force | Out-Null
}

Show-CurrentDynamicPorts

if (-not $Apply) {
    Write-Host ''
    Write-Host 'No -Apply provided. Showing current config only.'
    Write-Host 'Example apply command:'
    Write-Host '  .\scripts\tune-windows-dynamic-ports.ps1 -Apply -StartPort 1024 -PortCount 64511'
    Write-Host 'Optional TIME_WAIT setting:'
    Write-Host '  .\scripts\tune-windows-dynamic-ports.ps1 -Apply -SetTimedWait -TcpTimedWaitDelay 30'
    exit 0
}

Assert-Administrator
Validate-Range -Start $StartPort -Count $PortCount
Apply-DynamicPorts -Start $StartPort -Count $PortCount

if ($SetTimedWait) {
    Apply-TimedWait -Delay $TcpTimedWaitDelay
    Write-Host 'TcpTimedWaitDelay updated. Reboot is usually required.'
}

Write-Host ''
Write-Host '=== Dynamic Port Range After Change ==='
Show-CurrentDynamicPorts
Write-Host 'Dynamic port configuration applied.'
