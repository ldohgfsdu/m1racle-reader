$ErrorActionPreference = 'Stop'
$readerScriptRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $readerScriptRoot 'scripts/reader-script-contracts.ps1')

Describe 'Reader verification routes' {
    It 'routes the migration regression to core database' {
        $runs = @(Get-ReaderVerificationRuns -Mode Reading)

        $runs.Count | Should Be 2
        $runs[0].Task | Should Be ':app:testQaUnitTest'
        [bool](@($runs[0].TestFilters) -contains '*MigrationV15ToV16Test') | Should Be $false
        $runs[1].Task | Should Be ':core:database:testReleaseUnitTest'
        [bool](@($runs[1].TestFilters) -contains '*MigrationV15ToV16Test') | Should Be $true

        $invocations = @(New-ReaderVerificationInvocation -Mode Reading -CommonArguments @('--no-daemon'))
        $invocations.Count | Should Be 2
        [bool]($invocations[0] -contains '--tests') | Should Be $true
        [bool]($invocations[0] -contains '*BookmarkTargetTest') | Should Be $true
        [bool]($invocations[1] -contains '*MigrationV15ToV16Test') | Should Be $true
        [bool]($invocations[1] -contains '--no-daemon') | Should Be $true
        [bool]($invocations[0] -contains ':core:database:testReleaseUnitTest') | Should Be $false
        [bool]($invocations[1] -contains ':app:testQaUnitTest') | Should Be $false

        try {
            New-ReaderVerificationInvocation -Mode Reading -PrepareDeviceTests
            throw 'Expected device-test preparation to require Full mode.'
        } catch {
            $_.Exception.Message | Should Match 'requires -Mode Full'
        }
    }

    It 'keeps the full and release module gates intact' {
        $fullTasks = (Get-ReaderVerificationRuns -Mode Full)[0].Task
        $releaseTasks = (Get-ReaderVerificationRuns -Mode Release)[0].Task

        $fullTasks | Should Match ':core:database:testReleaseUnitTest'
        $fullTasks | Should Match ':pdf-tool:testDebugUnitTest'
        $fullTasks | Should Match ':app:testReleaseUnitTest'
        $fullTasks | Should Not Match ':app:assembleRelease'
        $releaseTasks | Should Match ':app:testReleaseUnitTest'
        $releaseTasks | Should Match ':app:assembleRelease'
    }

    It 'includes production tests in local default build gates' {
        $buildTasks = @(Get-ReaderDefaultGradleTasks)
        $releaseTasks = @(Get-ReaderDefaultGradleTasks -Release)

        [bool]($buildTasks -contains ':app:testQaUnitTest') | Should Be $true
        [bool]($buildTasks -contains ':app:testReleaseUnitTest') | Should Be $true
        [bool]($buildTasks -contains ':app:lintRelease') | Should Be $true
        [bool]($buildTasks -contains ':app:assembleRelease') | Should Be $false
        [bool]($releaseTasks -contains ':app:testReleaseUnitTest') | Should Be $true
        [bool]($releaseTasks -contains ':app:assembleRelease') | Should Be $true
    }
}

