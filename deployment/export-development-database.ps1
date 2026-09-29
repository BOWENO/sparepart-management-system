param(
    [string]$InstallDirectory = 'C:\SparePartSystem',
    [string]$OutputDirectory = (Join-Path $InstallDirectory 'developer-export')
)

$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $InstallDirectory 'sparepart-system.pid'
if (Test-Path $pidFile) {
    $backendPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue
    if ($backendPid -match '^\d+$' -and (Get-Process -Id $backendPid -ErrorAction SilentlyContinue)) {
        throw 'Stop the application before copying the H2 database.'
    }
}
$dataDirectory = Join-Path $InstallDirectory 'data'
$databaseFile = Join-Path $dataDirectory 'sparepart.mv.db'
if (-not (Test-Path $databaseFile)) { throw "Database file was not found: $databaseFile" }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$zipFile = Join-Path $OutputDirectory "sparepart-development-copy-$timestamp.zip"
Compress-Archive -LiteralPath $dataDirectory -DestinationPath $zipFile -CompressionLevel Optimal -Force
Write-Host "Development database copy created: $zipFile"
Write-Host 'Give developers a copy only. Do not let tools open the production database file.'
