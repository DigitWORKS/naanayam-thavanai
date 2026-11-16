package tn.digitworks.naanayamthavanai.preclosure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Snapshot of the settlement calculation at pre-closure. */
@Entity
@Table(name = "loan_preclosure")
public class LoanPreclosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "outstanding_principal", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(name = "outstanding_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingInterest;

    @Column(name = "pending_penalty", nullable = false, precision = 10, scale = 2)
    private BigDecimal pendingPenalty;

    @Column(name = "interest_rebate", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestRebate;

    @Column(name = "preclosure_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal preclosureCharge;

    @Column(name = "settlement_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "closed_by", nullable = false)
    private Long closedBy;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    protected LoanPreclosure() {
    }

    public LoanPreclosure(Long tenantId, Long loanId, Long paymentId, PreclosureCalculator.Quote q,
                          Long closedBy, LocalDateTime closedAt) {
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.paymentId = paymentId;
        this.outstandingPrincipal = q.outstandingPrincipal();
        this.outstandingInterest = q.outstandingInterest();
        this.pendingPenalty = q.pendingPenalty();
        this.interestRebate = q.interestRebate();
        this.preclosureCharge = q.preclosureCharge();
        this.settlementAmount = q.settlementAmount();
        this.closedBy = closedBy;
        this.closedAt = closedAt;
    }

    public Long getPaymentId() { return paymentId; }
}
