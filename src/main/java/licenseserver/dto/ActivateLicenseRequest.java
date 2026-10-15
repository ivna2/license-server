package licenseserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActivateLicenseRequest(
        @NotBlank @Size(max = 64) String activationKey,
        @NotBlank @Pattern(regexp = MacAddress.REGEX, message = MacAddress.MESSAGE) String deviceMac,
        @NotBlank @Size(max = 255) String deviceName) {
}
