package com.fauzi.ledger.auth;

import com.fauzi.ledger.auth.dto.LoginRequest;
import com.fauzi.ledger.auth.dto.RegisterRequest;
import com.fauzi.ledger.auth.dto.TokenResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTests {

    private static final String PASSWORD = "correct horse 42";
    private AppUserRepository userRepository;
    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        UUID userId = UUID.randomUUID();
        user = new AppUser("alice", passwordEncoder.encode(PASSWORD), "USER");
        ReflectionTestUtils.setField(user, "id", userId);

        SecretKey key = new SecretKeySpec(
                "local-test-signing-key-with-32-bytes".getBytes(),
                "HmacSHA256"
        );
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                new NimbusJwtEncoder(new ImmutableSecret<>(key))
        );
    }

    @Test
    void registersUsersWithHashedPasswordsAndUserRole() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenAnswer(call -> {
            AppUser saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        AuthService.RegisteredUser registered = authService.register(
                new RegisterRequest("Alice", PASSWORD)
        );

        assertEquals("alice", registered.username());
        assertEquals("USER", registered.role());
        verify(userRepository).save(argThat(saved ->
                "USER".equals(saved.getRole())
                        && passwordEncoder.matches(PASSWORD, saved.getPasswordHash())
                        && !PASSWORD.equals(saved.getPasswordHash())
        ));
    }

    @Test
    void issuesJwtThatCanBeValidatedByTheResourceServer() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        TokenResponse token = authService.issueToken(new LoginRequest("Alice", PASSWORD));
        SecretKey key = new SecretKeySpec(
                "local-test-signing-key-with-32-bytes".getBytes(),
                "HmacSHA256"
        );
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        var claims = decoder.decode(token.accessToken());

        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals(java.util.List.of("USER"), claims.getClaimAsStringList("roles"));
        assertEquals("Bearer", token.tokenType());
        assertEquals(3600, token.expiresIn());
    }

    @Test
    void rejectsInvalidCredentials() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThrows(
                BadCredentialsException.class,
                () -> authService.issueToken(new LoginRequest("alice", "wrong password"))
        );
    }

    @Test
    void rejectsDuplicateRegistration() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(
                UsernameAlreadyExistsException.class,
                () -> authService.register(new RegisterRequest("Alice", PASSWORD))
        );
        verify(userRepository, never()).save(any(AppUser.class));
    }
}
