param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingRoot = Join-Path $projectRoot '.tools\signing'
$keyPath = Join-Path $signingRoot 'play-upload.p12'
$passwordPath = Join-Path $signingRoot 'play-upload-password.dpapi'
if ((Test-Path -LiteralPath $keyPath) -xor (Test-Path -LiteralPath $passwordPath)) {
    throw 'Incomplete signing setup. Recover the existing matching key/password files; neither will be overwritten.'
}
if (Test-Path -LiteralPath $keyPath) { Write-Output 'Existing Play upload key retained.'; return }
New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
$signingOwner = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $signingRoot /inheritance:r /grant:r "${signingOwner}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the private signing directory.' }
$randomBytes = New-Object byte[] 32
$randomSource = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $randomSource.GetBytes($randomBytes) } finally { $randomSource.Dispose() }
$password = [Convert]::ToBase64String($randomBytes)
[Array]::Clear($randomBytes, 0, $randomBytes.Length)
$securePassword = ConvertTo-SecureString $password -AsPlainText -Force
$protectedPassword = ConvertFrom-SecureString $securePassword
[System.IO.File]::WriteAllText($passwordPath, $protectedPassword)
$previous = $env:PROPERTY_TRACKER_UPLOAD_KEY_PASSWORD
try {
    $env:PROPERTY_TRACKER_UPLOAD_KEY_PASSWORD = $password
    $keytool = Join-Path $projectRoot '.tools\jdk\bin\keytool.exe'
    & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $keyPath -alias property-tracker-upload `
        -storepass:env PROPERTY_TRACKER_UPLOAD_KEY_PASSWORD -keypass:env PROPERTY_TRACKER_UPLOAD_KEY_PASSWORD `
        -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Property Tracker Upload,OU=Android Release' *> (Join-Path $signingRoot 'key-generation.log')
    if ($LASTEXITCODE -ne 0) { throw 'Play upload key generation failed; see the private signing log.' }
    & $keytool -exportcert -rfc -keystore $keyPath -alias property-tracker-upload `
        -storepass:env PROPERTY_TRACKER_UPLOAD_KEY_PASSWORD -file (Join-Path $signingRoot 'play-upload-certificate.pem') *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Upload certificate export failed.' }
    Write-Output 'Created a private Play upload key. Its password is protected by this Windows account using DPAPI.'
} finally {
    $env:PROPERTY_TRACKER_UPLOAD_KEY_PASSWORD = $previous
    $password = $null; $securePassword.Dispose()
}
