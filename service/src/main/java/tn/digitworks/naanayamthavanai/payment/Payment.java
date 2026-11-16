package tn.digitworks.naanayamthavanai.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Append-only. Corrections are REVERSAL rows with a negative amount. */
@Entity
@Table(name = "payment")
public class Payment {

    public enum EntryType { PAYMENT, REVERSAL }
    public enum Purpose { REGULAR, PRECLOSURE }
    public enum Mode { CASH, UPI }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType = EntryType.PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose = Purpose.REGULAR;

    @Column(name = "reversal_of")
    private Long reversalOf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "upi_ref_no", length = 50)
    private String upiRefNo;

    /** Collection time on the device, in IST. */
    @Column(name = "paid_on", nullable = false)
    private LocalDateTime paidOn;

    @Column(name = "collected_by", nullable = false)
    private Long collectedBy;

    @Column(name = "client_txn_id", nullable = false, columnDefinition = "char(36)")
    private String clientTxnId;

    @Column(length = 255)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Payment() {
    }

    public Payment(Long tenantId, Long loanId, BigDecimal amount, Mode mode, String upiRefNo,
                   LocalDateTime paidOn, Long collectedBy, String clientTxnId, String remarks) {
        this.tenantId = tenantId;
        this.loanId = loanId;
        this.amount = amount;
        this.mode = mode;
        this.upiRefNo = upiRefNo;
        this.paidOn = paidOn;
        this.collectedBy = collectedBy;
        this.clientTxnId = clientTxnId;
        this.remarks = remarks;
    }

    public static Payment reversalOf(Payment original, String clientTxnId, LocalDateTime at, String remarks) {
        Payment r = new Payment(original.tenantId, original.loanId, original.amount.negate(), original.mode,
                original.upiRefNo, at, original.collectedBy, clientTxnId, remarks);
        r.entryType = EntryType.REVERSAL;
        r.purpose = original.purpose;
        r.reversalOf = original.id;
        return r;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getLoanId() { return loanId; }
    public BigDecimal getAmount() { return amount; }
    public EntryType getEntryType() { return entryType; }
    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }
    public Long getReversalOf() { return reversalOf; }
    public Mode getMode() { return mode; }
    public String getUpiRefNo() { return upiRefNo; }
    public LocalDateTime getPaidOn() { return paidOn; }
    public Long getCollectedBy() { return collectedBy; }
    public String getClientTxnId() { return clientTxnId; }
    public String getRemarks() { return remarks; }
}
