package licenseserver.repository;

import licenseserver.model.DeviceLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceLicenseRepository extends JpaRepository<DeviceLicense, Long> {

    long countByLicenseId(Long licenseId);

    boolean existsByLicenseIdAndDeviceId(Long licenseId, Long deviceId);

    /** Лицензии продукта, активированные на устройстве; самая поздняя по сроку — первой. */
    @Query("""
            select dl from DeviceLicense dl
            join fetch dl.license l
            where dl.device.id = :deviceId and l.product.id = :productId
            order by l.endingDate desc
            """)
    List<DeviceLicense> findByDeviceAndProduct(@Param("deviceId") Long deviceId, @Param("productId") Long productId);
}
