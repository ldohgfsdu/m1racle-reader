function Get-ReaderVerificationRuns {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('Reading', 'Full', 'Release')]
        [string]$Mode,
        [switch]$PrepareDeviceTests
    )

    if ($Mode -eq 'Reading') {
        return @(
            [pscustomobject]@{
                Task = ':app:testQaUnitTest'
                TestFilters = @(
                    '*BookmarkTargetTest',
                    '*ReaderTopBarScrollStateTest',
                    '*ReaderProgressWheelTest',
                    '*PdfHighlightLoopTest',
                    '*DocumentSearchUnicodeOffsetTest'
                )
            },
            [pscustomobject]@{
                Task = ':core:database:testReleaseUnitTest'
                TestFilters = @('*MigrationV15ToV16Test')
            }
        )
    }

    $tasks = @(
        ':core:model:testReleaseUnitTest',
        ':core:database:testReleaseUnitTest',
        ':core:pdf:testReleaseUnitTest',
        ':core:model:lintRelease',
        ':core:database:lintRelease',
        ':core:pdf:lintRelease',
        ':pdf-tool:testDebugUnitTest',
        ':pdf-tool:lintRelease',
        ':app:testQaUnitTest',
        ':app:testReleaseUnitTest',
        ':app:lintRelease'
    )
    if ($Mode -eq 'Release') {
        $tasks += ':app:assembleRelease'
    } elseif ($PrepareDeviceTests) {
        $tasks += @(':app:assembleQa', ':app:assembleQaAndroidTest')
    }
    return @(
        [pscustomobject]@{
            Task = $tasks -join ' '
            TestFilters = @()
        }
    )
}

function New-ReaderVerificationInvocation {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('Reading', 'Full', 'Release')]
        [string]$Mode,
        [switch]$PrepareDeviceTests,
        [string[]]$CommonArguments = @()
    )

    if ($PrepareDeviceTests -and $Mode -ne 'Full') {
        throw '-PrepareDeviceTests requires -Mode Full.'
    }

    $invocations = @()
    foreach ($run in @(Get-ReaderVerificationRuns -Mode $Mode -PrepareDeviceTests:$PrepareDeviceTests)) {
        $arguments = @($run.Task.Split(' ', [StringSplitOptions]::RemoveEmptyEntries))
        foreach ($filter in $run.TestFilters) {
            $arguments += @('--tests', $filter)
        }
        $arguments += $CommonArguments
        $invocations += ,@($arguments)
    }
    return $invocations
}

function Get-ReaderDefaultGradleTasks {
    param([switch]$Release)

    $tasks = @(':app:testQaUnitTest', ':app:testReleaseUnitTest', ':app:lintRelease')
    if ($Release) {
        $tasks += ':app:assembleRelease'
    }
    return $tasks
}

