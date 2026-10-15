#!/usr/bin/env bash
# Создаёт хранилище PKCS12 с ключом ЭЦП (RSA 3072) и сертификатом для подписи тикетов лицензий.
# В сертификате указан номер студенческого билета.
# Использование: ./scripts/generate-signing-key.sh <номер_студенческого>
# Пароль хранилища берётся из переменной SIGNING_KEYSTORE_PASSWORD или запрашивается.
# Результат в каталоге certs/ (в .gitignore): signing.p12 для сервера, signing.crt для клиентов.
set -euo pipefail

STUDENT_ID="${1:?Укажите номер студенческого билета: $0 <номер>}"
DIR="${CERT_DIR:-certs}"
if [ -z "${SIGNING_KEYSTORE_PASSWORD:-}" ]; then
  read -r -s -p "Пароль для хранилища ключа подписи (не короче 6 символов): " SIGNING_KEYSTORE_PASSWORD; echo
fi
P="$SIGNING_KEYSTORE_PASSWORD"
if [ ${#P} -lt 6 ]; then echo "Пароль хранилища должен быть не короче 6 символов" >&2; exit 1; fi

mkdir -p "$DIR"
cd "$DIR"
rm -f signing.p12 signing.crt

keytool -genkeypair -alias license-signing -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 730 \
  -dname "CN=LicenseServer Ticket Signing ${STUDENT_ID}, OU=Student ${STUDENT_ID}, O=LicenseServer Lab, C=RU" \
  -ext ku:c=digitalSignature,nonRepudiation \
  -storetype PKCS12 -keystore signing.p12 -storepass "$P"
keytool -exportcert -rfc -alias license-signing -keystore signing.p12 -storetype PKCS12 -storepass "$P" -file signing.crt

echo "Готово: $(pwd)/signing.p12 (ключ подписи), $(pwd)/signing.crt (сертификат для проверки)"
keytool -list -v -storetype PKCS12 -storepass "$P" -keystore signing.p12 -alias license-signing | grep -E "Owner:|Signature algorithm|Valid"
