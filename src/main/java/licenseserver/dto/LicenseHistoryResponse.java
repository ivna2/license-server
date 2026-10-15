package licenseserver.dto;

import licenseserver.model.LicenseHistory;
import licenseserver.model.LicenseStatus;

import java.time.Instant;

public record LicenseHistoryResponse(Long id, LicenseStatus status, Long userId, Instant changeDate, String description) {
    public static LicenseHistoryResponse of(LicenseHistory h) {
        return new LicenseHistoryResponse(h.getId(), h.getStatus(), h.getUser().getId(), h.getChangeDate(), h.getDescription());
    }
}
