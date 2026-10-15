package licenseserver.dto;

import licenseserver.model.Product;

public record ProductResponse(Long id, String name, boolean blocked) {
    public static ProductResponse of(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.isBlocked());
    }
}
