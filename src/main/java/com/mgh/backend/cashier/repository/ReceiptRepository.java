package com.mgh.backend.cashier.repository;

import com.mgh.backend.cashier.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt> {

    @Query("SELECT r FROM Receipt r WHERE r.isDeleted = false ORDER BY r.receiptDate DESC")
    List<Receipt> findAllActive();

    @Query("SELECT r FROM Receipt r WHERE r.id = :id AND r.isDeleted = false")
    Optional<Receipt> findActiveById(Long id);

    @Query("""
            SELECT r FROM Receipt r
            WHERE r.isDeleted = false
              AND (:search IS NULL OR :search = ''
                   OR LOWER(r.receiptNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Receipt> findActiveWithSearch(@Param("search") String search, Pageable pageable);

    Optional<Receipt> findByReceiptNumber(String number);

    boolean existsByReceiptNumber(String number);

    @Query("""
            SELECT DISTINCT r
            FROM Receipt r
            LEFT JOIN FETCH r.items
            LEFT JOIN FETCH r.cashier
            WHERE r.isDeleted = false
            ORDER BY r.id DESC
            """)
    List<Receipt> findLatestNavigationWindow(Pageable pageable);

    @Query("""
            SELECT DISTINCT r
            FROM Receipt r
            LEFT JOIN FETCH r.items
            LEFT JOIN FETCH r.cashier
            WHERE r.isDeleted = false
              AND r.id > :centerReceiptId
            ORDER BY r.id ASC
            """)
    List<Receipt> findNewerThan(@Param("centerReceiptId") Long centerReceiptId, Pageable pageable);

    @Query("""
            SELECT DISTINCT r
            FROM Receipt r
            LEFT JOIN FETCH r.items
            LEFT JOIN FETCH r.cashier
            WHERE r.isDeleted = false
              AND r.id < :centerReceiptId
            ORDER BY r.id DESC
            """)
    List<Receipt> findOlderThan(@Param("centerReceiptId") Long centerReceiptId, Pageable pageable);

    @Query("""
            SELECT DISTINCT r
            FROM Receipt r
            LEFT JOIN FETCH r.items
            LEFT JOIN FETCH r.cashier
            WHERE r.isDeleted = false
              AND r.id = :id
            """)
    Optional<Receipt> findActiveWithItemsById(@Param("id") Long id);

    boolean existsByIsDeletedFalseAndIdLessThan(Long id);

    boolean existsByIsDeletedFalseAndIdGreaterThan(Long id);

    // ─── Navigation by business order (receiptDate DESC, id DESC) ───

    @Query("""
            SELECT DISTINCT r
            FROM Receipt r
            LEFT JOIN FETCH r.items
            LEFT JOIN FETCH r.cashier
            WHERE r.isDeleted = false
              AND (r.receiptDate < :receiptDate
                   OR (r.receiptDate = :receiptDate AND r.id < :receiptId))
            ORDER BY r.receiptDate DESC, r.id DESC
            """)
    List<Receipt> findNextInOrder(
            @Param("receiptDate") LocalDateTime receiptDate,
            @Param("receiptId") Long receiptId,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT r
            FROM Receipt r
            LEFT JOIN FETCH r.items
            LEFT JOIN FETCH r.cashier
            WHERE r.isDeleted = false
              AND (r.receiptDate > :receiptDate
                   OR (r.receiptDate = :receiptDate AND r.id > :receiptId))
            ORDER BY r.receiptDate ASC, r.id ASC
            """)
    List<Receipt> findPreviousInOrder(
            @Param("receiptDate") LocalDateTime receiptDate,
            @Param("receiptId") Long receiptId,
            Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Receipt r
            WHERE r.isDeleted = false
              AND (r.receiptDate < :receiptDate
                   OR (r.receiptDate = :receiptDate AND r.id < :receiptId))
            """)
    boolean existsNextInOrder(
            @Param("receiptDate") LocalDateTime receiptDate,
            @Param("receiptId") Long receiptId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM Receipt r
            WHERE r.isDeleted = false
              AND (r.receiptDate > :receiptDate
                   OR (r.receiptDate = :receiptDate AND r.id > :receiptId))
            """)
    boolean existsPreviousInOrder(
            @Param("receiptDate") LocalDateTime receiptDate,
            @Param("receiptId") Long receiptId);
}