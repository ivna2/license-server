package licenseserver.dto;

import licenseserver.model.LicenseType;

public record LicenseTypeResponse(Long id, String name, int defaultDurationInDays, String description) {
    public static LicenseTypeResponse of(LicenseType t) {
        return new LicenseTypeResponse(t.getId(), t.getName(), t.getDefaultDurationInDays(), t.getDescription());
    }
}
