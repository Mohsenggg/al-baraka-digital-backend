# Purchasing Feature Specification

**Feature:** Purchasing / Purchase Receipts
**Application:** Al-Baraka POS
**Status:** Feature Definition / Implementation Specification
**Version:** 1.0

---

# 1. Feature Overview

The Purchasing feature is responsible for recording products received from suppliers, calculating their purchase costs, updating inventory, managing supplier invoice information, calculating selling prices and average inventory cost, and recording the payment status of the purchase.

The feature should follow the interaction model of the existing **Cashier** feature.

The main Purchasing page is a workspace rather than a traditional list page:

* The main area is used to create and view a purchase.
* The right sidebar contains previously recorded purchases.
* By default, the page opens in **New Purchase** mode.
* Selecting a purchase from the sidebar loads all of its details into the main area without navigating to a separate page.

The feature should prioritize:

* Fast data entry
* Barcode/product search
* Clear purchase calculations
* Accurate inventory updates
* Historical purchase preservation
* Clear distinction between purchase cost and selling price
* Simple supplier payment tracking

---

# 2. Business Concept

The Purchasing feature represents products entering the store.

The basic relationship is:

```text
Supplier
    ↓
Purchase
    ↓
Purchased Items
    ↓
Inventory increases
    ↓
Purchase payment / supplier balance
```

This is the inverse operational flow of Cashier:

```text
Cashier:
Customer → Sale → Money received → Stock decreases

Purchasing:
Supplier → Purchase → Money paid/owed → Stock increases
```

The first version should treat:

```text
Purchase Invoice = Stock Receipt
```

When a purchase is completed, the purchased quantities immediately enter inventory.

A separate Purchase Order / Goods Receipt workflow is intentionally outside the scope of the first version.

---

# 3. Main User Experience

The Purchasing page consists of two primary areas.

```text
┌──────────────────────────────────────────────────────────────┐
│                    PURCHASE WORKSPACE                        │
│                                                              │
│  Main Purchase Area                       Purchase Sidebar   │
│                                                              │
│  New Purchase / Selected Purchase         #1045              │
│                                          #1044               │
│  Purchase Information                    #1043               │
│  Purchased Items                         #1042               │
│  Payment & Summary                       ...                 │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

## 3.1 Default State

When the user opens Purchasing:

```text
Mode = New Purchase
```

The main area should be ready to create a new purchase.

The sidebar should display existing purchases.

## 3.2 Selecting an Existing Purchase

When the user clicks a purchase in the sidebar:

```text
Selected Purchase
        ↓
Load purchase details
        ↓
Display:
    Purchase Information
    Purchased Items
    Payment
    Summary
```

The user should remain inside the same Purchasing workspace.

There should be no requirement to navigate to another page.

## 3.3 Creating Another Purchase

The user can return to:

```text
+ New Purchase
```

and the main area should reset to an empty purchase form.

---

# 4. Purchase Lifecycle

The feature should distinguish between the state of the purchase itself and the payment state.

## 4.1 Purchase Status

```text
DRAFT
COMPLETED
CANCELLED
```

### DRAFT

The user is currently creating/editing the purchase.

A draft:

* Has not affected inventory.
* Has not finalized payment.
* Can be modified before completion.

### COMPLETED

The purchase has been finalized.

A completed purchase:

* Is stored permanently as a transaction.
* Updates inventory.
* Updates the product's average cost where applicable.
* Applies the configured selling-price behavior.
* Records the payment state.

### CANCELLED

A previously completed purchase may eventually be cancelled/reversed.

Cancellation behavior should be handled as a separate inventory reversal operation.

The first implementation does not need to support complex cancellation UI unless required.

---

# 5. Payment Status

Payment status is independent from Purchase Status.

Supported payment states:

```text
PAID
PARTIALLY_PAID
SUSPENDED
```

## 5.1 Paid

The complete purchase amount has been paid.

```text
Purchase Total = 5,000
Paid Amount    = 5,000
Remaining      = 0
```

## 5.2 Partially Paid

Only part of the purchase has been paid.

Example:

```text
Purchase Total = 5,000
Paid Amount    = 3,000
Remaining      = 2,000
```

The user enters:

```text
Paid Amount = 3,000
```

The system automatically calculates:

```text
Remaining = Purchase Total - Paid Amount
```

The remaining amount must not be manually editable.

## 5.3 Suspended

The purchase has been recorded but no amount has been paid.

```text
Purchase Total = 5,000
Paid Amount    = 0
Remaining      = 5,000
```

Important:

> Suspended payment does NOT mean the purchase is suspended.

The purchase itself can be completed and inventory can be updated even when payment is suspended.

Therefore:

```text
Purchase Status:
    COMPLETED

