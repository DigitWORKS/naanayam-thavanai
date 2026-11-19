package tn.digitworks.naanayamthavanai.security;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, read from the JWT. tenantId is taken ONLY from here, never from the request.
 *
 * @param tenantId null for SUPER_ADMIN
 */
public record CurrentUser(long userId, Long tenantId, Role role) {

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not signed in");
        }
        Number tenant = jwt.getClaim(JwtService.CLAIM_TENANT);
        return new CurrentUser(
                Long.parseLong(jwt.getSubject()),
                tenant == null ? null : tenant.longValue(),
                Role.valueOf(jwt.getClaimAsString(JwtService.CLAIM_ROLE)));
    }

    /** Tenant of the caller; fails for SUPER_ADMIN, who has no tenant. */
    public long requireTenant() {
        if (tenantId == null) {
            throw ApiException.forbidden("This action needs a business account");
        }
        return tenantId;
    }

    public boolean isAgent() {
        return role == Role.AGENT;
    }

    public boolean isOwner() {
        return role == Role.OWNER;
    }
}
