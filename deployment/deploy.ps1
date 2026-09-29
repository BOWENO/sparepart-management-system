param(
    [string]$InstallDirectory = 'C:\SparePartSystem',
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbName = 'sparepart',
    [string]$DbUser = 'root',
    [string]$DbPassword,
    [string]$AllowedOrigin = 'http://localhost',
    [switch]$InitializeDatabase,
    [switch]$UpgradeExistingDatabase,
    [switch]$InitializeAccounts
)

$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new()
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$java = Get-Command java -ErrorAction Stop
$sourceJar = Join-Path $PSScriptRoot 'sparepart-system.jar'
if (-not (Test-Path $sourceJar)) { throw 'Invalid deployment package: sparepart-system.jar is missing.' }
if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $securePassword = Read-Host "Enter the MySQL password for user $DbUser" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try { $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
}

if ($InitializeDatabase -or $UpgradeExistingDatabase) {
    $mysql = Get-Command mysql -ErrorAction Stop
    $oldMysqlPassword = $env:MYSQL_PWD
    $env:MYSQL_PWD = $DbPassword
    try {
        if ($InitializeDatabase) {
            $createSql = "CREATE DATABASE IF NOT EXISTS ``$DbName`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
            $createSql | & $mysql.Source --host=$DbHost --port=$DbPort --user=$DbUser --default-character-set=utf8mb4
            if ($LASTEXITCODE -ne 0) { throw 'Failed to create the database.' }
            $initSql = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'sql\init.sql') -Raw -Encoding UTF8
            $initSql = $initSql -replace '(?im)^USE\s+sparepart\s*;', "USE ``$DbName``;"
            $initSql | & $mysql.Source --host=$DbHost --port=$DbPort --user=$DbUser --default-character-set=utf8mb4
            if ($LASTEXITCODE -ne 0) { throw 'Failed to initialize the database.' }
            $seedSql = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'sql\initial-data.sql') -Raw -Encoding UTF8
            $seedSql | & $mysql.Source --host=$DbHost --port=$DbPort --user=$DbUser --default-character-set=utf8mb4 $DbName
            if ($LASTEXITCODE -ne 0) { throw 'Failed to load the initial production ledger data.' }
        }
        if ($UpgradeExistingDatabase) {
            foreach ($migrationFile in @('migration-v2.1.sql', 'migration-v2.2.sql')) {
                $migrationSql = Get-Content -LiteralPath (Join-Path $PSScriptRoot "sql\$migrationFile") -Raw -Encoding UTF8
                $migrationSql = $migrationSql -replace '(?im)^USE\s+sparepart\s*;', "USE ``$DbName``;"
                $migrationSql | & $mysql.Source --host=$DbHost --port=$DbPort --user=$DbUser --default-character-set=utf8mb4
                if ($LASTEXITCODE -ne 0) { throw "Database migration failed at $migrationFile. Restore the backup and inspect the migration script." }
            }
        }
    } finally { $env:MYSQL_PWD = $oldMysqlPassword }
}

New-Item -ItemType Directory -Path $InstallDirectory -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $InstallDirectory 'logs') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $InstallDirectory 'backup') -Force | Out-Null
$targetJar = Join-Path $InstallDirectory 'sparepart-system.jar'
$pidFile = Join-Path $InstallDirectory 'sparepart-system.pid'
if (Test-Path $pidFile) {
    $oldPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $oldPid
        Wait-Process -Id $oldPid -Timeout 20 -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}
if (Test-Path $targetJar) {
    Copy-Item -LiteralPath $targetJar -Destination (Join-Path $InstallDirectory "backup\sparepart-system-$(Get-Date -Format 'yyyyMMdd_HHmmss').jar")
}
Copy-Item -LiteralPath $sourceJar -Destination $targetJar -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'stop.ps1') -Destination $InstallDirectory -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'backup-database.ps1') -Destination $InstallDirectory -Force

$configPath = Join-Path $InstallDirectory 'application-env.ps1'
$escapedPassword = $DbPassword.Replace("'", "''")
$escapedOrigin = $AllowedOrigin.Replace("'", "''")
$config = @"
`$env:DB_USERNAME = '$($DbUser.Replace("'", "''"))'
`$env:DB_PASSWORD = '$escapedPassword'
`$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://$DbHost`:$DbPort/$DbName?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
`$env:CORS_ALLOWED_ORIGINS = '$escapedOrigin'
`$env:BOOTSTRAP_USERS_ENABLED = '$($InitializeAccounts.IsPresent.ToString().ToLowerInvariant())'
`$env:SESSION_COOKIE_SECURE = 'false'
`$env:SERVER_PORT = '8080'
`$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
"@
Set-Content -LiteralPath $configPath -Value $config -Encoding UTF8

. $configPath
$stdout = Join-Path $InstallDirectory 'logs\app-console.log'
$stderr = Join-Path $InstallDirectory 'logs\app-error.log'
$process = Start-Process -FilePath $java.Source -ArgumentList @('-jar', $targetJar) -WorkingDirectory $InstallDirectory `
    -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$process.Id | Set-Content -LiteralPath $pidFile -Encoding ASCII

for ($attempt = 0; $attempt -lt 45; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { throw "Application startup failed. Check: $stderr" }
    try {
    $health = Invoke-RestMethod 'http://localhost:8080/api/v1/health' -TimeoutSec 2
        if ($health.data.status -eq 'UP') {
            Write-Host "Deployment completed: http://localhost:8080/ (PID $($process.Id))"
            if ($InitializeAccounts) { Write-Warning 'Bootstrap accounts are enabled. After verifying the accounts, run deploy.ps1 again without -InitializeAccounts.' }
            exit 0
        }
    } catch { }
}
throw "Application startup timed out. Check: $stdout and $stderr"
