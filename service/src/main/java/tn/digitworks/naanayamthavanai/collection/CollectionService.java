package tn.digitworks.naanayamthavanai.collection;

import tn.digitworks.naanayamthavanai.area.Area;
import tn.digitworks.naanayamthavanai.area.AreaRepository;
import tn.digitworks.naanayamthavanai.client.Client;
import tn.digitworks.naanayamthavanai.client.ClientRepository;
import tn.digitworks.naanayamthavanai.loan.Installment;
import tn.digitworks.naanayamthavanai.loan.InstallmentRepository;
import tn.digitworks.naanayamthavanai.loan.Loan;
import tn.digitworks.naanayamthavanai.loan.LoanBalances;
import tn.digitworks.naanayamthavanai.loan.LoanRepository;
import tn.digitworks.naanayamthavanai.payment.PaymentRepository;
import tn.digitworks.naanayamthavanai.security.AreaScope;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The agent's daily list: loans with something to collect today. */
@Service
public class CollectionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public record DueItem(Long loanId, String loanNo, Long clientId, String clientName, String clientPhone,
                          String areaName, BigDecimal todayDue, BigDecimal overdueAmount, int overdueCount,
                          BigDecimal penaltyDue, BigDecimal totalDue, BigDecimal collectedToday) {
    }

    private final LoanRepository loans;
    private final InstallmentRepository installments;
    private final ClientRepository clients;
    private final AreaRepository areas;
    private final PaymentRepository payments;
    private final AreaScope areaScope;
    private final Clock clock;

    public CollectionService(LoanRepository loans, InstallmentRepository installments, ClientRepository clients,
                             AreaRepository areas, PaymentRepository payments, AreaScope areaScope, Clock clock) {
        this.loans = loans;
        this.installments = installments;
        this.clients = clients;
        this.areas = areas;
        this.payments = payments;
        this.areaScope = areaScope;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DueItem> due(LocalDate date, Long areaId) {
        long tenantId = CurrentUser.get().requireTenant();
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        AreaScope.Scope scope = areaScope.current();

        List<Loan> active = loans.findForAreas(tenantId, Loan.Status.ACTIVE, areaId, scope.all(), scope.areaIds());
        if (active.isEmpty()) {
            return List.of();
        }
        List<Long> loanIds = active.stream().map(Loan::getId).toList();
        Map<Long, List<Installment>> byLoan = installments.findByLoanIdIn(loanIds).stream()
                .collect(Collectors.groupingBy(Installment::getLoanId));
        Map<Long, Client> clientById = clients.findByTenantIdAndIdIn(tenantId,
                        active.stream().map(Loan::getClientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        Set<Long> areaIds = clientById.values().stream().map(Client::getAreaId).collect(Collectors.toSet());
        Map<Long, String> areaNames = areas.findByTenantIdAndIdIn(tenantId, areaIds).stream()
                .collect(Collectors.toMap(Area::getId, Area::getName));
        Map<Long, BigDecimal> collected = payments.sumByLoanBetween(tenantId, loanIds,
                        day.atStartOfDay(), day.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1]));

        return active.stream()
                .map(l -> {
                    LoanBalances b = LoanBalances.of(byLoan.getOrDefault(l.getId(), List.of()), day);
                    Client c = clientById.get(l.getClientId());
                    return new DueItem(l.getId(), l.getLoanNo(), c.getId(), c.getName(), c.getPhone(),
                            areaNames.get(c.getAreaId()), b.todayDue(), b.arrears(), b.arrearsCount(),
                            b.penaltyDue(), b.totalDue(),
                            collected.getOrDefault(l.getId(), ZERO).setScale(2));
                })
                .filter(d -> d.totalDue().signum() > 0 || d.collectedToday().signum() != 0)
                .sorted(Comparator.comparing((DueItem d) -> d.areaName() == null ? "" : d.areaName())
                        .thenComparing(DueItem::clientName))
                .toList();
    }
}
