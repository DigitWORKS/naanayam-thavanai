package tn.digitworks.naanayamthavanai.loan;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestCollection;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Loan terms are fixed after creation (no renewal / top-up). */
@Entity
@Table(name = "loan")
public class Loan {

    public enum Status { ACTIVE, CLOSED, PRECLOSED, DEFAULTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "loan_no", nullable = false, length = 30)
    private String loanNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal principal;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false)
    private InterestMethod interestMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_collection", nullable = false)
    private InterestCollection interestCollection;

    @Column(name = "interest_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "disbursed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal disbursedAmount;

    @Column(name = "total_payable", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPayable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(name = "installment_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "tenure_count", nullable = false)
    private int tenureCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "original_end_date", nullable = false)
    private LocalDate originalEndDate;

    @Column(name = "current_end_date", nullable = false)
    private LocalDate currentEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "legal_note", length = 1000)
    private String legalNote;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Loan() {
    }

    public Loan(Long tenantId, Long clientId, String loanNo, Long createdBy) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.loanNo = loanNo;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void close(Status finalStatus, LocalDateTime at) {
        this.status = finalStatus;
        this.closedAt = at;
    }

    public void reopen() {
        this.status = Status.ACTIVE;
        this.closedAt = null;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getClientId() { return clientId; }
    public String getLoanNo() { return loanNo; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal v) { this.principal = v; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal v) { this.interestRate = v; }
    public InterestMethod getInterestMethod() { return interestMethod; }
    public void setInterestMethod(InterestMethod v) { this.interestMethod = v; }
    public InterestCollection getInterestCollection() { return interestCollection; }
    public void setInterestCollection(InterestCollection v) { this.interestCollection = v; }
    public BigDecimal getInterestAmount() { return interestAmount; }
    public void setInterestAmount(BigDecimal v) { this.interestAmount = v; }
    public BigDecimal getDisbursedAmount() { return disbursedAmount; }
    public void setDisbursedAmount(BigDecimal v) { this.disbursedAmount = v; }
    public BigDecimal getTotalPayable() { return totalPayable; }
    public void setTotalPayable(BigDecimal v) { this.totalPayable = v; }
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency v) { this.frequency = v; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(BigDecimal v) { this.installmentAmount = v; }
    public int getTenureCount() { return tenureCount; }
    public void setTenureCount(int v) { this.tenureCount = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getOriginalEndDate() { return originalEndDate; }
    public void setOriginalEndDate(LocalDate v) { this.originalEndDate = v; }
    public LocalDate getCurrentEndDate() { return currentEndDate; }
    public void setCurrentEndDate(LocalDate v) { this.currentEndDate = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public String getLegalNote() { return legalNote; }
    public void setLegalNote(String v) { this.legalNote = v; }
}
