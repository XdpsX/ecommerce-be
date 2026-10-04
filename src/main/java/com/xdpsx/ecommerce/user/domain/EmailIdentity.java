package com.xdpsx.ecommerce.user.domain;

import java.util.Locale;

/** Canonical identity rules for email addresses used by local accounts. */
public final class EmailIdentity {
    private EmailIdentity() {}

    public static String canonicalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
