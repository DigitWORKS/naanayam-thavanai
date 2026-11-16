package tn.digitworks.naanayamthavanai.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByRole(Role role);

    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);

    List<AppUser> findByTenantIdOrderByName(Long tenantId);

    List<AppUser> findByTenantIdAndRoleOrderByName(Long tenantId, Role role);

    List<AppUser> findByIdIn(Collection<Long> ids);
}
