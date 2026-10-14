[CmdletBinding()]
param(
    [ValidateSet('Reading', 'Full', 'Release')][string]$Mode = 'Reading',
    [switch]$PrepareDeviceTests,
    [string[]]$GradleArguments = @()
)

$ErrorActionPreference = 'Stop'
$readerRepo = Split-Path -Parent $PSScriptRoot
$readerRunner = Join-Path $readerRepo 'gradlew.bat'
if ($PrepareDeviceTests -and $Mode -ne 'Full') { throw '-PrepareDeviceTests requires -Mode Full.' }
$readerLogDirectory = Join-Path $readerRepo 'output/verification'
[IO.Directory]::CreateDirectory($readerLogDirectory) | Out-Null
$readerLog = Join-Path $readerLogDirectory ("{0}-{1}.log" -f $Mode.ToLowerInvariant(), (Get-Date -Format 'yyyyMMdd-HHmmss'))
$readerGradleDirectory = Join-Path $readerRepo '.gradle'
[IO.Directory]::CreateDirectory($readerGradleDirectory) | Out-Null
$readerLock = $null
try {
    $readerLock = [IO.File]::Open((Join-Path $readerGradleDirectory 'reader-verification.lock'),
        [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $readerArguments = @('--max-workers=1', '--no-daemon',
        '-Pkotlin.compiler.execution.strategy=in-process')
    if ($Mode -eq 'Reading') {
        $readerTasks = @(':app:testQaUnitTest', '--tests', '*BookmarkTargetTest', '--tests', '*ReaderTopBarScrollStateTest',
            '--tests', '*ReaderProgressWheelTest', '--tests', '*PdfHighlightLoopTest', '--tests', '*DocumentSearchUnicodeOffsetTest',
            '--tests', '*MigrationV15ToV16Test')
    } else {
        $readerTasks = @(':app:testQaUnitTest', ':app:testReleaseUnitTest', ':app:lintRelease')
        if ($Mode -eq 'Release') { $readerTasks += ':app:assembleRelease' }
        elseif ($PrepareDeviceTests) { $readerTasks += @(':app:assembleQa', ':app:assembleQaAndroidTest') }
    }
    $readerArguments += @('--console=plain') + $GradleArguments
    Write-Host "Verification: $Mode. Evidence: $readerLog"
    Push-Location $readerRepo
    try {
        & $readerRunner @readerTasks @readerArguments *> $readerLog
    } finally { Pop-Location }
    $readerExit = $LASTEXITCODE
    Get-Content -LiteralPath $readerLog -Tail 24
    if ($readerExit -ne 0) { throw "Verification failed ($readerExit). See $readerLog" }
    if ($Mode -eq 'Release') {
        $readerBuiltApk = Join-Path $readerRepo 'app/build/outputs/apk/release/app-release.apk'
        $readerOutboxApk = Join-Path $readerRepo '.external_outbox/m1racle-reader-release-latest.apk'
        if ((Get-FileHash -LiteralPath $readerBuiltApk).Hash -ne (Get-FileHash -LiteralPath $readerOutboxApk).Hash) {
            throw 'Release build and Outbox do not match; delivery refused.'
        }
        $readerAdb = Get-Command adb -ErrorAction SilentlyContinue
        if (-not $readerAdb -and $env:ANDROID_HOME) {
            $readerAdbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
            if (Test-Path -LiteralPath $readerAdbPath) { $readerAdb = Get-Item -LiteralPath $readerAdbPath }
        }
        if ($readerAdb) {
            $readerAdbPath = if ($readerAdb.Source) { $readerAdb.Source } else { $readerAdb.FullName }
            $readerDevices = @(& $readerAdbPath devices | ForEach-Object { if ("$_" -match '^(\S+)\s+device$') { $Matches[1] } })
            if ($LASTEXITCODE -ne 0) { throw 'Cannot query ADB; Release delivery is pending.' }
            if ($readerDevices.Count -eq 1) {
                & $readerAdbPath -s $readerDevices[0] push $readerOutboxApk /storage/emulated/0/Download/m1racle-reader-release-latest.apk
                if ($LASTEXITCODE -ne 0) { throw 'Release built, but Download delivery failed.' }
            } else { Write-Warning 'Release is in Outbox; Download delivery pending (no unique connected device).' }
        } else { Write-Warning 'Release is in Outbox; Download delivery pending (ADB unavailable).' }
    }
} finally { if ($readerLock) { $readerLock.Dispose() } }
