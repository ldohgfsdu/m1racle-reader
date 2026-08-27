[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [ValidateSet("com.m1racle.reader", "com.m1racle.reader.qa")]
    [string]$PackageName = "com.m1racle.reader.qa",
    [switch]$AllowMainPackageRestore,
    [string]$AdbPath
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "app-data-safety-common.ps1")
$adb = Resolve-AdbPath $AdbPath
$backup = (Resolve-Path -LiteralPath $BackupDirectory).Path
$manifestPath = Join-Path $backup "manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Backup manifest missing: $manifestPath" }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ([string]$manifest.applicationId -ne $PackageName) {
    throw "Backup applicationId=$($manifest.applicationId) does not match target applicationId=$PackageName."
}
if ($PackageName -eq $script:ProtectedMainApplicationId -and -not $AllowMainPackageRestore) {
    throw "REFUSED main-package restore. Use QA for validation; -AllowMainPackageRestore is required for an intentional main restore."
}
$targetPackage = Get-PackageEvidence $adb $PackageName
if ([string]$targetPackage.versionCode -ne [string]$manifest.versionCode -or
    [string]$targetPackage.versionName -ne [string]$manifest.versionName) {
    throw "Installed version $($targetPackage.versionName) ($($targetPackage.versionCode)) does not match backup version $($manifest.versionName) ($($manifest.versionCode))."
}
$archivePath = Join-Path $backup ([string]$manifest.archive.path)
$payloadRoot = Join-Path $backup "payload"
if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw "Backup archive missing: $archivePath" }
if ((Get-Item -LiteralPath $archivePath).Length -ne [long]$manifest.archive.bytes) { throw "Backup archive size mismatch." }
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne [string]$manifest.archive.sha256) {
    throw "Backup archive SHA-256 mismatch."
}
Assert-PayloadManifest $payloadRoot @($manifest.files)

Write-Host "RESTORE_TARGET applicationId=$PackageName"
$remoteArchive = "/data/local/tmp/m1racle-reader-restore-$PID.tar"
$null = Invoke-AdbText $adb @("push", $archivePath, $remoteArchive)
try {
    $null = Invoke-AdbText $adb @("shell", "am", "force-stop", $PackageName)
    $restore = @'
set -eu
rm -rf cache/m1racle-reader-restore-new
mkdir -p cache/m1racle-reader-restore-new
tar -xf __ARCHIVE__ -C cache/m1racle-reader-restore-new
for d in databases shared_prefs files; do test -d cache/m1racle-reader-restore-new/$d; done
rollback() {
  for d in databases shared_prefs files; do
    if [ -e $d.m1racle-reader-restore-old ]; then rm -rf $d; mv $d.m1racle-reader-restore-old $d; fi
  done
}
trap rollback EXIT
for d in databases shared_prefs files; do
  rm -rf $d.m1racle-reader-restore-old
  if [ -e $d ]; then mv $d $d.m1racle-reader-restore-old; fi
  mv cache/m1racle-reader-restore-new/$d $d
done
trap - EXIT
rm -rf databases.m1racle-reader-restore-old shared_prefs.m1racle-reader-restore-old files.m1racle-reader-restore-old cache/m1racle-reader-restore-new
'@.Replace("__ARCHIVE__", $remoteArchive)
    $null = Invoke-AdbText $adb @("shell", "run-as $PackageName sh -c '$restore'")
} finally {
    $null = & $adb shell rm -f $remoteArchive 2>&1
}

$null = Invoke-AdbText $adb @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1")
Start-Sleep -Seconds 2
$pidValue = Invoke-AdbText $adb @("shell", "pidof", $PackageName)
if (-not $pidValue.Trim()) { throw "Restored application did not start: $PackageName" }
if ($PackageName -eq $script:QaApplicationId) {
    $instrumentation = "$PackageName.test/androidx.test.runner.AndroidJUnitRunner"
    $result = Invoke-AdbText $adb @(
        "shell", "am", "instrument", "-w", "-r", "-e", "class",
        "com.m1racle.reader.QaDataIsolationInstrumentedTest", $instrumentation
    )
    Write-Host $result
    if ($result -notmatch "OK \(1 test\)") { throw "QA Room-open verification instrumentation did not pass." }
}
Write-Host "RESTORE_COMPLETE applicationId=$PackageName process=$($pidValue.Trim()) manifestAndHashes=verified roomOpen=verified"
