package licenseserver.ticket;

import licenseserver.model.Device;
import licenseserver.model.License;
import licenseserver.signature.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Собирает тикет по лицензии и подписывает его. */
@Component
@RequiredArgsConstructor
public class TicketFactory {

    private final SignatureService signatureService;
    private final Clock clock;

    @Value("${license.ticket-lifetime:PT5M}")
    private Duration ticketLifetime;

    public TicketResponse issue(License license, Device device) {
        Ticket ticket = new Ticket(
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                ticketLifetime.toSeconds(),
                seconds(license.getFirstActivationDate()),
                seconds(license.getEndingDate()),
                license.getUser() == null ? null : license.getUser().getId(),
                device == null ? null : device.getId(),
                license.isBlocked() || license.getProduct().isBlocked());
        return new TicketResponse(ticket, signatureService.sign(ticket));
    }

    private static Instant seconds(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
