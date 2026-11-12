package com.polygis.account;

import java.util.UUID;
import java.util.function.Function;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.polygis.account.AccountController.ChangePasswordRequest;
import com.polygis.account.AccountController.UpdateProfileRequest;
import com.polygis.auth.AuthService;
import com.polygis.auth.TokenService;
import com.polygis.auth.dto.AuthDtos.MeResponse;
import com.polygis.common.ApiException;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;
import com.polygis.tenancy.TenantRegistry;
import com.polygis.user.User;
import com.polygis.user.UserRepository;

@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AuthService auth;
    private final TenantRegistry registry;
    private final TransactionTemplate tx;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                          AuthService auth, TenantRegistry registry, TransactionTemplate tx) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.auth = auth;
        this.registry = registry;
        this.tx = tx;
    }

    public MeResponse updateProfile(UUID userId, String homeTenant, UpdateProfileRequest req) {
        TenantInfo home = registry.requireBySlug(homeTenant);
        return inHomeTenant(home, userId, user -> {
            user.setFirstName(req.firstName());
            user.setLastName(req.lastName());
            return auth.toMe(user, home);
        });
    }

    /** Changing the password signs the user out everywhere (all refresh tokens are revoked). */
    public void changePassword(UUID userId, String homeTenant, ChangePasswordRequest req) {
        inHomeTenant(registry.requireBySlug(homeTenant), userId, user -> {
            if (user.getPasswordHash() != null && (req.currentPassword() == null
                    || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash()))) {
                throw ApiException.badRequest("wrong_password", "Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            tokens.revokeAll(user.getId());
            return null;
        });
    }

    /** A super-admin may be switched into another tenant (X-Tenant-ID); their own account lives at home. */
    private <T> T inHomeTenant(TenantInfo home, UUID userId, Function<User, T> work) {
        return TenantContext.callAs(home, () -> tx.execute(status -> {
            User user = users.findById(userId)
                    .orElseThrow(() -> ApiException.unauthorized("user_gone", "User no longer exists"));
            return work.apply(user);
        }));
    }
}
