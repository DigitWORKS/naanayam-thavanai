package tn.digitworks.naanayamthavanai.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tn.digitworks.naanayamthavanai.tenant.Tenant;
import tn.digitworks.naanayamthavanai.user.AppUserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Per-request check that an access token still belongs to an active user whose tokens were not revoked
 * (AppUser.tokenVersion) and whose financier is usable (not suspended, plan not expired). Without it a
 * deactivated agent, a phone whose PIN was reset, or a suspended financier keeps working until the access
 * token expires (12 h). One primary-key lookup per request.
 */
@Component
public class TokenState implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
            new OAuth2Error("invalid_token", "Signed out: account deactivated, PIN changed, or financier suspended / plan ended", null);

    private final AppUserRepository users;
    private final Clock clock;

    public TokenState(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        List<Object[]> rows = users.tokenState(userId);
        if (rows.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        Object[] r = rows.get(0);
        boolean ok = isCurrent(JwtService.tokenVersion(jwt), (Boolean) r[0], ((Number) r[1]).intValue())
                && tenantUsable((Tenant.Status) r[2], (LocalDate) r[3], LocalDate.now(clock));
        return ok ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }

    /** Same rule as Tenant.isUsable; no tenant (SUPER_ADMIN) is always usable. */
    static boolean tenantUsable(Tenant.Status status, LocalDate planExpiry, LocalDate today) {
        return status != Tenant.Status.SUSPENDED && (planExpiry == null || !planExpiry.isBefore(today));
    }

    /** Token usable: user active and its version is the current one. */
    static boolean isCurrent(int tokenVersion, boolean active, int currentVersion) {
        return active && tokenVersion == currentVersion;
    }
}
