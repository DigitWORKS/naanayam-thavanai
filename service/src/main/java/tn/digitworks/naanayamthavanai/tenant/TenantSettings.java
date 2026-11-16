package tn.digitworks.naanayamthavanai.tenant;

import tn.digitworks.naanayamthavanai.loan.missed.PenaltyPolicy;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    public enum PenaltyType { FIXED, PERCENT }
    public enum PreclosureRebate { NONE, UNEARNED_INTEREST }
    public enum ChargeType { NONE, FIXED, PERCENT }

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "max_interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxInterestRate;

    /** MySQL SET column, e.g. "FLAT,REDUCING". Use {@link #getAllowedMethods()}. */
    @Column(name = "allowed_interest_methods", nullable = false, columnDefinition = "set('FLAT','REDUCING')")
    private String allowedInterestMethods = "FLAT";

    @Column(name = "max_active_loans_per_client", nullable = false)
    private int maxActiveLoansPerClient = 2;

    @Column(name = "penalty_enabled", nullable = false)
    private boolean penaltyEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type")
    private PenaltyType penaltyType;

    @Column(name = "penalty_value", precision = 10, scale = 2)
    private BigDecimal penaltyValue;

    @Column(name = "penalty_grace_days", nullable = false)
    private int penaltyGraceDays;

    @Column(name = "penalty_max_cap", precision = 10, scale = 2)
    private BigDecimal penaltyMaxCap;

    @Column(name = "penalty_effective_from")
    private LocalDate penaltyEffectiveFrom;

    @Column(name = "record_retention_years", nullable = false)
    private int recordRetentionYears = 8;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_order", nullable = false)
    private AllocationPolicy.Order allocationOrder = AllocationPolicy.Order.PENALTY_FIRST;

    @Enumerated(EnumType.STRING)
    @Column(name = "advance_allocation", nullable = false)
    private AllocationPolicy.Advance advanceAllocation = AllocationPolicy.Advance.NEXT_INSTALLMENTS;

    @Column(name = "preclosure_enabled", nullable = false)
    private boolean preclosureEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "preclosure_rebate", nullable = false)
    private PreclosureRebate preclosureRebate = PreclosureRebate.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "preclosure_charge_type", nullable = false)
    private ChargeType preclosureChargeType = ChargeType.NONE;

    @Column(name = "preclosure_charge_value", precision = 10, scale = 2)
    private BigDecimal preclosureChargeValue;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected TenantSettings() {
    }

    public TenantSettings(Long tenantId, BigDecimal maxInterestRate) {
        this.tenantId = tenantId;
        this.maxInterestRate = maxInterestRate;
    }

    public Set<InterestMethod> getAllowedMethods() {
        if (allowedInterestMethods == null || allowedInterestMethods.isBlank()) {
            return EnumSet.noneOf(InterestMethod.class);
        }
        return Arrays.stream(allowedInterestMethods.split(","))
                .map(String::trim)
                .map(InterestMethod::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(InterestMethod.class)));
    }

    public void setAllowedMethods(Set<InterestMethod> methods) {
        this.allowedInterestMethods = EnumSet.copyOf(methods).stream()
                .map(Enum::name).collect(Collectors.joining(","));
    }

    public AllocationPolicy allocationPolicy() {
        return new AllocationPolicy(allocationOrder, advanceAllocation);
    }

    public PenaltyPolicy penaltyPolicy() {
        if (!penaltyEnabled || penaltyType == null) {
            return PenaltyPolicy.disabled();
        }
        return new PenaltyPolicy(true, PenaltyPolicy.Type.valueOf(penaltyType.name()), penaltyValue,
                penaltyGraceDays, penaltyMaxCap, penaltyEffectiveFrom);
    }

    public Long getTenantId() { return tenantId; }
    public BigDecimal getMaxInterestRate() { return maxInterestRate; }
    public void setMaxInterestRate(BigDecimal v) { this.maxInterestRate = v; }
    public int getMaxActiveLoansPerClient() { return maxActiveLoansPerClient; }
    public void setMaxActiveLoansPerClient(int v) { this.maxActiveLoansPerClient = v; }
    public boolean isPenaltyEnabled() { return penaltyEnabled; }
    public void setPenaltyEnabled(boolean v) { this.penaltyEnabled = v; }
    public PenaltyType getPenaltyType() { return penaltyType; }
    public void setPenaltyType(PenaltyType v) { this.penaltyType = v; }
    public BigDecimal getPenaltyValue() { return penaltyValue; }
    public void setPenaltyValue(BigDecimal v) { this.penaltyValue = v; }
    public int getPenaltyGraceDays() { return penaltyGraceDays; }
    public void setPenaltyGraceDays(int v) { this.penaltyGraceDays = v; }
    public BigDecimal getPenaltyMaxCap() { return penaltyMaxCap; }
    public void setPenaltyMaxCap(BigDecimal v) { this.penaltyMaxCap = v; }
    public LocalDate getPenaltyEffectiveFrom() { return penaltyEffectiveFrom; }
    public void setPenaltyEffectiveFrom(LocalDate v) { this.penaltyEffectiveFrom = v; }
    public int getRecordRetentionYears() { return recordRetentionYears; }
    public void setRecordRetentionYears(int v) { this.recordRetentionYears = v; }
    public AllocationPolicy.Order getAllocationOrder() { return allocationOrder; }
    public void setAllocationOrder(AllocationPolicy.Order v) { this.allocationOrder = v; }
    public AllocationPolicy.Advance getAdvanceAllocation() { return advanceAllocation; }
    public void setAdvanceAllocation(AllocationPolicy.Advance v) { this.advanceAllocation = v; }
    public boolean isPreclosureEnabled() { return preclosureEnabled; }
    public void setPreclosureEnabled(boolean v) { this.preclosureEnabled = v; }
    public PreclosureRebate getPreclosureRebate() { return preclosureRebate; }
    public void setPreclosureRebate(PreclosureRebate v) { this.preclosureRebate = v; }
    public ChargeType getPreclosureChargeType() { return preclosureChargeType; }
    public void setPreclosureChargeType(ChargeType v) { this.preclosureChargeType = v; }
    public BigDecimal getPreclosureChargeValue() { return preclosureChargeValue; }
    public void setPreclosureChargeValue(BigDecimal v) { this.preclosureChargeValue = v; }
}
