package com.trustsign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Outils communs aux tests d'intégration. */
public final class TestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TestSupport() {}

    public static void tempDirs(DynamicPropertyRegistry r) {
        try {
            Path dir = Files.createTempDirectory("trustsign-test");
            r.add("trustsign.storage.dir", () -> dir.resolve("files").toString());
            r.add("trustsign.pki.dir", () -> dir.resolve("pki").toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String login(MockMvc mvc, String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("username", user, "password", user + "123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JSON.readTree(body).get("token").asText();
    }

    public static JsonNode json(ResultActions r) throws Exception {
        return JSON.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
