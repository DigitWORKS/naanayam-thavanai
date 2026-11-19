package tn.digitworks.naanayamthavanai.tenant;

import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InterestMethod;
import tn.digitworks.naanayamthavanai.payment.allocation.AllocationPolicy;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.ChargeType;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.PenaltyType;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.PreclosureRebate;
import tn.digitworks.naanayamthavanai.user.UserDtos;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public final class TenantDtos {

    private TenantDtos() {
    }

    public record TenantResponse(Long id, String name, String ownerName, String phone, String licenceNo,
                                 Tenant.Status status, Plan plan, LocalDate planExpiry,
                                 LocalDateTime termsAcceptedAt, LocalDateTime createdAt) {
        public static TenantResponse of(Tenant t) {
            return new TenantResponse(t.getId(), t.getName(), t.getOwnerName(), t.getPhone(), t.getLicenceNo(),
                    t.getStatus(), t.getPlan(), t.getPlanExpiry(), t.getTermsAcceptedAt(), t.getCreatedAt());
        }
    }

    /** Super Admin header cards. expiringSoon: plan ends within the next 7 days (today included), not suspended. */
    public record TenantSummary(long all, long trial, long expiringSoon, long suspended) {
    }

    /** Super Admin onboarding. plan defaults to TRIAL; planExpiry overrides the date the plan gives. */
    public record TenantCreate(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 100) String ownerName,
            @NotBlank @Pattern(regexp = UserDtos.PHONE) String phone,
            @Size(max = 50) String licenceNo,
            @NotBlank @Pattern(regexp = UserDtos.PIN, message = "PIN must be 4 to 6 digits") String ownerPin,
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal maxInterestRate,
            Plan plan,
            LocalDate planExpiry) {
    }

    /**
     * Super Admin update. plan starts/renews that plan from today (keeping unused days); planExpiry, applied
     * after it, sets the date directly.
     */
    public record TenantUpdate(Tenant.Status status, Plan plan, LocalDate planExpiry,
                               @Size(max = 50) String licenceNo) {
    }

    public record SettingsDto(
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal maxInterestRate,
            @NotEmpty Set<InterestMethod> allowedInterestMethods,
            @NotNull @Min(1) Integer maxActiveLoansPerClient,
            boolean penaltyEnabled,
            PenaltyType penaltyType,
            @DecimalMin("0.00") BigDecimal penaltyValue,
            @Min(0) int penaltyGraceDays,
            @DecimalMin("0.00") BigDecimal penaltyMaxCap,
            LocalDate penaltyEffectiveFrom,
            @Min(1) int recordRetentionYears,
            AllocationPolicy.Order allocationOrder,
            AllocationPolicy.Advance advanceAllocation,
            boolean preclosureEnabled,
            PreclosureRebate preclosureRebate,
            ChargeType preclosureChargeType,
            @DecimalMin("0.00") BigDecimal preclosureChargeValue) {

        public static SettingsDto of(TenantSettings s) {
            return new SettingsDto(s.getMaxInterestRate(), s.getAllowedMethods(), s.getMaxActiveLoansPerClient(),
                    s.isPenaltyEnabled(), s.getPenaltyType(), s.getPenaltyValue(), s.getPenaltyGraceDays(),
                    s.getPenaltyMaxCap(), s.getPenaltyEffectiveFrom(), s.getRecordRetentionYears(),
                    s.getAllocationOrder(), s.getAdvanceAllocation(), s.isPreclosureEnabled(),
                    s.getPreclosureRebate(), s.getPreclosureChargeType(), s.getPreclosureChargeValue());
        }
    }
}
