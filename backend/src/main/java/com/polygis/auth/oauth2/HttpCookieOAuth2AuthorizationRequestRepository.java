package com.polygis.auth.oauth2;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;
import org.springframework.util.StringUtils;

import com.polygis.config.AppProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless storage of the in-flight OAuth2 authorization request (no HTTP session). The cookie is HMAC-signed:
 * it is only deserialized after the signature is verified, so a forged cookie can never reach the
 * deserializer. Set {@code app.security.oauth2.cookie-domain=.example.com} if the provider redirects to one
 * central callback host instead of each tenant host.
 */
@SuppressWarnings("deprecation") // SerializationUtils.deserialize is safe here: input is HMAC-authenticated
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE = "oauth2_auth_request";
    /** The removed request is exposed to the success/failure handlers through this request attribute. */
    public static final String REQUEST_ATTR = HttpCookieOAuth2AuthorizationRequestRepository.class.getName();
    private static final Duration TTL = Duration.ofMinutes(5);

    private final SecretKeySpec key;
    private final boolean secure;
    private final String domain;

    public HttpCookieOAuth2AuthorizationRequestRepository(AppProperties props) {
        this.key = new SecretKeySpec(props.security().jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.secure = props.security().refreshCookie().secure();
        this.domain = props.security().oauth2().cookieDomain();
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) return decode(c.getValue());
        }
        return null;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            write(response, "", Duration.ZERO);
            return;
        }
        write(response, encode(authorizationRequest), TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest existing = loadAuthorizationRequest(request);
        if (existing != null) request.setAttribute(REQUEST_ATTR, existing);
        write(response, "", Duration.ZERO);
        return existing;
    }

    private void write(HttpServletResponse response, String value, Duration maxAge) {
        var builder = ResponseCookie.from(COOKIE, value)
                .httpOnly(true).secure(secure).path("/").sameSite("Lax").maxAge(maxAge);
        if (StringUtils.hasText(domain)) builder.domain(domain);
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private String encode(OAuth2AuthorizationRequest req) {
        byte[] payload = SerializationUtils.serialize(req);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString(payload) + "." + b64.encodeToString(sign(payload));
    }

    private OAuth2AuthorizationRequest decode(String value) {
        try {
            int dot = value.indexOf('.');
            if (dot < 0) return null;
            byte[] payload = Base64.getUrlDecoder().decode(value.substring(0, dot));
            byte[] sig = Base64.getUrlDecoder().decode(value.substring(dot + 1));
            if (!MessageDigest.isEqual(sig, sign(payload))) return null;
            return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(payload);
        } catch (IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] prefix = "oauth2-cookie:".getBytes(StandardCharsets.UTF_8); // domain separation from JWTs
            mac.update(prefix);
            return mac.doFinal(Arrays.copyOf(payload, payload.length));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
