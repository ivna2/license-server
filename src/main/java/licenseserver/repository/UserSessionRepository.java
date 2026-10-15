package licenseserver.repository;

import licenseserver.model.SessionStatus;
import licenseserver.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByRefreshToken(String refreshTokenHash);

    List<UserSession> findByUserIdAndStatus(String userId, SessionStatus status);
}
