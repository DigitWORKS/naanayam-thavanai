package tn.digitworks.naanayamthavanai.client;

import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientCreate;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientDetail;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientResponse;
import tn.digitworks.naanayamthavanai.client.ClientDtos.ClientUpdate;
import tn.digitworks.naanayamthavanai.client.ClientDtos.DocumentResponse;
import tn.digitworks.naanayamthavanai.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/clients")
@PreAuthorize("hasAnyRole('OWNER','AGENT')")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ClientResponse> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Long areaId,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(q, areaId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody ClientCreate in) {
        return service.create(in);
    }

    @GetMapping("/{clientId}")
    public ClientDetail get(@PathVariable long clientId) {
        return service.get(clientId);
    }

    @PatchMapping("/{clientId}")
    public ClientResponse update(@PathVariable long clientId, @Valid @RequestBody ClientUpdate in) {
        return service.update(clientId, in);
    }

    @GetMapping("/{clientId}/documents")
    public List<DocumentResponse> documents(@PathVariable long clientId) {
        return service.documents(clientId);
    }

    @PostMapping(path = "/{clientId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@PathVariable long clientId,
                                   @RequestParam ClientDocument.DocType docType,
                                   @RequestPart("file") MultipartFile file) {
        return service.upload(clientId, docType, file);
    }

    @GetMapping("/{clientId}/documents/{documentId}/file")
    public ResponseEntity<Resource> download(@PathVariable long clientId, @PathVariable long documentId) {
        ClientService.DocumentFile f = service.download(clientId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(f.fileName()).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(f.resource());
    }
}
