package com.polygis.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.polygis.config.JwtConfig;

/** Public signing keys (RS256 mode) so gateways and other services can verify access tokens offline. */
@RestController
public class JwksController {

    private final JwtConfig jwt;

    public JwksController(JwtConfig jwt) {
        this.jwt = jwt;
    }

    @GetMapping("/api/auth/jwks.json")
    public Map<String, Object> jwks() {
        return jwt.jwks();
    }
}
