package tn.digitworks.naanayamthavanai.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant")
public class Tenant {

    /** CLOSED: the owner deleted the account; data is erased on {@link #getEraseOn()} unless restored. */
    public enum Status { TRIAL, ACTIVE, SUSPENDED, CLOSED }

    /** Days between the owner's delete request and the data being erased. */
    public static final int ERASE_AFTER_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "licence_no", length = 50)
    private String licenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.TRIAL;

    @Column(name = "plan_expiry")
    private LocalDate planExpiry;

    /** Set when the owner accepted the terms during self-registration. */
    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    /** When the owner deleted the account (status CLOSED). */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Date the nightly job erases this business's data. */
    @Column(name = "erase_on")
    private LocalDate eraseOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Starts or renews {@code newPlan} on {@code today}: sets the plan, extends the expiry (keeping unused days)
     * and moves TRIAL/ACTIVE status with it. A SUSPENDED tenant stays suspended until the status is changed.
     */
    public void applyPlan(Plan newPlan, LocalDate today) {
        this.planExpiry = newPlan.renewedExpiry(planExpiry, today);
        this.plan = newPlan;
        if (status != Status.SUSPENDED && status != Status.CLOSED) {
            this.status = newPlan.status();
        }
    }

    /** Owner deleted the account: nobody can sign in, and the data is erased {@value #ERASE_AFTER_DAYS} days later. */
    public void close(LocalDateTime now) {
        this.status = Status.CLOSED;
        this.closedAt = now;
        this.eraseOn = now.toLocalDate().plusDays(ERASE_AFTER_DAYS);
    }

    /** Super Admin undoes a closure before the erase date; the status follows the plan again. */
    public void restore() {
        this.status = plan == null ? Status.ACTIVE : plan.status();
        this.closedAt = null;
        this.eraseOn = null;
    }

    /** True when users of this tenant may sign in on {@code today}. */
    public boolean isUsable(LocalDate today) {
        return usable(status, planExpiry, today);
    }

    /** Shared with the per-request token check, which reads the two columns without loading the entity. */
    public static boolean usable(Status status, LocalDate planExpiry, LocalDate today) {
        return (status == Status.TRIAL || status == Status.ACTIVE)
                && (planExpiry == null || !planExpiry.isBefore(today));
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLicenceNo() { return licenceNo; }
    public void setLicenceNo(String licenceNo) { this.licenceNo = licenceNo; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Plan getPlan() { return plan; }
    public LocalDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(LocalDateTime termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }
    public LocalDate getPlanExpiry() { return planExpiry; }
    public void setPlanExpiry(LocalDate planExpiry) { this.planExpiry = planExpiry; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public LocalDate getEraseOn() { return eraseOn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
