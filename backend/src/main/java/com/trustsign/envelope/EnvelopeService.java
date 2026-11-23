package com.trustsign.envelope;

import com.trustsign.archive.ArchiveService;
import com.trustsign.audit.AuditService;
import com.trustsign.auth.AppUser;
import com.trustsign.auth.UserDirectory;
import com.trustsign.common.FileStorage;
import com.trustsign.common.Hashing;
import com.trustsign.common.Role;
import com.trustsign.config.TrustSignProperties;
import com.trustsign.pdf.PadesSigner;
import com.trustsign.pdf.SignatureVerifier;
import com.trustsign.pki.PkiService;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Création des dossiers, circuit de signature ordonné, téléchargement et archivage automatique. */
@Service
public class EnvelopeService {

    public record StepDto(int position, String username, String displayName, Instant signedAt,
                          String certificateSerial, boolean current) {}

    public record EnvelopeDto(Long id, String title, String fileName, String createdBy, Instant createdAt,
                              EnvelopeStatus status, String originalSha256, String currentSha256, Instant completedAt,
                              List<StepDto> steps, boolean myTurn, int signedCount, Integer archivePosition) {}

    private final EnvelopeRepository envelopes;
    private final SignerStepRepository steps;
    private final FileStorage storage;
    private final PkiService pki;
    private final PadesSigner signer;
    private final SignatureVerifier verifier;
    private final ArchiveService archive;
    private final UserDirectory users;
    private final AuditService audit;
    private final String location;

    public EnvelopeService(EnvelopeRepository envelopes, SignerStepRepository steps, FileStorage storage, PkiService pki,
                           PadesSigner signer, SignatureVerifier verifier, ArchiveService archive, UserDirectory users,
                           AuditService audit, TrustSignProperties props) {
        this.envelopes = envelopes;
        this.steps = steps;
        this.storage = storage;
        this.pki = pki;
        this.signer = signer;
        this.verifier = verifier;
        this.archive = archive;
        this.users = users;
        this.audit = audit;
        this.location = props.pki().location();
    }

