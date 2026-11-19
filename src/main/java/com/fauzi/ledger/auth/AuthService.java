package com.fauzi.ledger.auth;

import com.fauzi.ledger.auth.dto.LoginRequest;
import com.fauzi.ledger.auth.dto.RegisterRequest;
import com.fauzi.ledger.auth.dto.TokenResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public RegisteredUser register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        validateBcryptPasswordSize(request.password());
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }
        AppUser user = userRepository.save(new AppUser(
                username,
                passwordEncoder.encode(request.password()),
                "USER"
        ));
        return new RegisteredUser(user.getId(), user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public TokenResponse issueToken(LoginRequest request) {
        AppUser user = userRepository.findByUsername(normalizeUsername(request.username()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("transaction-coordinator-ledger")
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(user.getRole()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
        return new TokenResponse(token, "Bearer", TOKEN_LIFETIME.toSeconds());
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private void validateBcryptPasswordSize(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }
    }

    public record RegisteredUser(java.util.UUID id, String username, String role) {
    }
}
