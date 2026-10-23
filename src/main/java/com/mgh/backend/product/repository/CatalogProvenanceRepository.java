package com.mgh.backend.product.repository;

import com.mgh.backend.product.entity.CatalogProvenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface CatalogProvenanceRepository extends JpaRepository<CatalogProvenance, Long> {

    boolean existsBySourceCatalogAndSourceId(String sourceCatalog, Long sourceId);

    List<CatalogProvenance> findByEntityTypeAndEntityId(String entityType, Long entityId);

    @Query("SELECT cp.sourceId FROM CatalogProvenance cp WHERE cp.sourceCatalog = :catalog")
    Set<Long> findAllSourceIdsByCatalog(@Param("catalog") String catalog);

    @Query("SELECT cp.sourceCatalog, cp.sourceId FROM CatalogProvenance cp")
    List<Object[]> findAllExistingCatalogAndSourceIdPairs();
}
