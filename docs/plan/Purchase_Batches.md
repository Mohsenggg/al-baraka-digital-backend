


Inventory Cost Batches, FIFO Costing & Purchase Returns
1. Why This Change Is Required

The current inventory mechanism treats product stock as a single number:

Product
├── stock = 80
├── buyingPrice = 120
└── sellingPrice = 150

This model is insufficient when the same product is purchased multiple times at different costs.

For example:

Purchase #1
50 units × 100

Purchase #2
30 units × 120

The product now has:

Total Stock = 80

but the system must also know:

50 units cost 100
30 units cost 120

A single stock value cannot represent this information.

This becomes critical for:

FIFO inventory costing
accurate profit calculation
purchase history
purchase returns
stock returns
inventory auditing
determining the actual cost of sold products
handling products purchased at different prices
preserving historical inventory information

Therefore, the Purchase feature must introduce inventory cost batches and move inventory costing from a product-level stock-only model to a batch-based inventory model.

2. Core Concept

The system must separate two concepts that are currently mixed together:

Product pricing

Answers:

"How much do we currently sell this product for?"

ProductBarcode
├── currentSellingPrice
└── expectedMarkupPercentage
Inventory costing

Answers:

"How much did the units currently in stock actually cost us?"

InventoryBatch
├── receivedQuantity
├── remainingQuantity
├── unitCost
└── receivedAt

These concepts must remain independent.

A product can therefore have:

Selling Price = 150

while its inventory contains:

40 units × 100
25 units × 120

There is no requirement for all units of a product to have the same buying cost.

3. Inventory Batch

Every purchase receipt that adds stock must create an inventory batch for each purchased product/barcode.

Example:

Purchase #1001
Product A
Quantity = 50
Buying Price = 100

creates:

InventoryBatch #1
Product = A
Received Quantity = 50
Remaining Quantity = 50
Unit Cost = 100
Received At = purchase date
Source Purchase Item = PurchaseItem #1001

Later:

Purchase #1050
Product A
Quantity = 30
Buying Price = 120

creates:

InventoryBatch #2
Product = A
Received Quantity = 30
Remaining Quantity = 30
Unit Cost = 120
Received At = purchase date
Source Purchase Item = PurchaseItem #1050

The inventory becomes:

Product A

Batch #1
50 units @ 100

Batch #2
30 units @ 120

Total available stock is still:

80 units

but the system also knows the cost of every group of units.

4. Product Stock After This Change

The existing product-level stock field should no longer be treated as the authoritative source for inventory quantity.

Instead:

Available Stock
=
SUM(InventoryBatch.remainingQuantity)

for the relevant product/barcode.

For example:

Batch #1 → 40 remaining
Batch #2 → 25 remaining
Batch #3 → 10 remaining

Then:

Available Stock = 75

The system may keep a denormalized stock value for performance if necessary, but it must not become a second independent source of truth.

The authoritative inventory state is the collection of inventory batches and their remaining quantities.

5. FIFO Costing

The inventory system must use FIFO — First In, First Out when determining the cost of sold inventory.

FIFO means:

The oldest available inventory batch is consumed before newer inventory batches.

Example:

Batch #1
50 units @ 100

Batch #2
30 units @ 120

Customer purchases:

10 units

The system consumes:

Batch #1
10 × 100

Remaining inventory:

Batch #1 → 40 @ 100
Batch #2 → 30 @ 120
6. Sale Consuming Multiple Batches

A single sale can consume multiple batches.

Example:

Batch #1 → 40 units @ 100
Batch #2 → 30 units @ 120

Customer purchases:

45 units

FIFO allocation:

Batch #1
40 units × 100

Batch #2
5 units × 120

The actual inventory cost is:

40 × 100 = 4,000
5 × 120  =   600

Total Cost = 4,600

If the unified selling price is:

150

then:

Revenue = 45 × 150 = 6,750

Profit = 6,750 - 4,600
       = 2,150

This is the actual historical profit for that sale.

7. Sale-to-Batch Allocation Must Be Persisted

The system must not calculate FIFO only temporarily during the sale.

When a sale is finalized, the system must persist which batches were consumed.

Introduce a concept such as:

InventoryBatchConsumption

Example:

SaleItem
Product A
Quantity = 45
Selling Price = 150

BatchConsumption #1
Batch #1
Quantity = 40
Unit Cost = 100

BatchConsumption #2
Batch #2
Quantity = 5
Unit Cost = 120

This creates an immutable relationship:

SaleItem
    ↓
BatchConsumption
    ↓
InventoryBatch

This is critical for historical accuracy.

The system must always be able to answer:

"Which inventory batches were consumed by this sale, and what was their actual cost?"

8. Selling Price Must Be Independent of FIFO Cost

The selling price is a product pricing decision.

The inventory cost is an inventory accounting decision.

They must not be coupled.

Example:

Batch #1 → 100
Batch #2 → 120

Current Selling Price → 150

Both batches can be sold at:

150

The resulting margin is different:

Batch #1:
150 - 100 = 50 profit

Batch #2:
150 - 120 = 30 profit

This is expected and valid.

The product's expectedMarkupPercentage should therefore be treated as a pricing helper, not as a constraint on the actual selling price.

9. Expected Markup Percentage

ProductBarcode.expectedMarkupPercentage represents the product's current expected/default markup.

It is used to help the user calculate a suggested selling price when purchasing new inventory.

Example:

New Buying Price = 120
Expected Markup = 20%

Suggested Selling Price = 144

However, the user is not required to use 144.

The user may choose:

Selling Price = 150

or any other valid price.

The purchase's actual selling price must therefore be independent from the expected markup.

The actual margin for a sold unit is determined from:

Actual Selling Price
-
Actual FIFO Inventory Cost
10. Purchase Item Pricing

When adding a product to a purchase:

Product expectedMarkupPercentage
        ↓
Calculate suggested selling price

For example:

Buying Price = 120
Expected Markup = 20%
Suggested Selling Price = 144

The purchase UI should show the suggestion clearly but must allow the user to override the selling price.

If the user chooses:

Selling Price = 150

the system must accept it.

The purchase item should preserve the actual prices associated with that purchase.

The product's expected markup should not be automatically recalculated from the purchase.

11. Purchase Finalization

When a purchase is finalized:

For every purchase item that represents received stock:

Validate the product/barcode.
Validate quantity.
Validate buying cost.
Create an InventoryBatch.
Set:
receivedQuantity
remainingQuantity
unitCost
purchase reference
purchase item reference
receipt date/time
Increase the product's available inventory through the batch system.
Preserve the purchase as immutable historical data.

Example:

PurchaseItem
-------------------------
Product = A
Quantity = 30
Buying Price = 120
Selling Price = 150

creates:

InventoryBatch
-------------------------
Product = A
Received = 30
Remaining = 30
Unit Cost = 120
Source Purchase = X
Source Purchase Item = Y









Purchase_Batches.mFIFO + Customer Sales Return

Each purchase creates an InventoryBatch with its own cost and remaining quantity.

When a customer buys products:

The sale uses FIFO.
If 10 units are sold from Batch 1, the sale records that:
Batch 1 → 10 units consumed
Unit cost = Batch 1 cost
Batch 1's remainingQuantity is reduced by 10.

If the customer later returns those 10 units:

The return references the original sale item.
The system knows that those 10 units came from Batch 1.
The 10 returned units are added back to Batch 1.
The original sale and its batch consumption records are not modified or deleted.