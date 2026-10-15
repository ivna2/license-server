package licenseserver.service;

import licenseserver.dto.RegisterRequest;
import licenseserver.model.Role;
import licenseserver.model.User;
import licenseserver.repository.RoleRepository;
import licenseserver.repository.UserRepository;
import licenseserver.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findByUsername(String username) {
        return userRepo.findByUsername(username);
    }

    public Optional<User> findById(Long id) {
        return userRepo.findById(id);
    }

    public void createAdminIfNotExist(String adminUsername, String adminPassword) {
        if (userRepo.existsByUsername(adminUsername)) {
            return;
        }
        if (!PasswordPolicy.isStrong(adminPassword)) {
            throw new IllegalArgumentException("Пароль администратора слишком слабый. " + PasswordPolicy.MESSAGE);
        }
        userRepo.save(newUser(adminUsername, adminPassword, "ROLE_ADMIN"));
    }

    /** Регистрация пользователя с ролью USER. */
    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepo.existsByUsername(email)) {
            throw new IllegalArgumentException("Пользователь уже зарегистрирован: " + email);
        }
        return userRepo.save(newUser(email, request.password(), "ROLE_USER"));
    }

    private User newUser(String username, String rawPassword, String roleName) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        Role role = roleRepo.findByName(roleName)
                .orElseGet(() -> roleRepo.save(new Role(null, roleName)));
        user.setRoles(Set.of(role));
        return user;
    }
}
