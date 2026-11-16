package tn.digitworks.naanayamthavanai.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "client")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "alt_phone", length = 15)
    private String altPhone;

    @Column(length = 500)
    private String address;

    @Column(name = "aadhaar_last4", columnDefinition = "char(4)")
    private String aadhaarLast4;

    @Column(name = "guarantor_name", length = 100)
    private String guarantorName;

    @Column(name = "guarantor_phone", length = 15)
    private String guarantorPhone;

    @Column(name = "consent_given_at")
    private LocalDateTime consentGivenAt;

    @Column(name = "consent_version", length = 20)
    private String consentVersion;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Client() {
    }

    public Client(Long tenantId, Long areaId, String name, String phone, Long createdBy) {
        this.tenantId = tenantId;
        this.areaId = areaId;
        this.name = name;
        this.phone = phone;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public String getAltPhone() { return altPhone; }
    public void setAltPhone(String altPhone) { this.altPhone = altPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getAadhaarLast4() { return aadhaarLast4; }
    public void setAadhaarLast4(String aadhaarLast4) { this.aadhaarLast4 = aadhaarLast4; }
    public String getGuarantorName() { return guarantorName; }
    public void setGuarantorName(String guarantorName) { this.guarantorName = guarantorName; }
    public String getGuarantorPhone() { return guarantorPhone; }
    public void setGuarantorPhone(String guarantorPhone) { this.guarantorPhone = guarantorPhone; }
    public LocalDateTime getConsentGivenAt() { return consentGivenAt; }
    public void setConsentGivenAt(LocalDateTime consentGivenAt) { this.consentGivenAt = consentGivenAt; }
    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
