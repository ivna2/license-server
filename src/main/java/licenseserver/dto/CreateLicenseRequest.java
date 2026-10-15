package licenseserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLicenseRequest(
        @NotNull Long productId,
        @NotNull Long typeId,
        @NotNull Long ownerId,
        @Min(1) @Max(100) int deviceCount,
        @Size(max = 1000) String description) {
}
