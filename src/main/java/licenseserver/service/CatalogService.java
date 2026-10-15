package licenseserver.service;

import licenseserver.dto.LicenseTypeRequest;
import licenseserver.dto.LicenseTypeResponse;
import licenseserver.dto.ProductRequest;
import licenseserver.dto.ProductResponse;
import licenseserver.exception.ApiException;
import licenseserver.model.LicenseType;
import licenseserver.model.Product;
import licenseserver.repository.LicenseTypeRepository;
import licenseserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Справочники для выдачи лицензий: продукты и типы лицензий. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepo;
    private final LicenseTypeRepository typeRepo;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String name = request.name().trim();
        if (productRepo.existsByName(name)) {
            throw ApiException.conflict("Продукт уже существует: " + name);
        }
        Product product = new Product();
        product.setName(name);
        product.setBlocked(request.blocked());
        return ProductResponse.of(productRepo.save(product));
    }

    @Transactional
    public ProductResponse setProductBlocked(Long id, boolean blocked) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Продукт не найден: " + id));
        product.setBlocked(blocked);
        return ProductResponse.of(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> products() {
        return productRepo.findAll().stream().map(ProductResponse::of).toList();
    }

    @Transactional
    public LicenseTypeResponse createType(LicenseTypeRequest request) {
        String name = request.name().trim();
        if (typeRepo.existsByName(name)) {
            throw ApiException.conflict("Тип лицензии уже существует: " + name);
        }
        LicenseType type = new LicenseType();
        type.setName(name);
        type.setDefaultDurationInDays(request.defaultDurationInDays());
        type.setDescription(request.description());
        return LicenseTypeResponse.of(typeRepo.save(type));
    }

    @Transactional(readOnly = true)
    public List<LicenseTypeResponse> types() {
        return typeRepo.findAll().stream().map(LicenseTypeResponse::of).toList();
    }
}
