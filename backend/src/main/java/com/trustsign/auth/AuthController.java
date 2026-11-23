package com.trustsign.auth;

import com.trustsign.audit.AuditService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String token, String username, String displayName, String role) {}

    private final UserDirectory users;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthController(UserDirectory users, JwtService jwt, AuditService audit) {
        this.users = users;
        this.jwt = jwt;
        this.audit = audit;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        AppUser user = users.authenticate(req.username(), req.password()).orElseThrow(() -> {
            audit.loginFailed(req.username());
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect.");
        });
        audit.log(user, "CONNEXION", "Connexion réussie");
        return new LoginResponse(jwt.issue(user), user.username(), user.displayName(), user.role().name());
    }
}
