package licenseserver.ticket;

import java.time.Instant;

/**
 * Информация о лицензии, которую сервер передаёт клиенту.
 * Даты в UTC с точностью до секунды, чтобы клиент мог воспроизвести подписанные байты.
 *
 * @param serverDate            текущая дата сервера
 * @param ticketLifetimeSeconds время жизни тикета в секундах, считая от serverDate
 * @param activationDate        дата первой активации лицензии
 * @param expirationDate        дата истечения лицензии
 * @param userId                идентификатор пользователя
 * @param deviceId              идентификатор устройства (null, если операция не связана с устройством)
 * @param blocked               флаг блокировки лицензии
 */
public record Ticket(
        Instant serverDate,
        long ticketLifetimeSeconds,
        Instant activationDate,
        Instant expirationDate,
        Long userId,
        Long deviceId,
        boolean blocked) {
}
