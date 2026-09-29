param(
    [string]$InstallDirectory = 'C:\SparePartSystem',
    [switch]$InitializeAccounts,
    [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$sourceJar = Join-Path $PSScriptRoot 'sparepart-system.jar'
$sourceRuntime = Join-Path $PSScriptRoot 'runtime'
$sourceJava = Get-ChildItem $sourceRuntime -Filter 'java.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not (Test-Path $sourceJar)) { throw 'Invalid package: sparepart-system.jar is missing.' }
if (-not $sourceJava) { throw 'Invalid package: bundled Java runtime is missing.' }

New-Item -ItemType Directory -Path $InstallDirectory -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $InstallDirectory 'data') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $InstallDirectory 'logs') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $InstallDirectory 'backup') -Force | Out-Null

$pidFile = Join-Path $InstallDirectory 'sparepart-system.pid'
if (Test-Path $pidFile) {
    $oldPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue
    if ($oldPid -match '^\d+$' -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $oldPid -Force
        try { Wait-Process -Id $oldPid -Timeout 20 -ErrorAction Stop } catch { }
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

$targetJar = Join-Path $InstallDirectory 'sparepart-system.jar'
if (Test-Path $targetJar) {
    Copy-Item -LiteralPath $targetJar -Destination (Join-Path $InstallDirectory "backup\sparepart-system-$(Get-Date -Format 'yyyyMMdd_HHmmss').jar")
}
Copy-Item -LiteralPath $sourceJar -Destination $targetJar -Force
$targetRuntime = Join-Path $InstallDirectory 'runtime'
if (-not (Test-Path (Join-Path $targetRuntime 'bin\java.exe'))) {
    if (Test-Path $targetRuntime) { Remove-Item -LiteralPath $targetRuntime -Recurse -Force }
    Copy-Item -LiteralPath $sourceRuntime -Destination $targetRuntime -Recurse -Force
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'stop-portable.ps1') -Destination $InstallDirectory -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'export-development-database.ps1') -Destination $InstallDirectory -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'reset-password-portable.ps1') -Destination $InstallDirectory -Force

$javaPath = Join-Path $targetRuntime 'bin\java.exe'
$databasePath = (Join-Path $InstallDirectory 'data\sparepart').Replace('\', '/')
$stdout = Join-Path $InstallDirectory 'logs\app-console.log'
$stderr = Join-Path $InstallDirectory 'logs\app-error.log'
$bootstrapValue = $InitializeAccounts.IsPresent.ToString().ToLowerInvariant()
$arguments = @(
    '-Dfile.encoding=UTF-8',
    '-jar', $targetJar,
    '--spring.datasource.driver-class-name=org.h2.Driver',
    "--spring.datasource.url=jdbc:h2:file:$databasePath;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE",
    '--spring.datasource.username=sa',
    '--spring.datasource.password=',
    '--spring.sql.init.mode=always',
    '--spring.sql.init.schema-locations=classpath:schema-local.sql',
    '--spring.sql.init.continue-on-error=true',
    "--app.bootstrap-users-enabled=$bootstrapValue",
    '--server.address=127.0.0.1',
    '--server.port=8080',
    '--app.cors-allowed-origins=http://localhost,http://localhost:8080'
)
if ($InitializeAccounts) {
    $arguments += '--spring.sql.init.data-locations=classpath:data-production.sql'
}
$process = Start-Process -FilePath $javaPath -ArgumentList $arguments -WorkingDirectory $InstallDirectory `
    -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$process.Id | Set-Content -LiteralPath $pidFile -Encoding ASCII

for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { throw "Application startup failed. Check: $stderr" }
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/health' -TimeoutSec 2
        if ($health.data.status -eq 'UP') {
            Write-Host "Portable deployment completed: http://localhost:8080/ (PID $($process.Id))"
            if ($InitializeAccounts) { Write-Warning 'After verifying initial accounts, run deploy-portable.ps1 again without -InitializeAccounts.' }
            if ($OpenBrowser) { Start-Process 'http://localhost:8080/' }
            exit 0
        }
    } catch { }
}
throw "Application startup timed out. Check: $stdout and $stderr"
