package licenseserver.controller;

import licenseserver.dto.*;
import licenseserver.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return catalogService.products();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/products/{id}/blocked")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse setProductBlocked(@PathVariable Long id, @RequestBody BlockRequest request) {
        return catalogService.setProductBlocked(id, request.blocked());
    }

    @GetMapping("/license-types")
    public List<LicenseTypeResponse> types() {
        return catalogService.types();
    }

    @PostMapping("/license-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public LicenseTypeResponse createType(@Valid @RequestBody LicenseTypeRequest request) {
        return catalogService.createType(request);
    }
}
