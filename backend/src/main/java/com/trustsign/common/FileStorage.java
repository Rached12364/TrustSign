package com.trustsign.common;

import com.trustsign.config.TrustSignProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stocke les PDF sur le disque sous un nom aléatoire (jamais le nom envoyé par l'utilisateur). */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(TrustSignProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le dossier de stockage " + root, e);
        }
    }

    public String save(byte[] data) {
        String name = UUID.randomUUID() + ".pdf";
        try {
            Files.write(root.resolve(name), data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return name;
    }

    public Optional<byte[]> read(String name) {
        Path p = resolve(name);
        try {
            return Files.exists(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path resolve(String name) {
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Chemin interdit");
        }
        return p;
    }
}
