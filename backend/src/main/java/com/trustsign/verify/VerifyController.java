package com.trustsign.verify;

import com.trustsign.audit.AuditService;
import com.trustsign.auth.CurrentUser;
import com.trustsign.common.Hashing;
import com.trustsign.pdf.SignatureVerifier;
import com.trustsign.pki.PkiService;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Vérifie n'importe quel PDF déposé : signatures, confiance, modifications. */
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    public record VerifyResponse(String fileName, String sha256, long size, SignatureVerifier.Report report) {}

    private final SignatureVerifier verifier;
    private final PkiService pki;
    private final AuditService audit;

    public VerifyController(SignatureVerifier verifier, PkiService pki, AuditService audit) {
        this.verifier = verifier;
        this.pki = pki;
        this.audit = audit;
    }

    @PostMapping
    public VerifyResponse verify(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] data = file.getBytes();
        SignatureVerifier.Report r = verifier.verify(data, pki.rootCertificate());
        audit.log(CurrentUser.get(), "VERIFICATION", (file.getOriginalFilename() == null ? "document" : file.getOriginalFilename())
            + " : " + r.verdict() + " (" + r.signatureCount() + " signature(s))");
        return new VerifyResponse(file.getOriginalFilename(), Hashing.sha256(data), data.length, r);
    }
}
