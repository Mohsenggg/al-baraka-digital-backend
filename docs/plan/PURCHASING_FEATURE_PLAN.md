# Purchasing Feature — Backend Architecture & Implementation Plan

> **Document Location:** `Cashier-web-back/docs/plan/PURCHASING_FEATURE_PLAN.md`
> **Created:** 2026-10-02
> **Status:** Pre-Implementation Review — Ready for Development

---

## Table of Contents

1. [Feature Overview](#1-feature-overview)
2. [Reusable Backend Components](#2-reusable-backend-components)
3. [Missing Backend Components](#3-missing-backend-components)
4. [Business Rules & Calculation Models](#4-business-rules--calculation-models)
5. [Resolved Ambiguities & Design Decisions](#5-resolved-ambiguities--design-decisions)
6. [Recommended Backend Architecture](#6-recommended-backend-architecture)
7. [Phased Implementation Plan](#7-phased-implementation-plan)
8. [Appendix — Constraint & Convention Summary](#8-appendix--constraint--convention-summary)

---

## 1. Feature Overview

The Purchasing Feature provides the backend foundation for managing supplier purchase invoices. Its core responsibilities are:

- Record and manage supplier purchase invoices (header + line items).
- Increase warehouse/store stock upon purchase confirmation.
- Recalculate inventory valuation using the **Weighted-Average Cost** formula.
- Update `buyingPrice`, `sellingPrice`, and `expectedMarkupPercentage` on `ProductBarcode` atomically.
- Track payment status and remaining balance per invoice.
- Maintain an immutable audit trail of all inventory movements.

**The backend is the sole source of truth for all financial and inventory calculations.**
Frontend-supplied totals and amounts are never trusted; all values are recomputed server-side before persistence.

---

## 2. Reusable Backend Components

The following existing assets are reused directly without redesign.

### 2.1 Entity Layer

| Entity | Location | Role in Purchasing |
|---|---|---|
| `Supplier` | `product/entity/Supplier.java` | Direct FK on `Purchase.supplier` |
| `Product` | `product/entity/Product.java` | Referenced by `PurchaseItem.product` |
| `ProductBarcode` | `product/entity/ProductBarcode.java` | Target for stock increments, weighted-average cost update, selling price update |
| `Cashier` | `cashier/entity/Cashier.java` | FK for invoice creator / operator |
| `ProductGroup`, `ProductCategory` | `product/entity/` | Group price propagation (optional flag) |

### 2.2 Repository Patterns

| Repository | Pattern Reused |
|---|---|
| `ProductBarcodeRepository` | Pessimistic write lock (`findWithLockByBarcode`) — used to atomically update stock |
| `ProductRepository` | `findByIdAndDeletedAtIsNull` for active-only lookups |
| `SupplierRepository` | Existence checks and name lookups |

### 2.3 Architectural Patterns

| Pattern | Source | Reuse |
|---|---|---|
| Port / Adapter | `CashierProductPort` / `CashierProductPortImpl` | `PurchasingProductPort` follows the same interface segregation |
| JPA Specification | `ProductSpecification.java` | `PurchaseSpecification` for paginated, filtered search |
| Pagination wrapper | `PageResponseDto<T>` | Standard response envelope for all list endpoints |
| `@Transactional` orchestration | `ReceiptServiceImpl`, `ProductServiceImpl` | Atomic purchase confirmation |
| Exception hierarchy | `BadRequestException`, `ConflictException`, `ResourceNotFoundException`, `BusinessException` | Reused as-is for all validation errors |
| Markup calculation | `ProductServiceImpl.calculateMarkupPercentage()` | Same formula applied after weighted-average cost update |

---

## 3. Missing Backend Components

All new code lives in two new top-level packages to preserve the existing modular structure.

```
com.mgh.backend
├── purchasing/
│   ├── controller/
│   │   └── PurchaseController.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── PurchaseCreateRequest.java
│   │   │   ├── PurchaseUpdateRequest.java
│   │   │   ├── PurchaseItemRequest.java
│   │   │   └── PurchasePaymentRequest.java
│   │   ├── response/
│   │   │   ├── PurchaseResponseDto.java
│   │   │   ├── PurchaseListItemDto.java
│   │   │   ├── PurchaseItemResponseDto.java
│   │   │   └── PurchaseSummaryDto.java
│   │   └── filter/
│   │       ├── PurchaseSearchFilter.java
│   │       └── PurchaseSpecification.java
│   ├── entity/
│   │   ├── Purchase.java
│   │   ├── PurchaseItem.java
│   │   ├── PurchaseStatus.java               (DRAFT, COMPLETED, CANCELLED)
│   │   └── PurchasePaymentStatus.java        (PAID, PARTIALLY_PAID, UNPAID)
│   ├── mapper/
│   │   └── PurchaseMapper.java
│   ├── repository/
│   │   ├── PurchaseRepository.java
│   │   └── PurchaseItemRepository.java
│   └── service/
│       ├── PurchaseService.java
│       └── impl/PurchaseServiceImpl.java
│
├── inventory/
│   ├── controller/
│   │   └── StockMovementController.java
│   ├── dto/
│   │   ├── StockMovementDto.java
│   │   └── StockMovementFilter.java
│   ├── entity/
│   │   ├── StockMovement.java
│   │   └── StockMovementType.java            (PURCHASE, SALE, RETURN, REFILL, ADJUSTMENT)
│   ├── repository/
│   │   └── StockMovementRepository.java
│   └── service/
│       ├── InventoryMovementService.java
│       └── impl/InventoryMovementServiceImpl.java
│
└── product/port/
    └── PurchasingProductPort.java            (+ PurchasingProductPortImpl.java)
```

---

## 4. Business Rules & Calculation Models

### 4.1 Purchase & PurchaseItem Structure

#### `Purchase` (Header)

| Field | Type | Notes |
|---|---|---|
| `id` | Long | PK |
| `purchaseNumber` | String | Unique. Format: `PUR-YYYYMMDD-XXXX` |
| `invoiceDate` | LocalDateTime | Supplier invoice date |
| `supplier` | FK → Supplier | Required |
| `cashier` | FK → Cashier | Operator who recorded the invoice |
| `status` | `PurchaseStatus` | `DRAFT`, `COMPLETED`, `CANCELLED` |
| `paymentStatus` | `PurchasePaymentStatus` | `PAID`, `PARTIALLY_PAID`, `UNPAID` |
| `paymentMethod` | Enum | `CASH`, `BANK_TRANSFER`, `CREDIT` |
| `subtotal` | BigDecimal | Sum of all line totals |
| `discount` | BigDecimal | Invoice-level discount (financial only — does not affect item unit cost) |
| `totalAmount` | BigDecimal | `subtotal − discount` |
| `paidAmount` | BigDecimal | Cumulative payments received |
| `remainingAmount` | BigDecimal | `totalAmount − paidAmount` |
| `notes` | String | Free text |
| `createdAt` | LocalDateTime | Audit timestamp |
| `updatedAt` | LocalDateTime | Audit timestamp |

> **No tax field.** Tax is out of scope. Only `discount` (invoice-level) is tracked.

#### `PurchaseItem` (Line)

| Field | Type | Notes |
|---|---|---|
| `id` | Long | PK |
| `purchase` | FK → Purchase | Parent invoice |
| `product` | FK → Product | For reporting; nullable to survive product soft-delete |
| `barcode` | FK → ProductBarcode | The specific barcode being purchased |
| `productNameSnapshot` | String | Name at time of purchase — **never updated retroactively** |
| `barcodeValueSnapshot` | String | Barcode string at time of purchase — **never updated retroactively** |
| `quantity` | Double | Units purchased (must be > 0) |
| `baseBuyingPrice` | BigDecimal | Supplier quoted price before discount |
| `unitDiscount` | BigDecimal | Per-unit discount (may be 0) |
| `effectiveBuyingCost` | BigDecimal | `baseBuyingPrice − unitDiscount` — used for weighted-average calculation |
| `lineTotal` | BigDecimal | `quantity × effectiveBuyingCost` |
| `sellingPriceApplied` | BigDecimal | New selling price written to barcode (null = keep existing) |
| `stockBefore` | Double | Snapshot: barcode stock before purchase was applied |
| `stockAfter` | Double | Snapshot: barcode stock after purchase was applied |
| `buyingPriceBefore` | BigDecimal | Snapshot: barcode buying price before weighted-average update |
| `buyingPriceAfter` | BigDecimal | Snapshot: weighted-average cost after update |

**Barcode uniqueness rule:**
One barcode may appear **only once** per purchase invoice. The backend returns `409 Conflict` if a request contains duplicate barcode IDs within the same invoice. The frontend must merge duplicate lines into the existing line item before submission — not add a new line.

### 4.2 Supplier Relationship

- Each purchase must reference an existing `Supplier` record.
- An optional generic supplier (e.g., "مورد نقدي / Cash Purchase") may be pre-seeded to allow walk-in purchases without a registered account.
- Supplier details are not snapshotted on the purchase. If the supplier name must be preserved historically in a later phase, a `supplierNameSnapshot` field can be added then.

### 4.3 Invoice-Level Discount

Invoice-level `discount` is a **financial discount only**:

```
totalAmount = subtotal − discount
```

It does **not** alter individual item effective buying costs. For weighted-average cost valuation, only item-level `effectiveBuyingCost = baseBuyingPrice − unitDiscount` is used.

No tax fields exist anywhere in the purchasing domain. Tax is permanently out of scope.

### 4.4 Payment Status & Remaining Amount

```
totalAmount      = subtotal − discount
remainingAmount  = totalAmount − paidAmount
```

**Payment status — computed by backend, not accepted as client input:**

| Condition | Payment Status |
|---|---|
| `paidAmount = totalAmount` | `PAID` |
| `0 < paidAmount < totalAmount` | `PARTIALLY_PAID` |
| `paidAmount = 0` | `UNPAID` |

**Validation:**
- `paidAmount` must be `≥ 0`.
- `paidAmount` must not exceed `totalAmount`.
- `paymentStatus` is always derived and persisted by the backend.

### 4.5 Effective Buying Cost & Line Total

```
effectiveBuyingCost = baseBuyingPrice − unitDiscount
lineTotal           = quantity × effectiveBuyingCost
```

- `effectiveBuyingCost` must be `> 0` (minimum 0.01).
- All monetary arithmetic uses `BigDecimal` with scale 4 for intermediate calculations and scale 2 for persistence (`RoundingMode.HALF_UP`).
- Backend always recomputes `lineTotal`, `subtotal`, `totalAmount`, and `remainingAmount`. Client-supplied totals are discarded.

### 4.6 Selling Price & Profit Markup Percentage

Following the existing convention in `ProductServiceImpl.calculateMarkupPercentage()`:

```
markupPercentage = ((sellingPrice − buyingPrice) / buyingPrice) × 100
```

Where `buyingPrice` is the **new weighted-average cost** after the purchase is applied.

**Per-item selling price update rules:**
- If `sellingPriceApplied` is provided and `> 0`: update barcode `sellingPrice` and recalculate `expectedMarkupPercentage` using the new weighted-average cost.
- If `sellingPriceApplied` is `null` or `0`: preserve existing barcode `sellingPrice`, but still recalculate `expectedMarkupPercentage` against the new weighted-average cost.

**Group price propagation:**
An optional request flag `propagateGroupSellingPrice` (default `false`) controls whether the new selling price is propagated to sibling products in the same `ProductGroup`, following the existing pattern in `ProductController.updateProduct`.

### 4.7 Weighted-Average Inventory Cost Calculation

Triggered for each `PurchaseItem` during `completePurchase()`.

**Variables:**
- `Q_current` = stock on barcode **before** this purchase (read under pessimistic lock).
- `C_current` = `buyingPrice` on barcode before this purchase.
- `Q_new` = quantity in this purchase item.
- `C_effective` = `effectiveBuyingCost` for this item.

**Formula:**

```
IF Q_current > 0:
    C_weighted_avg = ( (Q_current × C_current) + (Q_new × C_effective) )
                     ────────────────────────────────────────────────────
                                   (Q_current + Q_new)

IF Q_current ≤ 0:
    C_weighted_avg = C_effective
```

> **Negative stock rule:** Negative existing stock is **not treated as existing inventory**. If the barcode stock is negative (e.g., from sales that exceeded recorded stock), it is treated as zero for the weighted-average calculation. The incoming purchase establishes the fresh cost baseline.

**Implementation note:**
```java
BigDecimal qCurrent = BigDecimal.valueOf(Math.max(barcode.getStock(), 0.0));
// Math.max ensures negative stock is treated as 0
```

### 4.8 Stock Updates — Atomic, on Completion Only

Stock and pricing changes are **only applied when a purchase transitions from `DRAFT` to `COMPLETED`**. A `DRAFT` purchase makes no inventory changes.

**Execution order for `completePurchase()`:**

1. Validate: all barcodes exist, are active (`deletedAt IS NULL`), and have no duplicate barcode IDs within this invoice.
2. Collect all `ProductBarcode` IDs from the purchase items. Sort ascending to prevent deadlocks.
3. Acquire `PESSIMISTIC_WRITE` locks on all affected `ProductBarcode` rows.
4. For each item (in ID-sorted order):
   a. Read `stockBefore` from the locked barcode.
   b. Compute `C_weighted_avg` using the formula above (`Q_current = max(stockBefore, 0)`).
   c. Set `barcode.stock = stockBefore + quantity`.
   d. Set `barcode.buyingPrice = C_weighted_avg`.
   e. If `sellingPriceApplied` is provided, update `barcode.sellingPrice` and recalculate `barcode.expectedMarkupPercentage`.
   f. Write snapshot values (`stockBefore`, `stockAfter`, `buyingPriceBefore`, `buyingPriceAfter`) to `PurchaseItem`.
   g. Insert an immutable `StockMovement` record.
5. Set `Purchase.status = COMPLETED`.
6. Commit. **If any step fails, the entire transaction rolls back — no partial stock updates.**

### 4.9 Inventory Movements Audit Trail

A `StockMovement` row is inserted for each item upon purchase completion.

| Field | Value |
|---|---|
| `productId` | Resolved from barcode FK |
| `barcodeId` | `ProductBarcode.id` |
| `movementType` | `PURCHASE` |
| `quantity` | `+Q_new` (always positive for purchases) |
| `stockBefore` | `Q_current` (actual barcode value, may be negative) |
| `stockAfter` | `Q_current + Q_new` |
| `unitCost` | `C_effective` |
| `referenceId` | `purchase.id` |
| `referenceNumber` | `purchase.purchaseNumber` |
| `createdAt` | Transaction timestamp |
| `cashierId` | Operator FK |

`StockMovement` records are **immutable**. They are never updated or deleted.

### 4.10 Historical Purchase Data Preservation

All `PurchaseItem` snapshot fields (`productNameSnapshot`, `barcodeValueSnapshot`, `baseBuyingPrice`, `effectiveBuyingCost`, `lineTotal`, `stockBefore`, `stockAfter`, `buyingPriceBefore`, `buyingPriceAfter`) are written **once at completion time** and are never updated, even if:
- Product prices are later changed from the product management screen.
- The product is soft-deleted.
- The supplier name is changed.

Historical purchase invoices always display the exact values that were true at the moment of purchase confirmation.

### 4.11 Purchase Lifecycle

```
          ┌──[cancelPurchase]──► CANCELLED
DRAFT ────┤
          └──[completePurchase]──► COMPLETED
```

**Allowed status transitions:**

| From | To | Inventory Effect |
|---|---|---|
| `DRAFT` | `COMPLETED` | Stock incremented, weighted-average cost applied, movements logged |
| `DRAFT` | `CANCELLED` | None — simple status update |
| `COMPLETED` | *(none)* | **Immutable.** No modifications, no cancellations, no reversals. |
| `CANCELLED` | *(none)* | **Immutable.** Already terminated. |

A `COMPLETED` invoice is permanent. Errors must be corrected via a separate manual stock adjustment (`POST /api/products/{id}/stock/adjust`) outside of the purchasing flow.

---

## 5. Resolved Ambiguities & Design Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | **Completed purchase cancellation / reversal** | **Not supported.** Completed invoices are permanent and immutable. No reversal flow exists in this feature. |
| 2 | **Tax fields** | **Out of scope.** No tax model. Only `discount` (invoice-level, financial only) is tracked. |
| 3 | **Invoice-level discount vs. item unit cost** | Invoice-level discount is financial only and does **not** affect the weighted-average cost calculation. Item-level `unitDiscount` does affect effective buying cost. |
| 4 | **Negative existing stock in weighted-average** | Negative stock is **not treated as existing inventory**. `Q_current = max(barcode.stock, 0)` is used in all calculations. |
| 5 | **Duplicate barcodes within a purchase** | **Rejected with `409 Conflict`.** One barcode per purchase line. Frontend must merge into the existing line rather than adding a new one. |
| 6 | **Supplier account ledger** | Per-invoice tracking only (`paidAmount`, `remainingAmount`, `paymentStatus`). No cross-invoice supplier balance ledger in this phase. |
| 7 | **Product types supported** | All active, non-deleted products with at least one active barcode (`INVENTORY` and `RAW`). |
| 8 | **Group price propagation** | Optional. Controlled by `propagateGroupSellingPrice` flag (default `false`), following the existing pattern in `ProductController`. |
| 9 | **Draft stock changes** | Draft invoices make **no inventory changes**. Only the `DRAFT → COMPLETED` transition updates stock and prices. |

---

## 6. Recommended Backend Architecture

```
[ HTTP Client ]
      │
      ▼
[ PurchaseController ]                 [ StockMovementController ]
      │                                           │
      ▼                                           │
[ PurchaseService ]                               │
      │                                           │
      │── (on completePurchase) ────────────────► [ InventoryMovementService ]
      │                                           │
      ▼                                           │
[ PurchasingProductPort ]                         │
      │                                           │
      ▼                                           ▼
[ ProductBarcodeRepository ]         [ StockMovementRepository ]
  (PESSIMISTIC_WRITE lock)           [ PurchaseRepository ]
                                     [ PurchaseItemRepository ]
```

### Package Ownership

| Package | Responsibility |
|---|---|
| `purchasing` | Purchase invoices, line items, payment recording, filtered search, DTO mapping |
| `inventory` | `StockMovement` entity, audit log queries, stock movement API |
| `product/port` | `PurchasingProductPort` interface + implementation — decouples purchasing from internal product service logic, following `CashierProductPort` pattern |

### Key Design Decisions

- No new transactional patterns are introduced. The existing `@Transactional` + pessimistic lock model from `ReceiptServiceImpl` is replicated.
- All monetary fields use `BigDecimal`. `Double` is used only for quantity fields (consistent with existing `ProductBarcode.stock`).
- `PurchaseStatus` transitions are validated in the service layer before any persistence occurs.
- The `PurchaseItem` FK to `ProductBarcode` is nullable (soft-delete compatible), but the `barcodeValueSnapshot` ensures historical accuracy regardless.

---

## 7. Phased Implementation Plan

### Phase 1 — Domain Entities, Repositories & Database Schema

**Goal:** Establish the persistent data layer. The application must start cleanly and create all tables without errors.

#### Files to Create

| File | Description |
|---|---|
| `purchasing/entity/PurchaseStatus.java` | Enum: `DRAFT`, `COMPLETED`, `CANCELLED` |
| `purchasing/entity/PurchasePaymentStatus.java` | Enum: `PAID`, `PARTIALLY_PAID`, `UNPAID` |
| `purchasing/entity/Purchase.java` | Header entity — see §4.1 for full field list |
| `purchasing/entity/PurchaseItem.java` | Line item entity with all snapshot fields — see §4.1 |
| `inventory/entity/StockMovementType.java` | Enum: `PURCHASE`, `SALE`, `RETURN`, `REFILL`, `ADJUSTMENT` |
| `inventory/entity/StockMovement.java` | Immutable audit record — all fields non-nullable |
| `purchasing/repository/PurchaseRepository.java` | Extends `JpaRepository<Purchase, Long>`, `JpaSpecificationExecutor<Purchase>` |
| `purchasing/repository/PurchaseItemRepository.java` | Standard `JpaRepository<PurchaseItem, Long>` |
| `inventory/repository/StockMovementRepository.java` | Extends `JpaRepository`, `JpaSpecificationExecutor` |

#### Required Database Indexes

| Table | Columns | Purpose |
|---|---|---|
| `purchases` | `purchase_number` (unique) | Invoice number lookup |
| `purchases` | `supplier_id`, `status`, `payment_status` | Filtering |
| `purchases` | `invoice_date DESC, id DESC` | Date-ordered navigation |
| `purchase_items` | `purchase_id` | Join to header |
| `purchase_items` | `barcode_id` | Barcode lookup |
| `purchase_items` | `(purchase_id, barcode_id)` UNIQUE | DB-level duplicate barcode enforcement |
| `stock_movements` | `product_id, created_at` | Per-product history queries |
| `stock_movements` | `barcode_id` | Per-barcode history |
| `stock_movements` | `reference_id, movement_type` | Purchase-to-movement join |

#### Dependencies
None. This is the foundation layer.

#### Manual Verification Points
- Application starts with `ddl-auto=update`; all tables and indexes are created without SQL errors.
- Insert two `PurchaseItem` rows with the same `(purchase_id, barcode_id)` manually via SQL — confirm the unique constraint violation is thrown.

---

### Phase 2 — Stock Movement Service & Purchasing Product Port (Calculation Engine)

**Goal:** Implement the weighted-average cost formula, pessimistic lock stock increment, and movement logging. This is the financial accuracy core of the feature.

#### Files to Create

| File | Description |
|---|---|
| `inventory/service/InventoryMovementService.java` | Interface — `recordMovement(...)` |
| `inventory/service/impl/InventoryMovementServiceImpl.java` | Inserts immutable `StockMovement` rows |
| `product/port/PurchasingProductPort.java` | Interface — `applyPurchaseStockIncrement(...)` |
| `product/port/impl/PurchasingProductPortImpl.java` | Acquires pessimistic lock, computes weighted-average, updates barcode, calls InventoryMovementService |
| `product/port/PurchaseStockIncrementResult.java` | Result record: `stockBefore`, `stockAfter`, `buyingPriceBefore`, `buyingPriceAfter` |

#### Files to Modify

| File | Change |
|---|---|
| `product/repository/ProductBarcodeRepository.java` | Add `findAllWithLockByIdIn(List<Long> ids, Sort sort)` for bulk pessimistic locking sorted by ID |

#### Weighted-Average Implementation

```java
// In PurchasingProductPortImpl.applyPurchaseStockIncrement():

// Negative stock treated as zero — not existing inventory
BigDecimal qCurrent = BigDecimal.valueOf(Math.max(barcode.getStock(), 0.0));
BigDecimal cCurrent = barcode.getBuyingPrice();
BigDecimal qNew     = BigDecimal.valueOf(quantity);
BigDecimal cEff     = effectiveBuyingCost;

BigDecimal cWeightedAvg;
if (qCurrent.compareTo(BigDecimal.ZERO) > 0) {
    cWeightedAvg = qCurrent.multiply(cCurrent)
                           .add(qNew.multiply(cEff))
                           .divide(qCurrent.add(qNew), 4, RoundingMode.HALF_UP);
} else {
    // Zero or negative existing stock: purchase sets the new baseline cost
    cWeightedAvg = cEff;
}
```

#### Dependencies
Phase 1 entities and repositories.

#### Important Validation Rules
- `quantity > 0`
- `effectiveBuyingCost ≥ 0.01`
- Barcode must be active (`deletedAt IS NULL`)

#### Manual Verification Points (Required Unit Tests)

| Scenario | Existing Stock | Existing Cost | Purchase Qty | Purchase Cost | Expected New Cost | Expected New Stock |
|---|---|---|---|---|---|---|
| A — Normal | 10 | 20.00 | 10 | 30.00 | **25.00** | 20 |
| B — Zero stock | 0 | any | 15 | 40.00 | **40.00** | 15 |
| C — Negative stock (treated as 0) | −5 | 25.00 | 10 | 50.00 | **50.00** | 5 |
| D — Rounding | 100 | 10.33 | 50 | 10.67 | **10.44** | 150 |

---

### Phase 3 — Purchase Service & Lifecycle Management (Business Logic)

**Goal:** Implement the complete invoice lifecycle: create draft, update draft, complete, cancel, and record payments.

#### Files to Create

| File | Description |
|---|---|
| `purchasing/dto/request/PurchaseCreateRequest.java` | Full invoice with items |
| `purchasing/dto/request/PurchaseUpdateRequest.java` | Same shape as create; only valid for `DRAFT` |
| `purchasing/dto/request/PurchaseItemRequest.java` | `barcodeId`, `quantity`, `baseBuyingPrice`, `unitDiscount`, `sellingPriceApplied` (optional) |
| `purchasing/dto/request/PurchasePaymentRequest.java` | `paymentAmount`, `paymentMethod`, `notes` |
| `purchasing/dto/response/PurchaseResponseDto.java` | Full invoice view with items |
| `purchasing/dto/response/PurchaseListItemDto.java` | Summary row for table views |
| `purchasing/dto/response/PurchaseItemResponseDto.java` | Line item with all snapshot values |
| `purchasing/dto/response/PurchaseSummaryDto.java` | Dashboard statistics |
| `purchasing/mapper/PurchaseMapper.java` | Entity ↔ DTO mapping |
| `purchasing/service/PurchaseService.java` | Interface |
| `purchasing/service/impl/PurchaseServiceImpl.java` | Full implementation |

#### PurchaseService Interface

```java
PurchaseResponseDto                  createPurchase(PurchaseCreateRequest request);
PurchaseResponseDto                  updateDraftPurchase(Long id, PurchaseUpdateRequest request);
PurchaseResponseDto                  completePurchase(Long id);
PurchaseResponseDto                  cancelPurchase(Long id);          // DRAFT only
PurchaseResponseDto                  addPayment(Long id, PurchasePaymentRequest request);
PurchaseResponseDto                  getPurchaseById(Long id);
PageResponseDto<PurchaseListItemDto> searchPurchases(PurchaseSearchFilter filter, Pageable pageable);
PurchaseSummaryDto                   getSummary();
```

#### Invoice Number Generation

```
Format:  PUR-YYYYMMDD-XXXX
Example: PUR-20261002-4A7F
```

Uses the same retry-loop uniqueness check pattern as `ReceiptServiceImpl.generateUniqueReceiptNumber()`.

#### Server-Side Total Recalculation

```java
// Called before every save (create and update)
subtotal        = sum(item.quantity × item.effectiveBuyingCost)
totalAmount     = subtotal − invoiceDiscount
remainingAmount = totalAmount − paidAmount
paymentStatus   = derived from remainingAmount and totalAmount
```

#### cancelPurchase Guard

```java
switch (purchase.getStatus()) {
    case COMPLETED -> throw new BusinessException("Completed purchases cannot be cancelled.");
    case CANCELLED -> throw new BusinessException("Purchase is already cancelled.");
    default        -> { /* DRAFT — proceed */ }
}
purchase.setStatus(PurchaseStatus.CANCELLED);
```

#### Dependencies
Phases 1 and 2.

#### Validation Rules

| Rule | Behaviour |
|---|---|
| At least 1 line item | `BadRequestException` |
| Supplier must exist | `ResourceNotFoundException` |
| All barcodes active | `ResourceNotFoundException` |
| Duplicate barcode IDs in request | `ConflictException` (409) |
| `effectiveBuyingCost > 0` | `BadRequestException` |
| `quantity > 0` | `BadRequestException` |
| `paidAmount ≥ 0` | `BadRequestException` |
| `paidAmount ≤ totalAmount` | `BadRequestException` |
| Update/cancel on `COMPLETED` invoice | `BusinessException` |

#### Manual Verification Points

- Creating a `DRAFT` purchase: `ProductBarcode.stock`, `buyingPrice`, and `sellingPrice` are **unchanged**.
- Confirming a `DRAFT` purchase: stock incremented, weighted-average cost applied, `StockMovement` rows inserted, `Purchase.status = COMPLETED` — all in a single transaction.
- Calling `completePurchase` on an already-`COMPLETED` invoice throws `BusinessException`.
- Recording a partial payment updates `remainingAmount` and sets `PARTIALLY_PAID`.
- Paying in full sets `PAID` and `remainingAmount = 0`.
- Cancelling a `COMPLETED` invoice throws `BusinessException`.
- Request with duplicate barcode IDs returns HTTP 409.

---

### Phase 4 — REST API, Filtering & Inventory Movement API (API Layer)

**Goal:** Expose all purchasing operations and stock movement history as REST endpoints.

#### Files to Create

| File | Description |
|---|---|
| `purchasing/controller/PurchaseController.java` | All purchase endpoints |
| `purchasing/dto/filter/PurchaseSearchFilter.java` | Filter DTO |
| `purchasing/dto/filter/PurchaseSpecification.java` | JPA Specification builder |
| `inventory/controller/StockMovementController.java` | Movement history endpoints |
| `inventory/dto/StockMovementDto.java` | API-facing movement response |
| `inventory/dto/StockMovementFilter.java` | Filter for movement queries |

#### PurchaseController Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/purchases` | Create purchase (Draft or direct Complete) |
| `GET` | `/api/purchases` | Paginated, filtered purchase list |
| `GET` | `/api/purchases/{id}` | Full detail view |
| `PUT` | `/api/purchases/{id}` | Update a `DRAFT` purchase |
| `POST` | `/api/purchases/{id}/complete` | Confirm and complete a draft |
| `POST` | `/api/purchases/{id}/payments` | Record a payment against an invoice |
| `POST` | `/api/purchases/{id}/cancel` | Cancel a `DRAFT` invoice |
| `GET` | `/api/purchases/summary` | Dashboard aggregate statistics |

#### PurchaseSearchFilter Parameters

| Parameter | Type | Description |
|---|---|---|
| `query` | String | Search by purchase number or supplier name |
| `supplierId` | Long | Filter by supplier |
| `status` | `PurchaseStatus` | Filter by invoice status |
| `paymentStatus` | `PurchasePaymentStatus` | Filter by payment status |
| `dateFrom` | LocalDate | Invoice date range start (inclusive) |
| `dateTo` | LocalDate | Invoice date range end (inclusive) |
| `page`, `size`, `sort` | Pageable | Standard pagination |

#### StockMovementController Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/products/{id}/stock/movements` | Paginated movement history for a specific product |
| `GET` | `/api/inventory/movements` | Global movement audit log (paginated, filterable) |

#### Standard Patterns

- `@Valid` on all request bodies.
- `PageResponseDto<T>` response envelope on all list endpoints.
- `GlobalExceptionHandler` + `CashierExceptionHandler` handle all validation and business exceptions uniformly.

#### Dependencies
Phases 1, 2, and 3.

#### Manual Verification Points

- All endpoints return correct HTTP codes: 200, 201, 400, 404, 409.
- Filtering by `status`, `paymentStatus`, `supplierId`, `dateFrom`, `dateTo` returns correct subsets.
- Paginated responses include consistent `totalElements` / `totalPages`.
- Unauthorized requests return 401/403 consistent with existing security configuration.

---

### Phase 5 — Catalog Invalidation & POS Integration Verification (Integration)

**Goal:** Confirm that completed purchases are immediately reflected in the live POS cashier stock levels.

#### End-to-End Verification Steps

1. Record current stock `S` for Product X, barcode B from `GET /api/products/all-products`.
2. Create a `DRAFT` purchase: Product X, barcode B, qty 50.
3. Confirm the draft via `POST /api/purchases/{id}/complete`.
4. Call `GET /api/products/all-products` again.
5. Verify barcode B stock = `S + 50`.
6. Verify barcode B `buyingPrice` = the computed weighted-average cost.
7. Open POS Cashier screen; scan barcode B.
8. Verify live stock shown matches database value.
9. Create a sale receipt for 3 units of barcode B.
10. Verify receipt creation succeeds and stock decrements to `S + 47`.

#### Frontend Invalidation Note

The Angular frontend's `ProductCatalogStore` uses a 5-minute TTL cache. After completing a purchase, the purchasing screen must call `catalogStore.invalidate()` before navigating back to POS — consistent with the pattern used by `ManageProductComponent` and `ProductTreeViewComponent` on mutations.

#### Dependencies
Phases 1–4 completed and tested.

---

## 8. Appendix — Constraint & Convention Summary

| Constraint | Rule |
|---|---|
| Monetary arithmetic | `BigDecimal` with `RoundingMode.HALF_UP`. Intermediate scale 4, persisted scale 2. |
| No `double`/`float` for money | Enforced. `Double` used only for quantity fields. |
| No tax fields | Tax is permanently out of scope for this feature. |
| Transactional boundary | All `completePurchase()` operations within a single `@Transactional`. |
| Pessimistic locking order | `ProductBarcode` rows locked by ascending `id` to prevent deadlocks. |
| `COMPLETED` invoices | Immutable. No updates, no cancellations, no reversals. |
| `DRAFT` invoices | No inventory effect whatsoever. |
| Negative existing stock in weighted-average | Treated as **0** — not counted as existing inventory. `Q_current = max(stock, 0)`. |
| Duplicate barcodes within one purchase | Rejected with `409 Conflict`. One barcode per line, enforced at service layer and DB constraint. |
| Historical snapshots | Written once at completion. Never updated retroactively. |
| Server-side totals | Backend always recomputes `lineTotal`, `subtotal`, `totalAmount`, `remainingAmount`, `paymentStatus`. Client values are discarded. |
| `paymentStatus` | Always derived by backend. Never accepted as client input. |
