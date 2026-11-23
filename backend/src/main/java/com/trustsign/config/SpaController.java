package com.trustsign.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Renvoie l'application Angular pour ses routes, afin qu'un rafraîchissement de page fonctionne. */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/dossiers", "/dossiers/{id}", "/verifier", "/archive", "/audit"})
    public String index() {
        return "forward:/index.html";
    }
}
