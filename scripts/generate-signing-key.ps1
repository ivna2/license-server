# Создаёт хранилище PKCS12 с ключом ЭЦП (RSA 3072) и сертификатом для подписи тикетов лицензий.
# В сертификате указан номер студенческого билета.
# Использование (из корня проекта):
#   powershell -ExecutionPolicy Bypass -File scripts\generate-signing-key.ps1 -StudentId <номер>
# Пароль хранилища берётся из переменной SIGNING_KEYSTORE_PASSWORD или запрашивается.
# Результат в каталоге certs\ (в .gitignore): signing.p12 для сервера, signing.crt для клиентов.
param(
    [Parameter(Mandatory = $true)][string]$StudentId,
    [string]$CertDir = "certs"
)

$keytool = "keytool"
if ($env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe" }

$P = $env:SIGNING_KEYSTORE_PASSWORD
if (-not $P) {
    $secure = Read-Host "Пароль для хранилища ключа подписи (не короче 6 символов)" -AsSecureString
    $P = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

New-Item -ItemType Directory -Force -Path $CertDir | Out-Null
Push-Location $CertDir
try {
    Remove-Item -ErrorAction SilentlyContinue signing.p12, signing.crt

    & $keytool -genkeypair -alias license-signing -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 730 `
        -dname "CN=LicenseServer Ticket Signing $StudentId, OU=Student $StudentId, O=LicenseServer Lab, C=RU" `
        -ext "ku:c=digitalSignature,nonRepudiation" `
        -storetype PKCS12 -keystore signing.p12 -storepass $P
    if ($LASTEXITCODE -ne 0) { throw "keytool -genkeypair завершился с ошибкой" }
    & $keytool -exportcert -rfc -alias license-signing -keystore signing.p12 -storetype PKCS12 -storepass $P -file signing.crt
    if ($LASTEXITCODE -ne 0) { throw "keytool -exportcert завершился с ошибкой" }

    Write-Host "Готово: $(Join-Path (Get-Location) 'signing.p12') (ключ подписи), $(Join-Path (Get-Location) 'signing.crt') (сертификат для проверки)"
    & $keytool -list -v -storetype PKCS12 -storepass $P -keystore signing.p12 -alias license-signing |
        Select-String "Owner:|Signature algorithm|Valid"
}
finally {
    Pop-Location
}
