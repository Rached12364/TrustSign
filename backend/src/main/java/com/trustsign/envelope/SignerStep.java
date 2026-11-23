package com.trustsign.envelope;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Une étape du circuit : le signataire n° position doit signer après les précédents. */
@Entity
@Table(name = "signer_step", indexes = @Index(columnList = "envelopeId"))
public class SignerStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long envelopeId;
    private int position;
    private String username;
    private String displayName;
    private Instant signedAt;
    private String certificateSerial;

    protected SignerStep() {}

    public SignerStep(Long envelopeId, int position, String username, String displayName) {
        this.envelopeId = envelopeId;
        this.position = position;
        this.username = username;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public Long getEnvelopeId() { return envelopeId; }
    public int getPosition() { return position; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public Instant getSignedAt() { return signedAt; }
    public String getCertificateSerial() { return certificateSerial; }

    public boolean isSigned() {
        return signedAt != null;
    }

    public void markSigned(String certificateSerial) {
        this.signedAt = Instant.now();
        this.certificateSerial = certificateSerial;
    }
}
