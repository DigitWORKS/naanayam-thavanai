package tn.digitworks.naanayamthavanai.tenant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.user.AppUser;
import tn.digitworks.naanayamthavanai.user.AppUserRepository;
import tn.digitworks.naanayamthavanai.user.Role;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Creates a financier: the tenant (with its plan), default settings and the OWNER login (same phone).
 * Used by Super Admin onboarding and by self-registration.
 */
@Service
public class TenantOnboarding {

    /**
     * @param planExpiry    optional override (Super Admin only); null = from the plan
     * @param termsAccepted true when the owner accepted the terms (self-registration)
     */
    public record NewFinancier(String businessName, String licenceNo, BigDecimal maxInterestRate,
                               String ownerName, String phone, String pin, Plan plan, LocalDate planExpiry,
                               boolean termsAccepted) {
    }

    public record Created(Tenant tenant, AppUser owner) {
    }

    private final TenantRepository tenants;
    private final TenantSettingsRepository settings;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public TenantOnboarding(TenantRepository tenants, TenantSettingsRepository settings, AppUserRepository users,
                            PasswordEncoder passwordEncoder, Clock clock) {
        this.tenants = tenants;
        this.settings = settings;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Created create(NewFinancier in) {
        if (tenants.existsByPhone(in.phone())) {
            throw ApiException.conflict("TENANT_EXISTS", "A financier with this phone number already exists");
        }
        if (users.existsByPhone(in.phone())) {
            throw ApiException.conflict("PHONE_IN_USE", "This phone number is already used by another login");
        }

        Tenant t = new Tenant();
        t.setName(in.businessName().trim());
        t.setOwnerName(in.ownerName().trim());
        t.setPhone(in.phone());
        t.setLicenceNo(in.licenceNo() == null || in.licenceNo().isBlank() ? null : in.licenceNo().trim());
        t.applyPlan(in.plan() == null ? Plan.TRIAL : in.plan(), LocalDate.now(clock));
        if (in.planExpiry() != null) {
            t.setPlanExpiry(in.planExpiry());
        }
        if (in.termsAccepted()) {
            t.setTermsAcceptedAt(LocalDateTime.now(clock));
        }
        t = tenants.save(t);

        settings.save(new TenantSettings(t.getId(), in.maxInterestRate()));
        AppUser owner = users.save(new AppUser(t.getId(), in.ownerName().trim(), in.phone(),
                passwordEncoder.encode(in.pin()), Role.OWNER));
        return new Created(t, owner);
    }
}
