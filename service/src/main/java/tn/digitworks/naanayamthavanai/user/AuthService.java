package tn.digitworks.naanayamthavanai.user;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.security.JwtService;
import tn.digitworks.naanayamthavanai.tenant.Plan;
import tn.digitworks.naanayamthavanai.tenant.Tenant;
import tn.digitworks.naanayamthavanai.tenant.TenantOnboarding;
import tn.digitworks.naanayamthavanai.tenant.TenantRepository;
import tn.digitworks.naanayamthavanai.user.UserDtos.TokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final UserService userService;
    private final TenantOnboarding onboarding;
    private final Clock clock;

    public AuthService(AppUserRepository users, TenantRepository tenants, PasswordEncoder passwordEncoder,
                       JwtService jwt, UserService userService, TenantOnboarding onboarding, Clock clock) {
        this.users = users;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.userService = userService;
        this.onboarding = onboarding;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse login(String phone, String pin) {
        AppUser user = users.findByPhone(phone)
                .filter(u -> passwordEncoder.matches(pin, u.getPinHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Phone number or PIN is incorrect"));
        checkCanSignIn(user);
        user.setLastLoginAt(LocalDateTime.now(clock));
        return tokens(user);
    }

    /**
     * Changes the caller's own PIN after checking the current one. Signs out every other device (token version)
     * and returns fresh tokens for this one. A wrong current PIN is a 400, not a 401, so clients do not treat
     * it as an expired session.
     */
    @Transactional
    public TokenResponse changePin(String currentPin, String newPin) {
        AppUser user = users.findById(CurrentUser.get().userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(currentPin, user.getPinHash())) {
            throw ApiException.rule("WRONG_PIN", "Current PIN is incorrect");
        }
        if (currentPin.equals(newPin)) {
            throw ApiException.rule("SAME_PIN", "Choose a PIN different from the current one");
        }
        user.setPinHash(passwordEncoder.encode(newPin));
        user.revokeTokens();
        return tokens(user);
    }

    /**
     * Public self-registration: creates the financier on a free trial and signs the owner in.
     * TODO: verify the mobile number with an OTP before this goes live (see docs/PROJECT_STATUS.md).
     */
    @Transactional
    public TokenResponse register(UserDtos.RegisterRequest in) {
        Plan plan = in.plan() == null ? Plan.TRIAL : in.plan();
        if (!plan.isSelfServe()) {
            throw ApiException.rule("PLAN_NEEDS_PAYMENT",
                    "Start with the free trial. Paid plans can be chosen once payments are available.");
        }
        TenantOnboarding.Created c = onboarding.create(new TenantOnboarding.NewFinancier(
                in.businessName(), in.licenceNo(), in.maxInterestRate(), in.ownerName(), in.phone(), in.pin(),
                plan, null, true));
        c.owner().setLastLoginAt(LocalDateTime.now(clock));
        return tokens(c.owner());
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        JwtService.RefreshClaims claims = jwt.readRefreshToken(refreshToken);
        AppUser user = users.findById(claims.userId())
                .filter(u -> u.getTokenVersion() == claims.tokenVersion())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired"));
        checkCanSignIn(user);
        return tokens(user);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse me() {
        AppUser user = users.findById(CurrentUser.get().userId()).orElseThrow(() -> ApiException.notFound("User"));
        return userService.toResponse(user);
    }

    /**
     * Owner deletes the business account after re-entering the PIN: the business closes at once, every login of
     * it is signed out, and all its data is erased {@value Tenant#ERASE_AFTER_DAYS} days later (TenantErasureService).
     */
    @Transactional
    public UserDtos.DeleteAccountResponse deleteAccount(String pin) {
        AppUser owner = users.findById(CurrentUser.get().userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(pin, owner.getPinHash())) {
            throw ApiException.rule("WRONG_PIN", "PIN is incorrect");
        }
        Tenant tenant = tenants.findById(owner.getTenantId()).orElseThrow(() -> ApiException.notFound("Tenant"));
        tenant.close(LocalDateTime.now(clock));
        users.findByTenantIdOrderByName(tenant.getId()).forEach(AppUser::revokeTokens);
        return new UserDtos.DeleteAccountResponse(tenant.getEraseOn());
    }

    private void checkCanSignIn(AppUser user) {
        if (!user.isActive()) {
            throw ApiException.forbidden("This account is deactivated");
        }
        if (user.getTenantId() != null) {
            Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow(() -> ApiException.notFound("Tenant"));
            if (tenant.getStatus() == Tenant.Status.CLOSED) {
                throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_CLOSED",
                        "This business account was deleted. Its data is erased on " + tenant.getEraseOn()
                                + "; contact support before then to restore it.");
            }
            if (!tenant.isUsable(LocalDate.now(clock))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_INACTIVE",
                        "This business account is suspended or its plan has ended");
            }
        }
    }

    private TokenResponse tokens(AppUser user) {
        JwtService.Tokens t = jwt.issue(user);
        return new TokenResponse(t.accessToken(), t.refreshToken(), t.expiresIn(), userService.toResponse(user));
    }
}
