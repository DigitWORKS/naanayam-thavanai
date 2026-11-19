package tn.digitworks.naanayamthavanai.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tn.digitworks.naanayamthavanai.user.AppUserRepository;

import java.util.List;

/**
 * Per-request check that an access token still belongs to an active user whose tokens were not revoked
 * (AppUser.tokenVersion). Without it a deactivated agent, or a phone whose PIN was reset, keeps working until
 * the access token expires (12 h). One primary-key lookup per request.
 */
@Component
public class TokenState implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
            new OAuth2Error("invalid_token", "Signed out: account deactivated or PIN changed", null);

    private final AppUserRepository users;

    public TokenState(AppUserRepository users) {
        this.users = users;
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
        boolean ok = !rows.isEmpty()
                && isCurrent(JwtService.tokenVersion(jwt), (Boolean) rows.get(0)[0], ((Number) rows.get(0)[1]).intValue());
        return ok ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }

    /** Token usable: user active and its version is the current one. */
    static boolean isCurrent(int tokenVersion, boolean active, int currentVersion) {
        return active && tokenVersion == currentVersion;
    }
}
