package com.fauzi.ledger.auth;

import com.fauzi.ledger.auth.dto.LoginRequest;
import com.fauzi.ledger.auth.dto.RegisterRequest;
import com.fauzi.ledger.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.RegisteredUser register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/token")
    public TokenResponse issueToken(@Valid @RequestBody LoginRequest request) {
        return authService.issueToken(request);
    }
}
