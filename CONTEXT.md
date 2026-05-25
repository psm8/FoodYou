# FoodYou

An Android food tracking app (KMP/Compose Multiplatform) with a diary module and a stash module for managing food inventory.

## Language

**StashEntry**: An inventory row tracked in a stash. Carries a `foodRef` (a `StashFoodRef` referencing the food catalog by ID) and a `measurement` (a `StashMeasurement` representing the amount and type). For product entries, uniqueness is per stash on `(productId, measurementType)` — same product + same unit type in the same stash reuses one entry, and retained zero rows on that key are revived instead of duplicated. Different measurement types become separate entries.

**StashFoodRef**: A sealed interface in `stash.domain.entity` representing how an entry references the food catalog. Two variants:
- **Product**: References a `Product` by `FoodId.Product`. All metadata (name, nutrition, weights) is read live from the product catalog. No snapshot data is stored.
- **Recipe**: References a `Recipe` by `FoodId.Recipe`, plus `totalWeight` and `totalAmount` captured at batch creation time. Recipe metadata is read live, but batch amounts are frozen because they represent a specific cooking event ("I made 3 servings weighing 900g").

**StashMeasurement**: A `Measurement` wrapped with same-type arithmetic (`+`, `-`, `negate()`). Lives in `stash.domain.entity`. Enforces that only measurements of the same type can be combined. Supports all 6 MeasurementTypes (Gram, Milliliter, Ounce, FluidOunce, Package, Serving) — full alignment with the diary module.

**StashMovement**: An audit ledger entry recording how a stash entry's measurement changed. Carries `measurementChange` (signed `StashMeasurement`) and `operation` (the lifecycle event type). The `note` field is reserved for auto-generated descriptions (e.g. move operations write `"Moved to X"` / `"Moved from X"`); manual adjust and remove actions set `note = null`.

**Measurement**: A sealed interface in `common.domain.measurement` representing a user-facing quantity (`Gram(150)`, `Serving(2)`, `Package(3)`, etc.). Not domain-specific — used by both diary and stash modules. Supports `times(Double)` but NOT arithmetic between measurements.

**ShoppingSession**: An in-memory draft of items to be confirmed into stash entries. Each `ShoppingSessionItem` carries a single `StashMeasurement`. For raw products, it follows the same per-stash uniqueness rule as the stash: `(productId, measurementType)`.

**Same-type merge rule**: Within a single stash, products merge by `(productId, measurementType)`. If an entry with that key already exists, quantities merge (e.g. `Gram(300)` + `Gram(200)` → `Gram(500)`); if the existing row is retained at zero, it is revived instead of creating a duplicate. Different measurement types for the same product create separate entries (e.g. `Serving(2)` alongside `Gram(300)`).

**Cross-unit consumption (recipes only)**: A recipe entry can be consumed across portion units (Serving/Package) and weight units (Gram/Milliliter) in both directions. Portion-based stash entries use the frozen batch context on `StashFoodRef.Recipe`; weight-based stash entries use the live recipe definition to convert portion demands into weight. Product entries only support same-type consumption.

**Cascade deletion**: Deleting a product or recipe that has stash entries also deletes those stash entries (FK CASCADE) and breaks future stash reversal for them. This is acceptable because the user explicitly chose to delete something they no longer use. Diary entries referencing that food still carry their own historical food data independently.

## Relationships

