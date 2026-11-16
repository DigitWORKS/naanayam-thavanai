package tn.digitworks.naanayamthavanai.area;

import tn.digitworks.naanayamthavanai.area.AreaDtos.AreaInput;
import tn.digitworks.naanayamthavanai.area.AreaDtos.AreaResponse;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.security.AreaScope;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AreaService {

    private final AreaRepository areas;
    private final AreaScope scope;

    public AreaService(AreaRepository areas, AreaScope scope) {
        this.areas = areas;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<AreaResponse> list(Boolean active) {
        long tenantId = CurrentUser.get().requireTenant();
        List<Area> list = active == null
                ? areas.findByTenantIdOrderByName(tenantId)
                : areas.findByTenantIdAndActiveOrderByName(tenantId, active);
        AreaScope.Scope s = scope.current();
        return list.stream().filter(a -> s.allows(a.getId())).map(AreaResponse::of).toList();
    }

    @Transactional
    public AreaResponse create(AreaInput in) {
        long tenantId = CurrentUser.get().requireTenant();
        String name = requireName(in.name());
        if (areas.existsByTenantIdAndName(tenantId, name)) {
            throw ApiException.conflict("AREA_EXISTS", "Area '" + name + "' already exists");
        }
        Area area = new Area(tenantId, name);
        if (in.isActive() != null) {
            area.setActive(in.isActive());
        }
        return AreaResponse.of(areas.save(area));
    }

    @Transactional
    public AreaResponse update(long areaId, AreaInput in) {
        long tenantId = CurrentUser.get().requireTenant();
        Area area = areas.findByIdAndTenantId(areaId, tenantId).orElseThrow(() -> ApiException.notFound("Area"));
        if (in.name() != null) {
            String name = requireName(in.name());
            if (areas.existsByTenantIdAndNameAndIdNot(tenantId, name, areaId)) {
                throw ApiException.conflict("AREA_EXISTS", "Area '" + name + "' already exists");
            }
            area.setName(name);
        }
        if (in.isActive() != null) {
            area.setActive(in.isActive());
        }
        return AreaResponse.of(area);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.rule("AREA_NAME_REQUIRED", "Area name is required");
        }
        return name.trim();
    }
}
