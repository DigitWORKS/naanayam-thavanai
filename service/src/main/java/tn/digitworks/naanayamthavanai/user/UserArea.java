package tn.digitworks.naanayamthavanai.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** Agent to area assignment (many-to-many). */
@Entity
@Table(name = "user_area")
@IdClass(UserArea.Key.class)
public class UserArea {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    protected UserArea() {
    }

    public UserArea(Long tenantId, Long userId, Long areaId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.areaId = areaId;
    }

    @PrePersist
    void onCreate() {
        assignedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public Long getAreaId() { return areaId; }
    public Long getTenantId() { return tenantId; }

    public static class Key implements Serializable {
        private Long userId;
        private Long areaId;

        public Key() {
        }

        public Key(Long userId, Long areaId) {
            this.userId = userId;
            this.areaId = areaId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(areaId, k.areaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, areaId);
        }
    }
}
