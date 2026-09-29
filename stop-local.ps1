$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $PSScriptRoot '.runtime\backend.pid'
$backendPid = $null

if (Test-Path $pidFile) {
    $savedPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue
    if ($savedPid -match '^\d+$') { $backendPid = [int]$savedPid }
}

if (-not $backendPid -or -not (Get-Process -Id $backendPid -ErrorAction SilentlyContinue)) {
    $listener = netstat -ano | Select-String '^\s*TCP\s+\S+:7878\s+\S+\s+LISTENING\s+(\d+)\s*$' | Select-Object -First 1
    if ($listener -and $listener.Matches[0].Groups[1].Value) {
        $candidatePid = [int]$listener.Matches[0].Groups[1].Value
        $candidate = Get-Process -Id $candidatePid -ErrorAction SilentlyContinue
        if ($candidate -and $candidate.ProcessName -eq 'java') { $backendPid = $candidatePid }
    }
}

$process = if ($backendPid) { Get-Process -Id $backendPid -ErrorAction SilentlyContinue } else { $null }
if (-not $process) {
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host 'Backend is not running.'
    exit 0
}

Stop-Process -Id $backendPid -Force
try {
    Wait-Process -Id $backendPid -Timeout 15 -ErrorAction Stop
} catch {
    if (Get-Process -Id $backendPid -ErrorAction SilentlyContinue) {
        throw "Unable to stop backend process PID $backendPid. Run PowerShell as Administrator."
    }
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
Write-Host "Backend stopped (PID $backendPid)."
