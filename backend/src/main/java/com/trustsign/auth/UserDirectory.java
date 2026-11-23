package com.trustsign.auth;

import com.trustsign.common.Role;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Annuaire de démonstration : quatre comptes, mots de passe hachés avec BCrypt.
 * En entreprise, on le remplacerait par l'annuaire de l'entreprise (LDAP / Active Directory / SSO).
 */
@Component
public class UserDirectory {

    private record Account(AppUser user, String passwordHash) {}

    private final Map<String, Account> accounts;
    private final PasswordEncoder encoder;

    public UserDirectory(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.accounts = Map.of(
            "alice", new Account(new AppUser("alice", "Alice Martin", Role.USER), encoder.encode("alice123")),
            "bob", new Account(new AppUser("bob", "Bob Durand", Role.USER), encoder.encode("bob123")),
            "claire", new Account(new AppUser("claire", "Claire Petit", Role.USER), encoder.encode("claire123")),
            "admin", new Account(new AppUser("admin", "Admin TrustSign", Role.ADMIN), encoder.encode("admin123"))
        );
    }

    public Optional<AppUser> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        Account a = accounts.get(username.trim().toLowerCase());
        return a != null && encoder.matches(password, a.passwordHash()) ? Optional.of(a.user()) : Optional.empty();
    }

    public Optional<AppUser> find(String username) {
        return Optional.ofNullable(accounts.get(username)).map(Account::user);
    }

    /** Les personnes qui peuvent être désignées comme signataires (l'administrateur n'en fait pas partie). */
    public List<AppUser> signers() {
        return accounts.values().stream().map(Account::user).filter(u -> u.role() == Role.USER)
            .sorted(Comparator.comparing(AppUser::displayName)).toList();
    }
}
