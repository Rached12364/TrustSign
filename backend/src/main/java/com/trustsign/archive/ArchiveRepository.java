package com.trustsign.archive;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveRepository extends JpaRepository<ArchiveEntry, Long> {

    List<ArchiveEntry> findAllByOrderByPositionAsc();

    Optional<ArchiveEntry> findTopByOrderByPositionDesc();

    Optional<ArchiveEntry> findByEnvelopeId(Long envelopeId);
}
