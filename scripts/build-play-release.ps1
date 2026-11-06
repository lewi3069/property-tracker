param([string]$BuildOutputRoot = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingRoot = Join-Path $projectRoot '.tools\signing'
$keyPath = Join-Path $signingRoot 'play-upload.p12'
$passwordPath = Join-Path $signingRoot 'play-upload-password.dpapi'
if (!(Test-Path -LiteralPath $keyPath) -or !(Test-Path -LiteralPath $passwordPath)) {
    throw 'Run scripts/initialize-play-signing.ps1 once to create the private upload key.'
}
if (!$BuildOutputRoot) { $BuildOutputRoot = $env:PROPERTY_TRACKER_BUILD_ROOT }
$previous = @{}
foreach ($name in @('PROPERTY_TRACKER_PLAY_KEYSTORE','PROPERTY_TRACKER_PLAY_STORE_PASSWORD','PROPERTY_TRACKER_PLAY_KEY_PASSWORD')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$secret = $null; $pointer = [IntPtr]::Zero
try {
    $secret = ConvertTo-SecureString ([System.IO.File]::ReadAllText($passwordPath))
    $pointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $password = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:PROPERTY_TRACKER_PLAY_KEYSTORE = $keyPath
    $env:PROPERTY_TRACKER_PLAY_STORE_PASSWORD = $password
    $env:PROPERTY_TRACKER_PLAY_KEY_PASSWORD = $password
    & (Join-Path $PSScriptRoot 'build-android.ps1') -Tasks @(':mobile:bundleRelease', ':mobile:lintRelease', ':mobile:validatePlayBundle', ':mobile:dumpPlayManifest') -BuildOutputRoot $BuildOutputRoot
    if ($BuildOutputRoot) { $mobileBuild = Join-Path ([System.IO.Path]::GetFullPath($BuildOutputRoot)) 'mobile' }
    else { $mobileBuild = Join-Path $projectRoot 'mobile\build' }
    $bundle = Join-Path $mobileBuild 'outputs\bundle\release\mobile-release.aab'
    [xml]$manifest = [System.IO.File]::ReadAllText((Join-Path $mobileBuild 'reports\play\manifest.xml'))
    $androidNamespace = 'http://schemas.android.com/apk/res/android'
    $releasePackage = $manifest.manifest.package
    $releaseTarget = [int]$manifest.manifest.'uses-sdk'.GetAttribute('targetSdkVersion', $androidNamespace)
    $releaseDebuggable = $manifest.manifest.application.GetAttribute('debuggable', $androidNamespace)
    if ($releasePackage -ne 'com.lewi3069.propertytracker' -or $releaseTarget -lt 36 -or $releaseDebuggable -eq 'true') {
        throw 'The Play bundle has the wrong application ID, target API, or debug flag.'
    }
    $verify = Join-Path $signingRoot 'last-bundle-signature.log'
    & (Join-Path $projectRoot '.tools\jdk\bin\jarsigner.exe') '-J-Duser.language=en' -verify -verbose -certs $bundle *> $verify
    if ($LASTEXITCODE -ne 0 -or !([System.IO.File]::ReadAllText($verify).Contains('jar verified.'))) {
        throw 'Release bundle signature validation failed.'
    }
    $rootGradle = [System.IO.File]::ReadAllText((Join-Path $projectRoot 'build.gradle'))
    $version = [regex]::Match($rootGradle, "propertyTrackerVersion\s*=\s*'([^']+)'").Groups[1].Value
    if (!$version) { throw 'Could not determine the release version.' }
    $destination = Join-Path $projectRoot "dist\property-tracker-phone-$version-release.aab"
    Copy-Item -LiteralPath $bundle -Destination $destination
    Write-Output "Signed Play bundle: $destination"
    Write-Output "SHA256: $((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant())"
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    if ($secret) { $secret.Dispose() }
    $password = $null
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
