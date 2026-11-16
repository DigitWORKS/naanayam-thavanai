package tn.digitworks.naanayamthavanai.handover;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashHandoverRepository extends JpaRepository<CashHandover, Long> {

    Optional<CashHandover> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndAgentIdAndHandoverDate(Long tenantId, Long agentId, LocalDate date);

    long countByTenantIdAndStatus(Long tenantId, CashHandover.Status status);

    @Query("""
            select h from CashHandover h
            where h.tenantId = :tenantId
              and (:agentId is null or h.agentId = :agentId)
              and (:status is null or h.status = :status)
              and (:from is null or h.handoverDate >= :from)
              and (:to is null or h.handoverDate <= :to)
            order by h.handoverDate desc, h.id desc
            """)
    List<CashHandover> search(Long tenantId, Long agentId, CashHandover.Status status, LocalDate from, LocalDate to);
}