Payment Status:
    SUSPENDED
```

is valid.

---

# 6. Purchase Information

Each purchase contains the following information:

| Field                   | Description                                       |
| ----------------------- | ------------------------------------------------- |
| Supplier ID             | Internal supplier identifier                      |
| Supplier Name           | Supplier display name                             |
| Supplier Invoice Number | Invoice/reference number provided by the supplier |
| System Invoice Number   | Internal POS-generated purchase number            |
| Purchase Date           | Date of the purchase                              |

## 6.1 Supplier

The user selects a supplier from the supplier list.

The purchase should store the supplier ID as the authoritative relationship.

The supplier name may be displayed through the relationship.

The system must preserve the historical relationship even if the supplier's name changes later.

## 6.2 Supplier Invoice Number

This is the number printed/provided by the supplier.

Example:

```text
Supplier Invoice Number:
INV-2026-984
```

This is different from the internal POS invoice number.

## 6.3 System Invoice Number

This is automatically generated by the POS.

Example:

```text
P-0001045
```

The user should not normally enter this number manually.

It must be unique.

## 6.4 Purchase Date

The date associated with the purchase.

The user should be able to select/change it according to the application's existing date-handling rules.

---

# 7. Purchased Items

A purchase contains one or more purchase items.

The primary table contains:

```text
Product
Quantity
Buying Price
Discount
Profit %
Selling Price
Old Price
Average Price
```

Internally, `Quantity` should be used rather than the ambiguous term `Purchased Amount`.

---

# 8. Product Selection

Products should be added using the same general philosophy as Cashier.

The user should be able to:

* Search for a product.
* Enter/scan a barcode.
* Select a product from search results.
* Add multiple products.
* Change quantity.
* Edit purchase-specific pricing.

Preferred workflow:

```text
Scan/Search Product
        ↓
Product identified
        ↓
Add purchase item
        ↓
Enter/adjust quantity
        ↓
Enter buying price
        ↓
Apply discount
        ↓
Calculate cost / selling price / average cost
```

Product lookup should reuse existing Product Catalog functionality where possible.

---

# 9. Buying Price

`Buying Price` represents the supplier's unit price before the purchase-item discount.

Example:

```text
Buying Price = 100 EGP
Discount     = 10%
```

The effective unit cost becomes:

```text
90 EGP
```

The buying price belongs to the purchase item and must be preserved historically.

A future purchase may have a different buying price for the same product.

Example:

```text
Purchase #100
Product A
Buying Price = 100

Purchase #101
Product A
Buying Price = 110
```

The historical purchase must retain its original price.

---

# 10. Discount

The discount is applied to the purchase item's buying price.

The exact discount representation must be consistent throughout the implementation.

Recommended first-version model:

```text
discountType
discountValue
```

Supported types can eventually be:

```text
PERCENTAGE
FIXED_AMOUNT
```


## Effective Buying Price

For a percentage discount:

```text
Effective Buying Price =
Buying Price × (1 - Discount Percentage / 100)
```

Example:

```text
Buying Price = 100
Discount = 10%

Effective Buying Price = 90
```

The effective buying price is the actual inventory cost per unit for this purchase.

---

# 11. Purchase Cost

The cost of a purchase item is:

```text
Item Cost =
Effective Buying Price × Quantity
```

Example:

```text
Buying Price = 100
Discount = 10%
Quantity = 20

Effective Buying Price = 90

Item Cost = 90 × 20
          = 1,800
```

The total purchase cost is:

```text
Total Purchase Cost =
SUM(all purchase item costs)
```

---

# 12. Profit Percentage

Each purchase item contains a configurable:

```text
Profit Percentage
and calculate the amount 

when the user enter percentage update the amount accordingly 
when the user enter amount update the percentage accordingly 
```

The intended meaning is:

> Percentage markup applied to the effective purchase cost to calculate the selling price.

Recommended formula:

```text
Selling Price =
Effective Buying Price × (1 + Profit Percentage / 100)
```

Example:

```text
Effective Buying Price = 100
Profit Percentage = 25%

Selling Price = 100 × 1.25
              = 125
