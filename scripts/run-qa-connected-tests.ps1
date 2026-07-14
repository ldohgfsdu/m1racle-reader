[CmdletBinding()]
param(
    [string]$GradleTask = ":app:connectedQaAndroidTest"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$mainApplicationId = "com.markleaf.app"
$allowedApplicationId = "com.markleaf.app.qa"

if ($GradleTask -ne ":app:connectedQaAndroidTest") {
    throw "REFUSED connected test task '$GradleTask'. Only :app:connectedQaAndroidTest is allowed."
}

Push-Location $projectRoot
try {
    & .\gradlew.bat :app:assembleQa --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "assembleQa failed with exit code $LASTEXITCODE." }

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