Describe 'Reader benchmark APK identity' {
    It 'reads the actual APK manifest and accepts QA' {
        $qaApk = Join-Path $readerScriptRoot '.external_outbox/qa/m1racle-reader-qa-latest.apk'
        $aapt2 = Resolve-ReaderAapt2Path -RepositoryRoot $readerScriptRoot

        Assert-ReaderQaApkIdentity $aapt2 $qaApk 'com.m1racle.reader.qa' |
            Should Be 'com.m1racle.reader.qa'
    }

    It 'rejects a production APK even without metadata' {
        $productionApk = Join-Path $readerScriptRoot '.external_outbox/m1racle-reader-release-latest.apk'
        $aapt2 = Resolve-ReaderAapt2Path -RepositoryRoot $readerScriptRoot

        try {
            Assert-ReaderQaApkIdentity $aapt2 $productionApk 'com.m1racle.reader.qa'
            throw 'Expected production APK to be refused.'
        } catch {
            $_.Exception.Message | Should Match 'applicationId=com\.m1racle\.reader'
        }
    }

    It 'rejects the actual Release 95 with absent or forged QA metadata before ADB' {
        $productionApk = Join-Path $readerScriptRoot '.external_outbox/m1racle-reader-release-latest.apk'
        $expectedHash = 'A19D529EB4B2C16067380D565C10EB41E85C80C4CFEA3B9D3B4F23874B20EEE5'
        (Get-FileHash -LiteralPath $productionApk -Algorithm SHA256).Hash | Should Be $expectedHash
        Test-Path -LiteralPath (Join-Path (Split-Path -Parent $productionApk) 'output-metadata.json') |
            Should Be $false

        $temporaryRoot = Join-Path (Split-Path -Parent $readerScriptRoot) "output/script-contract-b3-$([guid]::NewGuid().ToString('N'))"
        try {
            [IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
            $absentRoot = Join-Path $temporaryRoot 'absent'
            [IO.Directory]::CreateDirectory($absentRoot) | Out-Null
            $absentMetadataApk = Join-Path $absentRoot 'release-95-no-metadata.apk'
            Copy-Item -LiteralPath $productionApk -Destination $absentMetadataApk
            $forgedRoot = Join-Path $temporaryRoot 'forged'
            [IO.Directory]::CreateDirectory($forgedRoot) | Out-Null
            $forgedMetadataApk = Join-Path $forgedRoot 'release-95-forged-metadata.apk'
            Copy-Item -LiteralPath $productionApk -Destination $forgedMetadataApk
            [IO.File]::WriteAllText(
                (Join-Path $forgedRoot 'output-metadata.json'),
                '{"applicationId":"com.m1racle.reader.qa"}',
                [Text.UTF8Encoding]::new($false)
            )
            Test-Path -LiteralPath (Join-Path $absentRoot 'output-metadata.json') | Should Be $false
            Test-Path -LiteralPath (Join-Path $forgedRoot 'output-metadata.json') | Should Be $true
            $aapt2 = Resolve-ReaderAapt2Path -RepositoryRoot $readerScriptRoot

            foreach ($apk in @($absentMetadataApk, $forgedMetadataApk)) {
                try {
                    Assert-ReaderQaApkIdentity $aapt2 $apk 'com.m1racle.reader.qa'
                    throw 'Expected Release 95 to be refused.'
                } catch {
                    $_.Exception.Message | Should Match 'applicationId=com\.m1racle\.reader, expected com\.m1racle\.reader\.qa'
                }
            }
        } finally {
            $outputRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $readerScriptRoot) 'output'))
            $temporaryFullPath = [IO.Path]::GetFullPath($temporaryRoot)
            if ($temporaryFullPath.StartsWith($outputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and
                (Test-Path -LiteralPath $temporaryFullPath -PathType Container)) {
                [IO.Directory]::Delete($temporaryFullPath, $true)
            }
        }
    }

    It 'resolves the local build tool override without a system SDK' {
        $previousHome = $env:ANDROID_HOME
        $previousRoot = $env:ANDROID_SDK_ROOT
        try {
            $env:ANDROID_HOME = $null
            $env:ANDROID_SDK_ROOT = $null
            $aapt2 = Resolve-ReaderAapt2Path -RepositoryRoot $readerScriptRoot
        } finally {
            $env:ANDROID_HOME = $previousHome
            $env:ANDROID_SDK_ROOT = $previousRoot
        }

        Split-Path -Leaf $aapt2 | Should Be 'aapt2.exe'
    }
}

Describe 'Reader restore evidence' {
    It 'reports Room open as verified only after the QA instrumentation passes' {
        Get-ReaderRoomOpenStatus 'com.m1racle.reader.qa' 'com.m1racle.reader.qa' |
            Should Be 'not_run'
        Get-ReaderRoomOpenStatus 'com.m1racle.reader.qa' 'com.m1racle.reader.qa' -VerificationPassed |
            Should Be 'verified'
        Get-ReaderRoomOpenStatus 'com.m1racle.reader' 'com.m1racle.reader.qa' -VerificationPassed |
            Should Be 'not_run'
    }

    It 'emits completion evidence that matches the executed verification' {
        Get-ReaderRestoreCompletionEvidence 'com.m1racle.reader.qa' '123' 'verified' |
            Should Be 'RESTORE_COMPLETE applicationId=com.m1racle.reader.qa process=123 manifestAndHashes=verified roomOpen=verified'
        Get-ReaderRestoreCompletionEvidence 'com.m1racle.reader' '456' 'not_run' |
            Should Be 'RESTORE_COMPLETE applicationId=com.m1racle.reader process=456 manifestAndHashes=verified roomOpen=not_run'
    }
}

Describe 'Reader source baseline scope' {
    It 'freezes every module exercised by Full verification' {
        Test-ReaderSourceBaselineInput 'app/build.gradle.kts' | Should Be $true
        Test-ReaderSourceBaselineInput 'core/database/build.gradle.kts' | Should Be $true
        Test-ReaderSourceBaselineInput 'pdf-tool/src/main/AndroidManifest.xml' | Should Be $true
        Test-ReaderSourceBaselineInput 'benchmark/build.gradle.kts' | Should Be $true
        Test-ReaderSourceBaselineInput 'settings.gradle.kts' | Should Be $true
    }

    It 'continues to exclude generated, secret, and irrelevant files' {
        Test-ReaderSourceBaselineInput 'pdf-tool/build/output.apk' | Should Be $false
        Test-ReaderSourceBaselineInput 'app/src/main/assets/release.apk' | Should Be $false
        Test-ReaderSourceBaselineInput 'signing/release.p12' | Should Be $false
        Test-ReaderSourceBaselineInput 'docs/CURRENT_STATUS.md' | Should Be $false
    }
}
