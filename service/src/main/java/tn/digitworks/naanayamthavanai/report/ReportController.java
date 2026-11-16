package tn.digitworks.naanayamthavanai.report;

import tn.digitworks.naanayamthavanai.common.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
@PreAuthorize("hasRole('OWNER')")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ReportService.Dashboard dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.dashboard(date);
    }

    @GetMapping("/collections")
    public List<ReportService.CollectionRow> collections(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportService.GroupBy groupBy) {
        return service.collections(from, to, groupBy);
    }

    /** JSON for now; XLSX/PDF export comes later. */
    @GetMapping("/interest-income")
    public ReportService.InterestIncome interestIncome(@RequestParam int fy,
                                                       @RequestParam(defaultValue = "JSON") String format) {
        if (!"JSON".equalsIgnoreCase(format)) {
            throw ApiException.rule("FORMAT_NOT_SUPPORTED", "Only JSON is available right now");
        }
        if (fy < 2000 || fy > 2100) {
            throw ApiException.rule("INVALID_FY", "fy must be the starting year, e.g. 2026 for FY 2026-27");
        }
        return service.interestIncome(fy);
    }
}
