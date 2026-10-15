package licenseserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LicenseTypeRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(3650) int defaultDurationInDays,
        @Size(max = 1000) String description) {
}