function Resolve-ReaderAapt2Path {
    param(
        [string]$Aapt2Path,
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    if ($Aapt2Path) {
        if (-not (Test-Path -LiteralPath $Aapt2Path -PathType Leaf)) {
            throw "aapt2 not found: $Aapt2Path"
        }
        return (Resolve-Path -LiteralPath $Aapt2Path).Path
    }

    $command = Get-Command aapt2 -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $sdkRoots = [Collections.Generic.List[string]]::new()
    foreach ($root in @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path (Split-Path -Parent $RepositoryRoot) '.build-tools/sdk'),
        (Join-Path $env:USERPROFILE '.gradle/reader-aapt2-sdk')
    )) {
        if ($root) { $sdkRoots.Add($root) }
    }
    $localProperties = Join-Path $RepositoryRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkLine) {
            $sdkRoots.Add($sdkLine.Substring('sdk.dir='.Length).Replace('\:', ':').Replace('\\', '\'))
        }
    }
    $gradleProperties = Join-Path $RepositoryRoot 'gradle.properties'
    if (Test-Path -LiteralPath $gradleProperties -PathType Leaf) {
        $aapt2Override = Get-Content -LiteralPath $gradleProperties |
            Where-Object { $_ -match '^android\.aapt2FromMavenOverride=' } |
            Select-Object -First 1
        if ($aapt2Override) {
            $candidate = $aapt2Override.Substring('android.aapt2FromMavenOverride='.Length)
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }

    foreach ($sdkRoot in $sdkRoots | Select-Object -Unique) {
        $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) { continue }
        $versions = @(Get-ChildItem -LiteralPath $buildToolsRoot -Directory | Sort-Object {
            try { [version]$_.Name } catch { [version]'0.0' }
        } -Descending)
        foreach ($version in $versions) {
            $candidate = Join-Path $version.FullName 'aapt2.exe'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }

    throw 'aapt2 was not found. Configure ANDROID_HOME/ANDROID_SDK_ROOT or pass -Aapt2Path.'
}

function Get-ReaderApkApplicationId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Aapt2Path,
        [Parameter(Mandatory = $true)]
        [string]$ApkPath
    )

    if (-not (Test-Path -LiteralPath $Aapt2Path -PathType Leaf)) {
        throw "aapt2 not found: $Aapt2Path"
    }
    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
        throw "APK not found: $ApkPath"
    }

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $badging = & $Aapt2Path dump badging $ApkPath 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw "aapt2 failed ($exitCode) while reading APK manifest: $ApkPath`n$($badging -join "`n")"
    }
    $text = $badging -join "`n"
    if ($text -notmatch "(?m)^package: name='([^']+)'") {
        throw "APK manifest applicationId could not be read: $ApkPath"
    }
    return $Matches[1]
}

function Assert-ReaderQaApkIdentity {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Aapt2Path,
        [Parameter(Mandatory = $true)]
        [string]$ApkPath,
        [Parameter(Mandatory = $true)]
        [string]$ExpectedApplicationId
    )

    $actualApplicationId = Get-ReaderApkApplicationId $Aapt2Path $ApkPath
    if ($actualApplicationId -ne $ExpectedApplicationId) {
        throw "REFUSED: APK manifest applicationId=$actualApplicationId, expected $ExpectedApplicationId."
    }
    return $actualApplicationId
}

function Get-ReaderRoomOpenStatus {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PackageName,
        [Parameter(Mandatory = $true)]
        [string]$QaApplicationId,
        [switch]$VerificationPassed
    )

    if ($PackageName -eq $QaApplicationId -and $VerificationPassed) {
        return 'verified'
    }
    return 'not_run'
}

function Get-ReaderSourceBaselineRoots {
    return @('app', 'benchmark', 'core', 'gradle', 'pdf-tool', 'reader-markdown-editor')
}

function Test-ReaderSourceBaselineInput {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $segments = @($RelativePath.Split('/'))
    if ($segments -contains 'build' -or $segments -contains 'node_modules' -or $segments -contains 'dist') {
        return $false
    }
    $name = $segments[-1]
    if ($name -in @('local.properties', 'keystore.properties') -or $name -like '*.sha256') {
        return $false
    }
    if ([IO.Path]::GetExtension($name).ToLowerInvariant() -in @(
        '.jks', '.keystore', '.p12', '.pfx', '.apk', '.ap_', '.aab'
    )) {
        return $false
    }
    $rootFiles = @(
        'build.gradle.kts',
        'settings.gradle.kts',
        'gradle.properties',
        'gradlew',
        'gradlew.bat'
    )
    return ($segments[0] -in (Get-ReaderSourceBaselineRoots) -or $RelativePath -in $rootFiles)
}

function Get-ReaderRestoreCompletionEvidence {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PackageName,
        [Parameter(Mandatory = $true)]
        [string]$ProcessId,
        [Parameter(Mandatory = $true)]
        [string]$RoomOpenStatus
    )

    if ($RoomOpenStatus -notin @('verified', 'not_run')) {
        throw "Unsupported Room-open status: $RoomOpenStatus"
    }
    return "RESTORE_COMPLETE applicationId=$PackageName process=$ProcessId manifestAndHashes=verified roomOpen=$RoomOpenStatus"
}
