[CmdletBinding()]
param(
    [ValidateSet("com.markleaf.app", "com.markleaf.app.qa")]
    [string]$PackageName = "com.markleaf.app",
    [string]$OutputRoot,
    [string]$AdbPath
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "app-data-safety-common.ps1")
$adb = Resolve-AdbPath $AdbPath
if (-not $OutputRoot) {
    $OutputRoot = Join-Path (Split-Path -Parent $PSScriptRoot) ".local-backups\markleaf-debug"
}
$package = Get-PackageEvidence $adb $PackageName
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$finalDirectory = Join-Path $OutputRoot "$($PackageName.Replace('.', '-'))-$timestamp"
$temporaryDirectory = "$finalDirectory.incomplete"
$remoteArchive = "cache/markleaf-data-backup-$PID.tar"

if ((Test-Path -LiteralPath $finalDirectory) -or (Test-Path -LiteralPath $temporaryDirectory)) {
    throw "Backup destination already exists: $finalDirectory"
}
$null = New-Item -ItemType Directory -Path $temporaryDirectory
try {
    Write-Host "BACKUP_TARGET applicationId=$PackageName"
    $null = Invoke-AdbText $adb @("shell", "am", "force-stop", $PackageName)
    $preflight = "test -d databases && test -d shared_prefs && test -d files && " +
        "find databases shared_prefs files -type f | grep -q ."
    $null = Invoke-AdbText $adb @("shell", "run-as $PackageName sh -c '$preflight'")
    $createArchive = "rm -f $remoteArchive && tar -cf $remoteArchive databases shared_prefs files && test -s $remoteArchive"
    $null = Invoke-AdbText $adb @("shell", "run-as $PackageName sh -c '$createArchive'")

    $archivePath = Join-Path $temporaryDirectory "app-data.tar"
    Receive-AdbBinary $adb @("exec-out", "run-as", $PackageName, "cat", $remoteArchive) $archivePath
    $null = Invoke-AdbText $adb @("shell", "run-as", $PackageName, "rm", "-f", $remoteArchive)
    if ((Get-Item -LiteralPath $archivePath).Length -le 0) { throw "Received backup archive is empty." }

    $payloadRoot = Join-Path $temporaryDirectory "payload"
    $null = New-Item -ItemType Directory -Path $payloadRoot
    & tar.exe -xf $archivePath -C $payloadRoot
    if ($LASTEXITCODE -ne 0) { throw "Local backup archive extraction failed." }
    foreach ($required in @("databases", "shared_prefs", "files")) {
        if (-not (Test-Path -LiteralPath (Join-Path $payloadRoot $required) -PathType Container)) {
            throw "Backup is missing required directory: $required"
        }
    }
    $files = Get-PayloadManifest $payloadRoot
    Assert-PayloadManifest $payloadRoot $files
    $serial = Invoke-AdbText $adb @("get-serialno")
    $manifest = [ordered]@{
        formatVersion = 1
        applicationId = $package.applicationId
        versionName = $package.versionName
        versionCode = $package.versionCode
        packagePath = $package.packagePath
        deviceSerial = $serial.Trim()
        backedUpAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        archive = [ordered]@{
            path = "app-data.tar"
            bytes = (Get-Item -LiteralPath $archivePath).Length
            sha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
        }
        files = $files
    }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $temporaryDirectory "manifest.json") -Encoding UTF8
    Move-Item -LiteralPath $temporaryDirectory -Destination $finalDirectory
    Write-Host "BACKUP_COMPLETE path=$finalDirectory files=$($files.Count) bytes=$($manifest.archive.bytes) sha256=$($manifest.archive.sha256)"
} catch {
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
    throw
}
