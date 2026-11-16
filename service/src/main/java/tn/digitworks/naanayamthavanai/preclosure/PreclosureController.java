package tn.digitworks.naanayamthavanai.preclosure;

import tn.digitworks.naanayamthavanai.preclosure.PreclosureDtos.PreclosureConfirm;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureDtos.PreclosureResult;
import tn.digitworks.naanayamthavanai.preclosure.PreclosureDtos.QuoteResponse;
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

@RestController
@RequestMapping("/loans/{loanId}")
public class PreclosureController {

    private final PreclosureService service;

    public PreclosureController(PreclosureService service) {
        this.service = service;
    }

    @GetMapping("/preclosure-quote")
    @PreAuthorize("hasAnyRole('OWNER','AGENT')")
    public QuoteResponse quote(@PathVariable long loanId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.quote(loanId, asOf);
    }

    /** Owner only: closes the loan for good. */
    @PostMapping("/preclose")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PreclosureResult preclose(@PathVariable long loanId, @Valid @RequestBody PreclosureConfirm in) {
        return service.confirm(loanId, in);
    }
}
