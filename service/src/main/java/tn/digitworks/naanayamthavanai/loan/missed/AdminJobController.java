package tn.digitworks.naanayamthavanai.loan.missed;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/** Super Admin: run the nightly job on demand (testing, or after downtime). */
@RestController
@RequestMapping("/admin/jobs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminJobController {

    private final MissedInstallmentService service;
    private final Clock clock;

    public AdminJobController(MissedInstallmentService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping("/missed-installments")
    public MissedInstallmentService.RunSummary runMissedInstallments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        return service.run(date == null || date.isAfter(today) ? today : date);
    }
}