```

Important:

```text
25% markup
```

is not the same as:

```text
25% profit margin
```

The system should consistently use the defined markup calculation.

---

# 13. Selling Price

The purchase screen can calculate/display the selling price associated with the purchase item.

Example:

```text
Buying Cost = 100
Profit = 20%

Selling Price = 120
```

The system should clearly distinguish:

```text
Purchase Cost
Selling Price
```

The selling price should not be confused with the supplier's buying price.

---

# 14. Old Price

`Old Price` represents the product's selling price before the current purchase is applied.

Example:

```text
Current Product Selling Price = 150

New Purchase:
Effective Cost = 120
Profit = 25%

New Selling Price = 150
Old Price = 150
```

If the new purchase results in a different selling price:

```text
Old Price = 150
New Selling Price = 160
```

The old price provides the user with immediate visibility into whether the product's selling price is changing.

---

# 15. Average Price

Average Price represents the product's weighted average inventory cost after receiving the new purchase.

It must NOT be calculated as:

```text
(Old Price + New Price) / 2
```

because inventory quantities may be different.

The correct calculation is a weighted average.

## Formula

```text
New Average Cost =
(
    Existing Stock Quantity × Existing Average Cost
    +
    New Purchase Quantity × New Effective Buying Cost
)
/
(
    Existing Stock Quantity + New Purchase Quantity
)
```

Equivalent terminology:

```text
Existing Stock Value = Existing Quantity × Existing Average Cost

New Purchase Value = New Quantity × New Effective Buying Cost

New Average Cost =
(Existing Stock Value + New Purchase Value)
/
(Existing Quantity + New Quantity)
```

---

# 16. Average Price Example

Existing inventory:

```text
Quantity = 100
Average Cost = 80 EGP
```

Existing stock value:

```text
100 × 80 = 8,000
```

New purchase:

```text
Quantity = 50
Effective Buying Cost = 100 EGP
```

New purchase value:

```text
50 × 100 = 5,000
```

Combined:

```text
Total Quantity = 150
Total Value = 13,000
```

New average cost:

```text
13,000 / 150 = 86.67 EGP
```

Therefore:

```text
New Average Cost = 86.67 EGP
```

---

# 17. Average Price When There Is No Existing Stock

If:

```text
Existing Quantity = 0
```

then:

```text
New Average Cost =
New Effective Buying Cost
```

Example:

```text
Existing Quantity = 0

New Purchase:
Quantity = 50
Cost = 100

New Average Cost = 100
```

---

# 18. Multiple Purchase Items for the Same Product

The system should avoid ambiguous average-cost calculations when the same product appears more than once in a single purchase.

Preferred behavior:

> A product should appear only once in a purchase.

If the user attempts to add the same product again:

```text
Existing item found
        ↓
Increase quantity / edit existing item
```

rather than creating a duplicate line.

This keeps:

* Purchase calculations simpler
* Product quantity clearer
* Average-cost calculation deterministic
* UI cleaner

---

# 19. Purchase Summary

The summary should contain:

```text
Purchase Cost
Expected Sales Value
Number of Products
Total Quantity
```

## 19.1 Purchase Cost

The total cost of all purchased items after discounts.

```text
Purchase Cost =
SUM(Item Effective Cost × Quantity)
```

Example:

```text
Product A = 2,000
Product B = 3,000

Purchase Cost = 5,000
```

---

# 20. Expected Sales Value

The field originally described as:

```text
Revenue from the Purchase
```

should preferably be called:

```text
Expected Sales Value
```

because the products have not necessarily been sold yet.

Formula:

```text
Expected Sales Value =
SUM(Selling Price × Quantity)
```

Example:

```text
Product A:
10 × 120 = 1,200

Product B:
20 × 150 = 3,000

Expected Sales Value = 4,200
```

This is not actual accounting revenue.

It represents the expected sales value if the purchased quantities are sold at their configured selling prices.

---

# 21. Number of Products

This represents the number of distinct product lines.

Example:

```text
Ariel × 20
Persil × 10
Clorox × 15
```

Number of Products:

```text
3
```

---

# 22. Total Quantity

This represents the total number of physical units purchased.

Example:

```text
Ariel = 20
Persil = 10
Clorox = 15
```

Total Quantity:

```text
20 + 10 + 15 = 45
```

Therefore:

```text
Number of Products = 3
Total Quantity = 45
```

These values must not be confused.

---

# 23. Payment & Summary Section

The bottom section should combine payment information and purchase summary.

Conceptually:

```text
Payment

