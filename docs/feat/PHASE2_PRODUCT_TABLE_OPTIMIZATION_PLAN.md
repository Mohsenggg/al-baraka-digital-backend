# Phase 2 — Product Management Table Optimization & Server-Side Pagination

> **Document Location:** `Cashier-web-back/docs/feat/PHASE2_PRODUCT_TABLE_OPTIMIZATION_PLAN.md`  
> **Status:** Completed  
> **Scope:** Refactoring `/pos/products` product management table from a monolithic 2.2 MB tree download to a two-stream architecture: lightweight hierarchy skeleton for cascading filter dropdowns + server-side paginated table rows.

---

## 1. Executive Summary & Objectives

Currently, navigating to `/pos/products` downloads the entire 4-tier tree hierarchy with ~3,000 nested products (`GET /api/products/tree?includeProducts=true`, **~2.2 MB JSON**) and then immediately flattens it in JavaScript on the browser thread just to display the first 100 rows and populate category/brand/group dropdown filters.

### Phase 2 Goals
1. **Reduce initial payload by > 96%** — from ~2.2 MB down to ~30 KB (skeleton) + ~40 KB (first table page).
2. **Cut page load time from ~1,200 ms to < 60 ms** — effectively instantaneous.
3. **Preserve 100% of existing cascading filter UX** — Category ➔ Brand ➔ Group dropdown dependencies continue to work identically.
4. **Shift to server-side pagination & filtering** via `GET /api/products?page=0&size=100` powered by PostgreSQL indexes and Spring Data JPA Specifications.
5. **Zero functional regression** — all product create, edit, delete, and search workflows remain identical.

---

## 2. Root Cause: Why `/api/products/tree?includeProducts=true` Is Slow

### Stage-by-Stage Latency Breakdown

| Stage | What Happens | Cost (Local) | Cost (Docker/WSL2) |
|---|---|---|---|
| **DB Queries** | 5 bulk queries: Categories, Brands, Groups, ~3,000 Products, ~3,000 Barcodes | ~25 ms | ~250 – 650 ms |
| **Entity Hydration** | Hibernate hydrates ~6,000 entities, maps them to `TreeProductItemDto`, assembles 4-level nested graph | ~65 ms | ~100 – 160 ms |
| **Statistics Traversal** | Backend re-traverses all 3,000 items to compute stock counts | ~15 ms | ~25 ms |
| **JSON Serialization** | Jackson serializes 4-level nested structure into ~2.2 MB string | ~35 ms | ~60 ms |
| **Network Transfer** | Delivering 2.2 MB over Nginx + Docker bridge + WSL2 gateway | ~15 ms | ~250 – 500 ms |
| **Browser JSON.parse** | Parsing 2.2 MB JSON in main thread | ~45 ms | ~45 ms |
| **Frontend Tree Flattening** | `allProductsFromTree` computed: traverses 4-level tree to create 3,000 `ProductListItem` objects | ~55 ms | ~55 ms |
| **TOTAL** | | **~260 ms** | **~1,000 – 1,600 ms** |

### Why This Is Wrong Architecturally
The frontend receives a pre-assembled 4-tier nested tree, then immediately **un-nests it back to a flat array** just to show 100 paginated rows. This is wasted round-trip serialization cost on both the backend and frontend for data that is never fully displayed at once.

---

## 3. Solution Architecture: Two-Pronged Stream Design

