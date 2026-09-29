param([switch]$OpenBrowser)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'backend'
$runtimeRoot = Join-Path $projectRoot '.runtime'
$pidFile = Join-Path $runtimeRoot 'backend.pid'
$logFile = Join-Path $runtimeRoot 'backend.log'
$errorLogFile = Join-Path $runtimeRoot 'backend-error.log'
$jarFile = Join-Path $backendRoot 'target\sparepart-backend-1.0.0.jar'

New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null

try {
    $health = Invoke-RestMethod 'http://127.0.0.1:7878/api/v1/health' -TimeoutSec 2
    if ($health.data.status -eq 'UP') {
        Write-Host 'Backend is already running: http://localhost:7878/'
        if ($OpenBrowser) { Start-Process 'http://localhost:7878/' }
        exit 0
    }
} catch { }

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($javaCommand) {
    $javaPath = $javaCommand.Source
} else {
    $javaFile = Get-ChildItem (Join-Path $runtimeRoot 'jdk') -Filter 'java.exe' -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $javaFile) { throw 'Java 17 was not found in PATH or .runtime\jdk.' }
    $javaPath = $javaFile.FullName
}

if (-not (Test-Path $jarFile)) {
    throw "Application JAR was not found: $jarFile"
}

$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SPAREPART_DB_PATH = Join-Path $backendRoot 'data\sparepart'
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'

$process = Start-Process -FilePath $javaPath -ArgumentList @('-jar', $jarFile) `
    -WorkingDirectory $backendRoot -WindowStyle Hidden `
    -RedirectStandardOutput $logFile -RedirectStandardError $errorLogFile -PassThru
$process.Id | Set-Content -LiteralPath $pidFile -Encoding ASCII

for ($attempt = 0; $attempt -lt 45; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { throw "Backend startup failed. Check: $errorLogFile" }
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:7878/api/v1/health' -TimeoutSec 2
        if ($health.data.status -eq 'UP') {
            Write-Host "Backend started: http://localhost:7878/ (PID $($process.Id))"
            if ($OpenBrowser) { Start-Process 'http://localhost:7878/' }
            exit 0
        }
    } catch { }
}

throw "Backend startup timed out. Check: $logFile"
