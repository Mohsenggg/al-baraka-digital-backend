package com.mgh.backend.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "catalog_provenance",
        indexes = {
                @Index(name = "idx_provenance_entity", columnList = "entity_type, entity_id"),
                @Index(name = "idx_provenance_source", columnList = "source_catalog, source_code"),
                @Index(name = "idx_provenance_source_id", columnList = "source_catalog, source_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_provenance_source_record", columnNames = {"source_catalog", "source_id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProvenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "source_catalog", nullable = false, length = 20)
    private String sourceCatalog;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_code", nullable = false, length = 100)
    private String sourceCode;

    @Column(name = "source_parent_code", length = 100)
    private String sourceParentCode;

    @Column(name = "source_name", length = 500)
    private String sourceName;

    @Column(name = "is_canonical", nullable = false)
    @Builder.Default
    private boolean isCanonical = true;

    @Column(name = "mapping_status", nullable = false, length = 50)
    private String mappingStatus;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