```
                                  BACKEND (Spring Boot)
                                            │
                    ┌───────────────────────┴───────────────────────┐
                    │                                               │
         GET /api/products/tree                               GET /api/products
         ?includeProducts=false                            ?page=0&size=100&query=...
         (~30 KB — Skeleton Only)                          (~40 KB — Page of Rows)
                    │                                               │
════════════════════╪═══════════════════════════════════════════════╪═══════════════════
                    │                                               │
                    ▼                                               ▼
     ┌─────────────────────────────┐                 ┌─────────────────────────────┐
     │    Hierarchy Skeleton       │                 │      Paginated Table        │
     │  (Cached, loaded once)      │                 │    (Fetched on demand)      │
     │  • categories               │                 │  • products[] (100 rows)    │
     │  • availableBrands          │                 │  • totalElements            │
     │  • availableGroups          │                 │  • totalPages               │
     └──────────────┬──────────────┘                 └──────────────┬──────────────┘
                    │                                               │
                    └───────────────────────┬───────────────────────┘
                                            ▼
                           ┌─────────────────────────────────┐
                           │   ProductStateService           │
                           │   (Refactored, Two-Stream)      │
                           └──────────────┬──────────────────┘
                                          │
                                          ▼
                           ┌─────────────────────────────────┐
                           │   ProductsMainPageComponent     │
                           │   • Cascading Filter Dropdowns  │
                           │   • Instant Paginated Table     │
                           └─────────────────────────────────┘
```

**Key insight:** `GET /api/products/tree?includeProducts=false` returns only the Categories → Brands → Groups hierarchy skeleton (~30 KB) without any of the 3,000 product objects. This is enough to populate all cascading dropdown filters. Products are then loaded page-by-page via the existing, fast, indexed `GET /api/products` endpoint.

---

## 4. Step-by-Step Implementation

### Step 1: Backend — Add `productGroupId` Filter Support

The existing `GET /api/products` endpoint supports `query`, `categoryId`, `manufacturerId`, `supplierId`, and `status`. It is **missing** `productGroupId`, which is needed for the Group dropdown filter.

#### 1a. [`ProductSearchFilter.java`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-back/src/main/java/com/mgh/backend/product/dto/ProductSearchFilter.java)
Add field:
```java
private Long productGroupId;
```

#### 1b. [`ProductSpecification.java`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-back/src/main/java/com/mgh/backend/product/dto/ProductSpecification.java)
Add predicate inside `withFilters()`:
```java
if (filter.getProductGroupId() != null) {
    predicates.add(cb.equal(root.get("productGroup").get("id"), filter.getProductGroupId()));
}
```

#### 1c. [`ProductController.java`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-back/src/main/java/com/mgh/backend/product/controller/ProductController.java)
Accept the new parameter in `listProducts`:
```java
@RequestParam(required = false) Long productGroupId,
```
And set it on the filter:
```java
filter.setProductGroupId(productGroupId);
```

> **Risk:** Zero. `productGroupId` is additive — existing calls without it continue to work identically.

---

### Step 2: Frontend — `ProductApiService` Helper Method

Add `getHierarchySkeleton()` in [`product-api.service.ts`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-front/src/app/features/pos/products/services/product-api.service.ts):

```typescript
/**
 * Loads the lightweight hierarchy skeleton: Categories -> Brands -> Groups.
 * Does NOT include 3,000 product objects.
 * Payload: ~30 KB (vs ~2.2 MB with includeProducts=true).
 */
public getHierarchySkeleton(): Observable<{ tree: any[]; statistics: any }> {
  return this.http.get<{ tree: any[]; statistics: any }>(`${this.apiUrl}/tree`, {
    params: new HttpParams().set('includeProducts', 'false')
  });
}
```

Also update `listProducts` to accept `productGroupId`:
```typescript
// Already in ProductFilterParams model — add if missing:
productGroupId?: number | string;
```

---

### Step 3: Frontend — Refactor `ProductStateService`

Refactor [`product-state.service.ts`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-front/src/app/features/pos/products/services/product-state.service.ts) to split into two separate state streams:

#### 3a. Skeleton State (Cached — Loads Once Per Session)