Payment Status:
[ Fully Paid ▼ ]

Paid Amount:
[ 5,000 ]

Remaining:
0


Summary

Purchase Cost:          5,000
Expected Sales Value:   6,500
Products:                    8
Total Quantity:             43
```

For partially paid purchases:

```text
Purchase Cost:          5,000

Payment:
Partially Paid

Paid Amount:            3,000
Remaining:              2,000
```

The remaining value is calculated automatically.

---

# 24. Payment Validation

The following rules should be enforced:

```text
Paid Amount >= 0
```

and:

```text
Paid Amount <= Purchase Total
```

For `PAID`:

```text
Paid Amount = Purchase Total
Remaining = 0
```

For `PARTIALLY_PAID`:

```text
0 < Paid Amount < Purchase Total
Remaining > 0
```

For `SUSPENDED`:

```text
Paid Amount = 0
Remaining = Purchase Total
```

The backend must validate these rules regardless of frontend validation.

---

# 25. Inventory Behavior

Completing a purchase must increase inventory.

Example:

```text
Current stock:
Product A = 100

Purchase:
Quantity = 30

After completion:
Product A = 130
```

The inventory update must be part of the same business transaction as purchase creation.

Conceptually:

```text
Create Purchase
        +
Create Purchase Items
        +
Update Inventory
        +
Update Product Cost
        +
Update Selling Price if applicable
        +
Record Payment
```

These operations should succeed together or fail together.

---

# 26. Inventory Movement

The purchase should create an inventory movement/history entry.

Example:

```text
Inventory Movement

Product: Product A
Type: PURCHASE
Quantity: +30
Reference: P-0001045
Date: 2026-10-02
```

This will allow future inventory history/reporting features to answer:

> Why did this product's stock increase?

The answer should be:

```text
PURCHASE → P-0001045
```

---

# 27. Product Average Cost Update

When the purchase is completed:

```text
Existing Product Average Cost
            +
New Purchase
            ↓
Calculate Weighted Average
            ↓
Update Product Average Cost
```

The calculation must use the effective buying cost after discount.

Example:

```text
Buying Price = 100
Discount = 10%

Effective Cost = 90
```

The weighted-average calculation must use:

```text
90
```

not:

```text
100
```

---

# 28. Selling Price Update

The purchase may result in a new selling price.

The system should calculate:

```text
Effective Cost
        +
Profit Percentage
        ↓
New Selling Price
```

However, the exact policy for automatically applying this price should be isolated from the purchase calculation.

The purchase item should know:

```text
oldPrice
calculatedSellingPrice
```

The application can then decide whether the calculated price should become the product's current selling price.

The first implementation should avoid silently changing selling prices without clearly reflecting the change to the user.

---

# 29. Historical Data Integrity

Completed purchases are historical financial/inventory records.

The system must preserve the values that existed when the purchase was completed.

For example:

```text
Purchase #100

Buying Price = 100
Discount = 10%
Effective Cost = 90
Selling Price = 120
Old Price = 110
Quantity = 50
```

If the product later changes:

```text
Current Buying Cost = 95
Current Selling Price = 130
```

Purchase #100 must still display:

```text
Buying Price = 100
Effective Cost = 90
Selling Price = 120
Old Price = 110
Quantity = 50
```

Historical purchases must not be dynamically recalculated from current Product values.

---

# 30. Purchase Sidebar

The sidebar provides quick access to previous purchases.

Each sidebar item should provide enough information to identify the purchase.

Recommended information:

```text
System Invoice Number
Supplier Name
Date
Total
Payment Status
```

Example:

```text
P-0001045
ABC Trading
02/10/2026
5,400 EGP
Partially Paid
```

The sidebar should support:

* Selecting a purchase
* Visual indication of selected purchase
* Scrolling through previous purchases
* Loading the selected purchase into the main workspace

Future enhancements can include:

* Search
* Date filtering
* Supplier filtering
* Payment-status filtering

These should not complicate the first implementation unnecessarily.

---

# 31. New Purchase Mode

The main workspace should have a clear action to create a new purchase.

Conceptually:

```text
+ New Purchase
```

When selected:

```text
Clear current draft
        ↓
Create empty purchase workspace
```

If the user has unsaved changes, the system should eventually warn before discarding them.

---

# 32. Existing Purchase Mode

When a purchase is selected:

```text
Sidebar Purchase
        ↓
