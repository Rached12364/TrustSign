package com.trustsign.envelope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Un dossier de signature : un document PDF et la liste ordonnée de ses signataires. */
@Entity
@Table(name = "envelope")
public class Envelope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String fileName;
    private String createdBy;
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private EnvelopeStatus status = EnvelopeStatus.EN_COURS;

    /** Fichier stocké de la version actuelle (originale, puis après chaque signature). */
    private String currentFile;

    @Column(length = 64)
    private String originalSha256;

    @Column(length = 64)
    private String currentSha256;

    private Instant completedAt;

    protected Envelope() {}

    public Envelope(String title, String fileName, String createdBy, String file, String sha256) {
        this.title = title;
        this.fileName = fileName;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.currentFile = file;
        this.originalSha256 = sha256;
        this.currentSha256 = sha256;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public EnvelopeStatus getStatus() { return status; }
    public String getCurrentFile() { return currentFile; }
    public String getOriginalSha256() { return originalSha256; }
    public String getCurrentSha256() { return currentSha256; }
    public Instant getCompletedAt() { return completedAt; }

    public void newVersion(String file, String sha256) {
        this.currentFile = file;
        this.currentSha256 = sha256;
    }

    public void complete() {
        this.status = EnvelopeStatus.SIGNE_ARCHIVE;
        this.completedAt = Instant.now();
    }
}
