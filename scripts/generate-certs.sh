#!/usr/bin/env bash
# Генерирует цепочку из трёх сертификатов: корневой CA -> промежуточный CA -> сертификат сервера.
# Во всех сертификатах указан номер студенческого билета.
# Использование: ./scripts/generate-certs.sh <номер_студенческого>
# Пароль хранилищ берётся из переменной CERT_PASSWORD или запрашивается.
# Результат в каталоге certs/ (в .gitignore): server.p12 для Spring Boot, root-ca.crt для доверенных.
set -euo pipefail

STUDENT_ID="${1:?Укажите номер студенческого билета: $0 <номер>}"
DIR="${CERT_DIR:-certs}"
if [ -z "${CERT_PASSWORD:-}" ]; then
  read -r -s -p "Пароль для хранилищ ключей (не короче 6 символов): " CERT_PASSWORD; echo
fi
P="$CERT_PASSWORD"
O="O=LicenseServer Lab, OU=Student ${STUDENT_ID}, C=RU"

mkdir -p "$DIR"
cd "$DIR"
rm -f ls-root-ca.p12 ls-intermediate-ca.p12 server.p12 ./*.csr ./*.crt

kt() { keytool "$@" -storetype PKCS12 -storepass "$P"; }

# 1. Корневой CA (самоподписанный)
kt -genkeypair -alias ls-root-ca -keyalg RSA -keysize 4096 -validity 3650 \
   -dname "CN=LicenseServer Root CA ${STUDENT_ID}, ${O}" \
   -ext bc:c=ca:true -ext ku:c=keyCertSign,cRLSign -keystore ls-root-ca.p12
kt -exportcert -rfc -alias ls-root-ca -keystore ls-root-ca.p12 -file root-ca.crt

# 2. Промежуточный CA, подписан корневым
kt -genkeypair -alias ls-intermediate-ca -keyalg RSA -keysize 4096 -validity 1825 \
   -dname "CN=LicenseServer Intermediate CA ${STUDENT_ID}, ${O}" -keystore ls-intermediate-ca.p12
kt -certreq -alias ls-intermediate-ca -keystore ls-intermediate-ca.p12 -file intermediate-ca.csr
kt -gencert -alias ls-root-ca -keystore ls-root-ca.p12 -infile intermediate-ca.csr -outfile intermediate-ca.crt \
   -rfc -validity 1825 -ext bc:c=ca:true,pathlen:0 -ext ku:c=keyCertSign,cRLSign
kt -importcert -noprompt -alias ls-root-ca -file root-ca.crt -keystore ls-intermediate-ca.p12
kt -importcert -noprompt -alias ls-intermediate-ca -file intermediate-ca.crt -keystore ls-intermediate-ca.p12

# 3. Сертификат сервера для localhost, подписан промежуточным CA
kt -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 397 \
   -dname "CN=localhost, ${O}" -keystore server.p12
kt -certreq -alias server -keystore server.p12 -file server.csr
kt -gencert -alias ls-intermediate-ca -keystore ls-intermediate-ca.p12 -infile server.csr -outfile server.crt \
   -rfc -validity 397 -ext ku:c=digitalSignature,keyEncipherment -ext eku=serverAuth \
   -ext san=dns:localhost,ip:127.0.0.1
kt -importcert -noprompt -alias ls-root-ca -file root-ca.crt -keystore server.p12
kt -importcert -noprompt -alias ls-intermediate-ca -file intermediate-ca.crt -keystore server.p12
kt -importcert -noprompt -alias server -file server.crt -keystore server.p12

rm -f ./*.csr
echo "Готово: $(pwd)/server.p12 (цепочка из 3 сертификатов), $(pwd)/root-ca.crt"
keytool -list -v -storetype PKCS12 -storepass "$P" -keystore server.p12 -alias server | grep -E "Certificate chain length|Owner:|Issuer:"
