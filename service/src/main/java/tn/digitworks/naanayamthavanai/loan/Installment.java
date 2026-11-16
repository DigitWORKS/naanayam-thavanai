package tn.digitworks.naanayamthavanai.loan;

import tn.digitworks.naanayamthavanai.loan.missed.InstallmentRow;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import tn.digitworks.naanayamthavanai.payment.allocation.InstallmentBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "installment")
public class Installment {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal dueAmount;

    @Column(name = "principal_component", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalComponent = ZERO;

    @Column(name = "interest_component", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestComponent = ZERO;

    @Column(name = "principal_balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal principalBalanceAfter = ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = ZERO;

    @Column(name = "penalty_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal penaltyAmount = ZERO;

    @Column(name = "penalty_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal penaltyPaid = ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "is_extension", nullable = false)
    private boolean extension;

    @Column(name = "extended_from")
    private Long extendedFrom;

    protected Installment() {
    }

    public Installment(Long tenantId, Long loanId, int seqNo, LocalDate dueDate, BigDecimal dueAmount,
                       BigDecimal principalComponent, BigDecimal interestComponent,
                       BigDecimal principalBalanceAfter) {
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.seqNo = seqNo;
        this.dueDate = dueDate;
        this.dueAmount = dueAmount;
        this.principalComponent = principalComponent;
        this.interestComponent = interestComponent;
        this.principalBalanceAfter = principalBalanceAfter;
    }

    public static Installment extension(Long tenantId, Long loanId, InstallmentRow row) {
        Installment i = new Installment(tenantId, loanId, row.seqNo(), row.dueDate(), row.dueAmount(),
                row.principalComponent(), row.interestComponent(), ZERO);
        i.extension = true;
        i.extendedFrom = row.extendedFrom();
        return i;
    }

    // ------------------------------------------------------------- derived values

    /** Collectible installment amount (zero unless PENDING/PARTIAL). */
    public BigDecimal outstanding() {
        return status.isOpen() ? dueAmount.subtract(paidAmount) : ZERO;
    }

    /** Collectible penalty (zero once SETTLED). */
    public BigDecimal penaltyOutstanding() {
        return status == InstallmentStatus.SETTLED ? ZERO : penaltyAmount.subtract(penaltyPaid);
    }

    /** Interest share of {@code amount}, using this installment's interest/due ratio. */
    public BigDecimal interestShareOf(BigDecimal amount) {
        if (dueAmount.signum() == 0 || interestComponent.signum() == 0) {
            return ZERO;
        }
        return amount.multiply(interestComponent).divide(dueAmount, 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------- adapters to pure logic

    public InstallmentBalance toBalance() {
        return new InstallmentBalance(id, seqNo, dueDate, dueAmount, paidAmount, penaltyAmount, penaltyPaid, status);
    }

    public void apply(InstallmentBalance b) {
        this.paidAmount = b.paidAmount();
        this.penaltyPaid = b.penaltyPaid();
        this.status = b.status();
    }

    public InstallmentRow toRow() {
        return new InstallmentRow(id, seqNo, dueDate, dueAmount, principalComponent, interestComponent,
                paidAmount, penaltyAmount, penaltyPaid, status, extension, extendedFrom);
    }

    public void apply(InstallmentRow r) {
        this.status = r.status();
        this.penaltyAmount = r.penaltyAmount();
    }

    /** Pre-closure: close this installment with its final settled amounts. */
    public void settle(BigDecimal installmentPart, BigDecimal penaltyPart) {
        this.paidAmount = paidAmount.add(installmentPart);
        this.penaltyPaid = penaltyPaid.add(penaltyPart);
        this.status = InstallmentStatus.SETTLED;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getLoanId() { return loanId; }
    public int getSeqNo() { return seqNo; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getDueAmount() { return dueAmount; }
    public BigDecimal getPrincipalComponent() { return principalComponent; }
    public BigDecimal getInterestComponent() { return interestComponent; }
    public BigDecimal getPrincipalBalanceAfter() { return principalBalanceAfter; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public BigDecimal getPenaltyPaid() { return penaltyPaid; }
    public InstallmentStatus getStatus() { return status; }
    public boolean isExtension() { return extension; }
    public Long getExtendedFrom() { return extendedFrom; }
}
