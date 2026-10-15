# Генерирует цепочку из трёх сертификатов: корневой CA -> промежуточный CA -> сертификат сервера.
# Во всех сертификатах указан номер студенческого билета.
# Использование (из корня проекта):
#   powershell -ExecutionPolicy Bypass -File scripts\generate-certs.ps1 -StudentId <номер>
# Пароль хранилищ берётся из переменной CERT_PASSWORD или запрашивается.
# Результат в каталоге certs\ (в .gitignore): server.p12 для Spring Boot, root-ca.crt для доверенных.
param(
    [Parameter(Mandatory = $true)][string]$StudentId,
    [string]$CertDir = "certs"
)

$keytool = "keytool"
if ($env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe" }

$P = $env:CERT_PASSWORD
if (-not $P) {
    $secure = Read-Host "Пароль для хранилищ ключей (не короче 6 символов)" -AsSecureString
    $P = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
$O = "O=LicenseServer Lab, OU=Student $StudentId, C=RU"

New-Item -ItemType Directory -Force -Path $CertDir | Out-Null
Push-Location $CertDir
try {
    Remove-Item -ErrorAction SilentlyContinue ls-root-ca.p12, ls-intermediate-ca.p12, server.p12, *.csr, *.crt

    function kt {
        & $keytool @args -storetype PKCS12 -storepass $P
        if ($LASTEXITCODE -ne 0) { throw "keytool завершился с ошибкой: $args" }
    }

    # 1. Корневой CA (самоподписанный)
    kt -genkeypair -alias ls-root-ca -keyalg RSA -keysize 4096 -validity 3650 `
       -dname "CN=LicenseServer Root CA $StudentId, $O" `
       -ext bc:c=ca:true -ext "ku:c=keyCertSign,cRLSign" -keystore ls-root-ca.p12
    kt -exportcert -rfc -alias ls-root-ca -keystore ls-root-ca.p12 -file root-ca.crt

    # 2. Промежуточный CA, подписан корневым
    kt -genkeypair -alias ls-intermediate-ca -keyalg RSA -keysize 4096 -validity 1825 `
       -dname "CN=LicenseServer Intermediate CA $StudentId, $O" -keystore ls-intermediate-ca.p12
    kt -certreq -alias ls-intermediate-ca -keystore ls-intermediate-ca.p12 -file intermediate-ca.csr
    kt -gencert -alias ls-root-ca -keystore ls-root-ca.p12 -infile intermediate-ca.csr -outfile intermediate-ca.crt `
       -rfc -validity 1825 -ext "bc:c=ca:true,pathlen:0" -ext "ku:c=keyCertSign,cRLSign"
    kt -importcert -noprompt -alias ls-root-ca -file root-ca.crt -keystore ls-intermediate-ca.p12
    kt -importcert -noprompt -alias ls-intermediate-ca -file intermediate-ca.crt -keystore ls-intermediate-ca.p12

    # 3. Сертификат сервера для localhost, подписан промежуточным CA
    kt -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 397 `
       -dname "CN=localhost, $O" -keystore server.p12
    kt -certreq -alias server -keystore server.p12 -file server.csr
    kt -gencert -alias ls-intermediate-ca -keystore ls-intermediate-ca.p12 -infile server.csr -outfile server.crt `
       -rfc -validity 397 -ext "ku:c=digitalSignature,keyEncipherment" -ext eku=serverAuth `
       -ext "san=dns:localhost,ip:127.0.0.1"
    kt -importcert -noprompt -alias ls-root-ca -file root-ca.crt -keystore server.p12
    kt -importcert -noprompt -alias ls-intermediate-ca -file intermediate-ca.crt -keystore server.p12
    kt -importcert -noprompt -alias server -file server.crt -keystore server.p12

    Remove-Item *.csr
    Write-Host "Готово: $(Join-Path (Get-Location) 'server.p12') (цепочка из 3 сертификатов), $(Join-Path (Get-Location) 'root-ca.crt')"
    & $keytool -list -v -storetype PKCS12 -storepass $P -keystore server.p12 -alias server |
        Select-String "Certificate chain length|Owner:|Issuer:"
}
finally {
    Pop-Location
}
