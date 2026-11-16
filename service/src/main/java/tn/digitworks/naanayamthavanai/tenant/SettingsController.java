package tn.digitworks.naanayamthavanai.tenant;

import tn.digitworks.naanayamthavanai.tenant.TenantDtos.SettingsDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','AGENT')")
    public SettingsDto get() {
        return service.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public SettingsDto update(@Valid @RequestBody SettingsDto in) {
        return service.update(in);
    }
}
