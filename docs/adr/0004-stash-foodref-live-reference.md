# Stash entries reference food by ID instead of freezing snapshots

Supersedes: [ADR-0001](./0001-stash-item-quantity-only.md) (partially — extends the "quantity-only" concept to also remove snapshot storage)

## Status

**Accepted**

## Context

The previous stash model carried a `StashSnapshot` — a sealed interface with `RawProductSnapshot` and `AnonymousDishSnapshot` variants — that copied all product/recipe metadata (name, brand, barcode, nutrition facts, weights, source) at creation time and stored it as denormalized columns in the `StashItem` table. This created significant complexity:

- 17+ snapshot columns on `StashItemEntity`, plus 3 embedded nutrition objects
- ~88 lines of snapshot mapping code in `RoomStashRepository` (toModel/toEntity)
- A sealed interface hierarchy (`StashSnapshot` → `RawProductSnapshot` / `AnonymousDishSnapshot`) that must be pattern-matched in every use case
- Snapshot data diverges from the catalog when products or recipes are edited after stash creation — the stash shows stale data
- The merge/revive rule reaches into `snapshot.productId` for the uniqueness key, coupling the domain logic to snapshot internals
- Manual quick-adds create `RawProductSnapshot(productId = null)` — a special case that complicates `canDeleteWhenEmpty()` and merge logic

Meanwhile, the diary module already uses a different model: `MeasurementEntity` references `DiaryProduct` or `DiaryRecipe` by FK, keeping a separate snapshot per entry. But for stash — where items represent current inventory, not historical records — a live reference is more appropriate because the stash should reflect the current state of the food catalog.

## Decision

Replace `StashSnapshot` with `StashFoodRef` — a sealed interface whose variants reference the food catalog by ID rather than copying data:

```kotlin
sealed interface StashFoodRef {
    data class Product(val productId: FoodId.Product) : StashFoodRef
    data class Recipe(
        val recipeId: FoodId.Recipe,
        val totalWeight: Double,
        val totalAmount: Measurement,
    ) : StashFoodRef
}
```

- **`StashFoodRef.Product`**: live reference to a catalog `Product`. All metadata (name, nutrition, weights, brand, barcode) is read from `ProductRepository` at display/consumption time.
- **`StashFoodRef.Recipe`**: live reference to a catalog `Recipe` PLUS batch-context fields (`totalWeight`, `totalAmount`) captured at creation time. Recipe metadata (nutrition, ingredients) is read from `RecipeRepository` live, but batch amounts are frozen because they represent a specific cooking event.

Rename `StashItem` → `StashEntry` and `StashItemId` → `StashEntryId` to align with the diary module's naming convention (`Measurement` → `MeasurementEntity`).

### Persistence changes

Replace `StashItemEntity` with `StashMeasurementEntity` (table name `StashEntry`):

- Delete all 17+ `snapshot_*` columns
- Add `foodProductId: Long?` (FK → Product, CASCADE), `foodRecipeId: Long?` (FK → Recipe, CASCADE)
- Add `batchTotalWeight: Double?`, `batchTotalAmount: Double?`, `batchTotalAmountType: MeasurementType?` (only non-null for recipe batches)
- Unique index on `(stashId, foodProductId, measurementType)` for product merge rule
- Unique index on `(stashId, foodRecipeId, measurementType)` for recipe entries

### Manual quick-adds

Instead of `RawProductSnapshot(productId = null, ...)`, manual quick-adds create a real `Product` entity with `FoodSource.Type.User` in the catalog and reference it by ID. This eliminates the `productId = null` concept entirely.

### Cascade deletion

Both product and recipe FKs use `onDelete = ForeignKey.CASCADE`. If a user deletes a product or recipe that has stash entries, those stash entries are also deleted. This is acceptable because: the user explicitly chose to delete something they no longer use, and diary entries referencing that food still carry their own snapshot data independently.

### Consumption flow

- `toDiaryFood()` reads product/recipe data from repositories instead of snapshot fields
- `planConsumption()` for product entries: same-type consumption only (unchanged logic, different data source)
- `planConsumption()` for recipe entries: uses `batchTotalWeight`/`batchTotalAmount` from `StashFoodRef.Recipe` for cross-unit consumption (weight → serving proportional conversion), not live recipe data

### Merge/revive rule change

- Before: uniqueness key is `(snapshot.productId, measurementType)` — requires pattern-matching on `RawProductSnapshot`
- Now: uniqueness key is `(foodProductId, measurementType)` or `(foodRecipeId, measurementType)` — direct field access on `StashMeasurementEntity`, no pattern-matching needed

### canDeleteWhenEmpty simplification

- Before: `snapshot is RawProductSnapshot && productId == null` → deletable; `AnonymousDishSnapshot` → always deletable
- Now: `foodRef is StashFoodRef.Product` → retained for reversal; `foodRef is StashFoodRef.Recipe` → deletable when empty
- Since manual quick-adds now create real products, there is no `productId = null` special case

