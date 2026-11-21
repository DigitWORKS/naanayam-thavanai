package tn.digitworks.naanayamthavanai.tenant;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByPhone(String phone);

    Page<Tenant> findByStatus(Tenant.Status status, Pageable pageable);

    /**
     * Super Admin list. {@code nameLike} matches business or owner name (lower-cased), {@code phoneLike} the
     * phone; null skips text search. {@code expFrom}/{@code expTo}: plan ends in that range and not suspended or closed.
     */
    @Query("""
            select t from Tenant t
            where (:status is null or t.status = :status)
              and (:nameLike is null or lower(t.name) like :nameLike or lower(t.ownerName) like :nameLike
                   or t.phone like :phoneLike)
              and (:expFrom is null or (t.planExpiry between :expFrom and :expTo
                                        and t.status in (tn.digitworks.naanayamthavanai.tenant.Tenant.Status.TRIAL, tn.digitworks.naanayamthavanai.tenant.Tenant.Status.ACTIVE)))
            """)
    Page<Tenant> search(Tenant.Status status, String nameLike, String phoneLike, LocalDate expFrom, LocalDate expTo,
                        Pageable pageable);

    long countByStatus(Tenant.Status status);

    @Query("""
            select count(t) from Tenant t
            where t.planExpiry between :from and :to
              and t.status in (tn.digitworks.naanayamthavanai.tenant.Tenant.Status.TRIAL, tn.digitworks.naanayamthavanai.tenant.Tenant.Status.ACTIVE)
            """)
    long countExpiring(LocalDate from, LocalDate to);

    /** Closed businesses whose erase date has come. */
    @Query("""
            select t.id from Tenant t
            where t.status = tn.digitworks.naanayamthavanai.tenant.Tenant.Status.CLOSED and t.eraseOn <= :today
            """)
    List<Long> findIdsToErase(LocalDate today);

    /** Serialises per-tenant counters (e.g. loan numbers). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> lockById(Long id);
}