Load purchase
        ↓
Display complete details
```

A completed purchase should be treated as historical data.

The first version should prefer:

```text
View Mode
```

for completed purchases rather than allowing arbitrary modification.

If editing completed purchases is eventually required, it should be implemented as a controlled business operation because changing a completed purchase can affect:

* Inventory
* Average cost
* Supplier balance
* Payment
* Selling prices
* Financial history

---

# 33. Suggested Domain Model

Conceptually:

```text
Purchase
├── id
├── systemInvoiceNumber
├── supplierId
├── supplierInvoiceNumber
├── purchaseDate
├── status
├── paymentStatus
├── paidAmount
├── remainingAmount
├── totalCost
├── expectedSalesValue
├── productCount
├── totalQuantity
├── createdAt
└── updatedAt

PurchaseItem
├── id
├── purchaseId
├── productId
├── quantity
├── buyingPrice
├── discountType
├── discountValue
├── effectiveBuyingPrice
├── profitPercentage
├── sellingPrice
├── oldPrice
├── averagePrice
└── totalCost
```

Some calculated fields may be derived instead of persisted.

The final persistence decision should consider:

* Historical accuracy
* Reporting requirements
* Query performance
* Database normalization
* Existing POS architecture

---

# 34. Backend Responsibility

The backend must be authoritative for business calculations.

The frontend can calculate values for immediate display, but the backend must validate/recalculate them when saving.

The backend must control:

* Purchase total
* Effective buying cost
* Payment validation
* Remaining amount
* Inventory quantity
* Average cost
* Historical purchase values
* Purchase status
* Payment status

The frontend must never be trusted as the source of truth for inventory or financial calculations.

---

# 35. Transaction Boundary

Completing a purchase should be one database transaction.

Conceptually:

```text
@Transactional
completePurchase()
```

The operation should:

```text
1. Validate purchase
2. Validate products
3. Validate quantities
4. Calculate effective costs
5. Calculate purchase totals
6. Validate payment
7. Create purchase
8. Create purchase items
9. Update inventory
10. Update product average costs
11. Apply selling-price changes if configured
12. Create inventory movements
13. Record payment state
14. Commit
```

If any critical operation fails:

```text
Everything rolls back
```

This prevents situations such as:

```text
Purchase saved
BUT
Stock not updated
```

or:

```text
Stock updated
BUT
Purchase not saved
```

---

# 36. Frontend Architecture

The Purchasing feature should follow the existing POS architectural approach.

Suggested structure:

```text
purchasing/
│
├── components/
│   ├── purchasing-main-page/
│   ├── purchase-sidebar/
│   ├── purchase-header/
│   ├── purchase-items/
│   ├── purchase-item-row/
│   ├── purchase-payment/
│   └── purchase-summary/
│
├── models/
│   ├── purchase.model.ts
│   ├── purchase-item.model.ts
│   └── purchase-payment.model.ts
│
├── services/
│   ├── purchasing-api.service.ts
│   └── purchasing-state.service.ts
│
└── ...
```

The exact structure should follow the project's existing conventions rather than introducing an unrelated architecture.

---

# 37. State Management

The Purchasing workspace should maintain state for:

```text
Selected Purchase
Current Draft Purchase
Purchase Items
Supplier
Payment State
Calculated Totals
Loading State
Saving State
```

Conceptually:

```text
PurchasingState
│
├── selectedPurchase
├── currentPurchase
├── purchases
├── suppliers
├── loading
├── saving
└── errors
```

The state should allow the UI to update calculations immediately without repeatedly requesting the server for every small change.

---

# 38. Product Catalog Integration

The purchase feature should reuse the existing Product Catalog rather than creating a second product-selection implementation.

Product selection should use existing:

* Product ID
* Product name
* Barcode
* Category
* Company/Brand
* Product Group
* Selling price
* Current average cost
* Stock quantity

The purchase feature should retrieve only the information necessary for purchase entry.

---

# 39. Calculation Responsibilities

The following table defines where calculations belong.

| Calculation            | Frontend |           Backend |
| ---------------------- | -------: | ----------------: |
| Item discount preview  |      Yes |               Yes |
| Effective buying cost  |      Yes |               Yes |
| Item total             |      Yes |               Yes |
| Selling price preview  |      Yes |               Yes |
| Expected sales value   |      Yes |               Yes |
| Purchase total         |      Yes |               Yes |
| Paid amount validation |      Yes |               Yes |
| Remaining amount       |      Yes |               Yes |
| Average inventory cost |  Preview | **Authoritative** |
| Stock update           |       No |           **Yes** |
| Inventory movement     |       No |           **Yes** |
| Supplier balance       |       No |           **Yes** |

The backend is always authoritative.

---

# 40. Important Business Rules

## Rule 1 — Purchase quantities must be positive

```text
quantity > 0
```

## Rule 2 — Buying price cannot be negative

```text
buyingPrice >= 0
```

## Rule 3 — Discount cannot produce an invalid cost

```text
effectiveBuyingPrice >= 0
```

## Rule 4 — Paid amount cannot exceed purchase total

```text
paidAmount <= totalCost
```

## Rule 5 — Remaining amount is calculated

```text
remainingAmount =
totalCost - paidAmount
```

## Rule 6 — Completed purchases update inventory

```text
COMPLETED purchase
    → stock increases
