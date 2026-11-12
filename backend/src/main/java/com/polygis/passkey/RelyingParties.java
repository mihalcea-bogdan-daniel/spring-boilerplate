package com.polygis.passkey;

import java.net.URI;
import java.util.Locale;

import com.polygis.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Derives the relying party of a ceremony from the request: rpId is the tenant host; the expected origin is the
 * browser's Origin header, accepted only when it points at that same host. (A phishing site cannot obtain a
 * signature for our rpId at all - browsers enforce that - so this check is about rejecting confused requests.)
 */
final class RelyingParties {

    private RelyingParties() {
    }

    static PasskeyService.RelyingParty of(HttpServletRequest request, String displayName) {
        String host = request.getServerName().toLowerCase(Locale.ROOT);
        String origin = request.getHeader("Origin");
        if (origin == null) {
            throw ApiException.badRequest("origin_required", "Passkeys can only be used from a browser");
        }
        try {
            URI uri = URI.create(origin);
            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(host)) {
                throw ApiException.badRequest("origin_mismatch", "Origin does not match this workspace");
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("origin_mismatch", "Invalid Origin header");
        }
        return new PasskeyService.RelyingParty(host, displayName, origin);
    }
}
