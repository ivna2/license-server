package licenseserver.security;

/** Недействительный, отозванный или просроченный токен: обработчик ошибок отвечает 401. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
