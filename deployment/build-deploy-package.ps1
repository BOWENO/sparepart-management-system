param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\release'),
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$frontendRoot = Join-Path $projectRoot 'frontend'
$backendRoot = Join-Path $projectRoot 'backend'
$staticRoot = Join-Path $backendRoot 'src\main\resources\static'
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$packageName = "sparepart-system-$timestamp"
$stageRoot = Join-Path ([System.IO.Path]::GetTempPath()) $packageName
$packageRoot = Join-Path $stageRoot $packageName

try {
    $npm = Get-Command npm -ErrorAction Stop
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        $bundledJdk = Get-ChildItem (Join-Path $projectRoot '.runtime\jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $bundledJdk) { throw '未找到 Java 17。请安装 JDK 17，或在 .runtime/jdk 中提供便携 JDK。' }
        $env:JAVA_HOME = $bundledJdk.FullName
        $env:Path = "$(Join-Path $bundledJdk.FullName 'bin');$env:Path"
    }
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvn) {
        $bundledMaven = Get-ChildItem (Join-Path $projectRoot '.runtime\maven') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($bundledMaven) { $mvnPath = Join-Path $bundledMaven.FullName 'bin\mvn.cmd' }
        else { throw '未找到 Maven。请安装 Maven，或在 .runtime/maven 中提供便携 Maven。' }
    } else { $mvnPath = $mvn.Source }

    Write-Host '[1/4] 构建前端...'
    Push-Location $frontendRoot
    try {
        if (-not (Test-Path (Join-Path $frontendRoot 'node_modules'))) {
            & $npm.Source ci
            if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败。' }
        }
        & $npm.Source run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }
    } finally { Pop-Location }

    Write-Host '[2/4] 将前端产物嵌入后端...'
    if (Test-Path $staticRoot) { Remove-Item -LiteralPath $staticRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $staticRoot -Force | Out-Null
    Copy-Item -Path (Join-Path $frontendRoot 'dist\*') -Destination $staticRoot -Recurse -Force

    Write-Host '[3/4] 构建后端 JAR...'
    Push-Location $backendRoot
    try {
        $mavenRepository = Join-Path $projectRoot '.runtime\m2\repository'
        New-Item -ItemType Directory -Path $mavenRepository -Force | Out-Null
        $mavenArgs = @("-Dmaven.repo.local=$mavenRepository", 'clean', 'package')
        if ($SkipTests) { $mavenArgs += '-DskipTests' }
        & $mvnPath @mavenArgs
        if ($LASTEXITCODE -ne 0) { throw '后端构建或测试失败。' }
    } finally { Pop-Location }

    $jar = Get-ChildItem (Join-Path $backendRoot 'target') -Filter '*.jar' -File |
        Where-Object { $_.Name -notlike '*.original' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw '没有找到可部署 JAR。' }

    Write-Host '[4/4] 生成最小部署包...'
    New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $packageRoot 'sql') -Force | Out-Null
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $packageRoot 'sparepart-system.jar')
    Copy-Item -LiteralPath (Join-Path $backendRoot 'sql\init.sql') -Destination (Join-Path $packageRoot 'sql\init.sql')
    Copy-Item -LiteralPath (Join-Path $backendRoot 'sql\migration-v2.1.sql') -Destination (Join-Path $packageRoot 'sql\migration-v2.1.sql')
    Copy-Item -LiteralPath (Join-Path $backendRoot 'sql\migration-v2.2.sql') -Destination (Join-Path $packageRoot 'sql\migration-v2.2.sql')
    Copy-Item -LiteralPath (Join-Path $backendRoot 'src\main\resources\data-production.sql') -Destination (Join-Path $packageRoot 'sql\initial-data.sql')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'deploy.ps1') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'stop.ps1') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'DEPLOYMENT.md') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $projectRoot 'backup-database.ps1') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $projectRoot 'README.md') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $projectRoot '需求文档.md') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $projectRoot '备品备件管理系统_仓管员使用手册_V2.0.docx') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'initial-data') -Destination $packageRoot -Recurse

    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $zipPath = Join-Path (Resolve-Path $OutputDirectory).Path "$packageName.zip"
    Compress-Archive -LiteralPath $packageRoot -DestinationPath $zipPath -CompressionLevel Optimal -Force
    $hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
    Write-Host "部署包：$zipPath"
    Write-Host "SHA256：$hash"
} finally {
    if (Test-Path $stageRoot) { Remove-Item -LiteralPath $stageRoot -Recurse -Force }
}
