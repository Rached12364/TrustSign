package com.trustsign.auth;

import com.trustsign.common.Role;

/** Utilisateur connecté (placé dans le contexte de sécurité après validation du jeton). */
public record AppUser(String username, String displayName, Role role) {}
