package licenseserver.dto;

import licenseserver.model.License;

import java.time.Instant;

public record LicenseResponse(
        Long id,
        String code,
        Long productId,
        Long typeId,
        Long ownerId,
        Long userId,
        int deviceCount,
        Instant firstActivationDate,
        Instant endingDate,
        boolean blocked,
        String description) {

    public static LicenseResponse of(License l) {
        return new LicenseResponse(l.getId(), l.getCode(), l.getProduct().getId(), l.getType().getId(),
                l.getOwner().getId(), l.getUser() == null ? null : l.getUser().getId(), l.getDeviceCount(),
                l.getFirstActivationDate(), l.getEndingDate(), l.isBlocked(), l.getDescription());
    }
}
