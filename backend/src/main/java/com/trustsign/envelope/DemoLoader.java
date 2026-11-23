package com.trustsign.envelope;

import com.trustsign.auth.AppUser;
import com.trustsign.auth.UserDirectory;
import com.trustsign.config.TrustSignProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Au premier démarrage (base vide), crée quatre dossiers fictifs à différentes étapes :
 * deux entièrement signés et archivés, un en attente de la 2e signature, un pas encore signé.
 */
@Component
public class DemoLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoLoader.class);

    private final EnvelopeService envelopes;
    private final UserDirectory users;
    private final TrustSignProperties props;

    public DemoLoader(EnvelopeService envelopes, UserDirectory users, TrustSignProperties props) {
        this.envelopes = envelopes;
        this.users = users;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!props.demo().load() || envelopes.count() > 0) {
            return;
        }
        AppUser alice = user("alice");
        AppUser bob = user("bob");
        AppUser claire = user("claire");

        var contrat = envelopes.create("Contrat de prestation de services", "contrat_prestation.pdf",
            pdf("contrat_prestation.pdf"), List.of("alice", "bob"), alice);
        envelopes.sign(contrat.id(), alice);
        envelopes.sign(contrat.id(), bob);

        var nda = envelopes.create("Accord de confidentialité", "accord_confidentialite.pdf",
            pdf("accord_confidentialite.pdf"), List.of("claire", "bob"), claire);
        envelopes.sign(nda.id(), claire);
        envelopes.sign(nda.id(), bob);

        var avenant = envelopes.create("Avenant n° 1 au contrat", "avenant_1.pdf",
            pdf("avenant_1.pdf"), List.of("alice", "claire"), alice);
        envelopes.sign(avenant.id(), alice);

        envelopes.create("Procès-verbal de réception", "pv_reception.pdf",
            pdf("pv_reception.pdf"), List.of("bob", "alice"), bob);

        log.info("TrustSign : 4 dossiers de démonstration créés");
    }

    private AppUser user(String name) {
        return users.find(name).orElseThrow();
    }

    private static byte[] pdf(String name) throws IOException {
        try (InputStream in = new ClassPathResource("demo/" + name).getInputStream()) {
            return in.readAllBytes();
        }
    }
}
