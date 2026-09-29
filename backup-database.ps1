param(
    [Parameter(Mandatory=$true)][string]$BackupRoot,
    [string]$Database = 'sparepart',
    [string]$HostName = 'localhost',
    [string]$Username = 'root',
    [int]$RetentionWeeks = 12
)

$ErrorActionPreference = 'Stop'
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$targetDir = Join-Path $BackupRoot (Get-Date -Format 'yyyy-MM')
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
$sqlFile = Join-Path $targetDir "$Database`_$timestamp.sql"
$zipFile = Join-Path $targetDir "$Database`_$timestamp.zip"
$dump = Get-Command mysqldump -ErrorAction Stop
$arguments = @('--host', $HostName, '--user', $Username, '--single-transaction',
    '--routines', '--events', '--default-character-set=utf8mb4', $Database, "--result-file=$sqlFile")
$process = Start-Process -FilePath $dump.Source -ArgumentList $arguments -Wait -PassThru -NoNewWindow
if ($process.ExitCode -ne 0 -or -not (Test-Path $sqlFile)) {
    throw "Database backup failed. mysqldump exit code: $($process.ExitCode)"
}
Compress-Archive -LiteralPath $sqlFile -DestinationPath $zipFile -Force
Remove-Item -LiteralPath $sqlFile
$cutoff = (Get-Date).AddDays(-7 * $RetentionWeeks)
Get-ChildItem -LiteralPath $BackupRoot -Filter "$Database`_*.zip" -Recurse -File |
    Where-Object { $_.LastWriteTime -lt $cutoff } | Remove-Item -Force
Write-Host "Backup completed: $zipFile"
