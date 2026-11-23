package com.trustsign.archive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Une entrée du registre d'archive. Chaque entrée contient l'empreinte de la précédente :
 * entryHash = SHA-256(position | previousHash | envelopeId | title | documentSha256 | archivedAt).
 * Modifier, supprimer ou insérer une entrée casse toute la chaîne à partir de cet endroit.
 */
@Entity
@Table(name = "archive_entry")
public class ArchiveEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int position;
    private Long envelopeId;
    private String title;
    private String storedFile;

    @Column(length = 64)
    private String documentSha256;

    @Column(length = 64)
    private String previousHash;

    @Column(length = 64)
    private String entryHash;

    private Instant archivedAt;

    protected ArchiveEntry() {}

    public ArchiveEntry(int position, Long envelopeId, String title, String storedFile, String documentSha256,
                        String previousHash, Instant archivedAt) {
        this.position = position;
        this.envelopeId = envelopeId;
        this.title = title;
        this.storedFile = storedFile;
        this.documentSha256 = documentSha256;
        this.previousHash = previousHash;
        this.archivedAt = archivedAt;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public Long getEnvelopeId() { return envelopeId; }
    public String getTitle() { return title; }
    public String getStoredFile() { return storedFile; }
    public String getDocumentSha256() { return documentSha256; }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }
    public Instant getArchivedAt() { return archivedAt; }

    void setEntryHash(String entryHash) { this.entryHash = entryHash; }

    /** Réservé aux tests : simule une falsification directe de la base. */
    public void tamperTitle(String newTitle) { this.title = newTitle; }
}
