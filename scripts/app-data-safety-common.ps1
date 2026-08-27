$script:ProtectedMainApplicationId = "com.m1racle.reader"
$script:QaApplicationId = "com.m1racle.reader.qa"

function Resolve-AdbPath {
    param([string]$AdbPath)
    if ($AdbPath) {
        if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw "adb not found: $AdbPath" }
        return (Resolve-Path -LiteralPath $AdbPath).Path
    }
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($root) {
            $candidate = Join-Path $root "platform-tools\adb.exe"
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    $localProperties = Join-Path (Split-Path -Parent $PSScriptRoot) "local.properties"
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $sdkRoot = $sdkLine.Substring("sdk.dir=".Length).Replace('\:', ':').Replace('\\', '\')
            $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    throw "adb was not found. Pass -AdbPath or configure ANDROID_HOME/ANDROID_SDK_ROOT."
}

function Invoke-AdbText {
    param([string]$Adb, [string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $Adb @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { throw "adb failed ($exitCode): adb $($Arguments -join ' ')`n$output" }
    return ($output -join "`n")
}

function Assert-SafePackage {
    param([string]$PackageName)
    if ($PackageName -notin @($script:ProtectedMainApplicationId, $script:QaApplicationId)) {
        throw "Unsupported package/applicationId: $PackageName"
    }
}

function Get-PackageEvidence {
    param([string]$Adb, [string]$PackageName)
    Assert-SafePackage $PackageName
    $path = Invoke-AdbText $Adb @("shell", "pm", "path", $PackageName)
    if ($path -notmatch "package:") { throw "Package is not installed: $PackageName" }
    $dump = Invoke-AdbText $Adb @("shell", "dumpsys", "package", $PackageName)
    if ($dump -notmatch "DEBUGGABLE") { throw "Package is not debuggable; run-as backup/restore refused: $PackageName" }
    $null = Invoke-AdbText $Adb @("shell", "run-as", $PackageName, "id")
    $versionName = if ($dump -match "versionName=([^\s]+)") { $Matches[1] } else { "unknown" }
    $versionCode = if ($dump -match "versionCode=(\d+)") { $Matches[1] } else { "unknown" }
    return [ordered]@{
        applicationId = $PackageName
        versionName = $versionName
        versionCode = $versionCode
        packagePath = ($path -split "`n" | Select-Object -First 1).Replace("package:", "")
    }
}

function Receive-AdbBinary {
    param([string]$Adb, [string[]]$Arguments, [string]$Destination)
    $quoted = $Arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Adb
    $startInfo.Arguments = $quoted -join " "
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $file = [System.IO.File]::Open($Destination, [System.IO.FileMode]::CreateNew)
    try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
    $errorText = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        Remove-Item -LiteralPath $Destination -Force -ErrorAction SilentlyContinue
        throw "adb binary transfer failed ($($process.ExitCode)): $errorText"
    }
}

function Get-PayloadManifest {
    param([string]$PayloadRoot)
    return @(Get-ChildItem -LiteralPath $PayloadRoot -File -Recurse | Sort-Object FullName | ForEach-Object {
        [ordered]@{
            path = $_.FullName.Substring($PayloadRoot.Length).TrimStart('\').Replace('\', '/')
            bytes = $_.Length
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        }
    })
}

function Assert-PayloadManifest {
    param([string]$PayloadRoot, [object[]]$Files)
    if (-not $Files -or $Files.Count -eq 0) { throw "Backup manifest contains no files." }
    foreach ($entry in $Files) {
        $path = Join-Path $PayloadRoot ([string]$entry.path).Replace('/', '\')
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Backup file missing: $($entry.path)" }
        $file = Get-Item -LiteralPath $path
        if ($file.Length -ne [long]$entry.bytes) { throw "Size mismatch: $($entry.path)" }
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        if ($hash -ne [string]$entry.sha256) { throw "SHA-256 mismatch: $($entry.path)" }
    }
}
