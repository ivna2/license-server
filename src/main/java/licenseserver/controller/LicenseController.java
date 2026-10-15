package licenseserver.controller;

import licenseserver.dto.*;
import licenseserver.service.LicenseService;
import licenseserver.ticket.TicketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public LicenseResponse create(@Valid @RequestBody CreateLicenseRequest request, Authentication auth) {
        return licenseService.create(request, auth.getName());
    }

    @PostMapping("/activate")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public TicketResponse activate(@Valid @RequestBody ActivateLicenseRequest request, Authentication auth) {
        return licenseService.activate(request, auth.getName());
    }

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public TicketResponse check(@Valid @RequestBody CheckLicenseRequest request, Authentication auth) {
        return licenseService.check(request, auth.getName());
    }

    @PostMapping("/renew")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public TicketResponse renew(@Valid @RequestBody RenewLicenseRequest request, Authentication auth) {
        return licenseService.renew(request, auth.getName());
    }

    @PutMapping("/{id}/blocked")
    @PreAuthorize("hasRole('ADMIN')")
    public LicenseResponse setBlocked(@PathVariable Long id, @RequestBody BlockRequest request) {
        return licenseService.setBlocked(id, request.blocked());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<LicenseHistoryResponse> history(@PathVariable Long id) {
        return licenseService.history(id);
    }
}
