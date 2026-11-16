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

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> list(Tenant.Status status, int page, int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Tenant> result = status == null ? tenants.findAll(pr) : tenants.findByStatus(status, pr);
        return PageResponse.of(result, TenantResponse::of);
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
        if (in.status() != null) {
            t.setStatus(in.status());
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

    private Tenant find(long id) {
        return tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
    }
}
