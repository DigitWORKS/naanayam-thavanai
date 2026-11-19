package tn.digitworks.naanayamthavanai.handover;

import tn.digitworks.naanayamthavanai.handover.CashHandoverService.HandoverDecision;
import tn.digitworks.naanayamthavanai.handover.CashHandoverService.HandoverPreview;
import tn.digitworks.naanayamthavanai.handover.CashHandoverService.HandoverResponse;
import tn.digitworks.naanayamthavanai.handover.CashHandoverService.HandoverSubmit;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/cash-handovers")
public class CashHandoverController {

    private final CashHandoverService service;

    public CashHandoverController(CashHandoverService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','AGENT')")
    public List<HandoverResponse> list(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CashHandover.Status status) {
        return service.list(agentId, from, to, status);
    }

    @GetMapping("/preview")
    @PreAuthorize("hasRole('AGENT')")
    public HandoverPreview preview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.preview(date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENT')")
    public HandoverResponse submit(@Valid @RequestBody HandoverSubmit in) {
        return service.submit(in);
    }

    @GetMapping("/{handoverId}/payments")
    @PreAuthorize("hasAnyRole('OWNER','AGENT')")
    public List<CashHandoverService.HandoverPayment> payments(@PathVariable long handoverId) {
        return service.payments(handoverId);
    }

    @PostMapping("/{handoverId}/confirm")
    @PreAuthorize("hasRole('OWNER')")
    public HandoverResponse decide(@PathVariable long handoverId, @Valid @RequestBody HandoverDecision in) {
        return service.decide(handoverId, in);
    }
}
