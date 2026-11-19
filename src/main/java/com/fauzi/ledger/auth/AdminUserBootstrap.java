package com.fauzi.ledger.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class AdminUserBootstrap implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminUserBootstrap(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.bootstrap-admin-username:}") String username,
            @Value("${app.auth.bootstrap-admin-password:}") String password
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Both bootstrap admin username and password must be configured"
            );
        }
        if (username.length() < 3 || username.length() > 50
                || !username.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("Bootstrap admin username is invalid");
        }
        if (password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Bootstrap admin password must be 12 to 72 UTF-8 bytes"
            );
        }

        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        var existing = userRepository.findByUsername(normalizedUsername);
        if (existing.isPresent()) {
            if (!"ADMIN".equals(existing.get().getRole())) {
                throw new IllegalStateException(
                        "Bootstrap admin username is already used by a non-admin user"
                );
            }
            return;
        }
        userRepository.save(new AppUser(
                normalizedUsername,
                passwordEncoder.encode(password),
                "ADMIN"
        ));
    }
}
