[CmdletBinding()]
param(
    [string]$GradleTask = ":app:connectedQaAndroidTest"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$mainApplicationId = "com.m1racle.reader"
$allowedApplicationId = "com.m1racle.reader.qa"

if ($GradleTask -ne ":app:connectedQaAndroidTest") {
    throw "REFUSED connected test task '$GradleTask'. Only :app:connectedQaAndroidTest is allowed."
}

# 唯一入口合并（2026-10-11 审查 §5.2）：设备准备与 QA 构建统一委托给本机
# helper reader-emulator-local.ps1（起模拟器 → assembleQa → 包名校验 → 安装启动），
# 本脚本只保留受保护的 connected QA 测试执行。
$emulatorHelper = Join-Path $workspaceRoot "reader-emulator-local.ps1"
if (-not (Test-Path -LiteralPath $emulatorHelper -PathType Leaf)) {
    throw "Machine-local helper not found: $emulatorHelper"
}

Push-Location $projectRoot
try {
    & $emulatorHelper
    if (-not $?) { throw "reader-emulator-local.ps1 failed." }

    $metadataPath = Join-Path $projectRoot "app\build\outputs\apk\qa\output-metadata.json"
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
        throw "QA APK metadata was not produced: $metadataPath"
    }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $targetApplicationId = [string]$metadata.applicationId
    Write-Host "ANDROID_TEST_TARGET applicationId=$targetApplicationId"
    if ($targetApplicationId -eq $mainApplicationId) {
        throw "REFUSED: connected test resolved to protected main applicationId=$mainApplicationId."
    }
    if ($targetApplicationId -ne $allowedApplicationId) {
        throw "REFUSED: expected applicationId=$allowedApplicationId, resolved $targetApplicationId."
    }

    & .\gradlew.bat $GradleTask --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "$GradleTask failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
