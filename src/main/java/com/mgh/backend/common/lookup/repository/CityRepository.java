package com.mgh.backend.common.lookup.repository;

import com.mgh.backend.common.lookup.entity.City;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CityRepository extends JpaRepository<City, Long> {

    @Query("SELECT c FROM City c WHERE c.governorate.id = :governorateId")
    Page<City> findByGovernorateId(@Param("governorateId") Long governorateId, Pageable pageable);

    @Query("""
            SELECT c FROM City c
            WHERE LOWER(c.nameEn) LIKE LOWER(CONCAT('%', :name, '%'))
               OR LOWER(c.nameAr) LIKE LOWER(CONCAT('%', :name, '%'))
            """)
    Page<City> searchByName(@Param("name") String name, Pageable pageable);
}
