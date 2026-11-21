package com.mgh.backend.cashier.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "pos_settings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pos_settings_scope_key", columnNames = {"scope_type", "scope_id", "config_key"})
        },
        indexes = {
                @Index(name = "idx_pos_settings_lookup", columnList = "scope_type, scope_id, config_key")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_type", nullable = false, length = 50)
    @Builder.Default
    private String scopeType = "GLOBAL";

    @Column(name = "scope_id", nullable = false, length = 100)
    @Builder.Default
    private String scopeId = "DEFAULT";

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "data_type", nullable = false, length = 20)
    @Builder.Default
    private String dataType = "STRING";

    @Column(name = "description", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
