package com.trustsign.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Accès simple à l'utilisateur de la requête en cours. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AppUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AppUser u) {
            return u;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non connecté.");
    }
}
