[CmdletBinding()]
param(
    [string]$AdbPath,
    [string]$Serial,
    [string]$ApkPath = "app\build\outputs\apk\qa\app-qa.apk",
    [string]$OutputRoot = "dist\performance"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "app-data-safety-common.ps1")

$projectRoot = Split-Path -Parent $PSScriptRoot
$targetApplicationId = "com.m1racle.reader.qa"
$protectedApplicationId = "com.m1racle.reader"
$activity = "$targetApplicationId/com.m1racle.reader.ui.viewer.MarkdownScrollBenchmarkActivity"

if ($targetApplicationId -eq $protectedApplicationId) {
    throw "REFUSED: Markdown benchmark must never target protected applicationId=$protectedApplicationId."
}
if ($targetApplicationId -ne $script:QaApplicationId) {
    throw "REFUSED: only applicationId=$($script:QaApplicationId) is allowed."
}

$adb = Resolve-AdbPath $AdbPath
$adbPrefix = @()
if ($Serial) { $adbPrefix = @("-s", $Serial) }
function Adb([string[]]$Arguments) {
    Invoke-AdbText $adb ($adbPrefix + $Arguments)
}

$resolvedApk = Join-Path $projectRoot $ApkPath
if ([System.IO.Path]::IsPathRooted($ApkPath)) { $resolvedApk = $ApkPath }
if (-not (Test-Path -LiteralPath $resolvedApk -PathType Leaf)) {
    throw "QA APK not found: $resolvedApk. Run :app:assembleQa first."
}

