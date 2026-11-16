package tn.digitworks.naanayamthavanai.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** How a payment was applied to one installment. Negative values on REVERSAL payments. */
@Entity
@Table(name = "payment_allocation")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "installment_id", nullable = false)
    private Long installmentId;

    /** Applied to the installment amount (excludes penalty). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Interest within {@link #amount}; drives the interest income report. */
    @Column(name = "interest_part", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestPart;

    @Column(name = "penalty_part", nullable = false, precision = 10, scale = 2)
    private BigDecimal penaltyPart;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(Long tenantId, Long paymentId, Long installmentId, BigDecimal amount,
                             BigDecimal interestPart, BigDecimal penaltyPart) {
        this.tenantId = tenantId;
        this.paymentId = paymentId;
        this.installmentId = installmentId;
        this.amount = amount;
        this.interestPart = interestPart;
        this.penaltyPart = penaltyPart;
    }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public Long getInstallmentId() { return installmentId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getInterestPart() { return interestPart; }
    public BigDecimal getPenaltyPart() { return penaltyPart; }
}