```

## Rule 7 — Draft purchases do not update inventory

```text
DRAFT
    → no inventory effect
```

## Rule 8 — Historical purchase prices remain unchanged

Product price changes must not rewrite historical purchase records.

## Rule 9 — Average cost uses weighted quantity

Never calculate the average cost using a simple average of prices.

## Rule 10 — Suspended payment does not prevent stock receipt

```text
Purchase = COMPLETED
Payment = SUSPENDED

→ stock increases
```

---

# 41. Example Complete Purchase

Supplier:

```text
ABC Trading
```

Supplier Invoice:

```text
INV-88421
```

System Invoice:

```text
P-0001045
```

Date:

```text
02/10/2026
```

Items:

```text
Product A
Quantity: 20
Buying Price: 100
Discount: 10%
Effective Cost: 90
Profit: 25%
Selling Price: 112.50

Product B
Quantity: 10
Buying Price: 200
Discount: 0%
Effective Cost: 200
Profit: 20%
Selling Price: 240
```

Purchase cost:

```text
Product A = 20 × 90  = 1,800
Product B = 10 × 200 = 2,000

Total Cost = 3,800
```

Expected sales value:

```text
Product A = 20 × 112.50 = 2,250
Product B = 10 × 240    = 2,400

Expected Sales Value = 4,650
```

Product count:

```text
2
```

Total quantity:

```text
30
```

Payment:

```text
Partially Paid
Paid = 2,000
Remaining = 1,800
```

After completion:

```text
Purchase created
+
Inventory increased
+
Average costs recalculated
+
Selling prices applied according to pricing rules
+
Payment state recorded
```

---

# 42. Example Average Cost Calculation

Product A currently has:

```text
Existing Quantity = 100
Existing Average Cost = 80
```

Existing value:

```text
100 × 80 = 8,000
```

New purchase:

```text
Quantity = 20
Buying Price = 100
Discount = 10%
```

Effective cost:

```text
100 × 0.90 = 90
```

New purchase value:

```text
20 × 90 = 1,800
```

New inventory:

```text
Quantity = 120
Value = 9,800
```

New average:

```text
9,800 / 120
= 81.6667
```

Therefore:

```text
New Average Cost ≈ 81.67
```

---

# 43. What Should Not Be Included in Version 1

To keep the feature manageable, the first version should NOT implement:

* Purchase orders
* Multi-stage receiving
* Partial goods receipt
* Damaged goods workflows
* Supplier returns
* Complex accounts payable
* Multiple payment methods
* Tax accounting
* Currency conversion
* Batch/lot management
* Expiration-date management
* Automated supplier reconciliation
* Advanced purchasing analytics

These can be added later without changing the core concept.

---

# 44. Future Extensions

The design should leave room for:

```text
Supplier Management
        ↓
Purchase Orders
        ↓
Goods Receiving
        ↓
Purchase Invoice
        ↓
Supplier Payment
        ↓
Supplier Account / Payables
```

Additional future capabilities may include:

### Supplier Returns

```text
Purchase
    ↓
Return selected items
    ↓
Decrease inventory
    ↓
Reduce supplier balance
```

### Purchase History

Filtering by:

* Supplier
* Date
* Product
* Payment status
* Purchase status

### Inventory History

```text
Product
    ↓
Stock Movements
    ├── PURCHASE
    ├── SALE
    ├── RETURN
    ├── ADJUSTMENT
    └── ...
```

### Supplier Account

```text
Supplier
    ↓
Purchases
    ↓
Payments
    ↓
