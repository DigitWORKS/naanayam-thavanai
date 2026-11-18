package tn.digitworks.naanayamthavanai.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import tn.digitworks.naanayamthavanai.loan.Loan;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Client> findByTenantIdAndPhone(Long tenantId, String phone);

    List<Client> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    /**
     * @param allAreas true for owners; when false only {@code areaIds} are visible (agents)
     * @param areaIds  never empty (pass a dummy id when allAreas is true)
     * @param filter   ALL, BEHIND (an active loan has an open installment past due or carried forward,
     *                 as in LoanBalances.arrears) or NO_ACTIVE_LOAN
     */
    @Query("""
            select c from Client c
            where c.tenantId = :tenantId
              and (:areaId is null or c.areaId = :areaId)
              and (:allAreas = true or c.areaId in :areaIds)
              and (:q is null
                   or lower(c.name) like lower(concat('%', :q, '%'))
                   or c.phone like concat('%', :q, '%'))
              and (:filter <> 'NO_ACTIVE_LOAN' or not exists (
                    select 1 from Loan l
                    where l.tenantId = c.tenantId and l.clientId = c.id and l.status = :active))
              and (:filter <> 'BEHIND' or exists (
                    select 1 from Loan l, Installment i
                    where l.tenantId = c.tenantId and l.clientId = c.id and l.status = :active
                      and i.loanId = l.id and i.status in :openStatuses and i.dueAmount > i.paidAmount
                      and (i.dueDate < :today or i.extension = true)))
            order by c.name
            """)
    Page<Client> search(Long tenantId, String q, Long areaId, boolean allAreas, Collection<Long> areaIds,
                        String filter, Loan.Status active, Collection<InstallmentStatus> openStatuses,
                        LocalDate today, Pageable pageable);
}
