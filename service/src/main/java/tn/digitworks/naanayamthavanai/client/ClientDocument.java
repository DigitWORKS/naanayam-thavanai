package tn.digitworks.naanayamthavanai.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_document")
public class ClientDocument {

    public enum DocType { PHOTO, AADHAAR, PAN, GUARANTOR_ID, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    /** Storage key; never a public URL. */
    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    protected ClientDocument() {
    }

    public ClientDocument(Long tenantId, Long clientId, DocType docType, String fileKey, Long uploadedBy) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.docType = docType;
        this.fileKey = fileKey;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public DocType getDocType() { return docType; }
    public String getFileKey() { return fileKey; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
