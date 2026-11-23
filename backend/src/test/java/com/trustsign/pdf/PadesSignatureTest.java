package com.trustsign.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.trustsign.auth.AppUser;
import com.trustsign.common.Role;
import com.trustsign.config.TrustSignProperties;
import com.trustsign.pki.PkiService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Signature PAdES et vérification, sans Spring : les cas de fraude qu'une vraie plateforme doit détecter. */
class PadesSignatureTest {

    static PkiService pki;
    static PkiService otherPki;
    static byte[] original;
    static final PadesSigner signer = new PadesSigner();
    static final SignatureVerifier verifier = new SignatureVerifier();
    static final AppUser alice = new AppUser("alice", "Alice Martin", Role.USER);
    static final AppUser bob = new AppUser("bob", "Bob Durand", Role.USER);

    @BeforeAll
    static void setUp() throws Exception {
        pki = new PkiService(props(Files.createTempDirectory("pki-a")));
        otherPki = new PkiService(props(Files.createTempDirectory("pki-b")));
        try (InputStream in = new ClassPathResource("demo/contrat_prestation.pdf").getInputStream()) {
            original = in.readAllBytes();
        }
    }

    static TrustSignProperties props(Path dir) {
        return new TrustSignProperties(null, null, null, new TrustSignProperties.Pki(dir.toString(), "test-pass", "Tunis"));
    }

    byte[] sign(byte[] pdf, PkiService p, AppUser u) throws Exception {
        return signer.sign(pdf, p.identityOf(u), new PadesSigner.SignRequest(u.displayName(), "Test", "Tunis"));
    }

    @Test
    void un_document_signe_est_valide_et_porte_le_bon_signataire() throws Exception {
        var r = verifier.verify(sign(original, pki, alice), pki.rootCertificate());
        assertThat(r.verdict()).isEqualTo("VALIDE");
        assertThat(r.signatures()).hasSize(1);
        var s = r.signatures().get(0);
        assertThat(s.signer()).isEqualTo("Alice Martin");
        assertThat(s.signerUid()).isEqualTo("alice");
        assertThat(s.integrityOk()).isTrue();
        assertThat(s.trusted()).isTrue();
        assertThat(s.coversWholeDocument()).isTrue();
    }

    @Test
    void deux_signatures_successives_restent_toutes_les_deux_valides() throws Exception {
        byte[] both = sign(sign(original, pki, alice), pki, bob);
        var r = verifier.verify(both, pki.rootCertificate());
        assertThat(r.verdict()).isEqualTo("VALIDE");
        assertThat(r.signatures()).extracting(SignatureVerifier.SignatureCheck::signer).containsExactly("Alice Martin", "Bob Durand");
        assertThat(r.signatures()).allMatch(SignatureVerifier.SignatureCheck::valid);
        // la 1re signature ne couvre que la 1re version : c'est normal, la 2e couvre tout
        assertThat(r.signatures().get(0).coversWholeDocument()).isFalse();
        assertThat(r.modifiedAfterLastSignature()).isFalse();
    }

    @Test
    void un_seul_octet_modifie_dans_le_contenu_signe_est_detecte() throws Exception {
        byte[] signed = sign(original, pki, alice);
        String text = new String(signed, StandardCharsets.ISO_8859_1);
        int pos = text.indexOf("/Title");
        assertThat(pos).isPositive();
        byte[] tampered = Arrays.copyOf(signed, signed.length);
        tampered[pos + 8] ^= 0x01;
        var r = verifier.verify(tampered, pki.rootCertificate());
        assertThat(r.verdict()).isEqualTo("ALTERE");
        assertThat(r.signatures().get(0).integrityOk()).isFalse();
    }

    @Test
    void un_ajout_apres_la_signature_est_signale() throws Exception {
        byte[] signed = sign(original, pki, alice);
        byte[] extra = "\n% contenu ajoute apres la signature\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] appended = Arrays.copyOf(signed, signed.length + extra.length);
        System.arraycopy(extra, 0, appended, signed.length, extra.length);
        var r = verifier.verify(appended, pki.rootCertificate());
        assertThat(r.verdict()).isEqualTo("MODIFIE_APRES_SIGNATURE");
        assertThat(r.signatures().get(0).integrityOk()).isTrue();
    }

    @Test
    void une_signature_d_une_autre_autorite_n_est_pas_de_confiance() throws Exception {
        var r = verifier.verify(sign(original, otherPki, alice), pki.rootCertificate());
        assertThat(r.verdict()).isEqualTo("NON_FIABLE");
        assertThat(r.signatures().get(0).integrityOk()).isTrue();
        assertThat(r.signatures().get(0).trusted()).isFalse();
    }

    @Test
    void un_pdf_sans_signature_est_reconnu() {
        assertThat(verifier.verify(original, pki.rootCertificate()).verdict()).isEqualTo("AUCUNE_SIGNATURE");
    }

    @Test
    void le_certificat_est_reutilise_et_relu_apres_redemarrage() throws Exception {
        Path dir = Files.createTempDirectory("pki-c");
        var first = new PkiService(props(dir));
        var serial = first.identityOf(alice).certificate().getSerialNumber();
        var again = new PkiService(props(dir));
        assertThat(again.identityOf(alice).certificate().getSerialNumber()).isEqualTo(serial);
        assertThat(again.rootCertificate()).isEqualTo(first.rootCertificate());
    }
}
