package licenseserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CheckLicenseRequest(
        @NotBlank @Pattern(regexp = MacAddress.REGEX, message = MacAddress.MESSAGE) String deviceMac,
        @NotNull Long productId) {
}
