package licenseserver.ticket;

/**
 * Тикет и его электронная цифровая подпись.
 *
 * @param ticket    данные лицензии
 * @param signature подпись канонического JSON тикета в Base64 (алгоритм и сертификат: GET /api/signature/certificate)
 */
public record TicketResponse(Ticket ticket, String signature) {
}
