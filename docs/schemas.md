# Схемы модуля лицензий

## ER-диаграмма

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    ROLE ||--o{ USER_ROLES : grants
    USERS ||--o{ LICENSE : "owner_id (владелец)"
    USERS |o--o{ LICENSE : "user_id (активировал)"
    USERS ||--o{ DEVICE : owns
    PRODUCT ||--o{ LICENSE : "лицензируется"
    LICENSE_TYPE ||--o{ LICENSE : "задаёт срок"
    LICENSE ||--o{ DEVICE_LICENSE : "активирована на"
    DEVICE ||--o{ DEVICE_LICENSE : "использует"
    LICENSE ||--o{ LICENSE_HISTORY : "журнал"
    USERS ||--o{ LICENSE_HISTORY : "кто изменил"

    USERS { bigint id PK
            varchar username UK
            varchar password
            boolean enabled }
    PRODUCT { bigint id PK
              varchar name UK
              boolean is_blocked }
    LICENSE_TYPE { bigint id PK
                   varchar name UK
                   int default_duration_in_days
                   text description }
    LICENSE { bigint id PK
              varchar code UK
              bigint user_id FK
              bigint owner_id FK
              bigint product_id FK
              bigint type_id FK
              timestamptz first_activation_date
              timestamptz ending_date
              boolean blocked
              int device_count
              text description }
    DEVICE { bigint id PK
             varchar name
             varchar mac_address UK
             bigint user_id FK }
    DEVICE_LICENSE { bigint id PK
                     bigint license_id FK
                     bigint device_id FK
                     timestamptz activation_date }
    LICENSE_HISTORY { bigint id PK
                      bigint license_id FK
                      bigint user_id FK
                      varchar status
                      timestamptz change_date
                      text description }
```

Текстовое описание:

- **product**: лицензируемый продукт. Если `is_blocked = true`, на продукт не выдаются новые лицензии, а в тикетах ставится флаг блокировки.
- **license_type**: тип лицензии. Поле `default_duration_in_days` задаёт срок действия при активации и шаг продления.
- **license**: лицензия с уникальным ключом активации `code`.
  - `owner_id`: владелец (покупатель), задаётся при создании.
  - `user_id`, `first_activation_date`, `ending_date`: пустые до первой активации.
  - `device_count`: сколько устройств можно активировать.
  - `blocked`: блокировка лицензии администратором.
- **device**: устройство пользователя. MAC-адрес уникален и хранится в виде `AA:BB:CC:DD:EE:FF`.
- **device_license**: связь «многие ко многим» между лицензией и устройством, с датой активации. Пара (лицензия, устройство) уникальна.
- **license_history**: журнал изменений: CREATED, ACTIVATED, RENEWED. Для каждой записи хранится пользователь и время.
- **users, role, user_roles, user_sessions**: пользователи, роли и refresh-сессии, перенесены из прошлого проекта.

Схема создаётся миграцией Flyway `src/main/resources/db/migration/V1__init.sql`. Hibernate только сверяет сущности со схемой (`ddl-auto: validate`).

## Диаграммы последовательности

### Создание лицензии (ADMIN)

```mermaid
sequenceDiagram
    actor A as Администратор
    participant C as LicenseController
    participant S as LicenseService
    participant DB as PostgreSQL
    A->>C: POST /api/licenses {productId, typeId, ownerId, deviceCount}
    C->>S: create()
    S->>DB: найти продукт, тип, владельца
    alt не найден
        S-->>A: 404
    else продукт заблокирован
        S-->>A: 409
    end
    S->>S: сгенерировать ключ (SecureRandom, XXXXX-XXXXX-XXXXX-XXXXX-XXXXX)
    S->>DB: INSERT license, INSERT license_history(CREATED)
    S-->>A: 201 {id, code, ...}
```

### Активация лицензии (USER)

```mermaid
sequenceDiagram
    actor U as Пользователь
    participant S as LicenseService
    participant DB as PostgreSQL
    participant T as TicketFactory + SignatureService
    U->>S: POST /api/licenses/activate {activationKey, deviceMac, deviceName}
    S->>DB: SELECT license FOR UPDATE по ключу
    alt нет ключа
        S-->>U: 404
    else активирована другим пользователем / заблокирована
        S-->>U: 403
    else срок истёк
        S-->>U: 409
    end
    S->>DB: найти или зарегистрировать устройство по MAC
    alt устройство чужое / уже активировано / лимит устройств
        S-->>U: 409
    end
    opt первая активация
        S->>S: user = текущий, first_activation_date = now, ending_date = now + срок типа
    end
    S->>DB: INSERT device_license, INSERT license_history(ACTIVATED)
    S->>T: тикет по лицензии и устройству
    T-->>U: 200 TicketResponse {ticket, signature}
```

### Проверка лицензии (USER)

```mermaid
sequenceDiagram
    actor U as Пользователь
    participant S as LicenseService
    participant DB as PostgreSQL
    participant T as TicketFactory + SignatureService
    U->>S: POST /api/licenses/check {deviceMac, productId}
    S->>DB: устройство по MAC, принадлежащее пользователю
    alt нет устройства
        S-->>U: 404
    end
    S->>DB: лицензии продукта на устройстве, ending_date > now
    alt нет действующей
        S-->>U: 404
    end
    S->>T: тикет (blocked = true, если лицензия или продукт заблокированы)
    T-->>U: 200 TicketResponse
```

### Продление лицензии (USER)

```mermaid
sequenceDiagram
    actor U as Пользователь
    participant S as LicenseService
    participant DB as PostgreSQL
    participant T as TicketFactory + SignatureService
    U->>S: POST /api/licenses/renew {activationKey}
    S->>DB: SELECT license FOR UPDATE
    alt нет ключа
        S-->>U: 404
    else не владелец и не пользователь / заблокирована
        S-->>U: 403
    else ещё не активирована
        S-->>U: 409
    end
    S->>S: ending_date = max(ending_date, now) + срок типа
    S->>DB: UPDATE license, INSERT license_history(RENEWED)
    S->>T: тикет
    T-->>U: 200 TicketResponse
```

## Тикет и ЭЦП

`Ticket` содержит следующие поля:

| Поле | Значение |
|---|---|
| `serverDate` | текущая дата сервера |
| `ticketLifetimeSeconds` | время жизни тикета |
| `activationDate` | дата первой активации лицензии |
| `expirationDate` | дата истечения лицензии |
| `userId` | идентификатор пользователя |
| `deviceId` | идентификатор устройства |
| `blocked` | флаг блокировки |

Все даты передаются в UTC с точностью до секунды.

`TicketResponse` = `{ ticket, signature }`.

Подпись формируется так:
1. Тикет сериализуется в канонический JSON (`CanonicalJson`): ключи по алфавиту, без пробелов, UTF-8, даты в ISO-8601.
2. Канонический JSON подписывается алгоритмом SHA256withRSA приватным ключом из PKCS12-хранилища (`SigningKeyLoader`, `SignatureService`).
3. Подпись кодируется в Base64.

Проверка на стороне клиента:
1. Получить сертификат: `GET /api/signature/certificate`, доступ открытый.
2. Пересобрать канонический JSON из полученного `ticket`, например `jq -cS .ticket`.
3. Проверить подпись открытым ключом сертификата, например `openssl dgst -sha256 -verify`.

Такая проверка выполняется в тестах (`LicenseFlowTests`) и в CI.

Компоненты модуля ЭЦП (пакет `licenseserver.signature`):
- `SignatureProperties`: путь к хранилищу, пароль, алиас и алгоритм; задаются переменными окружения.
- `SigningKeyLoader`: загружает ключ и сертификат при старте и проверяет срок действия сертификата. Также проверяет, что ключ соответствует сертификату: подписывает пробные данные и проверяет подпись.
- `CanonicalJson`: детерминированная сериализация.
- `SignatureService`: подпись, проверка, выдача сертификата в формате PEM.
- `TicketFactory`: сборка тикета и подписание.
