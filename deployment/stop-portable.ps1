param([string]$InstallDirectory = $PSScriptRoot)

$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $InstallDirectory 'sparepart-system.pid'
if (-not (Test-Path $pidFile)) {
    Write-Host 'PID file was not found. The service may already be stopped.'
    exit 0
}
$backendPid = Get-Content -LiteralPath $pidFile
$process = Get-Process -Id $backendPid -ErrorAction SilentlyContinue
if ($process) {
    Stop-Process -Id $backendPid -Force
    try { Wait-Process -Id $backendPid -Timeout 20 -ErrorAction Stop } catch { }
}
Remove-Item -LiteralPath $pidFile -Force
Write-Host 'Service stopped.'
