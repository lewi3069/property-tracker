$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null

function Get-Archive($Uri, $Destination) {
    if (!(Test-Path -LiteralPath $Destination)) {
        & curl.exe --fail --location --silent --show-error --retry 2 --output $Destination $Uri
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $Uri" }
    }
}

if (!(Test-Path -LiteralPath (Join-Path $toolRoot 'jdk'))) {
    $jdkAssets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $jdkPackage = $jdkAssets[0].binary.package
    $jdkZip = Join-Path $toolRoot 'jdk.zip'
    Get-Archive $jdkPackage.link $jdkZip
    if ((Get-FileHash -LiteralPath $jdkZip -Algorithm SHA256).Hash.ToLower() -ne $jdkPackage.checksum) { throw 'JDK checksum mismatch' }
    Expand-Archive -LiteralPath $jdkZip -DestinationPath (Join-Path $toolRoot 'jdk-unpack') -Force
    $jdkDirectory = Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk-unpack') -Directory | Select-Object -First 1
    # Only move this verified direct child of the task-owned extraction directory.
    if ($jdkDirectory.Parent.FullName -ne (Join-Path $toolRoot 'jdk-unpack')) { throw 'Unexpected JDK extraction path' }
    Move-Item -LiteralPath $jdkDirectory.FullName -Destination (Join-Path $toolRoot 'jdk')
}
Write-Output 'Local JDK ready.'

$gradleZip = Join-Path $toolRoot 'gradle.zip'
if (!(Test-Path -LiteralPath (Join-Path $toolRoot 'gradle-8.11.1'))) {
    Get-Archive 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' $gradleZip
    $expectedGradleHash = (Invoke-RestMethod 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256').Trim()
    if ((Get-FileHash -LiteralPath $gradleZip -Algorithm SHA256).Hash.ToLower() -ne $expectedGradleHash) { throw 'Gradle checksum mismatch' }
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $toolRoot -Force
}
Write-Output 'Local Gradle ready.'

$sdkRoot = Join-Path $toolRoot 'android-sdk'
if (!(Test-Path -LiteralPath (Join-Path $sdkRoot 'cmdline-tools\bin\sdkmanager.bat'))) {
    $sdkZip = Join-Path $toolRoot 'android-cli.zip'
    Get-Archive 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' $sdkZip
    Expand-Archive -LiteralPath $sdkZip -DestinationPath $sdkRoot -Force
}
$env:JAVA_HOME = Join-Path $toolRoot 'jdk'
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\bin\sdkmanager.bat'
# SDK package licenses are accepted for this task-local Android build toolchain.
1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses | Out-Null
& $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-35' 'platforms;android-36' 'build-tools;35.0.0' 'build-tools;36.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK setup failed' }

$debugKey = Join-Path $toolRoot 'debug.keystore'
if (!(Test-Path -LiteralPath $debugKey)) {
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $debugKey -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Fieldview Debug,O=Local Development,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'Debug signing key generation failed' }
}
$sdkProperty = $sdkRoot.Replace('\', '/').Replace(':', '\:')
Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Value "sdk.dir=$sdkProperty"
Write-Output 'Android toolchain ready. Run scripts/build-android.ps1.'
