# Product Catalog Store — Architecture & Implementation Documentation

> **Document Location:** `Cashier-web-back/docs/feat/PRODUCT_CATALOG_STORE_DOCUMENTATION.md`  
> **Status:** Implemented (Phase 1 Complete)  
> **Scope:** Shared In-Memory Product Catalog Store, Data Model Contracts, $O(1)$ Indexing, Mutation Lifecycles, and POS Integration.

---

## 1. Overview & Motivation

In the POS application, product catalog data (~3,000 items) is needed across multiple pages and features:
* **Cashier / POS (`/pos/cashier`):** High-frequency barcode scanning, name search, stock validation, and atomic unit refills.
* **Product Search Popups (`ProductSearchPopupComponent`):** Fast auto-complete and search dialogs.
* **Product Management Table (`/pos/products`):** Multi-facet filtering, sorting, and paginated display.
* **Product Tree View (`/pos/products/tree`):** Hierarchical Category ➔ Brand ➔ Group ➔ Product structure.
* **Product Create / Edit Form (`/pos/product/manage`):** Composition materials and conversion rule lookups.

### Previous Limitations
1. **Redundant Network Calls:** Multiple components fetched product data independently.
2. **Linear $O(N)$ Lookups:** Barcode scans and stock checks iterated through arrays on every keystroke/scan.
3. **Scattered Stock Mutations:** Stock updates after receipt creation, receipt editing, receipt deletion, and refills were managed ad-hoc in component/service state.
4. **Dead Code:** Deprecated services existed without consumers.

### Solution: Global Singleton In-Memory Store
We introduced `ProductCatalogStore` as an Angular root singleton managing a unified in-memory product catalog with $O(1)$ hash map lookups, batch stock mutations, and 5-minute TTL caching.

---

## 2. Architectural Design

```
                                  BACKEND (Spring Boot)
                                            │
                                 GET /api/products/all-products
                                            │
                                            ▼
                       ┌─────────────────────────────────────────┐
                       │           ProductCatalogStore           │
                       │        (Global Root Singleton)          │
                       ├─────────────────────────────────────────┤
                       │  • products (Signal<CatalogProduct[]>)  │
                       │  • barcodeIndex (Map<string, Product>)  │
                       │  • idIndex (Map<number, Product>)       │
                       │  • Cache TTL: 5 Minutes                 │
                       │  • Batch Stock Mutations                │
                       │  • Atomic 3-Entity Refill Cascade       │
                       └────────────────────┬────────────────────┘
                                            │
               ┌────────────────────────────┼────────────────────────────┐
               │                            │                            │
               ▼                            ▼                            ▼
      ┌─────────────────┐          ┌─────────────────┐          ┌─────────────────┐
      │ CashierState-   │          │ ProductSearch-  │          │ ManageProduct-  │
      │ Service (POS)   │          │ PopupComponent  │          │ Component       │
      └─────────────────┘          └─────────────────┘          └─────────────────┘
```

### Scale & In-Memory Characteristics (~3,000 Products)
* **Raw JSON Payload:** ~250 KB (single HTTP GET download).
* **Transfer Size (Gzip):** ~50 KB (< 35 ms over local network).
* **V8 Heap Footprint:** ~400 KB (negligible memory impact).
* **Barcode Lookup ($O(1)$ Map):** `< 0.01 ms` (instant scanner response).
* **Client Search Filter:** `~1 – 3 ms`.

---

## 3. Data Models & Type Contracts

Located in: `src/app/core/products/models/catalog-product.model.ts`

```typescript
import { Product, RefillOption } from '../../../features/pos/core/models/pos.models';

export interface CatalogProduct extends Product {
  status?: string;
}

export interface CatalogFilterCriteria {
  query?: string;
  stockStatus?: 'all' | 'in_stock' | 'out_of_stock';
  minPrice?: number | null;
  maxPrice?: number | null;
  activeOnly?: boolean;
}

export interface StockUpdateItem {
  barcode: string;
  remainingStock: number;
}

export interface StockRestoreItem {
  barcode: string;
  quantity: number;
}
```

### Contract Guarantees
* `CatalogProduct` extends the core POS `Product` interface, guaranteeing 100% type-compatibility with all existing POS models, `CartItem`, and `SharedProduct` interfaces.
* Safe fallback defaults ensure that missing backend DTO fields (e.g., `costPrice`, `isActive`) are automatically handled during mapping (`costPrice: 0`, `isActive: true`).

---

## 4. `ProductCatalogStore` API Reference

Located in: `src/app/core/products/services/product-catalog.store.ts`

### Signals & Properties
| Member | Type | Description |
|---|---|---|
| `products` | `Signal<CatalogProduct[]>` | Readonly signal containing all cached products. |
| `isLoading` | `Signal<boolean>` | True while fetching catalog from backend. |
| `isLoaded` | `Signal<boolean>` | Computed boolean indicating whether cache has loaded. |
| `error` | `Signal<string \| null>` | Error message if loading failed. |
| `totalCount` | `Signal<number>` | Computed count of cached products. |
| `barcodeIndex` | `Signal<Map<string, CatalogProduct>>` | Computed $O(1)$ hash map indexed by lowercased barcode. |
| `idIndex` | `Signal<Map<number, CatalogProduct>>` | Computed $O(1)$ hash map indexed by product ID. |

### Methods

#### `loadCatalog(forceRefresh?: boolean): Observable<CatalogProduct[]>`
Fetches all products via `GET /api/products/all-products`. If called within the 5-minute TTL window and `forceRefresh` is false, returns the in-memory cache immediately via `of(this._products())`. Uses `shareReplay(1)` to deduplicate concurrent requests.

