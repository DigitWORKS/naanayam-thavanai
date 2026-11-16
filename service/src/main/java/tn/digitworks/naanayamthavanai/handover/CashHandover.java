package tn.digitworks.naanayamthavanai.handover;

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

/** Agent hands the day's cash to the owner. */
@Entity
@Table(name = "cash_handover")
public class CashHandover {

    public enum Status { PENDING, CONFIRMED, DISPUTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "handover_date", nullable = false)
    private LocalDate handoverDate;

    @Column(name = "expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "handed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal handedAmount;

    @Column(name = "received_by")
    private Long receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(length = 255)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected CashHandover() {
    }

    public CashHandover(Long tenantId, Long agentId, LocalDate handoverDate, BigDecimal expectedAmount,
                        BigDecimal handedAmount, String remarks) {
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.handoverDate = handoverDate;
        this.expectedAmount = expectedAmount;
        this.handedAmount = handedAmount;
        this.remarks = remarks;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void decide(Status decision, Long ownerId, String ownerRemarks, LocalDateTime at) {
        this.status = decision;
        this.receivedBy = ownerId;
        this.confirmedAt = at;
        if (ownerRemarks != null && !ownerRemarks.isBlank()) {
            this.remarks = ownerRemarks;
        }
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public LocalDate getHandoverDate() { return handoverDate; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public BigDecimal getHandedAmount() { return handedAmount; }
    public Long getReceivedBy() { return receivedBy; }
    public Status getStatus() { return status; }
    public String getRemarks() { return remarks; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
}
