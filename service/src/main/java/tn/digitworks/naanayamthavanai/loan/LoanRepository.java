package tn.digitworks.naanayamthavanai.loan;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
            select l from Loan l
            where l.tenantId = :tenantId
              and (:clientId is null or l.clientId = :clientId)
              and (:status is null or l.status = :status)
              and l.clientId in (select c.id from Client c
                                 where c.tenantId = :tenantId
                                   and (:areaId is null or c.areaId = :areaId)
                                   and (:allAreas = true or c.areaId in :areaIds))
            order by l.id desc
            """)
    Page<Loan> search(Long tenantId, Long clientId, Loan.Status status, Long areaId, boolean allAreas,
                      Collection<Long> areaIds, Pageable pageable);
}
