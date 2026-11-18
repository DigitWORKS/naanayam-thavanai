package tn.digitworks.naanayamthavanai.client;

import tn.digitworks.naanayamthavanai.area.Area;
import tn.digitworks.naanayamthavanai.area.AreaRepository;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientCreate;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientDetail;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientFilter;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientListItem;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientResponse;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientUpdate;
import tn.digitworks.naanayamthavanai.client.ClientDtos.DocumentResponse;
import tn.digitworks.naanayamthavanai.common.ApiException;
import tn.digitworks.naanayamthavanai.common.PageResponse;
import tn.digitworks.naanayamthavanai.loan.LoanDtos.LoanSummary;
import tn.digitworks.naanayamthavanai.loan.Loan;
import tn.digitworks.naanayamthavanai.loan.LoanService;
import tn.digitworks.naanayamthavanai.loan.schedule.LoanEnums.InstallmentStatus;
import tn.digitworks.naanayamthavanai.security.AreaScope;
import tn.digitworks.naanayamthavanai.security.CurrentUser;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClientService {

    private final ClientRepository clients;
    private final ClientDocumentRepository documents;
    private final AreaRepository areas;
    private final AreaScope areaScope;
    private final LoanService loanService;
    private final DocumentStorage storage;
    private final Clock clock;

    public ClientService(ClientRepository clients, ClientDocumentRepository documents, AreaRepository areas,
                         AreaScope areaScope, LoanService loanService, DocumentStorage storage, Clock clock) {
        this.clients = clients;
        this.documents = documents;
        this.areas = areas;
        this.areaScope = areaScope;
        this.loanService = loanService;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientListItem> search(String q, Long areaId, ClientFilter filter, int page, int size) {
        long tenantId = CurrentUser.get().requireTenant();
        AreaScope.Scope scope = areaScope.current();
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<Client> result = clients.search(tenantId, query, areaId, scope.all(), scope.areaIds(),
                (filter == null ? ClientFilter.ALL : filter).name(), Loan.Status.ACTIVE,
                List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIAL), LocalDate.now(clock),
                PageRequest.of(page, size));
        List<Client> rows = result.getContent();
        Map<Long, String> areaNames = areaNames(tenantId, rows.stream().map(Client::getAreaId).collect(Collectors.toSet()));
        Map<Long, List<LoanSummary>> loansByClient =
                loanService.activeSummariesByClient(tenantId, rows.stream().map(Client::getId).toList());
        return PageResponse.of(result, c -> toListItem(c, areaNames.get(c.getAreaId()),
                loansByClient.getOrDefault(c.getId(), List.of())));
    }

    private static ClientListItem toListItem(Client c, String areaName, List<LoanSummary> active) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new ClientListItem(c.getId(), c.getAreaId(), areaName, c.getName(), c.getPhone(), c.isActive(),
                active.size(),
                active.stream().map(LoanSummary::outstanding).reduce(zero, BigDecimal::add),
                active.stream().map(LoanSummary::overdueAmount).reduce(zero, BigDecimal::add));
    }

    @Transactional
    public ClientResponse create(ClientCreate in) {
        CurrentUser me = CurrentUser.get();
        long tenantId = me.requireTenant();
        Area area = activeArea(tenantId, in.areaId());
        clients.findByTenantIdAndPhone(tenantId, in.phone()).ifPresent(existing -> {
            throw clientExists(tenantId, existing);
        });
        Client c = new Client(tenantId, area.getId(), in.name().trim(), in.phone(), me.userId());
        c.setAltPhone(blankToNull(in.altPhone()));
        c.setAddress(blankToNull(in.address()));
        c.setAadhaarLast4(blankToNull(in.aadhaarLast4()));
        c.setGuarantorName(blankToNull(in.guarantorName()));
        c.setGuarantorPhone(blankToNull(in.guarantorPhone()));
        c.setConsentGivenAt(LocalDateTime.now(clock));
        c.setConsentVersion(in.consentVersion().trim());
        return toResponse(clients.save(c), area.getName());
    }

    @Transactional(readOnly = true)
    public ClientDetail get(long clientId) {
        long tenantId = CurrentUser.get().requireTenant();
        Client c = accessible(tenantId, clientId);
        List<LoanSummary> active = loanService.activeSummaries(tenantId, clientId);
        BigDecimal total = active.stream().map(LoanSummary::outstanding).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        String areaName = areaNames(tenantId, Set.of(c.getAreaId())).get(c.getAreaId());
        return new ClientDetail(c.getId(), c.getAreaId(), areaName, c.getName(), c.getPhone(), c.getAltPhone(),
                c.getAddress(), c.getAadhaarLast4(), c.getGuarantorName(), c.getGuarantorPhone(),
                c.getConsentGivenAt(), c.isActive(), active, total);
    }

    @Transactional
    public ClientResponse update(long clientId, ClientUpdate in) {
        long tenantId = CurrentUser.get().requireTenant();
        Client c = accessible(tenantId, clientId);
        if (in.areaId() != null && !in.areaId().equals(c.getAreaId())) {
            c.setAreaId(activeArea(tenantId, in.areaId()).getId());
        }
        if (in.name() != null) c.setName(in.name().trim());
        if (in.altPhone() != null) c.setAltPhone(blankToNull(in.altPhone()));
        if (in.address() != null) c.setAddress(blankToNull(in.address()));
        if (in.guarantorName() != null) c.setGuarantorName(blankToNull(in.guarantorName()));
        if (in.guarantorPhone() != null) c.setGuarantorPhone(blankToNull(in.guarantorPhone()));
        if (in.isActive() != null) c.setActive(in.isActive());
        return toResponse(c, areaNames(tenantId, Set.of(c.getAreaId())).get(c.getAreaId()));
    }

    // ------------------------------------------------------------------ documents

    @Transactional(readOnly = true)
    public List<DocumentResponse> documents(long clientId) {
        long tenantId = CurrentUser.get().requireTenant();
        accessible(tenantId, clientId);
        return documents.findByTenantIdAndClientIdOrderByUploadedAtDesc(tenantId, clientId).stream()
                .map(d -> toResponse(clientId, d)).toList();
    }

    @Transactional
    public DocumentResponse upload(long clientId, ClientDocument.DocType type, MultipartFile file) {
        CurrentUser me = CurrentUser.get();
        long tenantId = me.requireTenant();
        accessible(tenantId, clientId);
        String key = storage.store(tenantId, clientId, file);
        ClientDocument doc = documents.save(new ClientDocument(tenantId, clientId, type, key, me.userId()));
        return toResponse(clientId, doc);
    }

    public record DocumentFile(Resource resource, String fileName) {
    }

    @Transactional(readOnly = true)
    public DocumentFile download(long clientId, long documentId) {
        long tenantId = CurrentUser.get().requireTenant();
        accessible(tenantId, clientId);
        ClientDocument doc = documents.findByIdAndTenantIdAndClientId(documentId, tenantId, clientId)
                .orElseThrow(() -> ApiException.notFound("Document"));
        String key = doc.getFileKey();
        return new DocumentFile(storage.load(key), doc.getDocType().name().toLowerCase() + key.substring(key.lastIndexOf('.')));
    }

    // ------------------------------------------------------------------ helpers

    private Client accessible(long tenantId, long clientId) {
        Client c = clients.findByIdAndTenantId(clientId, tenantId).orElseThrow(() -> ApiException.notFound("Client"));
        areaScope.require(c.getAreaId(), "Client");
        return c;
    }

    private Area activeArea(long tenantId, long areaId) {
        Area area = areas.findByIdAndTenantId(areaId, tenantId).orElseThrow(() -> ApiException.rule("INVALID_AREA", "Area does not exist"));
        if (!area.isActive()) {
            throw ApiException.rule("AREA_INACTIVE", "Area is inactive");
        }
        if (!areaScope.current().allows(areaId)) {
            throw ApiException.rule("AREA_NOT_ASSIGNED", "You are not assigned to this area");
        }
        return area;
    }

    /**
     * 409 with enough detail for the app to offer "Open existing client" instead of a dead end.
     * {@code accessible} is false when an agent is not assigned to the client's area: the app shows
     * the name and area but cannot open it (GET would be 404).
     */
    private ApiException clientExists(long tenantId, Client existing) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("clientId", existing.getId());
        details.put("name", existing.getName());
        details.put("areaId", existing.getAreaId());
        details.put("areaName", areaNames(tenantId, Set.of(existing.getAreaId())).get(existing.getAreaId()));
        details.put("isActive", existing.isActive());
        details.put("accessible", areaScope.current().allows(existing.getAreaId()));
        return new ApiException(HttpStatus.CONFLICT, "CLIENT_EXISTS",
                "A client with this phone number already exists", details);
    }

    private Map<Long, String> areaNames(long tenantId, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return areas.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Area::getId, Area::getName));
    }

    private static ClientResponse toResponse(Client c, String areaName) {
        return new ClientResponse(c.getId(), c.getAreaId(), areaName, c.getName(), c.getPhone(), c.getAltPhone(),
                c.getAddress(), c.getAadhaarLast4(), c.getGuarantorName(), c.getGuarantorPhone(),
                c.getConsentGivenAt(), c.isActive());
    }

    private static DocumentResponse toResponse(long clientId, ClientDocument d) {
        return new DocumentResponse(d.getId(), d.getDocType(),
                "/v1/clients/" + clientId + "/documents/" + d.getId() + "/file", d.getUploadedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
