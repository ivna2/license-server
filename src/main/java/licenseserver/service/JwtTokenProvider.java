package licenseserver.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey signingKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String jwtSecret,
                            @Value("${app.jwt.access-token-expiration}") long accessTokenExpiration,
                            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        // Keys.hmacShaKeyFor отклоняет ключи короче 256 бит ещё при старте приложения
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public String generateAccessToken(String username, Collection<? extends GrantedAuthority> authorities) {
        return Jwts.builder()
                .claim("authorities", authorities.stream().map(GrantedAuthority::getAuthority).toList())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TYPE)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TYPE)
                .setId(UUID.randomUUID().toString()) // уникальность токена и его хэша в user_sessions
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Имя пользователя из действительного access-токена или null. */
    public String getUsernameFromAccessToken(String token) {
        return getSubject(token, ACCESS_TYPE);
    }

    /** Имя пользователя из действительного refresh-токена или null. */
    public String getUsernameFromRefreshToken(String token) {
        return getSubject(token, REFRESH_TYPE);
    }

    private String getSubject(String token, String expectedType) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class)) ? claims.getSubject() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public String hashRefreshToken(String token) {
        return Jwts.builder()
                .setSubject("refresh_token_hash")
                .claim("token", token)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
