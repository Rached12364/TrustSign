package com.trustsign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Toutes les options « trustsign.* » de application.properties. */
@ConfigurationProperties(prefix = "trustsign")
public record TrustSignProperties(Storage storage, Demo demo, Jwt jwt, Pki pki) {

    public record Storage(String dir) {}

    public record Demo(boolean load) {}

    public record Jwt(String secret, long ttlMinutes) {}

    /** dir : dossier des magasins de clés ; password : mot de passe des magasins PKCS#12 ; location : lieu de signature. */
    public record Pki(String dir, String password, String location) {}
}
