# License Server

Сервер лицензий на Spring Boot 3 / Java 21 / PostgreSQL.

Из прошлого проекта (fitness-club-backend) перенесено следующее:
- JWT access/refresh с ротацией и отзывом сессий;
- роли USER/ADMIN;
- Basic Auth с CSRF;
- HTTPS с цепочкой из трёх сертификатов;
- CI.

Новое в этом проекте:
- модуль лицензий: создание, активация, проверка, продление;
- модуль ЭЦП для подписи тикетов.

Схемы БД и диаграммы последовательности описаны в [docs/schemas.md](docs/schemas.md).

## Ключи и сертификаты

Хранятся только локально в `certs/` (каталог в `.gitignore`), в CI приходят из GitHub Secrets.

```powershell
# TLS: Root CA -> Intermediate CA -> localhost
powershell -ExecutionPolicy Bypass -File scripts\generate-certs.ps1 -StudentId <номер>
# ЭЦП: ключ RSA 3072 + сертификат для подписи тикетов
powershell -ExecutionPolicy Bypass -File scripts\generate-signing-key.ps1 -StudentId <номер>
```

## Переменные окружения

| Переменная | Назначение |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL; по умолчанию `jdbc:postgresql://localhost:5432/license_server` |
| `JWT_SECRET` | секрет подписи JWT, не короче 32 байт |
| `SSL_KEYSTORE_PASSWORD` | пароль `certs/server.p12` |
| `SIGNING_KEYSTORE_PASSWORD` | пароль `certs/signing.p12` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | первичный администратор; если не заданы, администратор не создаётся |

Запуск: `./mvnw spring-boot:run`. Адрес сервиса: `https://localhost:8443`.

## API

| Метод и путь | Роль | Описание |
|---|---|---|
| `POST /auth/register`, `/auth/login`, `/auth/refresh` | все | регистрация и токены |
| `GET /api/products`, `/api/license-types` | авторизованный | справочники |
| `POST /api/products`, `/api/license-types` | ADMIN | создание справочников |
| `PUT /api/products/{id}/blocked`, `/api/licenses/{id}/blocked` | ADMIN | блокировка |
| `POST /api/licenses` | ADMIN | создание лицензии |
| `POST /api/licenses/activate` | USER | активация на устройстве, ответ `TicketResponse` |
| `POST /api/licenses/check` | USER | проверка по MAC и продукту, ответ `TicketResponse` |
| `POST /api/licenses/renew` | USER | продление, ответ `TicketResponse` |
| `GET /api/licenses/{id}/history` | ADMIN | история лицензии |
| `GET /api/signature/certificate` | все | сертификат и алгоритм для проверки подписи |

## CI (GitHub Actions)

Шаги: compile, test, package, проверка HTTPS и ЭЦП, загрузка jar.

Нужные секреты:
- `JWT_SECRET`;
- `SSL_KEYSTORE_BASE64`, `SSL_KEYSTORE_PASSWORD`;
- `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`.
