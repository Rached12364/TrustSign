package com.trustsign.pki;

import com.trustsign.auth.AppUser;
import com.trustsign.config.TrustSignProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Petite infrastructure à clés publiques (PKI) de démonstration :
 * une autorité de certification racine, puis un certificat X.509 par signataire, émis à la demande.
 *
 * Les clés sont gardées côté serveur dans un magasin PKCS#12, comme dans une « signature à distance »
 * (en production : un HSM et une autorité de certification qualifiée eIDAS).
 */
@Service
public class PkiService {

    private static final Logger log = LoggerFactory.getLogger(PkiService.class);
    static final String CA_ALIAS = "trustsign-root-ca";

    /** Une identité de signature : clé privée + chaîne de certificats (signataire, puis autorité). */
    public record SigningIdentity(PrivateKey key, X509Certificate certificate, X509Certificate[] chain) {}

    private final Path storeFile;
    private final char[] password;
    private final KeyStore store;
    private final SecureRandom random = new SecureRandom();

    public PkiService(TrustSignProperties props) {
        this.password = props.pki().password().toCharArray();
        try {
            Path dir = Path.of(props.pki().dir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            this.storeFile = dir.resolve("trustsign.p12");
            this.store = KeyStore.getInstance("PKCS12");
            if (Files.exists(storeFile)) {
                try (InputStream in = Files.newInputStream(storeFile)) {
                    store.load(in, password);
                }
            } else {
                store.load(null, null);
            }
            if (!store.containsAlias(CA_ALIAS)) {
                createRootCa();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'ouvrir le magasin de clés", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Magasin de clés illisible (mauvais mot de passe ?)", e);
        }
    }

    public X509Certificate rootCertificate() {
        try {
            return (X509Certificate) store.getCertificate(CA_ALIAS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Renvoie l'identité de signature de l'utilisateur ; son certificat est émis la première fois. */
    public synchronized SigningIdentity identityOf(AppUser user) {
        try {
            if (!store.containsAlias(user.username())) {
                issueUserCertificate(user);
            }
            PrivateKey key = (PrivateKey) store.getKey(user.username(), password);
            Certificate[] raw = store.getCertificateChain(user.username());
            X509Certificate[] chain = new X509Certificate[raw.length];
            for (int i = 0; i < raw.length; i++) {
                chain[i] = (X509Certificate) raw[i];
            }
            return new SigningIdentity(key, chain[0], chain);
        } catch (GeneralSecurityException | OperatorCreationException e) {
            throw new IllegalStateException("Impossible de préparer l'identité de signature de " + user.username(), e);
        }
    }

    private void createRootCa() throws GeneralSecurityException {
        try {
            KeyPair pair = newKeyPair(3072);
            X500Name name = name("TrustSign Root CA (démo)", null);
            Instant now = Instant.now();
            X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(name, serial(),
                Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(3650, ChronoUnit.DAYS)), name, pair.getPublic());
            JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            b.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(pair.getPublic()));
            X509Certificate cert = sign(b, pair.getPrivate());
            store.setKeyEntry(CA_ALIAS, pair.getPrivate(), password, new Certificate[] {cert});
            save();
            log.info("PKI : autorité de certification racine créée ({})", cert.getSubjectX500Principal());
        } catch (OperatorCreationException | IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private void issueUserCertificate(AppUser user) throws GeneralSecurityException, OperatorCreationException {
        X509Certificate ca = rootCertificate();
        PrivateKey caKey = (PrivateKey) store.getKey(CA_ALIAS, password);
        KeyPair pair = newKeyPair(2048);
        Instant now = Instant.now();
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(ca, serial(),
            Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(730, ChronoUnit.DAYS)),
            name(user.displayName(), user.username()), pair.getPublic());
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        try {
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            b.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_emailProtection));
            b.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(ca));
            b.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(pair.getPublic()));
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        X509Certificate cert = sign(b, caKey);
        store.setKeyEntry(user.username(), pair.getPrivate(), password, new Certificate[] {cert, ca});
        save();
        log.info("PKI : certificat émis pour {} (n° {})", user.displayName(), cert.getSerialNumber().toString(16));
    }

    private X509Certificate sign(X509v3CertificateBuilder b, PrivateKey issuerKey)
            throws OperatorCreationException, GeneralSecurityException {
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey);
        return new JcaX509CertificateConverter().getCertificate(b.build(signer));
    }

    private KeyPair newKeyPair(int bits) throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits, random);
        return g.generateKeyPair();
    }

    private BigInteger serial() {
        return new BigInteger(64, random).abs().add(BigInteger.ONE);
    }

    private static X500Name name(String commonName, String uid) {
        X500NameBuilder b = new X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.CN, commonName)
            .addRDN(BCStyle.O, "TrustSign (démo)")
            .addRDN(BCStyle.C, "TN");
        if (uid != null) {
            b.addRDN(BCStyle.UID, uid);
        }
        return b.build();
    }

    private void save() throws GeneralSecurityException {
        try (OutputStream out = Files.newOutputStream(storeFile)) {
            store.store(out, password);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
