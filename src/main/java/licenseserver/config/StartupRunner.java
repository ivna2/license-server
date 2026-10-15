package licenseserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import licenseserver.service.UserService;

/**
 * Первичный администратор создаётся только из переменных окружения ADMIN_EMAIL и ADMIN_PASSWORD.
 * Учётных данных в коде и скриптах нет; если переменные не заданы, администратор не создаётся.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements CommandLineRunner {
    private final UserService userService;
    private final Environment env;

    @Override
    public void run(String... args) {
        String adminEmail = env.getProperty("admin.email");
        String adminPassword = env.getProperty("admin.password");

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD не заданы: администратор не создаётся");
            return;
        }
        if (!adminEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("Неверный формат email администратора");
        }

        userService.createAdminIfNotExist(adminEmail.trim().toLowerCase(), adminPassword);
        log.info("Администратор проверен: {}", adminEmail);
    }
}
