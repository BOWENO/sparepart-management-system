param(
    [string]$InstallDirectory = 'C:\SparePartSystem',
    [string]$TaskName = 'SparePartSystem'
)

$ErrorActionPreference = 'Stop'

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this script from PowerShell as Administrator.'
    }
}

function Stop-ExistingApplication {
    Disable-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue | Out-Null
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    $applicationProcesses = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq 'java.exe' -and $_.CommandLine -like '*sparepart-system.jar*'
    }
    foreach ($applicationProcess in $applicationProcesses) {
        Stop-Process -Id $applicationProcess.ProcessId -Force
    }
    if ($applicationProcesses) { Start-Sleep -Seconds 3 }
}

function Assert-Port80Available {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort 80 -ErrorAction SilentlyContinue
    if ($listeners) {
        $details = ($listeners | ForEach-Object {
            'address=' + $_.LocalAddress + ', pid=' + $_.OwningProcess
        }) -join '; '
        throw "Port 80 is still occupied: $details"
    }
}

function Write-TaskRunner {
    param([string]$Path)
    $escapedInstallDirectory = $InstallDirectory.Replace("'", "''")
    $lines = @(
        '$ErrorActionPreference = ''Stop''',
        ('$installDirectory = ''' + $escapedInstallDirectory + ''''),
        'Set-Location $installDirectory',
        '$javaPath = Join-Path $installDirectory ''runtime\bin\java.exe''',
        '$jarPath = Join-Path $installDirectory ''sparepart-system.jar''',
        '$databasePath = (Join-Path $installDirectory ''data\sparepart'').Replace(''\'', ''/'')',
        '$arguments = @(',
        '    ''-Dfile.encoding=UTF-8'',',
        '    ''-jar'', $jarPath,',
        '    ''--spring.datasource.driver-class-name=org.h2.Driver'',',
        '    "--spring.datasource.url=jdbc:h2:file:$databasePath;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE",',
        '    ''--spring.datasource.username=sa'',',
        '    ''--spring.datasource.password='',',
        '    ''--spring.sql.init.mode=always'',',
        '    ''--spring.sql.init.schema-locations=classpath:schema-local.sql'',',
        '    ''--spring.sql.init.continue-on-error=true'',',
        '    ''--app.bootstrap-users-enabled=false'',',
        '    ''--server.address=0.0.0.0'',',
        '    ''--server.port=80'',',
        '    ''--app.cors-allowed-origins=http://localhost''',
        ')',
        '& $javaPath @arguments',
        'exit $LASTEXITCODE'
    )
    Set-Content -LiteralPath $Path -Value $lines -Encoding ASCII
}

function Register-ApplicationTask {
    param([string]$RunnerPath)
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' `
        -Argument ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $RunnerPath + '"') `
        -WorkingDirectory $InstallDirectory
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $trigger.Delay = 'PT30S'
    $settings = New-ScheduledTaskSettingsSet -RestartCount 10 `
        -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable `
        -ExecutionTimeLimit ([TimeSpan]::Zero)
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' `
        -LogonType ServiceAccount -RunLevel Highest
    Register-ScheduledTask -TaskName $TaskName `
        -Description 'Spare parts management system startup and recovery' `
        -Action $action -Trigger $trigger -Settings $settings `
        -Principal $principal -Force | Out-Null
}

function Ensure-FirewallRule {
    $ruleName = 'SparePartSystem HTTP 80'
    $rule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
    if (-not $rule) {
        New-NetFirewallRule -DisplayName $ruleName -Direction Inbound `
            -Protocol TCP -LocalPort 80 -Action Allow -Profile Any | Out-Null
    } else {
        Enable-NetFirewallRule -DisplayName $ruleName | Out-Null
    }
}

function Wait-ForHealth {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        Start-Sleep -Seconds 1
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1/api/v1/health' -TimeoutSec 2
            if ($health.data.status -eq 'UP' -and $health.data.database -eq 'UP') {
                return $true
            }
        } catch { }
        $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        if ($task -and $task.State -eq 'Disabled') { break }
    }
    return $false
}

Assert-Administrator

$javaPath = Join-Path $InstallDirectory 'runtime\bin\java.exe'
$jarPath = Join-Path $InstallDirectory 'sparepart-system.jar'
if (-not (Test-Path $javaPath)) { throw "Bundled Java was not found: $javaPath" }
if (-not (Test-Path $jarPath)) { throw "Application JAR was not found: $jarPath" }

Write-Host '[1/6] Stopping old scheduled task and application process...'
Stop-ExistingApplication
Write-Host '[2/6] Checking port 80...'
Assert-Port80Available
Write-Host '[3/6] Creating the managed startup runner...'
$runnerPath = Join-Path $InstallDirectory 'start-server-task.ps1'
Write-TaskRunner -Path $runnerPath
Write-Host '[4/6] Registering the startup task...'
Register-ApplicationTask -RunnerPath $runnerPath
Write-Host '[5/6] Opening Windows Firewall TCP port 80...'
Ensure-FirewallRule
Write-Host '[6/6] Starting and verifying the application...'
Start-ScheduledTask -TaskName $TaskName

if (-not (Wait-ForHealth)) {
    $taskInfo = Get-ScheduledTaskInfo -TaskName $TaskName -ErrorAction SilentlyContinue
    throw "Startup verification failed. LastTaskResult: $($taskInfo.LastTaskResult). Check C:\SparePartSystem\logs."
}

$taskState = (Get-ScheduledTask -TaskName $TaskName).State
Write-Host "Autostart configured successfully. Task state: $taskState"
Write-Host 'Health check: UP / database UP'
Write-Host 'Open http://SERVER_IP/ from an intranet computer.'