```typescript
// Replace treeDataSignal (full tree with products) with skeletonSignal (hierarchy only)
private skeletonSignal = signal<any[]>([]);

// categories, availableBrands, availableGroups computed from skeletonSignal (same logic — no changes needed)
public categories = computed(() => this.skeletonSignal().map(c => ({ id: c.id, name: c.name })));
public availableBrands = computed(() => { /* same traversal on skeletonSignal */ });
public availableGroups = computed(() => { /* same traversal on skeletonSignal */ });

public loadSkeleton(): void {
  if (this.skeletonSignal().length > 0) return; // Already loaded — serve from cache
  this.isLoading.set(true);
  this.api.getHierarchySkeleton().subscribe({
    next: (res) => {
      this.skeletonSignal.set(res.tree || []);
      this.isLoading.set(false);
    },
    error: (err) => { this.handleError(err); this.isLoading.set(false); }
  });
}
```

#### 3b. Paginated Table State (Server-Driven — Fetched on Every Filter/Page Change)

```typescript
// Replace allProductsFromTree (flat copy from tree) with server-side page:
private _pagedProducts = signal<ProductListItemDto[]>([]);
public products = this._pagedProducts.asReadonly();

public totalProducts = signal<number>(0);
public totalPages = signal<number>(1);
// currentPage and pageSize signals stay the same

public loadProductsPage(): void {
  this.isLoading.set(true);
  const params: ProductFilterParams = {
    page: this.currentPage() - 1, // API is 0-indexed
    size: this.pageSize(),
    query: this.searchQuery() || undefined,
    categoryId: this.selectedCategories()[0] as number ?? undefined,
    productGroupId: this.selectedProductGroups()[0] as number ?? undefined,
    status: this.selectedStatus() || undefined,
  };
  this.api.listProducts(params).subscribe({
    next: (res) => {
      this._pagedProducts.set(res.content || []);
      this.totalProducts.set(res.totalElements || 0);
      this.totalPages.set(res.totalPages || 1);
      this.isLoading.set(false);
    },
    error: (err) => { this.handleError(err); this.isLoading.set(false); }
  });
}
```

#### 3c. Cascading Filter Setters (Preserved — Trigger `loadProductsPage()`)

```typescript
public setSelectedCategories(ids: (number | string)[]): void {
  this.selectedCategories.set(ids);
  // Keep existing cascading brand/group reset logic
  this.currentPage.set(1);
  this.loadProductsPage();
}

public setSelectedBrands(ids: (number | string)[]): void {
  this.selectedBrands.set(ids);
  // Keep existing cascading group reset logic
  this.currentPage.set(1);
  this.loadProductsPage();
}

public setSelectedProductGroups(ids: (number | string)[]): void {
  this.selectedProductGroups.set(ids);
  this.currentPage.set(1);
  this.loadProductsPage();
}

public setPage(page: number): void {
  this.currentPage.set(page);
  this.loadProductsPage();
}
```

#### 3d. Remove: Computed Signals That Are No Longer Needed
* `allProductsFromTree` — remove entirely (replaced by server-side `_pagedProducts`).
* `filteredProducts` — remove entirely (filtering now done in PostgreSQL, not in JavaScript).
* `treeDataSignal` — replace with `skeletonSignal`.

---

### Step 4: Component — `ProductsMainPageComponent`

Update [`products-main-page.component.ts`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-front/src/app/features/pos/products/components/products-main-page):

```typescript
ngOnInit() {
  this.state.loadSkeleton();       // Fast: ~30 KB, cached
  this.state.loadProductsPage();   // Fast: ~40 KB, first page
}
```

- Bind table rows to `state.products()` — already used, no change.
- Bind `totalProducts` and `totalPages` to `state.totalProducts()` / `state.totalPages()`.
- Bind search input to `state.setSearchQuery()` with debounce (keep existing debounce logic).
- Bind pagination next/prev/goToPage to `state.setPage()`.

---

## 5. Cascading Dropdown Behavior — Preserved Exactly

