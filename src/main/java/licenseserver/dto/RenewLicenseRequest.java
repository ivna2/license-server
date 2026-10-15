package licenseserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenewLicenseRequest(@NotBlank @Size(max = 64) String activationKey) {
}