Remaining Balance
```

---

# 45. UI Design Principles

The visual design should remain consistent with the existing Cashier application.

The Purchasing page should:

* Feel like the same application.
* Use the same spacing system.
* Use the same typography.
* Reuse existing product-selection patterns.
* Reuse existing buttons and input controls.
* Reuse existing table conventions.
* Maintain the existing RTL Arabic experience.
* Keep the main purchase-entry workflow visually dominant.
* Keep purchase history accessible through the right sidebar.

The Purchasing feature should feel like:

> **Cashier for incoming products**

while still exposing the additional purchasing-specific information.

---

# 46. Accessibility and Usability

The purchase-entry workflow should minimize unnecessary clicks.

Preferred flow:

```text
Select supplier
        ↓
Enter supplier invoice
        ↓
Scan/search product
        ↓
Enter quantity
        ↓
Enter buying price
        ↓
Apply discount
        ↓
Review calculated selling/average prices
        ↓
Add next product
        ↓
Select payment
        ↓
Save purchase
```

Keyboard-friendly data entry should be considered because purchase entry may involve many products.

Barcode scanning should behave like keyboard input where supported by the hardware.

---

# 47. Error Handling

The UI should provide clear validation messages for:

* Missing supplier when required
* Missing supplier invoice number when required
* Invalid quantity
* Invalid buying price
* Invalid discount
* Invalid profit percentage
* Payment amount greater than total
* Product unavailable
* Duplicate product line
* Failed inventory update
* Failed purchase creation

Backend errors must be surfaced without leaving the frontend in an inconsistent state.

---

# 48. Save Behavior

The primary action should be:

```text
Save Purchase
```

Before saving, the system should ensure:

```text
Supplier selected
Purchase date valid
At least one item exists
Every item has valid quantity
Every item has valid buying price
Payment information is valid
```

Then:

```text
Save
    ↓
Backend transaction
    ↓
Success
    ↓
Purchase becomes COMPLETED
    ↓
Refresh/update purchase sidebar
    ↓
