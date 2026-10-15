package licenseserver.repository;

import licenseserver.model.LicenseType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicenseTypeRepository extends JpaRepository<LicenseType, Long> {
    boolean existsByName(String name);
}
