package com.polygis.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.polygis.auth.dto.AuthDtos.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Tenant-scoped authentication API. The tenant is always the request host (acme.example.com/api/auth/...).
 * Access token -> response body (SPA keeps it in memory/cookie); refresh token -> HttpOnly cookie.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final MessageResponse ACCEPTED =
            new MessageResponse("If the address is eligible, a code has been sent to it.");

    private final AuthService auth;
    private final AuthCookies cookies;

    public AuthController(AuthService auth, AuthCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest req) {
        auth.register(req);
        return ACCEPTED;
    }

    @PostMapping("/verify-email")
    public TokenResponse verifyEmail(@Valid @RequestBody VerifyOtpRequest req, HttpServletRequest http,
                                     HttpServletResponse res) {
        return respond(auth.verifyEmail(req, client(http)), res);
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest req) {
        auth.resendVerification(req);
        return ACCEPTED;
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        return respondOrChallenge(auth.login(req, client(http)), res);
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse requestOtp(@Valid @RequestBody EmailRequest req) {
        auth.requestLoginOtp(req);
        return ACCEPTED;
    }

    @PostMapping("/otp/verify")
    public Object verifyOtp(@Valid @RequestBody VerifyOtpRequest req, HttpServletRequest http,
                            HttpServletResponse res) {
        return respondOrChallenge(auth.verifyLoginOtp(req, client(http)), res);
    }

    /** Second step for accounts with TOTP: trade the challenge + authenticator (or recovery) code for tokens. */
    @PostMapping("/mfa/verify")
    public TokenResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest req, HttpServletRequest http,
                                   HttpServletResponse res) {
        return respond(auth.verifyMfa(req, client(http)), res);
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest req) {
        auth.requestPasswordReset(req);
        return ACCEPTED;
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req);
        return new MessageResponse("Password updated, please sign in.");
    }

    /** Second leg of social login: trade the one-time code from the redirect for real tokens. */
    @PostMapping("/oauth2/exchange")
    public Object exchange(@Valid @RequestBody ExchangeRequest req, HttpServletRequest http, HttpServletResponse res) {
        return respondOrChallenge(auth.exchangeOAuth2Code(req.code(), client(http)), res);
    }

    /** Landing step of "sign in as user": no refresh cookie is set, the session ends with the access token. */
    @PostMapping("/impersonation/exchange")
    public TokenResponse exchangeImpersonation(@Valid @RequestBody ExchangeRequest req) {
        return auth.exchangeImpersonationCode(req.code());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(HttpServletRequest http, HttpServletResponse res) {
        try {
            return respond(auth.refresh(cookies.readRefreshToken(http), client(http)), res);
        } catch (RuntimeException e) {
            cookies.clearRefreshToken(res);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http, HttpServletResponse res) {
        auth.logout(cookies.readRefreshToken(http));
        cookies.clearRefreshToken(res);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(java.util.UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(JwtClaims.TENANT),
                jwt.getClaimAsString(JwtClaims.ACTOR));
    }

    private Object respondOrChallenge(AuthService.AuthResult result, HttpServletResponse res) {
        if (result.mfaToken() != null) return new MfaChallengeResponse(true, result.mfaToken());
        return respond(result, res);
    }

    private TokenResponse respond(AuthService.AuthResult result, HttpServletResponse res) {
        cookies.setRefreshToken(res, result.refreshToken());
        return result.body();
    }

    static TokenService.ClientInfo client(HttpServletRequest http) {
        return new TokenService.ClientInfo(http.getHeader("User-Agent"), http.getRemoteAddr());
    }
}
