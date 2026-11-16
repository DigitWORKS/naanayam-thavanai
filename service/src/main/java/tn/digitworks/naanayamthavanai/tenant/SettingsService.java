package tn.digitworks.naanayamthavanai.tenant;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.tenant.TenantDtos.SettingsDto;
import tn.digitworks.naanayamthavanai.tenant.TenantSettings.ChargeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class SettingsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TenantSettingsRepository settings;
    private final Clock clock;

    public SettingsService(TenantSettingsRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /** Settings of the given tenant, for use by other services. */
    @Transactional(readOnly = true)
    public TenantSettings forTenant(long tenantId) {
        return settings.findById(tenantId).orElseThrow(() -> ApiException.notFound("Tenant settings"));
    }

    @Transactional(readOnly = true)
    public SettingsDto get() {
        return SettingsDto.of(forTenant(CurrentUser.get().requireTenant()));
    }

    @Transactional
    public SettingsDto update(SettingsDto in) {
        TenantSettings s = forTenant(CurrentUser.get().requireTenant());
        validate(in);

        boolean penaltyTurnedOn = in.penaltyEnabled() && !s.isPenaltyEnabled();

        s.setMaxInterestRate(in.maxInterestRate());
        s.setAllowedMethods(in.allowedInterestMethods());
        s.setMaxActiveLoansPerClient(in.maxActiveLoansPerClient());
        s.setPenaltyEnabled(in.penaltyEnabled());
        s.setPenaltyType(in.penaltyType());
        s.setPenaltyValue(in.penaltyValue());
        s.setPenaltyGraceDays(in.penaltyGraceDays());
        s.setPenaltyMaxCap(in.penaltyMaxCap());
        if (penaltyTurnedOn) {
            // Missed days before today are never charged retroactively (client value is ignored)
            s.setPenaltyEffectiveFrom(LocalDate.now(clock));
        }
        s.setRecordRetentionYears(in.recordRetentionYears());
        if (in.allocationOrder() != null) {
            s.setAllocationOrder(in.allocationOrder());
        }
        if (in.advanceAllocation() != null) {
            s.setAdvanceAllocation(in.advanceAllocation());
        }
        s.setPreclosureEnabled(in.preclosureEnabled());
        if (in.preclosureRebate() != null) {
            s.setPreclosureRebate(in.preclosureRebate());
        }
        s.setPreclosureChargeType(in.preclosureChargeType() == null ? ChargeType.NONE : in.preclosureChargeType());
        s.setPreclosureChargeValue(in.preclosureChargeValue());
        return SettingsDto.of(s);
    }

    private static void validate(SettingsDto in) {
        if (in.penaltyEnabled()) {
            if (in.penaltyType() == null || in.penaltyValue() == null || in.penaltyValue().signum() <= 0) {
                throw ApiException.rule("INVALID_PENALTY", "Penalty type and a positive value are required");
            }
            if (in.penaltyType() == TenantSettings.PenaltyType.PERCENT && in.penaltyValue().compareTo(HUNDRED) > 0) {
                throw ApiException.rule("INVALID_PENALTY", "Penalty percent cannot be more than 100");
            }
        }
        ChargeType charge = in.preclosureChargeType() == null ? ChargeType.NONE : in.preclosureChargeType();
        if (charge != ChargeType.NONE
                && (in.preclosureChargeValue() == null || in.preclosureChargeValue().signum() <= 0)) {
            throw ApiException.rule("INVALID_PRECLOSURE_CHARGE", "A positive pre-closure charge value is required");
        }
        if (charge == ChargeType.PERCENT && in.preclosureChargeValue().compareTo(HUNDRED) > 0) {
            throw ApiException.rule("INVALID_PRECLOSURE_CHARGE", "Pre-closure charge percent cannot be more than 100");
        }
    }
}
