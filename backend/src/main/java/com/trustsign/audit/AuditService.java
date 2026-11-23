package com.trustsign.audit;

import com.trustsign.auth.AppUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    /** Utilisateur technique pour les actions automatiques (chargement de la démo, vérification planifiée). */
    public static final AppUser SYSTEM = new AppUser("systeme", "Système", com.trustsign.common.Role.ADMIN);

    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    public void log(AppUser user, String action, String detail) {
        repo.save(new AuditEvent(user.username(), user.role(), action, detail, null, 0));
    }

    public void question(AppUser user, String question, boolean grounded, int sources) {
        repo.save(new AuditEvent(user.username(), user.role(), "QUESTION", question, grounded, sources));
    }

    public void loginFailed(String username) {
        repo.save(new AuditEvent(username == null ? "?" : username, null, "CONNEXION_ECHEC", "Mot de passe incorrect ou compte inconnu", null, 0));
    }

    public List<AuditEvent> latest() {
        return repo.findTop300ByOrderByIdDesc();
    }
}
