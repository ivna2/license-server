package licenseserver.repository;

import licenseserver.model.LicenseHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LicenseHistoryRepository extends JpaRepository<LicenseHistory, Long> {
    List<LicenseHistory> findByLicenseIdOrderByChangeDateAsc(Long licenseId);
}
