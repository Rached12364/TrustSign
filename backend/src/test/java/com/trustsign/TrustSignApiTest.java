package com.trustsign;

import static com.trustsign.TestSupport.json;
import static com.trustsign.TestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Circuit de signature complet par l'API, avec les dossiers de démonstration. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TrustSignApiTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        TestSupport.tempDirs(r);
    }

    @Autowired
    MockMvc mvc;

    JsonNode envelopes(String token) throws Exception {
        return json(mvc.perform(get("/api/envelopes").header("Authorization", token)).andExpect(status().isOk()));
    }

    long idOf(JsonNode list, String title) {
        return StreamSupport.stream(list.spliterator(), false).filter(e -> e.get("title").asText().equals(title))
            .findFirst().orElseThrow().get("id").asLong();
    }

    @Test
    void api_inaccessible_sans_jeton() throws Exception {
        mvc.perform(get("/api/envelopes")).andExpect(status().isUnauthorized());
    }

    @Test
    void chacun_ne_voit_que_ses_dossiers() throws Exception {
        assertThat(envelopes(login(mvc, "claire"))).hasSize(2);   // accord + avenant
        assertThat(envelopes(login(mvc, "admin"))).hasSize(4);
    }

    @Test
    void ordre_du_circuit_respecte() throws Exception {
        String alice = login(mvc, "alice");
        long pv = idOf(envelopes(alice), "Procès-verbal de réception");
        // Bob doit signer avant Alice
        mvc.perform(post("/api/envelopes/" + pv + "/sign").header("Authorization", alice)).andExpect(status().isForbidden());
        mvc.perform(post("/api/envelopes/" + pv + "/sign").header("Authorization", login(mvc, "bob"))).andExpect(status().isOk());
        JsonNode done = json(mvc.perform(post("/api/envelopes/" + pv + "/sign").header("Authorization", alice)).andExpect(status().isOk()));
        assertThat(done.get("status").asText()).isEqualTo("SIGNE_ARCHIVE");
        assertThat(done.get("archivePosition").asInt()).isEqualTo(3);
        // plus personne ne peut signer
        mvc.perform(post("/api/envelopes/" + pv + "/sign").header("Authorization", alice)).andExpect(status().isConflict());
    }

    @Test
    void un_non_participant_ne_peut_ni_voir_ni_telecharger() throws Exception {
        String claire = login(mvc, "claire");
        long contrat = idOf(envelopes(login(mvc, "alice")), "Contrat de prestation de services");
        mvc.perform(get("/api/envelopes/" + contrat).header("Authorization", claire)).andExpect(status().isNotFound());
        mvc.perform(get("/api/envelopes/" + contrat + "/download").header("Authorization", claire)).andExpect(status().isNotFound());
    }

    @Test
    void le_document_telecharge_se_verifie_avec_ses_deux_signatures() throws Exception {
        String alice = login(mvc, "alice");
        long contrat = idOf(envelopes(alice), "Contrat de prestation de services");
        byte[] pdf = mvc.perform(get("/api/envelopes/" + contrat + "/download").header("Authorization", alice))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        JsonNode r = json(mvc.perform(multipart("/api/verify").file(new MockMultipartFile("file", "contrat_signe.pdf", "application/pdf", pdf))
            .header("Authorization", login(mvc, "claire"))).andExpect(status().isOk()));
        assertThat(r.at("/report/verdict").asText()).isEqualTo("VALIDE");
        assertThat(r.at("/report/signatures/0/signer").asText()).isEqualTo("Alice Martin");
        assertThat(r.at("/report/signatures/1/signer").asText()).isEqualTo("Bob Durand");
    }

    @Test
    void creation_d_un_dossier_avec_controles() throws Exception {
        String bob = login(mvc, "bob");
        byte[] pdf;
        try (InputStream in = new ClassPathResource("demo/avenant_1.pdf").getInputStream()) {
            pdf = in.readAllBytes();
        }
        mvc.perform(multipart("/api/envelopes").file(new MockMultipartFile("file", "x.pdf", "application/pdf", "bonjour".getBytes()))
            .param("title", "Test").param("signers", "alice").header("Authorization", bob)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/envelopes").file(new MockMultipartFile("file", "x.pdf", "application/pdf", pdf))
            .param("title", "Test").param("signers", "inconnu").header("Authorization", bob)).andExpect(status().isBadRequest());
        JsonNode created = json(mvc.perform(multipart("/api/envelopes").file(new MockMultipartFile("file", "avenant.pdf", "application/pdf", pdf))
            .param("title", "Nouvel avenant").param("signers", "bob", "claire").header("Authorization", bob)).andExpect(status().isCreated()));
        assertThat(created.get("steps")).hasSize(2);
        assertThat(created.get("myTurn").asBoolean()).isTrue();
    }

    @Test
    void archive_reservee_a_l_admin_et_intacte() throws Exception {
        mvc.perform(get("/api/archive").header("Authorization", login(mvc, "alice"))).andExpect(status().isForbidden());
        JsonNode r = json(mvc.perform(get("/api/archive").header("Authorization", login(mvc, "admin"))).andExpect(status().isOk()));
        assertThat(r.get("entries").asInt()).isEqualTo(2);
        assertThat(r.get("intact").asBoolean()).isTrue();
    }
}