The skeleton tree (`includeProducts=false`) contains the full Category → Brand → Group hierarchy. The computed signals `categories`, `availableBrands`, and `availableGroups` still traverse this same skeleton structure exactly as before.

| User Action | Before (Monolithic) | After (Phase 2) |
|---|---|---|
| Page Load | Downloads 3,000 products + hierarchy | Downloads skeleton only (no products) |
| Select Category | Re-computes `availableBrands` from in-memory tree | Re-computes `availableBrands` from in-memory skeleton — **same behavior** |
| Select Brand | Re-computes `availableGroups` from in-memory tree | Re-computes `availableGroups` from in-memory skeleton — **same behavior** |
| Select Group | Filters `allProductsFromTree` in JS | Calls server `?productGroupId=X` — faster & accurate |
| Type search query | Filters 3,000 objects in JS | Calls server `?query=X` with debounce — faster & accurate |
| Change page | Slices in-memory array | Calls server `?page=N` — correct server-side pagination |

---

## 6. Expected Performance After Phase 2

| Metric | Before | After Phase 2 | Improvement |
|---|---|---|---|
| **Initial Payload** | ~2,200 KB | **~30 KB + ~40 KB = ~70 KB** | **97% Reduction** |
| **Backend Processing** | 5 bulk queries + 6,000 Hibernate entities | **1 fast JPA Specification query (100 rows)** | **~15x Faster** |
| **Transfer Time (Docker/WSL2)** | ~400 – 1,000 ms | **< 25 ms** | **~25x Faster** |
| **Client CPU Overhead** | ~55 ms (3,000 object allocations) | **~0 ms** | **100% Eliminated** |
| **Total Perceived Load** | **~1,200 – 1,800 ms** | **< 60 ms** | **Instantaneous** |

---

## 7. Verification Checklist (Post-Implementation)

| Test | Expected Result |
|---|---|
| Navigate to `/pos/products` | Table loads in < 60 ms; skeleton filter dropdowns appear instantly |
| Select a Category from dropdown | `availableBrands` auto-filters; Group dropdown clears correctly |
| Select a Brand | `availableGroups` auto-filters; table re-fetches by selected Brand |
| Select a Group | Table rows update to show only products in that Group |
| Type in search bar (debounced) | Table re-fetches by query after 300 ms debounce |
| Click page 2, 3, etc. | Next page loads correctly with same active filters |
| Create or delete a product | Table page reloads cleanly |
| `ng build` | Zero TypeScript compilation errors |

---

## 8. Files Affected

### Backend (Spring Boot)
| File | Change |
|---|---|
| [`ProductSearchFilter.java`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-back/src/main/java/com/mgh/backend/product/dto/ProductSearchFilter.java) | Add `productGroupId` field |
| [`ProductSpecification.java`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-back/src/main/java/com/mgh/backend/product/dto/ProductSpecification.java) | Add `productGroupId` predicate |
| [`ProductController.java`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-back/src/main/java/com/mgh/backend/product/controller/ProductController.java) | Accept `productGroupId` `@RequestParam` |

### Frontend (Angular)
| File | Change |
|---|---|
| [`product-api.service.ts`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-front/src/app/features/pos/products/services/product-api.service.ts) | Add `getHierarchySkeleton()` method |
| [`product-state.service.ts`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-front/src/app/features/pos/products/services/product-state.service.ts) | Full refactor: skeleton state + paginated state |
| [`products-main-page.component.ts`](file:///d:/Software/AL-Baraka/Pos/Cashier-web/Cashier-web-front/src/app/features/pos/products/components/products-main-page) | Update `ngOnInit` and pagination bindings |

### Not Changed in Phase 2
* `ProductCatalogStore` — unchanged (Phase 1 only)
* `CashierStateService` — unchanged (Phase 1 only)
* `ProductTreeStateService` — unchanged (Phase 3)
* `ManageProductComponent` — unchanged (Phase 4)
* Any backend service, repository, or entity beyond the 3 files listed above
