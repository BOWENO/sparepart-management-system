param([string]$InstallDirectory = $PSScriptRoot)

$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $InstallDirectory 'sparepart-system.pid'
if (-not (Test-Path $pidFile)) {
    Write-Host 'PID file was not found. The service may already be stopped.'
    exit 0
}
$processId = Get-Content -LiteralPath $pidFile
$process = Get-Process -Id $processId -ErrorAction SilentlyContinue
if ($process) {
    Stop-Process -Id $processId
    Wait-Process -Id $processId -Timeout 20 -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $pidFile -Force
Write-Host 'Service stopped.'
