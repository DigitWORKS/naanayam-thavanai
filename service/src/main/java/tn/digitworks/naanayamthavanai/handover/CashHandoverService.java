package tn.digitworks.naanayamthavanai.handover;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.payment.Payment;
import tn.digitworks.naanayamthavanai.payment.PaymentRepository;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import tn.digitworks.naanayamthavanai.user.AppUser;
import tn.digitworks.naanayamthavanai.user.AppUserRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CashHandoverService {

    public record HandoverResponse(Long id, Long agentId, String agentName, LocalDate handoverDate,
                                   BigDecimal expectedAmount, BigDecimal handedAmount, BigDecimal difference,
                                   CashHandover.Status status, Long receivedBy, String remarks,
                                   LocalDateTime confirmedAt) {
    }

    public record HandoverSubmit(@NotNull LocalDate handoverDate,
                                 @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal handedAmount,
                                 @Size(max = 255) String remarks) {
    }

    public record HandoverDecision(@NotNull CashHandover.Status status, @Size(max = 255) String remarks) {
    }

    private final CashHandoverRepository handovers;
    private final PaymentRepository payments;
    private final AppUserRepository users;
    private final Clock clock;

    public CashHandoverService(CashHandoverRepository handovers, PaymentRepository payments,
                               AppUserRepository users, Clock clock) {
        this.handovers = handovers;
        this.payments = payments;
        this.users = users;
        this.clock = clock;
    }

    /** Agents see their own handovers; owners see all (optionally one agent). */
    @Transactional(readOnly = true)
    public List<HandoverResponse> list(Long agentId, LocalDate from, LocalDate to, CashHandover.Status status) {
        CurrentUser me = CurrentUser.get();
        Long agent = me.isAgent() ? Long.valueOf(me.userId()) : agentId;
        List<CashHandover> list = handovers.search(me.requireTenant(), agent, status, from, to);
        Map<Long, String> names = names(list.stream().map(CashHandover::getAgentId).collect(Collectors.toSet()));
        return list.stream().map(h -> toResponse(h, names.get(h.getAgentId()))).toList();
    }

    @Transactional
    public HandoverResponse submit(HandoverSubmit in) {
        CurrentUser me = CurrentUser.get();
        long tenantId = me.requireTenant();
        if (in.handoverDate().isAfter(LocalDate.now(clock))) {
            throw ApiException.rule("FUTURE_DATE", "Handover date cannot be in the future");
        }
        if (handovers.existsByTenantIdAndAgentIdAndHandoverDate(tenantId, me.userId(), in.handoverDate())) {
            throw ApiException.conflict("HANDOVER_EXISTS", "Cash for this date was already handed over");
        }
        BigDecimal expected = payments.sumByCollector(tenantId, me.userId(), Payment.Mode.CASH,
                in.handoverDate().atStartOfDay(), in.handoverDate().plusDays(1).atStartOfDay()).setScale(2);
        CashHandover h = handovers.save(new CashHandover(tenantId, me.userId(), in.handoverDate(), expected,
                in.handedAmount(), in.remarks() == null || in.remarks().isBlank() ? null : in.remarks().trim()));
        return toResponse(h, names(Set.of(me.userId())).get(me.userId()));
    }

    @Transactional
    public HandoverResponse decide(long handoverId, HandoverDecision in) {
        CurrentUser me = CurrentUser.get();
        if (in.status() == CashHandover.Status.PENDING) {
            throw ApiException.rule("INVALID_STATUS", "Status must be CONFIRMED or DISPUTED");
        }
        CashHandover h = handovers.findByIdAndTenantId(handoverId, me.requireTenant())
                .orElseThrow(() -> ApiException.notFound("Cash handover"));
        if (h.getStatus() != CashHandover.Status.PENDING) {
            throw ApiException.rule("ALREADY_DECIDED", "This handover was already " + h.getStatus());
        }
        h.decide(in.status(), me.userId(), in.remarks(), LocalDateTime.now(clock));
        return toResponse(h, names(Set.of(h.getAgentId())).get(h.getAgentId()));
    }

    private Map<Long, String> names(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getName));
    }

    private static HandoverResponse toResponse(CashHandover h, String agentName) {
        return new HandoverResponse(h.getId(), h.getAgentId(), agentName, h.getHandoverDate(), h.getExpectedAmount(),
                h.getHandedAmount(), h.getHandedAmount().subtract(h.getExpectedAmount()), h.getStatus(),
                h.getReceivedBy(), h.getRemarks(), h.getConfirmedAt());
    }
}
