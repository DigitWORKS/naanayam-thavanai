package tn.digitworks.naanayamthavanai.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, Long> {
}
