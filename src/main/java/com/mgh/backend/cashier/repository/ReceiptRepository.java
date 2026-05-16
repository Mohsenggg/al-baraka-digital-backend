package com.mgh.backend.cashier.repository;

import com.mgh.backend.cashier.entity.Receipt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt> {

    // Exclude soft‑deleted receipts from normal queries
    @Query("SELECT r FROM Receipt r WHERE r.isDeleted = false")
    List<Receipt> findAllActive();

    @Query("SELECT r FROM Receipt r WHERE r.id = :id AND r.isDeleted = false")
    Optional<Receipt> findActiveById(Long id);

    Optional<Receipt> findByReceiptNumber(String number);
    boolean existsByReceiptNumber(String number);


    @Query("""
            select distinct r
            from Receipt r
            left join fetch r.items
            left join fetch r.cashier
            where r.isDeleted = false
            order by r.id desc
            """)
    List<Receipt> findLatestNavigationWindow(Pageable pageable);

    @Query("""
            select distinct r
            from Receipt r
            left join fetch r.items
            left join fetch r.cashier
            where r.isDeleted = false
              and r.id > :centerReceiptId
            order by r.id asc
            """)
    List<Receipt> findNewerThan(Long centerReceiptId, Pageable pageable);

    @Query("""
            select distinct r
            from Receipt r
            left join fetch r.items
            left join fetch r.cashier
            where r.isDeleted = false
              and r.id < :centerReceiptId
            order by r.id desc
            """)
    List<Receipt> findOlderThan(Long centerReceiptId, Pageable pageable);


    @Query("""
            select distinct r
            from Receipt r
            left join fetch r.items
            left join fetch r.cashier
            where r.isDeleted = false
              and r.id = :id
            """)
    Optional<Receipt> findActiveWithItemsById(Long id);

    boolean existsByIsDeletedFalseAndIdLessThan(Long id);

    boolean existsByIsDeletedFalseAndIdGreaterThan(Long id);


}