package com.trustsign.envelope;

import com.trustsign.auth.AppUser;
import com.trustsign.auth.CurrentUser;
import com.trustsign.auth.UserDirectory;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class EnvelopeController {

    public record SignerOption(String username, String displayName) {}

    private final EnvelopeService service;
    private final UserDirectory users;

    public EnvelopeController(EnvelopeService service, UserDirectory users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/signers")
    public List<SignerOption> signers() {
        return users.signers().stream().map(u -> new SignerOption(u.username(), u.displayName())).toList();
    }

    @GetMapping("/envelopes")
    public List<EnvelopeService.EnvelopeDto> list() {
        return service.visibleTo(CurrentUser.get());
    }

    @GetMapping("/envelopes/{id}")
    public EnvelopeService.EnvelopeDto get(@PathVariable Long id) {
        return service.get(id, CurrentUser.get());
    }

    @PostMapping("/envelopes")
    public ResponseEntity<EnvelopeService.EnvelopeDto> create(@RequestParam("file") MultipartFile file,
                                                              @RequestParam("title") String title,
                                                              @RequestParam("signers") List<String> signers) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide.");
        }
        var dto = service.create(title, file.getOriginalFilename(), file.getBytes(), signers, CurrentUser.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/envelopes/{id}/sign")
    public EnvelopeService.EnvelopeDto sign(@PathVariable Long id) {
        return service.sign(id, CurrentUser.get());
    }

    @GetMapping("/envelopes/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        AppUser user = CurrentUser.get();
        byte[] data = service.download(id, user);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(service.downloadName(id, user)).build().toString())
            .body(data);
    }
}
