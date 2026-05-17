# FoodYou

An Android food tracking app (KMP/Compose Multiplatform) with a diary module and a stash module for managing food inventory.

## Language

**StashItem**: A food item tracked in a stash. Carries a `snapshot` (product or dish metadata) and a `measurement` (a `StashMeasurement` representing the amount and type). Merge key is `(productId, measurementType)` — same product + same unit type merges, different unit types become separate items.

**StashMeasurement**: A `Measurement` wrapped with same-type arithmetic (`+`, `-`, `negate()`). Lives in `stash.domain.entity`. Enforces that only measurements of the same type can be combined. Supports all 6 MeasurementTypes (Gram, Milliliter, Ounce, FluidOunce, Package, Serving) — full alignment with the diary module.

**StashMovement**: An audit ledger entry recording how and why a stash item's measurement changed. Carries `measurementChange` (signed `StashMeasurement`) and `operation` (the lifecycle event type).

**Measurement**: A sealed interface in `common.domain.measurement` representing a user-facing quantity (`Gram(150)`, `Serving(2)`, `Package(3)`, etc.). Not domain-specific — used by both diary and stash modules. Supports `times(Double)` but NOT arithmetic between measurements.

**AnonymousDishSnapshot.totalAmount**: Stores a plain `Measurement` (descriptive metadata about the dish batch, e.g. "this dish is 3 servings"). NOT a `StashMeasurement` because it's not something you add/subtract — it's descriptive.

**ShoppingSession**: An in-memory draft of items to be confirmed into stash items. Each `ShoppingSessionItem` carries a single `StashMeasurement`. Merges by `(productId, measurementType)` — same rule as the stash.

**Same-type merge rule**: When adding a product to the stash, if an existing item has the same `productId` AND `measurementType`, quantities merge (e.g. `Gram(300)` + `Gram(200)` → `Gram(500)`). Different measurement types for the same product create separate items (e.g. `Serving(2)` alongside `Gram(300)`).

**Cross-unit consumption (dishes only)**: An `AnonymousDishSnapshot` stored in servings can be consumed in a weight unit (Gram/Milliliter). The weight is converted back to servings proportionally using `totalWeight / totalAmount`. Raw products only support same-type consumption.

## Relationships

- A **Measurement** enters the stash domain and is wrapped as a **StashMeasurement** — no conversion to weight
- A **StashItem** has one **StashMeasurement** (preserving the user's original unit type)
- A **StashMovement** records changes to **StashItem** measurements with an **operation** type
- A **ShoppingSession** confirms into **StashItem**s and **StashMovement**s
- **AnonymousDishSnapshot** carries a plain **Measurement** for its total amount (descriptive, not arithmetic)

## Flagged ambiguities (all resolved)

- "rawMeasurement" vs "measurement" on StashItem — resolved: replaced by `StashMeasurement`. The user's original unit type is now preserved; no separate rawMeasurement field needed.
- "rawMeasurement" on StashMovement — removed. The `measurementChange: StashMeasurement` on each movement provides sufficient audit information.
- "Fraction" in StashQuantityUnit — resolved: replaced by `MeasurementType.Serving`. StashQuantityUnit eliminated entirely; StashMeasurement uses the common MeasurementType.
- "toMeasurement()" on StashQuantity — resolved: eliminated. StashMeasurement wraps Measurement directly; no conversion needed.
- StashQuantityUnit as parallel enum — resolved: eliminated. StashMeasurement uses MeasurementType (all 6 types, full alignment with diary).
- Weight conversion at boundary — resolved: no more conversion. StashMeasurement preserves the user's chosen Measurement type. Serving(2) stays as Serving(2), not Gram(300).
- Same-type merge rule — resolved: merge by (productId, measurementType). Gram merges with Gram, Serving with Serving, but Gram does NOT merge with Serving.

## Rejected alternatives

- **Weight-only stash** (current approach before this redesign): Convert all Measurements to Gram/Milliliter at the boundary. Rejected because it loses user intent ("2 servings" becomes "300g") and forces a parallel StashQuantityUnit enum.
- **Measurement with arithmetic**: Adding `plus`/`minus` to the `Measurement` sealed interface. Rejected because it pollutes the common domain type with stash-specific concerns.
- **No merging at all** (each addition = separate StashItem): Rejected because weight-based items of the same product and unit should merge (500g + 300g chicken = 800g chicken), matching user expectations and diary conventions.