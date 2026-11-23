package com.trustsign.envelope;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignerStepRepository extends JpaRepository<SignerStep, Long> {

    List<SignerStep> findByEnvelopeIdOrderByPositionAsc(Long envelopeId);

    List<SignerStep> findByUsername(String username);
}
