package com.trustsign.archive;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trustsign.common.FileStorage;
import com.trustsign.common.Hashing;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Archive à valeur probante : registre chaîné par empreintes SHA-256 + contrôle des fichiers archivés. */
@Service
public class ArchiveService {

    public static final String GENESIS = "0".repeat(64);

    public record EntryCheck(int position, Long envelopeId, String title, String documentSha256, String previousHash,
                             String entryHash, Instant archivedAt, boolean linkOk, boolean hashOk, boolean fileOk) {

        @JsonProperty("ok")
        public boolean ok() {
            return linkOk && hashOk && fileOk;
        }
    }

    public record ChainReport(int entries, boolean intact, Integer firstBrokenPosition, String message,
                              List<EntryCheck> checks) {}

    private final ArchiveRepository repo;
    private final FileStorage storage;

    public ArchiveService(ArchiveRepository repo, FileStorage storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Transactional
    public synchronized ArchiveEntry append(Long envelopeId, String title, String storedFile, String documentSha256) {
        ArchiveEntry last = repo.findTopByOrderByPositionDesc().orElse(null);
        int position = last == null ? 1 : last.getPosition() + 1;
        String previous = last == null ? GENESIS : last.getEntryHash();
        ArchiveEntry e = new ArchiveEntry(position, envelopeId, title, storedFile, documentSha256, previous,
            Instant.now().truncatedTo(ChronoUnit.MILLIS));
        e.setEntryHash(hashOf(e));
        return repo.save(e);
    }

    public Integer positionOf(Long envelopeId) {
        return repo.findByEnvelopeId(envelopeId).map(ArchiveEntry::getPosition).orElse(null);
    }

    /** Recalcule toute la chaîne et l'empreinte de chaque fichier archivé. */
    public ChainReport verify() {
        List<ArchiveEntry> entries = repo.findAllByOrderByPositionAsc();
        List<EntryCheck> checks = new ArrayList<>();
        String expectedPrevious = GENESIS;
        Integer firstBroken = null;
        for (ArchiveEntry e : entries) {
            boolean link = Hashing.same(expectedPrevious, e.getPreviousHash());
            boolean hash = Hashing.same(hashOf(e), e.getEntryHash());
            boolean file = storage.read(e.getStoredFile()).map(Hashing::sha256)
                .map(h -> Hashing.same(h, e.getDocumentSha256())).orElse(false);
            EntryCheck c = new EntryCheck(e.getPosition(), e.getEnvelopeId(), e.getTitle(), e.getDocumentSha256(),
                e.getPreviousHash(), e.getEntryHash(), e.getArchivedAt(), link, hash, file);
            if (!c.ok() && firstBroken == null) {
                firstBroken = e.getPosition();
            }
            checks.add(c);
            expectedPrevious = e.getEntryHash();
        }
        String message = firstBroken == null
            ? entries.size() + " document(s) archivé(s) : chaîne intacte, aucun fichier modifié."
            : "Archive altérée à partir de l'entrée n° " + firstBroken + ".";
        return new ChainReport(entries.size(), firstBroken == null, firstBroken, message, checks);
    }

    static String hashOf(ArchiveEntry e) {
        return Hashing.sha256(e.getPosition() + "|" + e.getPreviousHash() + "|" + e.getEnvelopeId() + "|" + e.getTitle()
            + "|" + e.getDocumentSha256() + "|" + e.getArchivedAt().toEpochMilli());
    }
}