Clear workspace or display saved purchase
```

The preferred post-save behavior should be decided during implementation, but keeping the newly created purchase visible immediately is useful for verification.

---

# 49. Data Consistency Requirements

The following values must remain consistent:

```text
Purchase Total
=
SUM(Purchase Item Totals)
```

```text
Expected Sales Value
=
SUM(Item Selling Price × Quantity)
```

```text
Total Quantity
=
SUM(Item Quantity)
```

```text
Product Count
=
Number of unique products
```

```text
Remaining Amount
=
Purchase Total - Paid Amount
```

```text
New Average Cost
=
Weighted inventory cost
```

These calculations should be tested at both frontend and backend levels where practical.

---

# 50. Implementation Priorities

Implementation should proceed in this order:

## Phase 1 — Domain and Backend Foundation

Implement:

* Purchase entity/model
* Purchase item entity/model
* Purchase status
* Payment status
* Supplier relationship
* System invoice number
* Supplier invoice number
* Purchase date
* Purchase calculations

Do not start with UI complexity.

---

## Phase 2 — Inventory Integration

Implement:

* Stock increase
* Effective buying cost
* Weighted average cost
* Inventory movement
* Transactional save

This phase is critical because purchasing directly changes inventory.

---

## Phase 3 — Purchase API

Implement endpoints for:

```text
Create Purchase
Get Purchase
Get Purchases
Get Purchase Details
```

Future endpoints can include:

```text
Update Draft
Cancel Purchase
Search Purchases
```

---

## Phase 4 — Frontend State

Implement:

```text
PurchasingStateService
PurchasingApiService
```

Manage:

* Purchase list
* Selected purchase
* Current draft
* Purchase items
* Calculated totals
* Payment state
* Loading/saving state

---

## Phase 5 — Purchasing Workspace

Implement the Cashier-like layout:

```text
Main Purchase Area
+
Right Purchase Sidebar
```

Default:

```text
New Purchase
```

Selecting sidebar purchase:

```text
View Selected Purchase
```

---

## Phase 6 — Product Entry

Implement:

* Product search
* Barcode entry
* Add product
* Quantity
* Buying price
* Discount
* Profit percentage
* Selling price
* Old price
* Average price

---

## Phase 7 — Payment & Summary

Implement:

* Fully paid
* Partially paid
* Suspended
* Paid amount
* Remaining amount
* Purchase cost
* Expected sales value
* Product count
* Total quantity

---

## Phase 8 — Integration Verification

Verify complete scenarios:

### Scenario A — New Product

```text
No existing stock
+
Purchase
=
Stock created
Average cost = purchase cost
```

### Scenario B — Existing Product

```text
Existing stock
+
New purchase
=
Stock increased
Average cost recalculated
```

### Scenario C — Different Buying Price

```text
Existing cost = 100
New cost = 120
=
Weighted average recalculated
```

### Scenario D — Discount

```text
Buying Price = 100
Discount = 10%
=
Effective Cost = 90
```

### Scenario E — Full Payment

```text
Total = 5,000
Paid = 5,000
Remaining = 0
Status = PAID
```

### Scenario F — Partial Payment

```text
Total = 5,000
Paid = 3,000
Remaining = 2,000
Status = PARTIALLY_PAID
```

### Scenario G — Suspended

```text
Total = 5,000
Paid = 0
Remaining = 5,000
Status = SUSPENDED
Stock = Updated
```

---

# 51. Final Feature Definition

The Purchasing feature is a **Cashier-like incoming-stock transaction workspace**.

Its primary purpose is to allow the user to:

1. Select a supplier.
2. Record the supplier invoice number.
3. Generate an internal system purchase number.
4. Record the purchase date.
5. Add purchased products.
6. Enter quantity and supplier buying price.
7. Apply purchase discounts.
8. Calculate the effective purchase cost.
9. Calculate/display profit percentage and selling price.
10. Show the product's previous selling price.
11. Calculate the weighted average inventory cost.
12. Calculate total purchase cost.
13. Calculate expected sales value.
14. Show number of distinct products.
15. Show total quantity.
16. Record full, partial, or suspended payment.
17. Calculate remaining supplier balance.
18. Complete the purchase.
19. Increase inventory.
20. Update weighted average product cost.
21. Record an inventory movement.
22. Preserve the purchase as historical data.
23. Display previous purchases in the right sidebar.
24. Allow the user to select and review previous purchases within the same workspace.

The fundamental workflow is:

```text
                    PURCHASE
                       │
        ┌──────────────┼──────────────┐
        │              │              │
     Supplier       Products       Payment
        │              │              │
        │        ┌─────┴─────┐        │
        │        │           │        │
        │     Quantity     Cost       │
        │                    │        │
        │                 Discount    │
        │                    │        │
        │              Effective Cost │
        │                    │        │
        │             Average Cost    │
        │                    │        │
        │              Selling Price  │
        │                    │        │
        └──────────────┬─────┴────────┘
                       │
                  COMPLETE PURCHASE
                       │
             ┌─────────┼─────────┐
             │         │         │
          Purchase   Inventory  Payment
           Record     Update     Record
             │         │         │
             └─────────┼─────────┘
                       │
                 PURCHASE HISTORY
```

The core principle is:

> **A completed purchase is one atomic business transaction that records what was bought, at what cost, from whom, how much was paid, and how the purchase changed inventory.**

---

# 52. Important Terminology

Use the following terminology consistently throughout the implementation:

| Concept                   | Recommended Name             |
| ------------------------- | ---------------------------- |
| Purchase document         | Purchase                     |
| Supplier invoice number   | Supplier Invoice Number      |
| Internal number           | System Invoice Number        |
| Supplier                  | Supplier                     |
| Product amount            | Quantity                     |
| Supplier price            | Buying Price                 |
| Price after discount      | Effective Buying Price       |
| Product old selling price | Old Price                    |
| New selling price         | Selling Price                |
| Inventory weighted cost   | Average Cost / Average Price |
| Total purchase value      | Purchase Cost                |
| Future sales value        | Expected Sales Value         |
| Payment amount            | Paid Amount                  |
| Outstanding amount        | Remaining Amount             |
| Payment with zero payment | Suspended                    |
| Distinct product count    | Number of Products           |
| Physical units            | Total Quantity               |

Avoid using **Revenue** for the calculated future sales value because revenue technically represents realized sales rather than the expected value of unsold inventory.

---

# 53. Implementation Constraint

The feature should be implemented incrementally.

Do not attempt to build the entire Purchasing module in one step.

Each phase should:

1. Make one coherent architectural change.
2. Keep existing Cashier functionality working.
3. Verify the change manually.
4. Update the Purchasing feature progress documentation.
5. Avoid introducing unrelated refactoring.

The final implementation should integrate with the existing POS architecture instead of creating a separate purchasing architecture.