## Files deleted

- `StashSnapshot.kt` (entire file: `StashSnapshot`, `RawProductSnapshot`, `AnonymousDishSnapshot`)
- `StashItemSnapshotType.kt` (enum)
- `StashSnapshotTypeConverter.kt` (Room converter)
- ~88 lines of snapshot mapping code in `RoomStashRepository`

## Files renamed

- `StashItem.kt` → `StashEntry.kt` (domain entity)
- `StashItemId.kt` → `StashEntryId.kt` (value type)
- `StashItemEntity.kt` → `StashMeasurementEntity.kt` (Room entity)
- Related test files updated accordingly

## Files significantly modified

- `RoomStashRepository.kt` — remove snapshot mapping, add product/recipe repo lookups
- `AddProductToStashUseCase.kt` — use `StashFoodRef.Product(productId)` instead of `RawProductSnapshot.from(product)`
- `CreateAnonymousDishSnapshotUseCase.kt` — use `StashFoodRef.Recipe(recipeId, totalWeight, totalAmount)` instead of `AnonymousDishSnapshot.from(recipe, ...)`
- `CreateManualStashSnapshotUseCase.kt` — create a `Product` entity, then add to stash via normal product flow
- `ConsumeFromStashUseCase.kt` — read food data from repos, use `batchTotalWeight`/`batchTotalAmount` from `StashFoodRef.Recipe`
- `ReturnPartialMealToStashUseCase.kt` — simplify: prefer `foodRef` reference, no snapshot copying
- `RestoreLinkedDiaryEntryStashUseCase.kt` — simplify: use `foodRef` for item recreation
- `SelectStashItemToConsumeUseCase.kt` — pattern-match on `StashFoodRef` variants instead of `StashSnapshot`
- `AdjustStashItemQuantityUseCase.kt` — simplify `canDeleteWhenEmpty()`
- `ShoppingSession.kt` — `ShoppingSessionItem` uses `StashFoodRef.Product` instead of `RawProductSnapshot`
- UI state/viewmodel files — read food data from repos instead of `item.snapshot.*`
- Database migration (version bump, schema change)

## Migration note

`FoodYouDatabase` version `33` ships the live-reference stash schema directly through
`StashCoreMigration`.

- `StashEntry` is created with `foodProductId` / `foodRecipeId` foreign keys and recipe batch
  context columns from the start.
- The product and recipe uniqueness indices ship as part of that initial stash schema.
- Because the snapshot-era stash schema was never released, there is no separate shipped
  snapshot-to-live stash migration.

## Consequences

### Positive

1. **Massive code simplification**: Delete 17+ DB columns, the entire `StashSnapshot` hierarchy, the `StashSnapshotTypeConverter`, and ~88 lines of mapping code. Replace with 6 nullable columns and direct repo lookups.

2. **Live product data**: Stash entries automatically reflect product catalog edits (nutrition corrections, name changes). No more stale snapshot data.

3. **Simpler merge rule**: `(foodProductId, measurementType)` is a direct column pair — no need to reach into sealed interface internals.

4. **No `productId = null` special case**: Manual quick-adds create real products, eliminating the synthetic-row concept.

5. **Aligns with diary pattern**: Single table with nullable FKs (`foodProductId`/`foodRecipeId`) matches `MeasurementEntity`'s `productId`/`recipeId` pattern.

6. **Recipe creation is still correct**: Batch-specific data (`totalWeight`, `totalAmount`) is captured at creation time, preserving the "specific cooking event" semantics.

### Negative (Trade-offs)

1. **Offline/async loading required**: Display stash items requires loading product/recipe data from repositories. Stash UI must handle loading states or cache product/recipe data.

2. **Cascade deletion breaks reversal chain**: If a user deletes a product, the live `StashEntry` is removed by FK cascade and future stash reversal for that entry is no longer possible. `StashMovement` history can still remain because `itemId` is not a foreign key, and diary entries referencing that product still work because they store their own history. Accepted: users who delete products explicitly don't care about future reversal.

3. **Product deletion must be coordinated**: The FK cascade means deleting a product also deletes stash items. UI should be aware of this (potentially warn the user).

4. **Two reference models**: Product entries reference catalog data live; recipe entries reference catalog data live but also carry batch context. This is principled (recipes have per-batch state, products don't) but requires explaining to future developers.

5. **Cannot view stash items without product/recipe availability**: If a product or recipe is temporarily unavailable (network issue, database migration), the stash item's display name and nutrition would be missing. Room's reactive lookups mitigate this in the local-only architecture.

## Related ADRs

- [ADR-0001](./0001-stash-item-quantity-only.md) — proposed "quantity-only" storage; superseded by ADR-0002's `StashMeasurement` and now further by this ADR's live-reference model
- [ADR-0002](./0002-stash-measurement-refactor.md) — replaced `StashQuantity` with `StashMeasurement`; this ADR builds on that by also replacing `StashSnapshot` with `StashFoodRef`