$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
$jdkRoot = Join-Path $toolRoot 'jdk21'
if (!(Test-Path -LiteralPath (Join-Path $jdkRoot 'bin\java.exe'))) {
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $assets[0].binary.package
    $archive = Join-Path $toolRoot 'jdk21.zip'
    if (!(Test-Path -LiteralPath $archive)) {
        & curl.exe --fail --location --silent --show-error --retry 2 --output $archive $package.link
        if ($LASTEXITCODE -ne 0) { throw 'Could not download the official JDK21 archive.' }
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.checksum) { throw 'JDK21 checksum mismatch.' }
    $unpack = Join-Path $toolRoot 'jdk21-unpack'
    Expand-Archive -LiteralPath $archive -DestinationPath $unpack -Force
    $directories = @(Get-ChildItem -LiteralPath $unpack -Directory)
    if ($directories.Count -ne 1 -or $directories[0].Parent.FullName -ne $unpack) { throw 'Unexpected JDK21 archive layout.' }
    if (Test-Path -LiteralPath $jdkRoot) { throw 'Incomplete JDK21 directory exists; it will not be overwritten.' }
    Move-Item -LiteralPath $directories[0].FullName -Destination $jdkRoot
}
$env:JAVA_HOME = $jdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$sdkRoot = Join-Path $toolRoot 'android-sdk'
& (Join-Path $sdkRoot 'cmdline-tools\bin\sdkmanager.bat') "--sdk_root=$sdkRoot" 'platforms;android-36' 'build-tools;36.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android16 SDK setup failed.' }
Write-Output 'Android16 SDK and Java21 test runtime are ready.'
