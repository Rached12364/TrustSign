package com.trustsign.archive;

import com.trustsign.audit.AuditService;
import com.trustsign.auth.CurrentUser;
import com.trustsign.pki.PkiService;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ArchiveController {

    private final ArchiveService archive;
    private final AuditService audit;
    private final PkiService pki;

    public ArchiveController(ArchiveService archive, AuditService audit, PkiService pki) {
        this.archive = archive;
        this.audit = audit;
        this.pki = pki;
    }

    @GetMapping("/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ArchiveService.ChainReport list() {
        return archive.verify();
    }

    @PostMapping("/archive/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ArchiveService.ChainReport verify() {
        ArchiveService.ChainReport r = archive.verify();
        audit.log(CurrentUser.get(), r.intact() ? "CONTROLE_ARCHIVE" : "ALERTE_ARCHIVE", r.message());
        return r;
    }

    /** Certificat de l'autorité racine (à importer dans un lecteur PDF pour qu'il fasse confiance aux signatures). */
    @GetMapping("/pki/root")
    public ResponseEntity<String> root() throws CertificateEncodingException {
        String pem = "-----BEGIN CERTIFICATE-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pki.rootCertificate().getEncoded())
            + "\n-----END CERTIFICATE-----\n";
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"trustsign-root-ca.pem\"").body(pem);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        var r = archive.verify();
        return Map.of("archived", r.entries(), "archiveIntact", r.intact(),
            "rootCa", pki.rootCertificate().getSubjectX500Principal().getName());
    }
}
