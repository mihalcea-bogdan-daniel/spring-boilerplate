package com.polygis.auth.oauth2;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.common.ApiException;
import com.polygis.config.AppProperties;
import com.polygis.settings.SettingsService;
import com.polygis.user.*;

/** Maps a provider identity onto a local user inside the current tenant schema (link or auto-register). */
@Service
public class OAuth2UserProvisioner {

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final RoleRepository roles;
    private final SettingsService settings;
    private final AppProperties props;
    private final com.polygis.tenancy.PlanService plans;

    public OAuth2UserProvisioner(UserRepository users, UserIdentityRepository identities, RoleRepository roles,
                                 SettingsService settings, AppProperties props,
                                 com.polygis.tenancy.PlanService plans) {
        this.plans = plans;
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.settings = settings;
        this.props = props;
    }

    public record ProviderProfile(String provider, String providerUserId, String email, boolean emailVerified,
                                  String firstName, String lastName) {

        public static ProviderProfile from(String provider, Map<String, Object> a) {
            return switch (provider) {
                case "google" -> new ProviderProfile(provider, str(a, "sub"), str(a, "email"),
                        Boolean.TRUE.equals(a.get("email_verified")), str(a, "given_name"), str(a, "family_name"));
                // e-mail + email_verified are resolved from /user/emails by GitHubEmailOAuth2UserService
                case "github" -> new ProviderProfile(provider, str(a, "id"), str(a, "email"),
                        Boolean.TRUE.equals(a.get("email_verified")), str(a, "name"), null);
                default -> new ProviderProfile(provider, str(a, "sub"), str(a, "email"),
                        Boolean.TRUE.equals(a.get("email_verified")), str(a, "given_name"), str(a, "family_name"));
            };
        }

        private static String str(Map<String, Object> a, String k) {
            Object v = a.get(k);
            return v == null ? null : v.toString();
        }
    }

    @Transactional
    public User findOrCreate(ProviderProfile p) {
        if (!settings.getBoolean(SettingsService.ALLOW_SOCIAL_LOGIN, true)) {
            throw ApiException.forbidden("social_disabled", "Social login is disabled for this tenant");
        }
        var linked = identities.findByProviderAndProviderUserId(p.provider(), p.providerUserId());
        if (linked.isPresent()) return linked.get().getUser();

        if (p.email() == null || !p.emailVerified()) {
            throw ApiException.forbidden("email_required", "The provider did not return a verified e-mail");
        }
        User user = users.findByEmailIgnoreCase(p.email()).orElseGet(() -> {
            if (!props.security().oauth2().autoRegister()
                    || !settings.getBoolean(SettingsService.ALLOW_SELF_REGISTRATION, true)) {
                throw ApiException.forbidden("registration_disabled", "No account for this e-mail");
            }
            plans.assertCanAddUser(users::count);
            User u = new User();
            u.setEmail(p.email().toLowerCase());
            u.setFirstName(p.firstName());
            u.setLastName(p.lastName());
            roles.findByName(Role.USER).ifPresent(r -> u.getRoles().add(r));
            return u;
        });
        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.forbidden("account_disabled", "Account disabled");
        }
        user.verifyEmailAsOwner();
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);

        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setProvider(p.provider());
        identity.setProviderUserId(p.providerUserId());
        identities.save(identity);
        return user;
    }
}
