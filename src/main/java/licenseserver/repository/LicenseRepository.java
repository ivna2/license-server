package licenseserver.repository;

import licenseserver.model.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {

    Optional<License> findByCode(String code);

    /** Блокировка строки защищает лимит устройств от параллельных активаций одного ключа. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from License l where l.code = :code")
    Optional<License> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);
}
