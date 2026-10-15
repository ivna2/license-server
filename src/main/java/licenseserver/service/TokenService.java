package licenseserver.service;

import licenseserver.dto.TokenResponse;
import licenseserver.model.SessionStatus;
import licenseserver.model.UserSession;
import licenseserver.repository.UserSessionRepository;
import licenseserver.security.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserSessionRepository sessionRepository;
    private final UserDetailsService userDetailsService;

    public TokenResponse login(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String accessToken = jwtTokenProvider.generateAccessToken(username, userDetails.getAuthorities());
        String refreshToken = jwtTokenProvider.generateRefreshToken(username);

        LocalDateTime now = LocalDateTime.now();
        UserSession session = new UserSession();
        session.setRefreshToken(jwtTokenProvider.hashRefreshToken(refreshToken));
        session.setUserId(username);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiration())));
        sessionRepository.save(session);

        return new TokenResponse(accessToken, refreshToken);
    }

    // noRollbackFor: статусы REVOKED/EXPIRED должны сохраниться, даже когда запрос отклонён
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        String username = jwtTokenProvider.getUsernameFromRefreshToken(refreshToken);
        if (username == null) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        UserSession session = sessionRepository.findByRefreshToken(jwtTokenProvider.hashRefreshToken(refreshToken))
                .orElseThrow(() -> new InvalidTokenException("Session not found"));

        if (SessionStatus.REVOKED.equals(session.getStatus())) {
            // Повторное использование уже отозванного токена: вероятна кража, отзываем все сессии пользователя
            revokeAllActiveSessions(session.getUserId());
            throw new InvalidTokenException("Refresh token has already been used");
        }
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            throw new InvalidTokenException("Session is not active");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new InvalidTokenException("Refresh token expired");
        }

        // Ротация: старая сессия отзывается, выдаётся новая пара токенов
        session.setStatus(SessionStatus.REVOKED);
        sessionRepository.save(session);

        return login(username);
    }

    private void revokeAllActiveSessions(String username) {
        for (UserSession active : sessionRepository.findByUserIdAndStatus(username, SessionStatus.ACTIVE)) {
            active.setStatus(SessionStatus.REVOKED);
        }
    }
}
