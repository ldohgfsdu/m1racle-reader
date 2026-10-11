[CmdletBinding()]
param([ValidateSet('Reading', 'Full', 'Release')][string]$Mode = 'Reading')

$ErrorActionPreference = 'Stop'
$readerRepo = Split-Path -Parent $PSScriptRoot
$readerWorkspace = Split-Path -Parent $readerRepo
$readerRunner = Join-Path $readerWorkspace 'reader-build-local.ps1'
if (-not (Test-Path -LiteralPath $readerRunner)) { throw 'Local build runner is missing. See docs/LOCAL_ENVIRONMENT.md.' }
$readerLogDirectory = Join-Path $readerWorkspace 'output/verification'
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
        $readerTasks = @(':app:testQaUnitTest', ':app:lintRelease')
        if ($Mode -eq 'Release') { $readerTasks += ':app:assembleRelease' }
        else { $readerTasks += @(':app:assembleQa', ':app:assembleQaAndroidTest') }
    }
    Write-Host "Verification: $Mode. Evidence: $readerLog"
    if ($Mode -eq 'Release') { & $readerRunner -Release @readerTasks @readerArguments *> $readerLog }
    else { & $readerRunner @readerTasks @readerArguments *> $readerLog }
    $readerExit = $LASTEXITCODE
    Get-Content -LiteralPath $readerLog -Tail 24
    if ($readerExit -ne 0) { throw "Verification failed ($readerExit). See $readerLog" }
} finally { if ($readerLock) { $readerLock.Dispose() } }
