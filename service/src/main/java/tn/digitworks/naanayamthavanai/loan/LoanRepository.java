package tn.digitworks.naanayamthavanai.loan;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.Frequency;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByIdAndTenantId(Long id, Long tenantId);

    /** Locks the loan row; every money-changing operation on a loan goes through this first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id and l.tenantId = :tenantId")
    Optional<Loan> lockByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> lockById(Long id);

    long countByTenantIdAndClientIdAndStatus(Long tenantId, Long clientId, Loan.Status status);

    long countByTenantId(Long tenantId);

    List<Loan> findByTenantIdAndClientIdAndStatusOrderByIdDesc(Long tenantId, Long clientId, Loan.Status status);

    List<Loan> findByTenantIdAndClientIdInAndStatusOrderByIdDesc(Long tenantId, Collection<Long> clientIds,
                                                                Loan.Status status);

    @Query("select l.id from Loan l where l.status = :status order by l.id")
    List<Long> findIdsByStatus(Loan.Status status);

    @Query("""
            select l from Loan l
            where l.tenantId = :tenantId and l.status = :status
              and l.clientId in (select c.id from Client c
                                 where c.tenantId = :tenantId
                                   and (:areaId is null or c.areaId = :areaId)
                                   and (:allAreas = true or c.areaId in :areaIds))
            """)
    List<Loan> findForAreas(Long tenantId, Loan.Status status, Long areaId, boolean allAreas,
                            Collection<Long> areaIds);

    /**
     * Loan list. {@code nameLike} matches loan no. or client name, {@code phoneLike} the client phone (null to
     * skip text search). {@code behind}: has arrears as in LoanBalances — an open installment past due, or a
     * carried-forward one not due today.
     */
    @Query("""
            select l from Loan l
            where l.tenantId = :tenantId
              and (:clientId is null or l.clientId = :clientId)
              and (:status is null or l.status = :status)
              and (:frequency is null or l.frequency = :frequency)
              and l.clientId in (select c.id from Client c
                                 where c.tenantId = :tenantId
                                   and (:areaId is null or c.areaId = :areaId)
                                   and (:allAreas = true or c.areaId in :areaIds))
              and (:nameLike is null
                   or l.loanNo like :nameLike
                   or l.clientId in (select c2.id from Client c2
                                     where c2.tenantId = :tenantId
                                       and (c2.name like :nameLike or c2.phone like :phoneLike)))
              and (:behind = false or exists (
                     select i.id from Installment i
                     where i.loanId = l.id and i.status in :openStatuses and i.dueAmount > i.paidAmount
                       and (i.dueDate < :today or (i.extension = true and i.dueDate <> :today))))
            order by l.id desc
            """)
    Page<Loan> search(Long tenantId, Long clientId, Loan.Status status, Long areaId, boolean allAreas,
                      Collection<Long> areaIds, Frequency frequency, String nameLike, String phoneLike,
                      boolean behind, Collection<InstallmentStatus> openStatuses, LocalDate today,
                      Pageable pageable);

    /** Loan count per status, within the caller's areas (tabs of the web loan list). */
    @Query("""
            select l.status, count(l) from Loan l
            where l.tenantId = :tenantId
              and l.clientId in (select c.id from Client c
                                 where c.tenantId = :tenantId
                                   and (:allAreas = true or c.areaId in :areaIds))
            group by l.status
            """)
    List<Object[]> countByStatus(Long tenantId, boolean allAreas, Collection<Long> areaIds);
}
