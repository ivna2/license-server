package licenseserver.security;

/** Требования к надёжности пароля: 8–64 символа, строчная и заглавная буквы, цифра и спецсимвол. */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$";
    public static final String MESSAGE =
            "Пароль должен содержать 8–64 символа, строчную и заглавную буквы, цифру и спецсимвол";

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        return password != null && password.matches(REGEX);
    }
}
