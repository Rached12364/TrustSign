package com.trustsign;

import static org.assertj.core.api.Assertions.assertThat;

import com.trustsign.archive.ArchiveEntry;
import com.trustsign.archive.ArchiveRepository;
import com.trustsign.archive.ArchiveService;
import com.trustsign.common.FileStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** L'archive chaînée détecte une modification de la base comme une modification d'un fichier archivé. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ArchiveTamperTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        TestSupport.tempDirs(r);
    }

    @Autowired
    ArchiveService archive;
    @Autowired
    ArchiveRepository repo;
    @Autowired
    FileStorage storage;

    @Test
    void chaine_intacte_au_depart() {
        assertThat(archive.verify().intact()).isTrue();
        assertThat(archive.verify().entries()).isEqualTo(2);
    }

    @Test
    void modifier_une_entree_en_base_casse_la_chaine() {
        ArchiveEntry first = repo.findAllByOrderByPositionAsc().get(0);
        first.tamperTitle("Contrat modifié en douce");
        repo.save(first);
        var r = archive.verify();
        assertThat(r.intact()).isFalse();
        assertThat(r.firstBrokenPosition()).isEqualTo(1);
        assertThat(r.checks().get(0).hashOk()).isFalse();
    }

    @Test
    void modifier_un_fichier_archive_est_detecte() throws Exception {
        ArchiveEntry second = repo.findAllByOrderByPositionAsc().get(1);
        Path file = storage.resolve(second.getStoredFile());
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length / 3] ^= 0x01;
        Files.write(file, bytes);
        var r = archive.verify();
        assertThat(r.intact()).isFalse();
        assertThat(r.firstBrokenPosition()).isEqualTo(2);
        assertThat(r.checks().get(1).fileOk()).isFalse();
        assertThat(r.checks().get(0).ok()).isTrue();
    }

    @Test
    void supprimer_une_entree_casse_le_lien_suivant() {
        repo.delete(repo.findAllByOrderByPositionAsc().get(0));
        var r = archive.verify();
        assertThat(r.intact()).isFalse();
        assertThat(r.checks().get(0).linkOk()).isFalse();
    }
}
