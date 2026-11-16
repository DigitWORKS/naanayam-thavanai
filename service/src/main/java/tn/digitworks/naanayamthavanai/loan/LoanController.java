package tn.digitworks.naanayamthavanai.loan;

import tn.digitworks.naanayamthavanai.common.PageResponse;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.DefaultRequest;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.InstallmentResponse;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanCreate;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanPreview;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanResponse;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanSummary;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.LedgerEntry;
import tn.digitworks.naanayamthavanai.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.List;

@RestController
@RequestMapping("/loans")
@PreAuthorize("hasAnyRole('OWNER','AGENT')")
public class LoanController {

    private final LoanService service;
    private final PaymentService payments;

    public LoanController(LoanService service, PaymentService payments) {
        this.service = service;
        this.payments = payments;
    }

    @GetMapping
    public PageResponse<LoanSummary> list(@RequestParam(required = false) Long clientId,
                                          @RequestParam(required = false) Loan.Status status,
                                          @RequestParam(required = false) Long areaId,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(clientId, status, areaId, page, size);
    }

    /** Owners disburse loans; agents can preview. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public LoanResponse create(@Valid @RequestBody LoanCreate in) {
        return service.create(in);
    }

    @PostMapping("/preview")
    public LoanPreview preview(@Valid @RequestBody LoanCreate in) {
        return service.preview(in);
    }

    @GetMapping("/{loanId}")
    public LoanResponse get(@PathVariable long loanId) {
        return service.get(loanId);
    }

    @GetMapping("/{loanId}/installments")
    public List<InstallmentResponse> installments(@PathVariable long loanId) {
        return service.installments(loanId);
    }

    @GetMapping("/{loanId}/ledger")
    public List<LedgerEntry> ledger(@PathVariable long loanId) {
        return payments.ledger(loanId);
    }

    @PostMapping("/{loanId}/default")
    @PreAuthorize("hasRole('OWNER')")
    public LoanResponse markDefaulted(@PathVariable long loanId, @Valid @RequestBody DefaultRequest in) {
        return service.markDefaulted(loanId, in.legalNote());
    }
}
