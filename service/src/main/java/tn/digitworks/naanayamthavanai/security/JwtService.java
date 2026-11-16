package tn.digitworks.naanayamthavanai.security;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Issues and reads our own HS256 tokens. Refresh tokens are stateless JWTs with typ=refresh. */
@Service
public class JwtService {

    public static final String CLAIM_TENANT = "tenantId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    private static final String ISSUER = "naanayam-thavanai";

    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(JwtEncoder encoder,
                      @org.springframework.beans.factory.annotation.Qualifier("refreshTokenDecoder") JwtDecoder refreshDecoder,
                      Clock clock,
                      @Value("${app.jwt.access-ttl:PT12H}") Duration accessTtl,
                      @Value("${app.jwt.refresh-ttl:P30D}") Duration refreshTtl) {
        this.encoder = encoder;
        this.refreshDecoder = refreshDecoder;
        this.clock = clock;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public record Tokens(String accessToken, String refreshToken, long expiresIn) {
    }

    public Tokens issue(AppUser user) {
        return new Tokens(
                encode(user, TYPE_ACCESS, accessTtl),
                encode(user, TYPE_REFRESH, refreshTtl),
                accessTtl.toSeconds());
    }

    /** Validates a refresh token and returns the user id it was issued for. */
    public long userIdFromRefreshToken(String token) {
        try {
            Jwt jwt = refreshDecoder.decode(token);
            if (!TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TYPE))) {
                throw invalid();
            }
            return Long.parseLong(jwt.getSubject());
        } catch (JwtException | NumberFormatException e) {
            throw invalid();
        }
    }

    private String encode(AppUser user, String type, Duration ttl) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, type);
        if (user.getTenantId() != null) {
            claims.claim(CLAIM_TENANT, user.getTenantId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }
}
