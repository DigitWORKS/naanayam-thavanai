package tn.digitworks.naanayamthavanai.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientDocumentRepository extends JpaRepository<ClientDocument, Long> {

    List<ClientDocument> findByTenantIdAndClientIdOrderByUploadedAtDesc(Long tenantId, Long clientId);

    Optional<ClientDocument> findByIdAndTenantIdAndClientId(Long id, Long tenantId, Long clientId);
}
