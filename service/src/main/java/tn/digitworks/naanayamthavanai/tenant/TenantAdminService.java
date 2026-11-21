package tn.digitworks.naanayamthavanai.tenant;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.common.PageResponse;
import tn.digitworks.naanayamthavanai.tenant.TenantDtos.TenantCreate;
import tn.digitworks.naanayamthavanai.tenant.TenantDtos.TenantResponse;
import tn.digitworks.naanayamthavanai.tenant.TenantDtos.TenantUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Super Admin: financier (tenant) management. */
@Service
public class TenantAdminService {

    private final TenantRepository tenants;
    private final TenantOnboarding onboarding;
    private final Clock clock;

    public TenantAdminService(TenantRepository tenants, TenantOnboarding onboarding, Clock clock) {
        this.tenants = tenants;
        this.onboarding = onboarding;
        this.clock = clock;
    }

    /** Days ahead counted as "expiring soon" (today included). */
    static final int EXPIRING_DAYS = 7;

    /**
     * Newest first; with {@code expiringSoon}, soonest expiry first. {@code q}: business or owner name (contains,
     * any case) or phone digits.
     */
    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> list(Tenant.Status status, String q, boolean expiringSoon, int page, int size) {
        String text = q == null ? "" : q.trim().toLowerCase();
        String nameLike = text.isEmpty() ? null : "%" + escapeLike(text) + "%";
        String digits = text.replaceAll("\\D", "");
        String phoneLike = digits.isEmpty() ? "~" : "%" + digits + "%";
        LocalDate today = LocalDate.now(clock);
        Sort sort = expiringSoon
                ? Sort.by(Sort.Direction.ASC, "planExpiry").and(Sort.by(Sort.Direction.DESC, "id"))
                : Sort.by(Sort.Direction.DESC, "id");
        Page<Tenant> result = tenants.search(status, nameLike, phoneLike,
                expiringSoon ? today : null, expiringSoon ? today.plusDays(EXPIRING_DAYS - 1) : null,
                PageRequest.of(page, size, sort));
        return PageResponse.of(result, TenantResponse::of);
    }

    @Transactional(readOnly = true)
    public TenantDtos.TenantSummary summary() {
        LocalDate today = LocalDate.now(clock);
        return new TenantDtos.TenantSummary(tenants.count(), tenants.countByStatus(Tenant.Status.TRIAL),
                tenants.countExpiring(today, today.plusDays(EXPIRING_DAYS - 1)),
                tenants.countByStatus(Tenant.Status.SUSPENDED));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Creates the tenant, its OWNER login (same phone) and default settings. Any plan is allowed here. */
    @Transactional
    public TenantResponse create(TenantCreate in) {
        TenantOnboarding.Created c = onboarding.create(new TenantOnboarding.NewFinancier(
                in.name(), in.licenceNo(), in.maxInterestRate(), in.ownerName(), in.phone(), in.ownerPin(),
                in.plan(), in.planExpiry(), false));
        return TenantResponse.of(c.tenant());
    }

    @Transactional(readOnly = true)
    public TenantResponse get(long id) {
        return TenantResponse.of(find(id));
    }

    @Transactional
    public TenantResponse update(long id, TenantUpdate in) {
        Tenant t = find(id);
        if (t.getStatus() == Tenant.Status.CLOSED) {
            throw ApiException.rule("TENANT_CLOSED", "The owner deleted this business. Restore it before changing it.");
        }
        if (in.status() == Tenant.Status.CLOSED) {
            throw ApiException.rule("INVALID_STATUS", "Only the owner can delete the business account");
        }
        if (in.status() == Tenant.Status.SUSPENDED) {
            t.setStatus(Tenant.Status.SUSPENDED);
        } else if (in.status() != null && t.getStatus() == Tenant.Status.SUSPENDED) {
            // Lifting a suspension: the status follows the plan (a trial stays TRIAL), whatever value was sent.
            t.setStatus(t.getPlan() == null ? Tenant.Status.ACTIVE : t.getPlan().status());
        }
        if (in.plan() != null) {
            t.applyPlan(in.plan(), LocalDate.now(clock));
        }
        if (in.planExpiry() != null) {
            t.setPlanExpiry(in.planExpiry());
        }
        if (in.licenceNo() != null) {
            t.setLicenceNo(in.licenceNo().isBlank() ? null : in.licenceNo().trim());
        }
        return TenantResponse.of(t);
    }

    /** Undoes the owner's delete request before the erase date. Users sign in again with their existing PINs. */
    @Transactional
    public TenantResponse restore(long id) {
        Tenant t = find(id);
        if (t.getStatus() != Tenant.Status.CLOSED) {
            throw ApiException.rule("NOT_CLOSED", "This business is not closed");
        }
        t.restore();
        return TenantResponse.of(t);
    }

    private Tenant find(long id) {
        return tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
    }
}
