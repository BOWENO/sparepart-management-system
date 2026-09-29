param([string]$InstallDirectory = 'C:\SparePartSystem')
$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $InstallDirectory 'sparepart-system.pid'
if (Test-Path $pidFile) {
    $runningPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue
    if ($runningPid -match '^\d+$' -and (Get-Process -Id $runningPid -ErrorAction SilentlyContinue)) {
        throw 'Stop the application before running offline password reset.'
    }
}
$username = Read-Host 'Account to reset'
$securePassword = Read-Host 'Temporary password (at least 6 characters with letters and digits)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try { $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
$env:SPAREPART_RESET_USERNAME = $username
$env:SPAREPART_RESET_PASSWORD = $plainPassword
try {
    $java = Join-Path $InstallDirectory 'runtime\bin\java.exe'
    $jar = Join-Path $InstallDirectory 'sparepart-system.jar'
    $databasePath = (Join-Path $InstallDirectory 'data\sparepart').Replace('\', '/')
    & $java '-Dfile.encoding=UTF-8' '-jar' $jar '--spring.main.web-application-type=none' `
      '--app.offline-password-reset=true' '--spring.datasource.driver-class-name=org.h2.Driver' `
      "--spring.datasource.url=jdbc:h2:file:$databasePath;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE" `
      '--spring.datasource.username=sa' '--spring.datasource.password=' '--spring.sql.init.mode=never'
    if ($LASTEXITCODE -ne 0) { throw 'Password reset failed. Review the console output.' }
} finally {
    Remove-Item Env:SPAREPART_RESET_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:SPAREPART_RESET_PASSWORD -ErrorAction SilentlyContinue
    $plainPassword = $null
}
Write-Host 'Password reset completed. Run deploy-portable.ps1 to start the application.'