$metadataPath = Join-Path (Split-Path -Parent $resolvedApk) "output-metadata.json"
if (Test-Path -LiteralPath $metadataPath) {
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ([string]$metadata.applicationId -ne $targetApplicationId) {
        throw "REFUSED: APK metadata applicationId=$($metadata.applicationId), expected $targetApplicationId."
    }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path (Join-Path $projectRoot $OutputRoot) "markdown-scroll-$timestamp"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$remoteVideo = "/sdcard/m1racle-reader-qa-markdown-scroll.mp4"
$installedQa = $false
$screenRecord = $null

try {
    Write-Host "MARKDOWN_BENCHMARK_TARGET applicationId=$targetApplicationId"
    $install = Adb (@("install", "-r", $resolvedApk))
    if ($install -notmatch "Success") { throw "QA install failed: $install" }
    $installedQa = $true
    if ((Adb @("shell", "pm", "path", $protectedApplicationId)) -match "package:") {
        Write-Host "MAIN_PACKAGE_SAFETY applicationId=$protectedApplicationId remainsInstalled=true"
    }

    Adb @("logcat", "-c") | Out-Null
    Adb @("shell", "run-as", $targetApplicationId, "rm", "-f", "files/markdown-anchor-events.jsonl") | Out-Null
    Adb @("shell", "am", "force-stop", $targetApplicationId) | Out-Null
    Adb @("shell", "am", "start", "-W", "-n", $activity) | Out-Null
    Start-Sleep -Seconds 2

    $sizeText = Adb @("shell", "wm", "size")
    if ($sizeText -notmatch "(\d+)x(\d+)") { throw "Unable to read display size: $sizeText" }
    $width = [int]$Matches[1]
    $height = [int]$Matches[2]
    $x = [int]($width / 2)
    $top = [int]($height * 0.24)
    $bottom = [int]($height * 0.82)

    function Get-UiHierarchy {
        Adb @("shell", "uiautomator", "dump", "/sdcard/m1racle-reader-window.xml") | Out-Null
        return (Adb @("shell", "cat", "/sdcard/m1racle-reader-window.xml"))
    }
    function Start-BenchmarkAtTop {
        Adb @("shell", "am", "force-stop", $targetApplicationId) | Out-Null
        Adb @("shell", "am", "start", "-W", "-n", $activity) | Out-Null
        Start-Sleep -Milliseconds 700
    }
    function Tap-Link([string]$Label) {
        [xml]$tree = Get-UiHierarchy
        $node = $tree.SelectSingleNode("//node[@text='$Label']")
        if (-not $node) { throw "TOC link not found in QA hierarchy: $Label" }
        if ([string]$node.bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
            throw "Invalid bounds for TOC link '$Label': $($node.bounds)"
        }
        # Click inside the rendered link glyphs; the semantics bounds span the full paragraph width.
        $tapX = [int]([math]::Min([int]$Matches[1] + 60, [int]$Matches[3] - 1))
        $tapY = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        Adb @("shell", "input", "tap", "$tapX", "$tapY") | Out-Null
        Start-Sleep -Milliseconds 700
    }
    function Assert-HeadingVisible([string]$Heading) {
        [xml]$tree = Get-UiHierarchy
        $node = $tree.SelectSingleNode("//node[@text='$Heading']")
        if (-not $node) { throw "Anchor target heading is not visible: $Heading" }
    }

    function Unicode-Text([int[]]$CodePoints) {
        return -join ($CodePoints | ForEach-Object { [char]$_ })
    }
    $firstLabel = Unicode-Text @(0x7B2C, 0x4E00, 0x8282)
    $middleLabel = Unicode-Text @(0x4E2D, 0x95F4, 0x7AE0, 0x8282)
    $lastLabel = Unicode-Text @(0x6700, 0x540E, 0x91CD, 0x590D, 0x7AE0, 0x8282)
    $missingLabel = Unicode-Text @(0x4E0D, 0x5B58, 0x5728, 0x7684, 0x7AE0, 0x8282)
    $longHeading = Unicode-Text @(0x957F, 0x6587, 0x6863, 0x7AE0, 0x8282)
    $duplicateHeading = Unicode-Text @(0x91CD, 0x590D, 0x7AE0, 0x8282)
    $missingAnchor = Unicode-Text @(0x4E0D, 0x5B58, 0x5728)
    $tocResults = @()
    foreach ($case in @(
        @{ label = $firstLabel; target = "$longHeading 1"; anchor = "$longHeading-1" },
        @{ label = $middleLabel; target = "$longHeading 8"; anchor = "$longHeading-8" },
        @{ label = $lastLabel; target = $duplicateHeading; anchor = "$duplicateHeading-3" }
    )) {
        Start-BenchmarkAtTop
        Tap-Link $case.label
        Assert-HeadingVisible $case.target
        $tocResults += [ordered]@{ label = $case.label; anchor = $case.anchor; result = "PASS" }
    }
    Start-BenchmarkAtTop
    Tap-Link $missingLabel
    $pidText = Adb @("shell", "pidof", $targetApplicationId)
    if (-not $pidText.Trim()) { throw "Missing-anchor activation crashed the QA process." }
    $tocResults += [ordered]@{ label = $missingLabel; anchor = $missingAnchor; result = "PASS_NO_CRASH" }
    $tocResults | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDir "toc-anchor-results.json") -Encoding utf8
    $anchorEventsPath = Join-Path $outputDir "anchor-events.jsonl"
    [System.IO.File]::WriteAllText($anchorEventsPath, (Adb @("exec-out", "run-as", $targetApplicationId, "cat", "files/markdown-anchor-events.jsonl")))
    $anchorEvents = Get-Content -LiteralPath $anchorEventsPath | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json }
    foreach ($expectedAnchor in @("$longHeading-1", "$longHeading-8", "$duplicateHeading-3", $missingAnchor)) {
        $expectedBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($expectedAnchor))
        if (-not ($anchorEvents | Where-Object { $_.anchorBase64 -eq $expectedBase64 })) {
            throw "Anchor activation event was not recorded: $expectedAnchor"
        }
    }
    $missingBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($missingAnchor))
    if (($anchorEvents | Where-Object { $_.anchorBase64 -eq $missingBase64 }).blockIndex -ne $null) {
        throw "Missing anchor unexpectedly resolved to a block."
    }
    Start-BenchmarkAtTop

    # Warm every lazy block/table once before the measured round trips.
    1..32 | ForEach-Object { Adb @("shell", "input", "swipe", "$x", "$bottom", "$x", "$top", "90") | Out-Null }
    1..32 | ForEach-Object { Adb @("shell", "input", "swipe", "$x", "$top", "$x", "$bottom", "90") | Out-Null }
    Start-Sleep -Seconds 1

    # Preserve first-layout/warm-up cost separately; measured deltas start after this snapshot.
    Adb @("shell", "am", "start", "-f", "0x20000000", "-n", $activity, "--es", "benchmark_command", "stop") | Out-Null
    Start-Sleep -Milliseconds 300
    $warmupCountersPath = Join-Path $outputDir "warmup-counters.json"
    [System.IO.File]::WriteAllText($warmupCountersPath, (Adb @("exec-out", "run-as", $targetApplicationId, "cat", "files/markdown-scroll-counters.json")))

    Adb @("shell", "dumpsys", "gfxinfo", $targetApplicationId, "reset") | Out-Null
    Adb @("shell", "am", "start", "-f", "0x20000000", "-n", $activity, "--es", "benchmark_command", "reset") | Out-Null
    Adb @("shell", "rm", "-f", $remoteVideo) | Out-Null
    $screenArgs = @($adbPrefix + @("shell", "screenrecord", "--bit-rate", "4000000", "--time-limit", "120", $remoteVideo))
    $screenRecord = Start-Process -FilePath $adb -ArgumentList $screenArgs -PassThru -WindowStyle Hidden

    1..5 | ForEach-Object {
        Adb @("shell", "input", "swipe", "$x", "$bottom", "$x", "$top", "280") | Out-Null
        Start-Sleep -Milliseconds 180
    }
    1..5 | ForEach-Object {
        Adb @("shell", "input", "swipe", "$x", "$top", "$x", "$bottom", "280") | Out-Null
        Start-Sleep -Milliseconds 180
    }
    Start-Sleep -Seconds 1
    Adb @("shell", "am", "start", "-f", "0x20000000", "-n", $activity, "--es", "benchmark_command", "stop") | Out-Null
    Start-Sleep -Milliseconds 500
    Adb @("shell", "pkill", "-INT", "screenrecord") | Out-Null
    if ($screenRecord) { $screenRecord.WaitForExit(5000) | Out-Null }

    $framesPath = Join-Path $outputDir "jankstats.jsonl"
    $countersPath = Join-Path $outputDir "counters.json"
    $gfxPath = Join-Path $outputDir "gfxinfo-framestats.txt"
    $logPath = Join-Path $outputDir "logcat.txt"
    [System.IO.File]::WriteAllText($framesPath, (Adb @("exec-out", "run-as", $targetApplicationId, "cat", "files/markdown-scroll-frames.jsonl")))
    [System.IO.File]::WriteAllText($countersPath, (Adb @("exec-out", "run-as", $targetApplicationId, "cat", "files/markdown-scroll-counters.json")))
    [System.IO.File]::WriteAllText($gfxPath, (Adb @("shell", "dumpsys", "gfxinfo", $targetApplicationId, "framestats")))
    [System.IO.File]::WriteAllText($logPath, (Adb @("logcat", "-d", "-s", "MARKDOWN_BENCHMARK:I", "MARKDOWN_TOC:W", "*:S")))
    Adb @("pull", $remoteVideo, (Join-Path $outputDir "scroll.mp4")) | Out-Null

    $counterData = Get-Content -LiteralPath $countersPath -Raw | ConvertFrom-Json
    $warmupData = Get-Content -LiteralPath $warmupCountersPath -Raw | ConvertFrom-Json
    $frameRows = Get-Content -LiteralPath $framesPath | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json }
    $durations = @($frameRows | ForEach-Object { [double]$_.frameDurationMs } | Sort-Object)
    if ($durations.Count -eq 0) { throw "No frame telemetry was captured." }
    function Percentile([double[]]$Values, [double]$P) {
        $index = [math]::Ceiling($P * $Values.Count) - 1
        return $Values[[math]::Max(0, [math]::Min($Values.Count - 1, $index))]
    }
    $budgetMs = 1000.0 / [double]$counterData.refreshRate
    $jankCount = @($durations | Where-Object { $_ -gt $budgetMs }).Count
    $summary = [ordered]@{
        applicationId = $targetApplicationId
        refreshRate = [double]$counterData.refreshRate
        frameBudgetMs = [math]::Round($budgetMs, 3)
        frames = $durations.Count
        p50Ms = Percentile $durations 0.50
        p95Ms = Percentile $durations 0.95
        jankRatePercent = [math]::Round(100.0 * $jankCount / $durations.Count, 2)
        severeStallOver100Ms = @($durations | Where-Object { $_ -gt 100 }).Count
        frozenFrameOver700Ms = @($durations | Where-Object { $_ -gt 700 }).Count
        parseCountDelta = [long]$counterData.parseCount
        tableSolveCountDelta = [long]$counterData.tableSolveCount
        cellMeasureCountDelta = [long]$counterData.cellMeasureCount
        cacheHitCountDelta = [long]$counterData.cacheHitCount
        cacheMissCountDelta = [long]$counterData.cacheMissCount
        warmupParseCount = [long]$warmupData.parseCount
        warmupTableSolveCount = [long]$warmupData.tableSolveCount
        warmupCellMeasureCount = [long]$warmupData.cellMeasureCount
    }
    $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDir "summary.json") -Encoding utf8
    $summary | Format-List
    Write-Host "MARKDOWN_BENCHMARK_OUTPUT $outputDir"
} finally {
    if ($screenRecord -and -not $screenRecord.HasExited) {
        try { Adb @("shell", "pkill", "-INT", "screenrecord") | Out-Null } catch {}
    }
    if ($installedQa) {
        if ($targetApplicationId -eq $protectedApplicationId) { throw "Internal guard failure: refusing main package uninstall." }
        try { Adb @("uninstall", $targetApplicationId) | Out-Null } catch { Write-Warning $_ }
    }
}
