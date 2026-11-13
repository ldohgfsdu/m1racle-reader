# Freeze and verify the actual working-tree bytes used to compile Reader.
# The Git HEAD name is recorded only as context; it is never the baseline.
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('snapshot', 'verify')]
    [string]$Mode,
    [string]$EvidenceDirectory,
    [string]$SnapshotLabel = 'source-tested'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'reader-script-contracts.ps1')
$readerRepositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

function ConvertTo-ReaderRepositoryPath([string]$FullPath) {
    $readerRelative = [IO.Path]::GetRelativePath($readerRepositoryRoot, $FullPath).Replace('\', '/')
    if ([IO.Path]::IsPathRooted($readerRelative) -or $readerRelative.StartsWith('../') -or $readerRelative -eq '..') {
        throw "Path escapes repository: $FullPath"
    }
    return $readerRelative
}

function Test-ReaderCompiledInput([string]$RelativePath) {
    return Test-ReaderSourceBaselineInput $RelativePath
}

function Get-ReaderCompiledInputs {
    $readerProcess = [Diagnostics.Process]::new()
    $readerProcess.StartInfo.FileName = 'git'
    $readerProcess.StartInfo.ArgumentList.Add('-C'); $readerProcess.StartInfo.ArgumentList.Add($readerRepositoryRoot); foreach ($readerArgument in @('-c','core.quotepath=false','-c','core.outputencoding=utf-8','ls-files','--cached','--others','--exclude-standard','-z')) { $readerProcess.StartInfo.ArgumentList.Add($readerArgument) }
    $readerProcess.StartInfo.RedirectStandardOutput = $true
    $readerProcess.StartInfo.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $readerProcess.StartInfo.UseShellExecute = $false
    $readerProcess.StartInfo.CreateNoWindow = $true
    if (-not $readerProcess.Start()) { throw 'Cannot start Git.' }
    $readerOutput = $readerProcess.StandardOutput.ReadToEnd()
    $readerProcess.WaitForExit()
    if ($readerProcess.ExitCode -ne 0) { throw 'Cannot list the working tree through Git ignore rules.' }
    $readerFiles = [Collections.Generic.List[string]]::new()
    foreach ($readerRawPath in @($readerOutput.Split([char]0))) {
        if ([string]::IsNullOrWhiteSpace($readerRawPath)) { continue }
        $readerRelative = $readerRawPath.Replace('\', '/')
        if (-not (Test-ReaderCompiledInput $readerRelative)) { continue }
        $readerFullPath = Join-Path $readerRepositoryRoot ($readerRelative.Replace('/', [IO.Path]::DirectorySeparatorChar))
        if (-not (Test-Path -LiteralPath $readerFullPath -PathType Leaf)) { continue }
        $readerFiles.Add($readerRelative)
    }
    return @($readerFiles | Sort-Object -Unique)
}

function Get-ReaderInputHashes([string[]]$RelativePaths) {
    $readerHashes = [ordered]@{}
    foreach ($readerRelative in $RelativePaths) {
        $readerFullPath = Join-Path $readerRepositoryRoot ($readerRelative -replace '/', [IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path -LiteralPath $readerFullPath -PathType Leaf)) {
            throw "Compiled input missing: $readerRelative"
        }
        $readerHashes[$readerRelative] = (Get-FileHash -LiteralPath $readerFullPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return $readerHashes
}

function Get-ReaderHead {
    $readerHead = git -C $readerRepositoryRoot rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($readerHead)) { throw 'Cannot read Git HEAD.' }
    return $readerHead.Trim()
}

$readerCompiledInputs = @(Get-ReaderCompiledInputs)
if ($readerCompiledInputs.Count -eq 0) { throw 'No compiled inputs found.' }
if (-not ($readerCompiledInputs -contains 'core/model/build.gradle.kts')) {
    throw 'The core model module is missing from the compiled-input baseline.'
}
if (@($readerCompiledInputs | Where-Object { $_ -like 'core/*' -and $_ -notlike '*/build/*' }).Count -eq 0) {
    throw 'No core source inputs were included.'
}

if ($Mode -eq 'snapshot') {
    if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) { throw 'EvidenceDirectory is required for snapshot mode.' }
    if ($SnapshotLabel -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$' -or $SnapshotLabel.Contains('..')) {
        throw 'SnapshotLabel must be a simple file name without path separators.'
    }
    $readerEvidence = if ([IO.Path]::IsPathRooted($EvidenceDirectory)) {
        [IO.Path]::GetFullPath($EvidenceDirectory)
    } else {
        [IO.Path]::GetFullPath((Join-Path $readerRepositoryRoot $EvidenceDirectory))
    }
    $readerEvidenceRelative = ConvertTo-ReaderRepositoryPath $readerEvidence
    if ($readerEvidenceRelative -notlike 'output/*' -and $readerEvidenceRelative -notlike '.external_outbox/*') {
        throw 'Snapshots must be written under output or .external_outbox.'
    }
    New-Item -ItemType Directory -Path $readerEvidence -Force | Out-Null
    $readerHashes = Get-ReaderInputHashes $readerCompiledInputs
    $readerManifest = [ordered]@{
        baseline = 'working-tree-bytes'
        headContextOnly = Get-ReaderHead
        compiledInputs = $readerHashes
    }
    $readerManifestPath = Join-Path $readerEvidence "$SnapshotLabel-sha256.json"
    $readerZipPath = Join-Path $readerEvidence "$SnapshotLabel.zip"
    $readerManifestJson = $readerManifest | ConvertTo-Json -Depth 5
    [IO.File]::WriteAllText($readerManifestPath, $readerManifestJson + "`n", [Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $readerZipPath) { Remove-Item -LiteralPath $readerZipPath -Force }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $readerZip = [IO.Compression.ZipFile]::Open($readerZipPath, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($readerRelative in $readerCompiledInputs) {
            $readerFullPath = Join-Path $readerRepositoryRoot ($readerRelative -replace '/', [IO.Path]::DirectorySeparatorChar)
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $readerZip,
                $readerFullPath,
                $readerRelative,
                [IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    } finally {
        $readerZip.Dispose()
    }
    [ordered]@{
        mode = 'snapshot'
        compiledInputs = $readerCompiledInputs.Count
        coreInputs = @($readerCompiledInputs | Where-Object { $_ -like 'core/*' }).Count
        manifest = $readerManifestPath
        archive = $readerZipPath
        baseline = 'working-tree-bytes'
        verificationStatus = 'pending'
    } | ConvertTo-Json -Depth 4
    return
}

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) { throw 'EvidenceDirectory is required for verify mode.' }
if ($SnapshotLabel -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$' -or $SnapshotLabel.Contains('..')) {
    throw 'SnapshotLabel must be a simple file name without path separators.'
}
$readerEvidence = if ([IO.Path]::IsPathRooted($EvidenceDirectory)) {
    [IO.Path]::GetFullPath($EvidenceDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $readerRepositoryRoot $EvidenceDirectory))
}
$readerManifestPath = Join-Path $readerEvidence "$SnapshotLabel-sha256.json"
if (-not (Test-Path -LiteralPath $readerManifestPath -PathType Leaf)) {
    throw "Frozen manifest missing: $readerManifestPath"
}
$readerFrozen = Get-Content -LiteralPath $readerManifestPath -Raw | ConvertFrom-Json
$readerFrozenInputs = [ordered]@{}
foreach ($readerProperty in $readerFrozen.compiledInputs.PSObject.Properties) {
    $readerFrozenInputs[$readerProperty.Name] = [string]$readerProperty.Value
}
$readerCurrent = Get-ReaderInputHashes $readerCompiledInputs
$readerMissing = @($readerFrozenInputs.Keys | Where-Object { -not $readerCurrent.Contains($_) })
$readerAdded = @($readerCurrent.Keys | Where-Object { -not $readerFrozenInputs.Contains($_) })
$readerChanged = @($readerFrozenInputs.Keys | Where-Object {
    $readerCurrent.Contains($_) -and $readerCurrent[$_] -ne $readerFrozenInputs[$_]
})
$readerResult = [ordered]@{
    mode = 'verify'
    compiledInputs = $readerCompiledInputs.Count
    coreInputs = @($readerCompiledInputs | Where-Object { $_ -like 'core/*' }).Count
    unchanged = ($readerMissing.Count -eq 0 -and $readerAdded.Count -eq 0 -and $readerChanged.Count -eq 0)
    missing = $readerMissing
    added = $readerAdded
    changed = $readerChanged
    baseline = 'working-tree-bytes'
    headContextOnly = Get-ReaderHead
    verificationStatus = 'source-manifest-only'
}
$readerResult | ConvertTo-Json -Depth 5
if (-not $readerResult.unchanged) { exit 2 }
