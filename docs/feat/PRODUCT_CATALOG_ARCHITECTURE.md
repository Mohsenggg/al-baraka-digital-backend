# Product Catalog — Architecture & Implementation Reference

> **Document Location:** `Cashier-web-back/docs/feat/PRODUCT_CATALOG_ARCHITECTURE.md`
> **Status:** Complete (All 4 Phases Implemented)
> **Build Status:** ✅ `ng build` passes | ✅ 82/82 unit tests pass

---

## Table of Contents

1. [Feature Overview](#1-feature-overview)
2. [System Architecture](#2-system-architecture)
3. [Data Models & Type Contracts](#3-data-models--type-contracts)
4. [ProductCatalogStore — API Reference](#4-productcatalogstore--api-reference)
5. [Product Management Table — Two-Stream Architecture](#5-product-management-table--two-stream-architecture)
6. [Mutation Lifecycle & Cache Invalidation](#6-mutation-lifecycle--cache-invalidation)
7. [POS Integration (CashierStateService)](#7-pos-integration-cashierstateservice)
8. [Key Files & Responsibilities](#8-key-files--responsibilities)
9. [Backend Endpoints](#9-backend-endpoints)
10. [Performance Profile](#10-performance-profile)

---

## 1. Feature Overview

The Product Catalog system manages access to ~3,000 products across all parts of the POS application:

| Consumer | Access Pattern | Data Source |
|---|---|---|
| **Cashier / POS** (`/pos/cashier`) | Barcode scan, name search, stock validation, refills | `ProductCatalogStore` (O(1) in-memory) |
| **Product Search Popups** | Auto-complete dialogs | `ProductCatalogStore` (filtered in-memory) |
| **Product Management Table** (`/pos/products`) | Paginated list, cascading filters | `ProductStateService` (server-side, paginated) |
| **Product Tree View** (`/pos/products/tree`) | Drag-and-drop hierarchy management | `ProductTreeViewComponent` (own API calls) |
| **Manage Product Form** (`/pos/product/manage`) | Composition/BOM material search, conversion lookup | `ProductCatalogStore` (in-memory) |

### The Two-Tier Access Pattern

```
High-Frequency / Low-Latency                   Large-Scale / Server-Driven
─────────────────────────────                  ─────────────────────────────
ProductCatalogStore                            ProductStateService
  • Single load, 5-min TTL cache                 • Paginated (100 rows/page)
  • O(1) barcode & ID hash-map lookups           • Server-side filtering via
  • Feeds: POS, popups, manage form                PostgreSQL JPA Specifications
  • Endpoint: GET /api/products/all-products     • Endpoint: GET /api/products
```

Legacy `ProductService` (monolithic array-scan pattern) has been **deleted**. Both tiers replace it entirely.

---

## 2. System Architecture

```
                            BACKEND (Spring Boot)
                                    |
         +──────────────────────────+──────────────────────────+
         |                         |                          |
GET /api/products/all-products  GET /api/products/tree    GET /api/products
   (~250 KB — full catalog)     ?includeProducts=false    ?page=0&size=100
                                   (~30 KB skeleton)       (~40 KB page)
         |                         |                          |
         v                         v                          v
+─────────────────────+   +────────────────────+   +─────────────────────+
|  ProductCatalogStore|   |  Hierarchy Skeleton |   |  Paginated Table    |
|  (Global Singleton) |   |  (Cached, once/ses.)|   |  (Fetched on demand)|
|  • products signal  |   |  • categories       |   |  • products[]       |
|  • barcodeIndex Map |   |  • availableBrands  |   |  • totalElements    |
|  • idIndex Map      |   |  • availableGroups  |   |  • totalPages       |
|  • TTL: 5 min       |   +────────+───────────+   +────────+────────────+
|  • invalidate()     |            +──────────────+──────────+
+──────────+──────────+                           v
           |                         +────────────────────────+
           |                         |    ProductStateService  |
           |                         |    (Two-Stream)         |
           |                         +────────────+───────────+
           |                                      |
    +──────+──────────────────────────────────────+───────────────────+
    |                         |                                       |
    v                         v                                       v
CashierStateService   ManageProductComponent              ProductsMainPageComponent
(POS / Receipts)      (Composition & BOM Search)          (Table + Cascading Filters)
```

---

## 3. Data Models & Type Contracts

**File:** `src/app/core/products/models/catalog-product.model.ts`

```typescript
// Extends the core POS Product — 100% type-compatible with CartItem and SharedProduct
export interface CatalogProduct extends Product {
  status?: string;
}

// Multi-criteria search filter for in-memory search
export interface CatalogFilterCriteria {
  query?: string;
  stockStatus?: 'all' | 'in_stock' | 'out_of_stock';
  minPrice?: number | null;
  maxPrice?: number | null;
  activeOnly?: boolean;
}

// Batch stock update after receipt creation/modification
export interface StockUpdateItem {
  barcode: string;
  remainingStock: number;
}

// Additive stock restoration after receipt deletion
export interface StockRestoreItem {
  barcode: string;
  quantity: number;
}
```

**Contract guarantees:**
- `CatalogProduct extends Product` ensures compatibility with all POS models.
- Missing backend DTO fields (`costPrice`, `isActive`) are safely defaulted during mapping.

---

## 4. `ProductCatalogStore` — API Reference

**File:** `src/app/core/products/services/product-catalog.store.ts`

### Signals & Computed Properties

| Member | Type | Description |
|---|---|---|
| `products` | `Signal<CatalogProduct[]>` | All cached products (readonly). |
| `isLoading` | `Signal<boolean>` | True while fetching from backend. |
| `isLoaded` | `Signal<boolean>` | Computed — true when cache is populated. |
| `error` | `Signal<string \| null>` | Error message from last failed load. |
| `totalCount` | `Signal<number>` | Computed count of cached products. |
| `barcodeIndex` | `Signal<Map<string, CatalogProduct>>` | O(1) hash map indexed by lowercased barcode. |
| `idIndex` | `Signal<Map<number, CatalogProduct>>` | O(1) hash map indexed by product ID. |

### Methods

#### `loadCatalog(forceRefresh?: boolean): Observable<CatalogProduct[]>`
Fetches via `GET /api/products/all-products`. Returns cached data immediately if within the 5-minute TTL window. Uses `shareReplay(1)` to deduplicate concurrent calls during startup.

#### `findByBarcode(barcode: string): CatalogProduct | undefined`
O(1) lookup using `barcodeIndex`. Primary method for POS barcode scanner.

#### `findById(id: number): CatalogProduct | undefined`
O(1) lookup using `idIndex`.

#### `search(criteria: CatalogFilterCriteria): CatalogProduct[]`
In-memory multi-criteria search supporting name/barcode query, active-only, stock status, and price range filters.

#### `updateStockBatchByBarcode(items: StockUpdateItem[]): void`
Atomically updates `stockQuantity` and `stock` in-memory after receipt creation or modification.

#### `restoreStockBatchByBarcode(items: StockRestoreItem[]): void`
Additive batch stock restoration after receipt deletion.

#### `applyRefillUpdate(updatedChild, parentProductId, parentUnitsUsed): void`
Atomic 3-entity cascade for unit refills:
1. Updates child product (stock, prices, name).
2. Decrements parent stock by `parentUnitsUsed`.
3. Synchronizes `parentStock` on all sibling `refillOptions` referencing the parent.

#### `patchProduct(updated: Partial<CatalogProduct> & { id: number }): void`
Updates a single product record in-memory when edited from the manage form.

#### `invalidate(): void`
Clears the TTL timestamp — forces a fresh backend fetch on the next `loadCatalog()` call. Must be called after any structural mutation (create, update, delete, rename, move).

---

## 5. Product Management Table — Two-Stream Architecture

The `/pos/products` page uses two independent data streams to avoid the previous monolithic `GET /api/products/tree?includeProducts=true` (~2.2 MB) call.

### Stream 1: Hierarchy Skeleton (Cascading Filters)

- **Endpoint:** `GET /api/products/tree?includeProducts=false`
- **Payload:** ~30 KB (categories → brands → groups, no product objects)
- **Loaded:** Once per session, cached in-memory in `ProductStateService`.
- **Purpose:** Populates the Category → Brand → Group cascading dropdown filters.

### Stream 2: Paginated Table Rows

- **Endpoint:** `GET /api/products?page=N&size=100&query=...&categoryId=X&productGroupId=Y`
- **Payload:** ~40 KB (100 rows)
- **Loaded:** On every filter change, search query, or page navigation.
- **Purpose:** Displays the table. Filtering and pagination are handled server-side by PostgreSQL.

### Cascading Filter Behavior (Unchanged UX)

| User Action | Behavior |
|---|---|
| Select Category | `availableBrands` recomputed from skeleton; Group dropdown clears |
| Select Brand | `availableGroups` recomputed from skeleton; table re-fetches |
| Select Group | Table re-fetches with `productGroupId` filter |
| Type search query | Table re-fetches after 300 ms debounce |
| Change page | Next server page fetched with current active filters |

### `ProductStateService` — Responsibilities

**File:** `src/app/features/pos/products/services/product-state.service.ts`

```typescript
// Stream 1: Skeleton — loaded once, cached in-memory
private skeletonSignal = signal<any[]>([]);
public categories = computed(() => skeletonSignal().map(c => ({ id: c.id, name: c.name })));
public availableBrands = computed(() => { /* traverses skeletonSignal */ });
public availableGroups = computed(() => { /* traverses skeletonSignal */ });

public loadSkeleton(): void {
  if (this.skeletonSignal().length > 0) return; // cache hit — no network call
  this.api.getHierarchySkeleton().subscribe(...);
}

// Stream 2: Paginated table — fetched on demand
private _pagedProducts = signal<ProductListItemDto[]>([]);
public products = this._pagedProducts.asReadonly();
public totalProducts = signal<number>(0);
public totalPages = signal<number>(1);

public loadProductsPage(): void {
  // Builds filter from active signals, calls GET /api/products
}
```

**Removed signals** (replaced by server-side equivalents):
- `allProductsFromTree` → replaced by `_pagedProducts` (server-driven).
- `filteredProducts` → replaced by server-side PostgreSQL filtering.
- `treeDataSignal` → replaced by `skeletonSignal`.

---

## 6. Mutation Lifecycle & Cache Invalidation

Every structural mutation must call `catalogStore.invalidate()` to keep the in-memory store consistent with the database.

### Invalidation Points

| Mutation | Location | Trigger |
|---|---|---|
| Product create / update | `ManageProductComponent.persistProduct()` | On success |
| Node rename (Category/Brand/Group) | `ProductTreeViewComponent.submitRename()` | On success |
| Node deletion | `ProductTreeViewComponent.deleteNode()` | On success |
| Node / product move | `ProductTreeViewComponent.submitMove()` | On success |
| Group price unification toggle | `ProductTreeViewComponent.toggleGroupPriceUnification()` | On success |

### How Invalidation Works

```
User mutates data
      |
      v
API call succeeds
      |
      v
catalogStore.invalidate()  <── clears TTL timestamp
      |
      v
Next loadCatalog() call  ──> fresh GET /api/products/all-products
```

The store does **not** push reactive invalidation. Consumers that call `loadCatalog()` after TTL is cleared will automatically receive fresh data on their next natural load cycle (POS startup, next popup open, next form open).

---

## 7. POS Integration (`CashierStateService`)

**File:** `src/app/features/pos/core/services/cashier-state.service.ts`

`CashierStateService` acts as a high-level POS coordinator and delegates all product catalog access to `ProductCatalogStore`:

```typescript
@Injectable({ providedIn: 'root' })
export class CashierStateService {
  private catalogStore = inject(ProductCatalogStore);

  // Expose store signal directly for template backward-compatibility
  public products = this.catalogStore.products;

  // Catalog load with TTL cache
  public loadAllProducts(forceRefresh = false): Observable<Product[]> {
    return this.catalogStore.loadCatalog(forceRefresh).pipe(
      tap(() => {
        this.refreshNavigationCacheStock();
        this.refreshCurrentReceiptDisplay();
      })
    );
  }

  // O(1) barcode lookup
  private getLiveStockForProductCode(code: string, fallback = 0): number {
    return this.catalogStore.findByBarcode(code)?.stockQuantity ?? fallback;
  }

  // Delegated batch stock mutations (after receipt create/edit)
  private updateProductsCacheFromReceipt(receipt: ReceiptResponse): void {
    const items = receipt.items
      .filter(i => i.productCode && i.remainingStock !== undefined)
      .map(i => ({ barcode: i.productCode, remainingStock: i.remainingStock }));
    this.catalogStore.updateStockBatchByBarcode(items);
  }

  // Delegated stock restoration (after receipt delete)
  private restoreStockForDeletedReceipt(receipt: ReceiptResponse): void {
    const items = receipt.items
      .filter(i => i.productCode && i.quantity)
      .map(i => ({ barcode: i.productCode, quantity: i.quantity }));
    this.catalogStore.restoreStockBatchByBarcode(items);
  }

  // Delegated atomic refill cascade
  public executeRefill(payload: RefillExecuteRequest): Promise<Product> {
    return firstValueFrom(this.api.executeRefill(payload).pipe(
      map(response => {
        const updatedProduct = this.normalizeRefillExecuteResponse(response, payload);
        this.catalogStore.applyRefillUpdate(
          updatedProduct, payload.parentProductId, payload.parentUnitsUsed
        );
        return updatedProduct;
      })
    ));
  }
}
```

---

## 8. Key Files & Responsibilities

### Core (Shared)

| File | Role |
|---|---|
| `src/app/core/products/services/product-catalog.store.ts` | Singleton in-memory store; O(1) indexes; TTL cache; batch stock mutations |
| `src/app/core/products/models/catalog-product.model.ts` | `CatalogProduct`, `CatalogFilterCriteria`, `StockUpdateItem`, `StockRestoreItem` |

### POS / Cashier

| File | Role |
|---|---|
| `src/app/features/pos/core/services/cashier-state.service.ts` | POS coordinator; delegates all catalog access to `ProductCatalogStore` |

### Product Management (Frontend)

| File | Role |
|---|---|
| `src/app/features/pos/products/services/product-api.service.ts` | HTTP layer; `getHierarchySkeleton()`, `listProducts()`, CRUD endpoints |
| `src/app/features/pos/products/services/product-state.service.ts` | Two-stream state manager; skeleton + paginated table; cascading filter signals |
| `src/app/features/pos/products/components/products-main-page/` | Products table page; binds to `ProductStateService` |
| `.../components/new/productTreeView/product-tree-view.component.ts` | Drag-and-drop tree; injects `ProductCatalogStore`; calls `invalidate()` on mutations |
| `.../components/manage-product/manage-product.component.ts` | Create/edit form; uses `catalogStore.loadCatalog()` for BOM search; calls `invalidate()` on save |

### Backend (Spring Boot)

| File | Role |
|---|---|
| `ProductController.java` | REST endpoints; accepts `productGroupId` filter param |
| `ProductSearchFilter.java` | Filter DTO; includes `productGroupId` field |
| `ProductSpecification.java` | JPA Specification builder; includes `productGroupId` predicate |

---

## 9. Backend Endpoints

| Endpoint | Payload | Used By |
|---|---|---|
| `GET /api/products/all-products` | ~250 KB (all products, flat) | `ProductCatalogStore` initial load |
| `GET /api/products/tree?includeProducts=false` | ~30 KB (hierarchy skeleton) | `ProductStateService.loadSkeleton()` |
| `GET /api/products/tree?includeProducts=true` | ~2.2 MB (full nested tree) | `ProductTreeViewComponent` only |
| `GET /api/products?page=N&size=100&...` | ~40 KB (one page) | `ProductStateService.loadProductsPage()` |

> `GET /api/products/tree?includeProducts=true` is only called by the Tree View page for its drag-and-drop display. It is **no longer used** by the Products Table.

---

## 10. Performance Profile

### In-Memory Store (~3,000 Products)

| Metric | Value |
|---|---|
| Raw JSON payload | ~250 KB |
| Transfer size (Gzip, local) | ~50 KB (~35 ms) |
| V8 heap footprint | ~400 KB |
| Barcode lookup | < 0.01 ms (O(1)) |
| In-memory search | ~1–3 ms |

### Products Table — Before vs. After

| Metric | Before (Monolithic Tree) | After (Two-Stream) |
|---|---|---|
| Initial payload | ~2,200 KB | ~30 KB + ~40 KB = **~70 KB** |
| Backend processing | 5 bulk queries + 6,000 Hibernate entities | 1 JPA Spec query (100 rows) |
| Transfer time (Docker/WSL2) | ~400–1,000 ms | **< 25 ms** |
| Client CPU (JS tree flattening) | ~55 ms | **~0 ms** |
| Total perceived load time | ~1,200–1,800 ms | **< 60 ms** |

### Lookup Performance — Before vs. After

| Operation | Before | After |
|---|---|---|
| Barcode scanner lookup | O(N) array scan ~0.5–2 ms | **O(1) < 0.01 ms** |
| Receipt item population | O(M×N) | **O(M) hash map lookups** |
| Catalog re-fetch on navigation | Every navigation | **5-min TTL cache** |
| Stock deduction | Manual array copy | **Atomic batch map update** |
| Refill cascade | Manual nested loops | **Atomic 3-entity cascade** |
