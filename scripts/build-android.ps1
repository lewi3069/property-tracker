param([string[]]$Tasks = @('testDebugUnitTest', 'assembleDebug', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-cache'
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-user'
$gradle = Join-Path $projectRoot '.tools\gradle-8.11.1\bin\gradle.bat'
if (!(Test-Path -LiteralPath $gradle)) { throw 'Run scripts/bootstrap-android.ps1 first, or open this project in Android Studio.' }
# OneDrive can mark generated directories read-only after syncing them. Gradle's
# Java cleanup cannot replace those directories until the attribute is cleared.
# Restrict this normalization to the app's disposable build output.
$appBuildPath = Join-Path $projectRoot 'app\build'
if (Test-Path -LiteralPath $appBuildPath) {
    $generatedEntries = @((Get-Item -LiteralPath $appBuildPath)) + @(Get-ChildItem -LiteralPath $appBuildPath -Recurse -Force)
    foreach ($generatedEntry in $generatedEntries) {
        if ($generatedEntry.Attributes -band [System.IO.FileAttributes]::ReadOnly) {
            $generatedEntry.Attributes = $generatedEntry.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly)
        }
    }
}
Push-Location $projectRoot
try {
    & $gradle --no-daemon --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
} finally { Pop-Location }
