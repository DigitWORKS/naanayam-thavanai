package tn.digitworks.naanayamthavanai.payment;

import tn.digitworks.naanayamthavanai.payment.PaymentDtos.PaymentCreate;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.PaymentResponse;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.Recorded;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.ReverseRequest;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.SyncRequest;
import tn.digitworks.naanayamthavanai.payment.PaymentDtos.SyncResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments")
@PreAuthorize("hasAnyRole('OWNER','AGENT')")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** 201 when recorded, 200 when the same clientTxnId was already recorded. */
    @PostMapping
    public ResponseEntity<PaymentResponse> record(@Valid @RequestBody PaymentCreate in) {
        Recorded r = service.record(in);
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(r.payment());
    }

    @PostMapping("/sync")
    public List<SyncResult> sync(@Valid @RequestBody SyncRequest in) {
        return service.sync(in.payments());
    }

    @PostMapping("/{paymentId}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PaymentResponse reverse(@PathVariable long paymentId, @Valid @RequestBody ReverseRequest in) {
        return service.reverse(paymentId, in.reason());
    }
}
