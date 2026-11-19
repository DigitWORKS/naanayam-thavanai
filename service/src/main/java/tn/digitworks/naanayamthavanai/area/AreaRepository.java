package tn.digitworks.naanayamthavanai.area;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findByTenantIdOrderByName(Long tenantId);

    List<Area> findByTenantIdAndActiveOrderByName(Long tenantId, boolean active);

    Optional<Area> findByIdAndTenantId(Long id, Long tenantId);

    List<Area> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    boolean existsByTenantIdAndName(Long tenantId, String name);

    boolean existsByTenantIdAndNameAndIdNot(Long tenantId, String name, Long id);

    /** [areaId, count] of active clients per area. */
    @Query("select c.areaId, count(c) from Client c where c.tenantId = :tenantId and c.active = true group by c.areaId")
    List<Object[]> activeClientCounts(Long tenantId);

    /** [areaId, userId, name] of active agents assigned to each area, by name. */
    @Query("""
            select ua.areaId, u.id, u.name from UserArea ua, AppUser u
            where u.id = ua.userId and ua.tenantId = :tenantId and u.active = true
              and u.role = tn.digitworks.naanayamthavanai.user.Role.AGENT
            order by u.name
            """)
    List<Object[]> activeAgents(Long tenantId);
}
