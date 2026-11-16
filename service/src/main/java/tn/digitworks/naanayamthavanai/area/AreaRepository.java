package tn.digitworks.naanayamthavanai.area;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
