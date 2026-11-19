package tn.digitworks.naanayamthavanai.tenant;

import tn.digitworks.naanayamthavanai.common.PageResponse;
import tn.digitworks.naanayamthavanai.tenant.TenantDtos.TenantCreate;
import tn.digitworks.naanayamthavanai.tenant.TenantDtos.TenantResponse;
import tn.digitworks.naanayamthavanai.tenant.TenantDtos.TenantUpdate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantAdminController {

    private final TenantAdminService service;

    public TenantAdminController(TenantAdminService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TenantResponse> list(@RequestParam(required = false) Tenant.Status status,
                                             @RequestParam(required = false) @Size(max = 100) String q,
                                             @RequestParam(defaultValue = "false") boolean expiringSoon,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, q, expiringSoon, page, size);
    }

    @GetMapping("/summary")
    public TenantDtos.TenantSummary summary() {
        return service.summary();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody TenantCreate in) {
        return service.create(in);
    }

    @GetMapping("/{tenantId}")
    public TenantResponse get(@PathVariable long tenantId) {
        return service.get(tenantId);
    }

    @PatchMapping("/{tenantId}")
    public TenantResponse update(@PathVariable long tenantId, @Valid @RequestBody TenantUpdate in) {
        return service.update(tenantId, in);
    }
}
