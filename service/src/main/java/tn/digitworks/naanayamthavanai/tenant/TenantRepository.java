package tn.digitworks.naanayamthavanai.tenant;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByPhone(String phone);

    Page<Tenant> findByStatus(Tenant.Status status, Pageable pageable);

    /** Serialises per-tenant counters (e.g. loan numbers). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> lockById(Long id);
}
