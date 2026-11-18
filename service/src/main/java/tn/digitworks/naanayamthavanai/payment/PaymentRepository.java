package tn.digitworks.naanayamthavanai.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTenantIdAndClientTxnId(Long tenantId, String clientTxnId);

    Optional<Payment> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByReversalOf(Long paymentId);

    List<Payment> findByLoanIdOrderByPaidOnAscIdAsc(Long loanId);

    /**
     * True when this UTR is already on a payment that has not been reversed. A reversed payment's UTR may be
     * used again (e.g. recorded on the wrong loan, reversed, then recorded correctly).
     */
    @Query("""
            select count(p) > 0 from Payment p
            where p.tenantId = :tenantId and p.upiRefNo = :upiRefNo and p.entryType = :entryType
              and not exists (select r.id from Payment r where r.reversalOf = p.id)
            """)
    boolean existsUnreversed(Long tenantId, String upiRefNo, Payment.EntryType entryType);

    default boolean isUpiRefInUse(Long tenantId, String upiRefNo) {
        return existsUnreversed(tenantId, upiRefNo, Payment.EntryType.PAYMENT);
    }

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.loanId = :loanId")
    BigDecimal sumByLoanId(Long loanId);

    /** Sum of an agent's CASH entries (payments and their reversals) in [from, to). */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.tenantId = :tenantId and p.collectedBy = :agentId and p.mode = :mode
              and p.paidOn >= :from and p.paidOn < :to
            """)
    BigDecimal sumByCollector(Long tenantId, Long agentId, Payment.Mode mode, LocalDateTime from, LocalDateTime to);

    /** [count, max(paidOn)] of an agent's PAYMENT entries (not reversals) of one mode in [from, to). */
    @Query("""
            select count(p), max(p.paidOn) from Payment p
            where p.tenantId = :tenantId and p.collectedBy = :agentId and p.mode = :mode
              and p.entryType = :type and p.paidOn >= :from and p.paidOn < :to
            """)
    List<Object[]> countAndLastByCollector(Long tenantId, Long agentId, Payment.Mode mode, Payment.EntryType type,
                                           LocalDateTime from, LocalDateTime to);

    /** Rows of [loanId, sum(amount)] collected in [from, to) for the given loans. */
    @Query("""
            select p.loanId, sum(p.amount) from Payment p
            where p.tenantId = :tenantId and p.loanId in :loanIds
              and p.paidOn >= :from and p.paidOn < :to
            group by p.loanId
            """)
    List<Object[]> sumByLoanBetween(Long tenantId, Collection<Long> loanIds, LocalDateTime from, LocalDateTime to);
}
