package tn.digitworks.naanayamthavanai.report;

import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.handover.CashHandover;
import tn.digitworks.naanayamthavanai.handover.CashHandoverRepository;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Owner reports. Aggregates run in SQL; every query is filtered by tenant_id. */
@Service
public class ReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int MAX_REPORT_DAYS = 366;

    public enum GroupBy { DAY, AGENT, AREA }

    /** Owner home; {@code plan} / {@code planExpiry} also feed the plan card in Settings. */
    public record Dashboard(LocalDate date, String businessName, String plan, LocalDate planExpiry,
                            BigDecimal todayTarget, BigDecimal todayCollected, BigDecimal todayCash,
                            BigDecimal todayUpi, BigDecimal totalOutstanding, BigDecimal overdueAmount,
                            long overdueLoans, long activeLoans, long pendingHandovers, long activeClients,
                            BigDecimal penaltyDue, String fy, BigDecimal fyInterest) {
    }

    /** Today's handover of one agent (dashboard "Today by agent"). */
    public record HandoverBrief(Long id, BigDecimal expectedAmount, BigDecimal handedAmount, String status,
                                String remarks) {
    }

    /**
     * One collector's day. {@code target} is the sum due that day in the agent's assigned areas (null for owners,
     * who are not assigned areas); an area shared by two agents counts towards both.
     */
    public record AgentDay(Long userId, String name, String role, boolean active, List<String> areas,
                           BigDecimal target, BigDecimal collected, BigDecimal cash, BigDecimal upi, long payments,
                           HandoverBrief handover) {
    }

    /** An active loan behind schedule; arrears as in LoanBalances (past-due open + carried forward). */
    public record BehindLoan(Long loanId, String loanNo, Long clientId, String clientName, String areaName,
                             BigDecimal arrears, int installmentsBehind, LocalDate behindSince) {
    }

    /** target is null when grouping by AGENT (installments are not assigned to agents). */
    public record CollectionRow(String key, BigDecimal target, BigDecimal collected, BigDecimal cash,
                                BigDecimal upi, long paymentCount) {
    }

    public record MonthIncome(String month, BigDecimal interest, BigDecimal penalty, BigDecimal preclosureCharges) {
    }

    public record InterestIncome(String fy, BigDecimal totalInterestIncome, BigDecimal totalPenaltyIncome,
                                 BigDecimal totalPreclosureCharges, List<MonthIncome> months) {
    }

    private final EntityManager em;
    private final CashHandoverRepository handovers;
    private final Clock clock;

    public ReportService(EntityManager em, CashHandoverRepository handovers, Clock clock) {
        this.em = em;
        this.handovers = handovers;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public Dashboard dashboard(LocalDate date) {
        long t = CurrentUser.get().requireTenant();
        LocalDate day = date == null ? LocalDate.now(clock) : date;

        BigDecimal target = money(single("""
                select coalesce(sum(i.due_amount), 0) from installment i join loan l on l.id = i.loan_id
                where i.tenant_id = :t and i.due_date = :d and i.status <> 'SETTLED' and l.status <> 'DEFAULTED'
                """, Map.of("t", t, "d", Date.valueOf(day))));

        Object[] coll = row("""
                select coalesce(sum(p.amount), 0),
                       coalesce(sum(case when p.mode = 'CASH' then p.amount else 0 end), 0),
                       coalesce(sum(case when p.mode = 'UPI' then p.amount else 0 end), 0)
                from payment p
                where p.tenant_id = :t and p.paid_on >= :from and p.paid_on < :to
                """, Map.of("t", t, "from", day.atStartOfDay(), "to", day.plusDays(1).atStartOfDay()));

        Object[] bal = row("""
                select coalesce(sum(case when i.status in ('PENDING','PARTIAL') then i.due_amount - i.paid_amount else 0 end), 0)
                       + coalesce(sum(case when i.status <> 'SETTLED' then i.penalty_amount - i.penalty_paid else 0 end), 0),
                       coalesce(sum(case when i.status in ('PENDING','PARTIAL') and (i.due_date < :d or i.is_extension = 1)
                                         then i.due_amount - i.paid_amount else 0 end), 0),
                       count(distinct case when i.status in ('PENDING','PARTIAL') and (i.due_date < :d or i.is_extension = 1)
                                           then i.loan_id end)
                from installment i join loan l on l.id = i.loan_id
                where i.tenant_id = :t and l.status = 'ACTIVE'
                """, Map.of("t", t, "d", Date.valueOf(day)));

        Object[] active = row("""
                select count(*), count(distinct client_id) from loan where tenant_id = :t and status = 'ACTIVE'
                """, Map.of("t", t));

        BigDecimal penaltyDue = money(single("""
                select coalesce(sum(i.penalty_amount - i.penalty_paid), 0)
                from installment i join loan l on l.id = i.loan_id
                where i.tenant_id = :t and l.status = 'ACTIVE' and i.status <> 'SETTLED'
                """, Map.of("t", t)));

        int fyStart = day.getMonthValue() >= 4 ? day.getYear() : day.getYear() - 1;
        InterestIncome fy = interestIncome(fyStart);

        Object[] tenant = row("select name, plan, plan_expiry from tenant where id = :t", Map.of("t", t));

        return new Dashboard(day, (String) tenant[0], (String) tenant[1], localDate(tenant[2]), target, money(coll[0]), money(coll[1]), money(coll[2]), money(bal[0]),
                money(bal[1]), ((Number) bal[2]).longValue(), ((Number) active[0]).longValue(),
                handovers.countByTenantIdAndStatus(t, CashHandover.Status.PENDING), ((Number) active[1]).longValue(),
                penaltyDue, fy.fy(), fy.totalInterestIncome());
    }

    // ------------------------------------------------------------------ agents today

    /** Owners and agents: active ones, plus inactive ones who collected that day. Agents first, by name. */
    @Transactional(readOnly = true)
    public List<AgentDay> agentsDay(LocalDate date) {
        long t = CurrentUser.get().requireTenant();
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        Map<String, Object> range = Map.of("t", t, "from", day.atStartOfDay(), "to", day.plusDays(1).atStartOfDay());
        Map<String, Object> onDay = Map.of("t", t, "d", Date.valueOf(day));

        Map<Long, List<String>> areas = new java.util.HashMap<>();
        for (Object[] r : rows("""
                select ua.user_id, a.name from user_area ua join area a on a.id = ua.area_id
                where ua.tenant_id = :t order by a.name
                """, Map.of("t", t))) {
            areas.computeIfAbsent(id(r[0]), k -> new ArrayList<>()).add((String) r[1]);
        }

        Map<Long, BigDecimal> targets = new java.util.HashMap<>();
        for (Object[] r : rows("""
                select ua.user_id, coalesce(sum(i.due_amount), 0)
                from user_area ua
                join client c on c.area_id = ua.area_id
                join loan l on l.client_id = c.id
                join installment i on i.loan_id = l.id
                where ua.tenant_id = :t and i.due_date = :d and i.status <> 'SETTLED' and l.status <> 'DEFAULTED'
                group by ua.user_id
                """, onDay)) {
            targets.put(id(r[0]), money(r[1]));
        }

        Map<Long, Object[]> paid = new java.util.HashMap<>();
        for (Object[] r : rows("""
                select collected_by, coalesce(sum(amount), 0),
                       coalesce(sum(case when mode = 'CASH' then amount else 0 end), 0),
                       coalesce(sum(case when mode = 'UPI' then amount else 0 end), 0),
                       sum(case when entry_type = 'PAYMENT' then 1 else 0 end)
                from payment
                where tenant_id = :t and paid_on >= :from and paid_on < :to
                group by collected_by
                """, range)) {
            paid.put(id(r[0]), r);
        }

        Map<Long, HandoverBrief> handed = new java.util.HashMap<>();
        for (Object[] r : rows("""
                select agent_id, id, expected_amount, handed_amount, status, remarks
                from cash_handover where tenant_id = :t and handover_date = :d
                """, onDay)) {
            handed.put(id(r[0]), new HandoverBrief(id(r[1]), money(r[2]), money(r[3]), (String) r[4], (String) r[5]));
        }

        List<AgentDay> out = new ArrayList<>();
        for (Object[] u : rows("""
                select id, name, role, is_active from app_user
                where tenant_id = :t and role in ('OWNER', 'AGENT')
                order by role = 'OWNER', name
                """, Map.of("t", t))) {
            long userId = id(u[0]);
            boolean isActive = u[3] instanceof Boolean b ? b : ((Number) u[3]).intValue() != 0;
            Object[] p = paid.get(userId);
            if (!isActive && p == null) {
                continue;
            }
            boolean agent = "AGENT".equals(u[2]);
            out.add(new AgentDay(userId, (String) u[1], (String) u[2], isActive,
                    areas.getOrDefault(userId, List.of()),
                    agent ? targets.getOrDefault(userId, ZERO) : null,
                    p == null ? ZERO : money(p[1]), p == null ? ZERO : money(p[2]), p == null ? ZERO : money(p[3]),
                    p == null ? 0 : ((Number) p[4]).longValue(), handed.get(userId)));
        }
        return out;
    }

    // ------------------------------------------------------------------ most behind

    @Transactional(readOnly = true)
    public List<BehindLoan> mostBehind(int limit) {
        long t = CurrentUser.get().requireTenant();
        LocalDate today = LocalDate.now(clock);
        Query q = query("""
                select l.id, l.loan_no, c.id, c.name, a.name, sum(i.due_amount - i.paid_amount) as arrears,
                       count(*), min(i.due_date) as since
                from installment i
                join loan l on l.id = i.loan_id
                join client c on c.id = l.client_id
                join area a on a.id = c.area_id
                where i.tenant_id = :t and l.status = 'ACTIVE' and i.status in ('PENDING', 'PARTIAL')
                  and i.due_amount > i.paid_amount
                  and (i.due_date < :d or (i.is_extension = 1 and i.due_date <> :d))
                group by l.id, l.loan_no, c.id, c.name, a.name
                order by arrears desc, since
                """, Map.of("t", t, "d", Date.valueOf(today)));
        q.setMaxResults(limit);
        @SuppressWarnings("unchecked")
        List<Object[]> result = q.getResultList();
        return result.stream().map(r -> new BehindLoan(id(r[0]), (String) r[1], id(r[2]), (String) r[3],
                (String) r[4], money(r[5]), ((Number) r[6]).intValue(), localDate(r[7]))).toList();
    }

    // ------------------------------------------------------------------ collections

    @Transactional(readOnly = true)
    public List<CollectionRow> collections(LocalDate from, LocalDate to, GroupBy groupBy) {
        long t = CurrentUser.get().requireTenant();
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_REPORT_DAYS) {
            throw ApiException.rule("INVALID_RANGE", "'to' must be on or after 'from', within " + MAX_REPORT_DAYS + " days");
        }
        Map<String, Object> range = Map.of("t", t, "from", from.atStartOfDay(), "to", to.plusDays(1).atStartOfDay());
        Map<String, Object> dates = Map.of("t", t, "from", Date.valueOf(from), "to", Date.valueOf(to));

        String payKey = switch (groupBy) {
            case DAY -> "date_format(p.paid_on, '%Y-%m-%d')";
            case AGENT -> "u.name";
            case AREA -> "a.name";
        };
        List<Object[]> paid = rows("""
                select %s as k, coalesce(sum(p.amount), 0),
                       coalesce(sum(case when p.mode = 'CASH' then p.amount else 0 end), 0),
                       coalesce(sum(case when p.mode = 'UPI' then p.amount else 0 end), 0),
                       count(*)
                from payment p
                join app_user u on u.id = p.collected_by
                join loan l on l.id = p.loan_id
                join client c on c.id = l.client_id
                join area a on a.id = c.area_id
                where p.tenant_id = :t and p.paid_on >= :from and p.paid_on < :to
                group by k
                """.formatted(payKey), range);

        Map<String, BigDecimal> targets = new TreeMap<>();
        if (groupBy != GroupBy.AGENT) {
            String dueKey = groupBy == GroupBy.DAY ? "date_format(i.due_date, '%Y-%m-%d')" : "a.name";
            for (Object[] r : rows("""
                    select %s as k, coalesce(sum(i.due_amount), 0)
                    from installment i
                    join loan l on l.id = i.loan_id
                    join client c on c.id = l.client_id
                    join area a on a.id = c.area_id
                    where i.tenant_id = :t and i.due_date between :from and :to and i.status <> 'SETTLED'
                    group by k
                    """.formatted(dueKey), dates)) {
                targets.put((String) r[0], money(r[1]));
            }
        }

        Map<String, CollectionRow> out = new TreeMap<>();
        for (Object[] r : paid) {
            String key = (String) r[0];
            out.put(key, new CollectionRow(key, groupBy == GroupBy.AGENT ? null : targets.getOrDefault(key, ZERO),
                    money(r[1]), money(r[2]), money(r[3]), ((Number) r[4]).longValue()));
        }
        targets.forEach((key, target) -> out.putIfAbsent(key, new CollectionRow(key, target, ZERO, ZERO, ZERO, 0)));
        return new ArrayList<>(out.values());
    }

    // ------------------------------------------------------------------ interest income

    /** FY runs 1 April to 31 March. Upfront-deducted interest counts in the month the loan started. */
    @Transactional(readOnly = true)
    public InterestIncome interestIncome(int fyStartYear) {
        long t = CurrentUser.get().requireTenant();
        LocalDate start = LocalDate.of(fyStartYear, 4, 1);
        LocalDate end = start.plusYears(1);
        Map<String, Object> range = Map.of("t", t, "from", start.atStartOfDay(), "to", end.atStartOfDay());

        Map<String, BigDecimal[]> months = new LinkedHashMap<>();
        for (LocalDate m = start; m.isBefore(end); m = m.plusMonths(1)) {
            months.put(m.toString().substring(0, 7), new BigDecimal[]{ZERO, ZERO, ZERO});
        }

        for (Object[] r : rows("""
                select date_format(p.paid_on, '%Y-%m'), coalesce(sum(pa.interest_part), 0), coalesce(sum(pa.penalty_part), 0)
                from payment_allocation pa join payment p on p.id = pa.payment_id
                where p.tenant_id = :t and p.paid_on >= :from and p.paid_on < :to
                group by 1
                """, range)) {
            add(months, (String) r[0], 0, r[1]);
            add(months, (String) r[0], 1, r[2]);
        }
        for (Object[] r : rows("""
                select date_format(l.start_date, '%Y-%m'), coalesce(sum(l.interest_amount), 0)
                from loan l
                where l.tenant_id = :t and l.interest_collection = 'UPFRONT_DEDUCTED'
                  and l.start_date >= :fromDate and l.start_date < :toDate
                group by 1
                """, Map.of("t", t, "fromDate", Date.valueOf(start), "toDate", Date.valueOf(end)))) {
            add(months, (String) r[0], 0, r[1]);
        }
        for (Object[] r : rows("""
                select date_format(lp.closed_at, '%Y-%m'), coalesce(sum(lp.preclosure_charge), 0)
                from loan_preclosure lp
                where lp.tenant_id = :t and lp.closed_at >= :from and lp.closed_at < :to
                group by 1
                """, range)) {
            add(months, (String) r[0], 2, r[1]);
        }

        List<MonthIncome> list = months.entrySet().stream()
                .map(e -> new MonthIncome(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        return new InterestIncome(fyStartYear + "-" + String.valueOf(fyStartYear + 1).substring(2),
                list.stream().map(MonthIncome::interest).reduce(ZERO, BigDecimal::add),
                list.stream().map(MonthIncome::penalty).reduce(ZERO, BigDecimal::add),
                list.stream().map(MonthIncome::preclosureCharges).reduce(ZERO, BigDecimal::add),
                list);
    }

    // ------------------------------------------------------------------ helpers

    private static void add(Map<String, BigDecimal[]> months, String month, int idx, Object value) {
        BigDecimal[] m = months.get(month);
        if (m != null) {
            m[idx] = m[idx].add(money(value));
        }
    }

    private Object single(String sql, Map<String, Object> params) {
        return query(sql, params).getSingleResult();
    }

    private Object[] row(String sql, Map<String, Object> params) {
        return (Object[]) query(sql, params).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Map<String, Object> params) {
        return query(sql, params).getResultList();
    }

    private Query query(String sql, Map<String, Object> params) {
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q;
    }

    private static long id(Object v) {
        return ((Number) v).longValue();
    }

    /** DATE columns come back from native queries as java.sql.Date or LocalDate depending on the driver. */
    private static LocalDate localDate(Object v) {
        return v == null ? null : v instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) v;
    }

    private static BigDecimal money(Object v) {
        if (v == null) {
            return ZERO;
        }
        BigDecimal b = v instanceof BigDecimal d ? d : new BigDecimal(v.toString());
        return b.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