#### `findByBarcode(barcode: string): CatalogProduct | undefined`
Zero-latency $O(1)$ lookup using `barcodeIndex`. Ideal for hardware barcode scanners and receipt line mapping.

#### `findById(id: number): CatalogProduct | undefined`
Zero-latency $O(1)$ lookup using `idIndex`.

#### `search(criteria: CatalogFilterCriteria): CatalogProduct[]`
In-memory multi-criteria search supporting name/barcode query, active-only filtering, stock status (`in_stock` / `out_of_stock`), and price range filters.

#### `updateStockBatchByBarcode(items: StockUpdateItem[]): void`
Batch stock level update after receipt creation or modification. Updates `stockQuantity` and `stock` fields atomically in memory.

#### `restoreStockBatchByBarcode(items: StockRestoreItem[]): void`
Additive batch stock restoration after receipt deletion. Restores returned item quantities to current stock.

#### `applyRefillUpdate(updatedChild: CatalogProduct, parentProductId: number, parentUnitsUsed: number): void`
Atomic 3-entity cascade for unit refills:
1. Updates child product stock, buying/selling prices, and name (or adds child if newly created).
2. Decrements parent product stock by `parentUnitsUsed`.
3. Synchronizes `parentStock` across all sibling `refillOptions` that reference `parentProductId`.

#### `patchProduct(updated: Partial<CatalogProduct> & { id: number }): void`
Updates a single product record when modified from the product management form.

#### `invalidate(): void`
Clears the last-loaded timestamp to force a fresh backend fetch on the next call.

---

## 5. Integration with `CashierStateService`

`CashierStateService` now acts as a high-level POS coordinator and delegates catalog caching and stock mutations to `ProductCatalogStore`:

```typescript
@Injectable({ providedIn: 'root' })
export class CashierStateService {
  private api = inject(CashierApiService);
  private seed = inject(CashierSeedService);
  private catalogStore = inject(ProductCatalogStore);

  // Expose store products signal directly for template backward-compatibility
  public products = this.catalogStore.products;

  public loadAllProducts(forceRefresh: boolean = false): Observable<Product[]> {
    this.setLoading(true);
    return this.catalogStore.loadCatalog(forceRefresh).pipe(
      tap(() => {
        this.refreshNavigationCacheStock();
        this.refreshCurrentReceiptDisplay();
        this.clearError();
      }),
      finalize(() => this.setLoading(false))
    );
  }

  // O(1) Barcode Lookup
  private getLiveStockForProductCode(productCode: string, fallback = 0): number {
    const product = this.catalogStore.findByBarcode(productCode);
    return product ? product.stockQuantity : fallback;
  }

  // Delegated Batch Stock Updates
  private updateProductsCacheFromReceipt(receipt: ReceiptResponse): void {
    if (!receipt.items?.length) return;
    const items = receipt.items
      .filter(i => i.productCode && i.remainingStock !== undefined)
      .map(i => ({ barcode: i.productCode, remainingStock: i.remainingStock }));
    this.catalogStore.updateStockBatchByBarcode(items);
  }

  // Delegated Batch Stock Restorations
  private restoreStockForDeletedReceipt(receipt: ReceiptResponse): void {
    if (!receipt.items?.length) return;
    const items = receipt.items
      .filter(i => i.productCode && i.quantity)
      .map(i => ({ barcode: i.productCode, quantity: i.quantity }));
    this.catalogStore.restoreStockBatchByBarcode(items);
  }

  // Delegated Refill Execution
  public executeRefill(payload: RefillExecuteRequest): Promise<Product> {
    return firstValueFrom(this.api.executeRefill(payload).pipe(
      map(response => {
        const updatedProduct = this.normalizeRefillExecuteResponse(response, payload);
        this.catalogStore.applyRefillUpdate(updatedProduct, payload.parentProductId, payload.parentUnitsUsed);
        this.syncUpdatedProductAcrossState(updatedProduct);
        return updatedProduct;
      })
    ));
  }
}
```

---

## 6. Performance & Complexity Comparison

| Operation | Before | After (ProductCatalogStore) |
|---|---|---|
| **Barcode Scanner Lookup** | $O(N)$ linear array search (~0.5 – 2 ms) | **$O(1)$ Hash Map lookup (< 0.01 ms)** |
| **Receipt Item Population** | $O(M \times N)$ linear searches for $M$ items | **$O(M)$ Hash Map lookups** |
| **Catalog Re-fetch on Navigation** | Fetched on every navigation | **Served from 5-min TTL cache** |
| **Stock Deduction Mutation** | In-place manual array copy | **Atomic batch map update** |
| **Refill Cascade Mutation** | Manual nested loops | **Atomic 3-entity cascade** |
| **Dead Code Footprint** | Unused `product.service.ts` | **Removed completely** |

---

## 7. Migration Roadmap

```
Phase 1: Core Store & POS (COMPLETE)
  ├── Create CatalogProduct model & interfaces
  ├── Implement ProductCatalogStore with $O(1)$ indexing & batch mutations
  ├── Refactor CashierStateService to delegate to store
  └── Delete legacy product.service.ts

Phase 2: Product Management Table (Next)
  ├── Load hierarchy skeleton (GET /api/products/tree?includeProducts=false)
  └── Implement server-side pagination (GET /api/products?page=0&size=100)

Phase 3: Product Tree View Decoupling
  └── Isolate tree drag-and-drop hierarchy in ProductTreeStateService

Phase 4: Manage Form & Composition Search
  └── Connect BOM/composition search to ProductCatalogStore
```