    @Transactional
    public EnvelopeDto create(String title, String fileName, byte[] pdf, List<String> signerNames, AppUser by) {
        if (by.role() != Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seuls les utilisateurs créent des dossiers de signature.");
        }
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donnez un titre au dossier.");
        }
        if (pdf.length < 5 || !new String(pdf, 0, 5).startsWith("%PDF")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seuls les fichiers PDF sont acceptés.");
        }
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les PDF protégés par mot de passe ne sont pas acceptés.");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce fichier n'est pas un PDF lisible.");
        }
        List<String> ordered = List.copyOf(new LinkedHashSet<>(signerNames == null ? List.of() : signerNames));
        if (ordered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez au moins un signataire.");
        }
        List<AppUser> signers = ordered.stream().map(u -> users.find(u)
            .filter(x -> x.role() == Role.USER)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signataire inconnu : " + u))).toList();

        String sha = Hashing.sha256(pdf);
        Envelope env = envelopes.save(new Envelope(title.trim(), clean(fileName), by.username(), storage.save(pdf), sha));
        for (int i = 0; i < signers.size(); i++) {
            steps.save(new SignerStep(env.getId(), i + 1, signers.get(i).username(), signers.get(i).displayName()));
        }
        audit.log(by, "CREATION", "Dossier « " + env.getTitle() + " » : " + signers.size() + " signataire(s), SHA-256 "
            + sha.substring(0, 12) + "…");
        return toDto(env, by);
    }

    public List<EnvelopeDto> visibleTo(AppUser user) {
        return envelopes.findAllByOrderByIdDesc().stream().filter(e -> canSee(e, user)).map(e -> toDto(e, user)).toList();
    }

    public EnvelopeDto get(Long id, AppUser user) {
        return toDto(visible(id, user), user);
    }

    /** Signe le dossier si c'est le tour de l'utilisateur ; archive automatiquement après la dernière signature. */
    @Transactional
    public EnvelopeDto sign(Long id, AppUser user) {
        Envelope env = visible(id, user);
        if (env.getStatus() != EnvelopeStatus.EN_COURS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce dossier est déjà entièrement signé.");
        }
        List<SignerStep> circuit = steps.findByEnvelopeIdOrderByPositionAsc(id);
        SignerStep next = circuit.stream().filter(s -> !s.isSigned()).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Toutes les signatures sont déjà faites."));
        if (!next.getUsername().equals(user.username())) {
            boolean listed = circuit.stream().anyMatch(s -> s.getUsername().equals(user.username()) && !s.isSigned());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, listed
                ? "Ce n'est pas encore votre tour : " + next.getDisplayName() + " doit signer avant vous."
                : "Vous n'êtes pas signataire de ce dossier (ou vous avez déjà signé).");
        }

        byte[] current = storage.read(env.getCurrentFile())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fichier du dossier introuvable."));
        if (!Hashing.same(Hashing.sha256(current), env.getCurrentSha256())) {
            audit.log(user, "ALERTE_INTEGRITE", "Dossier « " + env.getTitle() + " » : le fichier stocké a été modifié, signature refusée");
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Le fichier stocké ne correspond plus à la dernière version connue : signature refusée.");
        }

        PkiService.SigningIdentity identity = pki.identityOf(user);
        byte[] signed;
        try {
            signed = signer.sign(current, identity, new PadesSigner.SignRequest(user.displayName(),
                "Signature n° " + next.getPosition() + " – " + env.getTitle(), location));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La signature a échoué.");
        }

        // On ne garde la nouvelle version que si elle se vérifie entièrement.
        SignatureVerifier.Report check = verifier.verify(signed, pki.rootCertificate());
        if (!"VALIDE".equals(check.verdict())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Contrôle après signature en échec : " + check.verdict());
        }

        String serial = identity.certificate().getSerialNumber().toString(16);
        env.newVersion(storage.save(signed), Hashing.sha256(signed));
        next.markSigned(serial);
        steps.save(next);
        audit.log(user, "SIGNATURE", "Dossier « " + env.getTitle() + " » : signature " + next.getPosition() + "/" + circuit.size()
            + " (certificat n° " + serial + ")");

        if (circuit.stream().allMatch(SignerStep::isSigned)) {
            env.complete();
            var entry = archive.append(env.getId(), env.getTitle(), env.getCurrentFile(), env.getCurrentSha256());
            audit.log(user, "ARCHIVAGE", "Dossier « " + env.getTitle() + " » archivé (entrée n° " + entry.getPosition() + ")");
        }
        envelopes.save(env);
        return toDto(env, user);
    }

    public byte[] download(Long id, AppUser user) {
        Envelope env = visible(id, user);
        return storage.read(env.getCurrentFile())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable."));
    }

    public String downloadName(Long id, AppUser user) {
        Envelope env = visible(id, user);
        String base = env.getFileName().replaceAll("(?i)\\.pdf$", "");
        return base + (env.getStatus() == EnvelopeStatus.SIGNE_ARCHIVE ? "_signe.pdf" : ".pdf");
    }

    public long count() {
        return envelopes.count();
    }

    private Envelope visible(Long id, AppUser user) {
        return envelopes.findById(id).filter(e -> canSee(e, user))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable."));
    }

    private boolean canSee(Envelope e, AppUser user) {
        return user.role() == Role.ADMIN || e.getCreatedBy().equals(user.username())
            || steps.findByEnvelopeIdOrderByPositionAsc(e.getId()).stream().anyMatch(s -> s.getUsername().equals(user.username()));
    }

    private EnvelopeDto toDto(Envelope e, AppUser viewer) {
        List<SignerStep> circuit = steps.findByEnvelopeIdOrderByPositionAsc(e.getId());
        Optional<SignerStep> next = circuit.stream().filter(s -> !s.isSigned()).findFirst();
        List<StepDto> stepDtos = circuit.stream().map(s -> new StepDto(s.getPosition(), s.getUsername(), s.getDisplayName(),
            s.getSignedAt(), s.getCertificateSerial(), next.map(n -> n.getId().equals(s.getId())).orElse(false))).toList();
        boolean myTurn = e.getStatus() == EnvelopeStatus.EN_COURS && next.map(n -> n.getUsername().equals(viewer.username())).orElse(false);
        int signed = (int) circuit.stream().filter(SignerStep::isSigned).count();
        Integer archivePos = e.getStatus() == EnvelopeStatus.SIGNE_ARCHIVE ? archive.positionOf(e.getId()) : null;
        return new EnvelopeDto(e.getId(), e.getTitle(), e.getFileName(), e.getCreatedBy(), e.getCreatedAt(), e.getStatus(),
            e.getOriginalSha256(), e.getCurrentSha256(), e.getCompletedAt(), stepDtos, myTurn, signed, archivePos);
    }

    static String clean(String original) {
        String n = original == null || original.isBlank() ? "document.pdf" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N} ._()-]", "_");
        return n.length() > 120 ? n.substring(0, 120) : n;
    }
}
