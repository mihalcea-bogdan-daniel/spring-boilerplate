package com.polygis.mfa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.common.ApiException;
import com.polygis.common.Hashing;
import com.polygis.config.AppProperties;
import com.polygis.user.User;

/**
 * TOTP second factor with single-use recovery codes. All methods expect to run inside the caller's transaction
 * and tenant (the user's home tenant).
 * <p>The TOTP secret is encrypted at rest (AES-GCM, see FieldEncryption).
 */
@Service
public class MfaService {

    private static final int RECOVERY_CODES = 8;

    private final RecoveryCodeRepository recoveryCodes;
    private final AppProperties props;

    public MfaService(RecoveryCodeRepository recoveryCodes, AppProperties props) {
        this.recoveryCodes = recoveryCodes;
        this.props = props;
    }

    public record Setup(String secret, String otpauthUri) {
    }

    /** Step 1: generate a secret; MFA is not active until {@link #enable} confirms the user can produce codes. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Setup beginSetup(User user) {
        if (user.isTotpEnabled()) throw ApiException.conflict("mfa_enabled", "Two-factor authentication is already on");
        user.setTotpSecret(Totp.newSecret());
        return new Setup(user.getTotpSecret(),
                Totp.uri(props.mail().productName(), user.getEmail(), user.getTotpSecret()));
    }

    /** Step 2: confirm with a code. Returns the recovery codes - shown exactly once. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> enable(User user, String code) {
        if (user.isTotpEnabled()) throw ApiException.conflict("mfa_enabled", "Two-factor authentication is already on");
        if (user.getTotpSecret() == null || !verifyTotp(user, code)) throw invalid();
        user.setTotpEnabled(true);
        recoveryCodes.deleteByUserId(user.getId());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String raw = Hashing.randomToken(8).toLowerCase().replace("-", "x").replace("_", "y").substring(0, 10);
            RecoveryCode rc = new RecoveryCode();
            rc.setUserId(user.getId());
            rc.setCodeHash(hash(user, raw));
            recoveryCodes.save(rc);
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        return codes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void disable(User user, String code) {
        if (!user.isTotpEnabled()) return;
        if (!verify(user, code)) throw invalid();
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpLastStep(null);
        recoveryCodes.deleteByUserId(user.getId());
    }

    /** Accepts a 6-digit TOTP code or an unused recovery code (which is then burned). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean verify(User user, String code) {
        if (code == null) return false;
        String c = code.trim();
        if (c.matches("\\d{6}")) return verifyTotp(user, c);
        String normalized = c.replace("-", "").toLowerCase();
        return recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), hash(user, normalized))
                .map(rc -> {
                    rc.setUsedAt(Instant.now());
                    return true;
                }).orElse(false);
    }

    private boolean verifyTotp(User user, String code) {
        long last = user.getTotpLastStep() == null ? -1 : user.getTotpLastStep();
        long step = Totp.verify(user.getTotpSecret(), code, Totp.currentStep(), 1, last);
        if (step < 0) return false;
        user.setTotpLastStep(step);
        return true;
    }

    private static String hash(User user, String code) {
        return Hashing.sha256(user.getId() + ":" + code);
    }

    private static ApiException invalid() {
        return ApiException.badRequest("mfa_invalid", "Invalid authentication code");
    }
}
