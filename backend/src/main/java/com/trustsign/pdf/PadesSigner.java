package com.trustsign.pdf;

import com.trustsign.pki.PkiService.SigningIdentity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

/**
 * Signe un PDF au format PAdES (ETSI EN 319 142, niveau B-B) :
 * signature CMS détachée « ETSI.CAdES.detached », attribut signing-certificate-v2,
 * pas d'attribut signing-time (l'heure est dans le dictionnaire de signature, entrée /M).
 * Chaque nouvelle signature est ajoutée en mise à jour incrémentale : les signatures précédentes restent valides.
 */
@Component
public class PadesSigner {

    public record SignRequest(String name, String reason, String location) {}

    public byte[] sign(byte[] pdf, SigningIdentity id, SignRequest req) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            sig.setName(req.name());
            sig.setReason(req.reason());
            sig.setLocation(req.location());
            sig.setSignDate(Calendar.getInstance());

            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);
            doc.addSignature(sig, content -> cms(content, id), options);

            ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length + 20_000);
            doc.saveIncremental(out);
            options.close();
            return out.toByteArray();
        }
    }

    /** Construit la signature CMS (PKCS#7) des octets couverts par la signature. */
    static byte[] cms(InputStream content, SigningIdentity id) throws IOException {
        try {
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(id.key());
            byte[] certHash = MessageDigest.getInstance("SHA-256").digest(id.certificate().getEncoded());
            Attribute signingCertV2 = new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                new DERSet(new SigningCertificateV2(new ESSCertIDv2[] {new ESSCertIDv2(certHash)})));

            Hashtable<ASN1ObjectIdentifier, Attribute> extra = new Hashtable<>();
            extra.put(PKCSObjectIdentifiers.id_aa_signingCertificateV2, signingCertV2);
            CMSAttributeTableGenerator signedAttrs = new PadesAttributes(new AttributeTable(extra));

            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().build())
                .setSignedAttributeGenerator(signedAttrs)
                .build(signer, id.certificate()));
            gen.addCertificates(new JcaCertStore(Arrays.asList(id.chain())));

            CMSSignedData data = gen.generate(new CMSProcessableByteArray(content.readAllBytes()), false);
            return data.getEncoded();
        } catch (OperatorCreationException | CertificateEncodingException | CMSException | NoSuchAlgorithmException e) {
            throw new IOException("Échec de la création de la signature CMS", e);
        }
    }

    /** Attributs signés PAdES : ceux par défaut, moins signing-time (interdit en PAdES baseline). */
    private static final class PadesAttributes extends DefaultSignedAttributeTableGenerator {
        PadesAttributes(AttributeTable extra) {
            super(extra);
        }

        @Override
        public AttributeTable getAttributes(Map parameters) {
            return super.getAttributes(parameters).remove(CMSAttributes.signingTime);
        }
    }
}