- A **Measurement** enters the stash domain and is wrapped as a **StashMeasurement** — no conversion to weight
- A **StashEntry** has one **StashMeasurement** (preserving the user's original unit type) and one **StashFoodRef** (referencing the food catalog by ID)
- A **StashMovement** records changes to **StashEntry** measurements with an **operation** type
- A **ShoppingSession** confirms into **StashEntry**s and **StashMovement**s
- **StashFoodRef.Product** references a catalog **Product** by ID — all metadata is read live
- **StashFoodRef.Recipe** references a catalog **Recipe** by ID, plus frozen batch context (**totalWeight**, **totalAmount**) — recipe metadata is read live, batch amounts are captured at creation

## Resolved decisions (beyond ambiguities)

- **Adjust mode default**: `SetTo` is the default mode for stash quantity adjustment (changed from `ChangeBy`). Reasoning: "I have 500g left" is the natural mental model; `ChangeBy` requires mental arithmetic. `ChangeBy` remains available as a secondary option. Amount field is pre-filled based on mode: current quantity for `SetTo`, empty/zero for `ChangeBy`.
- **Manual action reason/note removed**: The `ManualStashAction` class (carrying `reason` + `note`) is eliminated. Adjust and remove actions no longer require user-supplied text. The `StashMovement.note` field stays for auto-generated notes from move operations (`"Moved to X"`, `"Moved from X"`); manual adjust and remove set it to `null`. No screen in the app currently reads movement notes.
- **Remove and Move keep confirmation dialogs**: Remove shows a confirm/cancel dialog (destructive). Move shows a stash selector + confirm/cancel dialog. Both are streamlined — no reason/note text fields.
- **Adjust dialog simplified**: Only mode selector (`SetTo`/`ChangeBy`) and amount field remain. No reason or note fields.

## Flagged ambiguities (all resolved)

- "rawMeasurement" vs "measurement" on StashEntry — resolved: replaced by `StashMeasurement`. The user's original unit type is now preserved; no separate rawMeasurement field needed.
- "rawMeasurement" on StashMovement — removed. The `measurementChange: StashMeasurement` on each movement provides sufficient audit information.
- "Fraction" in StashQuantityUnit — resolved: replaced by `MeasurementType.Serving`. StashQuantityUnit eliminated entirely; StashMeasurement uses the common MeasurementType.
- "toMeasurement()" on StashQuantity — resolved: eliminated. StashMeasurement wraps Measurement directly; no conversion needed.
- StashQuantityUnit as parallel enum — resolved: eliminated. StashMeasurement uses MeasurementType (all 6 types, full alignment with diary).
- Weight conversion at boundary — resolved: no more conversion. StashMeasurement preserves the user's chosen Measurement type. Serving(2) stays as Serving(2), not Gram(300).
- Cross-unit recipe conversion direction — resolved: symmetric for recipe entries. Portion-based demands may consume weight-based recipe stash, and weight-based demands may consume portion-based recipe stash.
- Portion-to-weight source of truth for weight-based recipe stash — resolved: use the live recipe definition when the stash row itself is stored in weight units, because the row does not preserve a serving count.
- Direct recipe match precedence — resolved: if stash contains the exact nested recipe, consume that recipe stash entry instead of flattening to ingredient products.
- Partial direct recipe fallback — resolved: when a direct nested recipe stash entry is only partially sufficient, do not mix it with ingredient fallback for the remainder.
- Live recipe drift for weight-based recipe stash — resolved: accept that serving-to-weight conversion follows the current recipe definition for now, rather than adding new frozen batch data.
- Same-type merge rule — resolved: for products, merge is per stash by `(productId, measurementType)`, and retained zero rows on that key are revived instead of duplicated. Gram merges with Gram, Serving with Serving, but Gram does NOT merge with Serving.
- Revive reference rule — resolved: reviving a retained zero row preserves its `foodRef`. The food reference is live (reads current catalog data), so there is no stale snapshot to worry about.
- Unlinked diary return rule — resolved: returning leftovers from a diary entry with no stash link creates a `Product` in the catalog (with `FoodSource.Type.User`) and references it by ID. This follows the same uniqueness and merge rules as catalog-backed products.
- Frozen metadata vs. live reference — resolved: stash entries reference the food catalog by ID (`StashFoodRef`) instead of storing copied product/recipe metadata inline. Product entries read all metadata live; recipe entries read metadata live but capture batch context (`totalWeight`, `totalAmount`) at creation time. See [ADR-0004](docs/adr/0004-stash-foodref-live-reference.md).
- Manual quick-add identity — resolved: manual quick-adds create a real `Product` entity (with `FoodSource.Type.User`) in the catalog and reference it by `FoodId.Product`. There is no `productId = null` concept; all stash entries have a valid food catalog reference.

## Rejected alternatives

- **Weight-only stash** (current approach before this redesign): Convert all Measurements to Gram/Milliliter at the boundary. Rejected because it loses user intent ("2 servings" becomes "300g") and forces a parallel StashQuantityUnit enum.
- **Measurement with arithmetic**: Adding `plus`/`minus` to the `Measurement` sealed interface. Rejected because it pollutes the common domain type with stash-specific concerns.
- **No merging at all** (each addition = separate StashEntry): Rejected because weight-based items of the same product and unit should merge (500g + 300g chicken = 800g chicken), matching user expectations and diary conventions.
- **Frozen inline food metadata** (previous approach): Copying all product/recipe metadata into denormalized `snapshot_*` columns on the legacy stash table. Rejected because product data in the stash should reflect current catalog edits; only recipe batch context needs to be frozen. The 17+ snapshot columns, 88+ lines of mapping code, and sealed interface hierarchy are replaced by a simple FK reference with optional batch fields. See [ADR-0004](docs/adr/0004-stash-foodref-live-reference.md).
- **Separate stash-product and stash-recipe tables**: Using separate `StashProductEntity` and `StashRecipeEntity` tables instead of one table with nullable FKs. Rejected because it matches neither the diary pattern (which uses a single `MeasurementEntity` with nullable `productId`/`recipeId`) nor the stash module's existing conventions. A single `StashMeasurementEntity` with nullable `foodProductId`/`foodRecipeId` is simpler for queries, merge rules, and FK constraints.
