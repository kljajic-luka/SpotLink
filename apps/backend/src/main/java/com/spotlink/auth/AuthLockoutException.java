package com.spotlink.auth;

import java.time.Instant;

public class AuthLockoutException extends RuntimeException {

    public static final String CODE = "AUTH_TEMPORARILY_LOCKED";

    private final Instant lockedUntil;

    public AuthLockoutException(Instant lockedUntil) {
        super("Too many failed sign-in attempts. Try again later.");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
