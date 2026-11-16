package tn.digitworks.naanayamthavanai.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     */
    @Query("""
            select c from Client c
            where c.tenantId = :tenantId
              and (:areaId is null or c.areaId = :areaId)
              and (:allAreas = true or c.areaId in :areaIds)
              and (:q is null
                   or lower(c.name) like lower(concat('%', :q, '%'))
                   or c.phone like concat('%', :q, '%'))
            order by c.name
            """)
    Page<Client> search(Long tenantId, String q, Long areaId, boolean allAreas,
                        Collection<Long> areaIds, Pageable pageable);
}
