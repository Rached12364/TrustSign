package com.trustsign.pdf;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vérifie toutes les signatures d'un PDF, sans faire confiance au document :
 * 1. intégrité : l'empreinte des octets signés correspond à la signature CMS ;
 * 2. confiance : le certificat du signataire est bien émis par notre autorité de certification ;
 * 3. validité : le certificat était valide au moment de la signature ;
 * 4. couverture : la dernière signature couvre-t-elle tout le fichier ? Sinon, quelque chose a été ajouté après.
 */
@Component
public class SignatureVerifier {

    public record SignatureCheck(int index, String signer, String signerUid, String certSerial, String issuer,
                                 Instant signedAt, String reason, String location,
                                 boolean integrityOk, boolean trusted, boolean certValidAtSigning,
                                 boolean coversWholeDocument, String detail) {

        @JsonProperty("valid")
        public boolean valid() {
            return integrityOk && trusted && certValidAtSigning;
        }
    }

    public record Report(int signatureCount, boolean allValid, boolean modifiedAfterLastSignature,
                         String verdict, List<SignatureCheck> signatures) {}

    public Report verify(byte[] pdf, X509Certificate trustAnchor) {
        if (pdf.length < 5 || !new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce fichier n'est pas un PDF.");
        }
        List<SignatureCheck> checks = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<PDSignature> sigs = doc.getSignatureDictionaries();
            for (int i = 0; i < sigs.size(); i++) {
                checks.add(check(i + 1, sigs.get(i), pdf, trustAnchor, i == sigs.size() - 1));
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce fichier n'est pas un PDF lisible.");
        }

        boolean allValid = !checks.isEmpty() && checks.stream().allMatch(SignatureCheck::valid);
        boolean modifiedAfter = !checks.isEmpty() && !checks.get(checks.size() - 1).coversWholeDocument();
        String verdict;
        if (checks.isEmpty()) {
            verdict = "AUCUNE_SIGNATURE";
        } else if (checks.stream().anyMatch(c -> !c.integrityOk())) {
            verdict = "ALTERE";
        } else if (!allValid) {
            verdict = "NON_FIABLE";
        } else if (modifiedAfter) {
            verdict = "MODIFIE_APRES_SIGNATURE";
        } else {
            verdict = "VALIDE";
        }
        return new Report(checks.size(), allValid, modifiedAfter, verdict, checks);
    }

    private SignatureCheck check(int index, PDSignature sig, byte[] pdf, X509Certificate anchor, boolean last) {
        Instant signedAt = sig.getSignDate() == null ? null : sig.getSignDate().toInstant();
        int[] range = sig.getByteRange();
        boolean covers = range != null && range.length == 4 && (long) range[2] + range[3] == pdf.length;
        try {
            byte[] signedContent = sig.getSignedContent(pdf);
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), sig.getContents(pdf));
            SignerInformation si = cms.getSignerInfos().getSigners().iterator().next();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = cms.getCertificates().getMatches(si.getSID());
            if (matches.isEmpty()) {
                return failed(index, sig, signedAt, covers, "Certificat du signataire absent de la signature");
            }
            X509CertificateHolder cert = matches.iterator().next();

            boolean integrity;
            String detail = "";
            try {
                integrity = si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));
            } catch (Exception e) {
                integrity = false;
                detail = "Les octets signés ont été modifiés : l'empreinte ne correspond plus à la signature.";
            }

            boolean trusted;
            try {
                X509CertificateHolder ca = new JcaX509CertificateHolder(anchor);
                trusted = cert.getIssuer().equals(ca.getSubject())
                    && cert.isSignatureValid(new JcaContentVerifierProviderBuilder().build(anchor));
            } catch (Exception e) {
                trusted = false;
            }
            if (!trusted && detail.isEmpty()) {
                detail = "Le certificat n'a pas été émis par l'autorité de certification de confiance.";
            }

            boolean validAt = signedAt != null && cert.isValidOn(java.util.Date.from(signedAt));
            if (!validAt && detail.isEmpty()) {
                detail = "Le certificat n'était pas valide à la date de signature.";
            }
            if (detail.isEmpty() && !covers && last) {
                detail = "Des données ont été ajoutées au fichier après cette signature.";
            }
            return new SignatureCheck(index, cn(cert.getSubject()), uid(cert.getSubject()), cert.getSerialNumber().toString(16),
                cn(cert.getIssuer()), signedAt, sig.getReason(), sig.getLocation(), integrity, trusted, validAt, covers, detail);
        } catch (Exception e) {
            return failed(index, sig, signedAt, covers, "Signature illisible : " + e.getClass().getSimpleName());
        }
    }

    private static SignatureCheck failed(int index, PDSignature sig, Instant at, boolean covers, String detail) {
        return new SignatureCheck(index, sig.getName(), null, null, null, at, sig.getReason(), sig.getLocation(),
            false, false, false, covers, detail);
    }

    private static String cn(X500Name name) {
        return value(name, BCStyle.CN);
    }

    private static String uid(X500Name name) {
        return value(name, BCStyle.UID);
    }

    private static String value(X500Name name, org.bouncycastle.asn1.ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        return rdns.length == 0 ? null : IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }
}
