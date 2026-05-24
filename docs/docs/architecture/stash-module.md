---
title: Stash module architecture
---

# Stash module architecture

The stash module is the bounded context for current local inventory. It owns stash definitions,
`StashEntry` rows, `StashMeasurement` arithmetic, and the `StashMovement` ledger. Food metadata is
not stored inline on stash rows anymore: entries point at the catalog through `StashFoodRef`, and
UI or consumption flows resolve product or recipe data live. See
[ADR-0004](../../adr/0004-stash-foodref-live-reference.md) for the refactor rationale and
trade-offs.

## Bounded context and module structure

| Layer | Key files | Responsibility |
| --- | --- | --- |
| Composition root | `stash/StashModule.kt` | Registers stash domain and infrastructure modules. |
| Domain | `stash/domain/entity/*`, `stash/domain/usecase/*`, `stash/domain/repository/StashRepository.kt` | Owns stash policies, value objects, use cases, and repository contracts. |
| Infrastructure | `stash/infrastructure/room/*`, `stash/infrastructure/repository/RoomStashRepository.kt`, `app/infrastructure/room/FoodYouDatabase.kt` | Maps domain models to Room, registers migrations, and provides the local owner implementation. |
| UI adapters | `app/ui/stash/*`, `app/ui/home/stash/*`, `app/ui/food/diary/*`, `app/ui/settings/*` | Turns user actions into stash use case calls and combines stash entries with live food display data. |

```text
Food catalog
(ProductRepository, RecipeRepository)
        │  live product / recipe metadata
        ▼
Home, stash, diary, settings UI
(app.ui.home.stash.*, app.ui.stash.*, app.ui.food.diary.*, app.ui.settings.*)
        │
        ▼
stash.domain.usecase.*
        ├── uses StashRepository + StashOwnerProvider as the stash boundary
        ├── uses ProductRepository + RecipeRepository for validation, batch creation, and consumption
        ├── uses FoodDiaryEntryRepository + MealRepository for consume/return/reversal flows
        └── emits StashEntry / StashMovement models
                         │
                         ▼
stash.infrastructure.repository.RoomStashRepository
        ├── StashDefinitionDao -> StashDefinition
        ├── StashItemDao       -> StashEntry via StashMeasurementEntity
        └── StashMovementDao   -> StashMovement
```

### Boundary rules

- The stash domain depends on abstractions (`StashRepository`, product/recipe/diary repositories), not
  on Room.
- UI code should enter stash through use cases or read-only repository flows; it should not query
  stash tables directly.
- `StashEntry` stores only stash-owned measurement, timestamps, and a `StashFoodRef`. Product and
  recipe metadata is resolved live from the catalog.
- `StashFoodRef.Recipe` captures batch context (`totalWeight`, `totalAmount`) because recipe
  consumption may need serving-to-weight conversion later.
- Diary integration is ledger-based through `LinkedDiaryEntryId`, so edit/delete flows can rebalance
  or restore stash state.
- Product and recipe foreign keys use `ON DELETE CASCADE`; diary entries remain safe because the
  diary owns its own historical food data.

## Data model guide

### Core value objects

| Type | Purpose | Important constraints |
| --- | --- | --- |
| `StashDefinitionId`, `StashEntryId`, `StashMovementId`, `LinkedDiaryEntryId`, `StashOwnerId` | Strongly typed IDs | `StashOwnerId.Local` is the current offline owner. |
| `StashName` | Human-readable stash name | Trimmed, must not be blank. |
| `StashMeasurement` | A stash-scoped wrapper around `Measurement` | Supports same-type arithmetic across all 6 `MeasurementType`s (`Gram`, `Milliliter`, `Ounce`, `FluidOunce`, `Package`, `Serving`). Raw value must be finite. |
| `ShoppingSessionId`, `ShoppingSessionItemId` | Shopping draft identifiers | Only exist in the in-memory shopping-session flow. |

### `StashDefinition`

`StashDefinition` is the container metadata:

- `id`: persisted identifier
- `ownerId`: current stash owner
- `name`: unique per owner at the database level
- `createdAt`
- `ordering`: non-negative display order

Current-state invariant: `ordering >= 0`, and Room enforces a unique `(ownerId, name)` pair.

### `StashEntry`

`StashEntry` is the current inventory row:

- `stashId`: owning stash
- `foodRef`: the live food reference
- `measurement`: current remaining amount as a `StashMeasurement`
- `createdAt`

`StashEntry` does not duplicate catalog metadata. The live target is stored in `StashFoodRef`:

| Variant | Stored on the stash row | Resolved live |
| --- | --- | --- |
| `StashFoodRef.Product` | `foodProductId` | Product name, nutrition, brand, barcode, package weight, serving weight |
| `StashFoodRef.Recipe` | `foodRecipeId`, `batchTotalWeight`, `batchTotalAmount`, `batchTotalAmountType` | Recipe name, nutrition, and ingredient tree |

Recipe entries keep the batch context because "2 servings from the batch I just cooked" is
meaningful stash state. Product entries keep only the catalog ID.

### `StashMovement`

`StashMovement` is the append-only audit trail for stash changes:

- `stashId`, `itemId`
- `operation`
- `measurementChange`
- `linkedDiaryEntryId`
- `note`
- `createdAt`

Current operations:

- `Purchase`
- `CreateSnapshot`
- `ManualQuickAdd`
- `DirectConsume`
- `IngredientSubtract`
- `ReturnToStash`
- `ManualAdjust`
- `AutoReversalOnEdit`
- `AutoReversalOnDelete`

Sign convention:

- Positive `measurementChange` adds inventory.
- Negative `measurementChange` removes inventory.

### Lifecycle constraints

- `observeStashContents()` hides rows where `rawValue <= 0`, but zero rows may still remain in the
  table.
- Product-backed entries are retained at zero when they may be needed for later diary reversal;
  recipe-backed entries are deleted when they reach zero.
- Product additions merge or revive per stash on `(foodProductId, measurementType)`. A retained zero
  row with that key is reused instead of creating a duplicate.
- Manual quick-add creates a real catalog product with `FoodSource.Type.User`, so live stash rows
  never depend on a null product identity.
- `StashMovement` rows are append-only; reversals write new movements instead of mutating old ones.
- Deleting a catalog product or recipe cascades to stash entries. That trade-off is accepted because
  the user explicitly removed the catalog item.

## Data flow

### Display and browse flow

1. `StashRepository.observeStashContents(stashId)` emits `StashEntry` rows from
   `StashMeasurementEntity`.
2. UI view models resolve each `StashFoodRef` through `ObserveFoodUseCase.observe(...)`.
3. UI state combines the live food headline with the stash measurement and exposes
   loading/unavailable placeholders while the food lookup completes.

Representative consumers of this flow:

- `StashBrowserViewModel`
- `HomeStashCardViewModel`
- `ConsumeStashItemViewModel`

```text
StashEntry(foodRef, measurement)
        │
        ├── observeStashContents()
        ▼
UI view model
        │
        ├── ObserveFoodUseCase.observe(foodRef)
        ▼
StashFoodDisplay / home summary / consume screen state
```

### Consumption and diary flow

1. `ConsumeFromStashUseCase` resolves the live `Product` or `Recipe` referenced by the entry.
2. Product entries consume only in the stored measurement type.
3. Recipe entries can convert between weight and serving/package units using the stored batch
   context on `StashFoodRef.Recipe`.
4. The resulting diary entry stores its own historical food data; the stash keeps only its live
   reference plus movement history.

### Shopping-session flow

`ShoppingSession` is a draft-only aggregate. Each `ShoppingSessionItem` stores:

- `StashFoodRef.Product` for the future persisted stash identity
- `ShoppingSessionProductDetails` for immediate UI and calorie display
- `StashMeasurement` for the draft quantity

On confirm, `ConfirmShoppingSessionUseCase` persists each draft item as a `StashEntry` plus a
`Purchase` movement inside one transaction.

## Primary use cases

### Add product to stash (`AddProductToStashUseCase.add`)

- **Input:** `productId`, positive `StashMeasurement`, optional `stashId`
- **Output:** `Result<AddProductToStashResult, AddProductToStashError>`
- **Writes:** `StashEntry` with `StashFoodRef.Product`; `Purchase` movement
- **Invariants:**
  - Product must exist.
  - Measurement must be positive and supported by the product.
  - Product uniqueness is per stash on `(productId, measurementType)`.
  - If an existing product entry in the same stash has that key, its measurement is incremented
    instead of creating a second row.
  - If that same-key row is retained at zero because of linked diary history, it is revived instead
    of duplicated.
  - If `stashId` is omitted and no stashes exist, a default stash named `Stash` is created.
  - Multiple stashes require an explicit selection.

### Manual quick-add flow

- **Input:** product name, nutrition facts, supported measurement, optional `stashId`
- **Output:** created stash and entry IDs or a validation error
- **Writes:** new catalog `Product` (`FoodSource.Type.User`), `StashEntry` with
  `StashFoodRef.Product`, `ManualQuickAdd` movement
- **Invariants:**
  - Name must be non-blank.
  - Only gram / milliliter / ounce / fluid-ounce inputs are accepted.
  - The generated product is treated like any other catalog product afterward, including
    merge/revive behavior.

### Add recipe batch to stash (`AddRecipeToStashUseCase.add`)

- **Input:** `recipeId`, optional `stashId`, positive `totalAmount`, positive `servings`
- **Output:** `Result<AddRecipeToStashResult, AddRecipeToStashError>`
- **Writes:** `StashEntry` with `StashFoodRef.Recipe`; `CreateSnapshot` movement
- **Invariants:**
  - Recipe must exist.
  - The created `StashFoodRef.Recipe` stores both the catalog ID and the batch context needed for
    future consumption.
  - `Serving` or `Package` input computes `totalWeight` from the recipe and produced servings;
    direct weight units store the entered total as-is.

### Batch add / shopping session (`StartShoppingSessionUseCase`, `AddToShoppingSessionUseCase`, `ConfirmShoppingSessionUseCase`)

- **Input:** existing `stashId`, per-item `productId` + positive measurement
- **Output:** in-memory `ShoppingSession` during drafting, then
  `Result<List<StashEntryId>, ConfirmShoppingSessionError>` on confirm
- **Writes:** none during draft; on confirm each session item becomes a `StashEntry` plus a
  `Purchase` movement inside one transaction
- **Invariants:**
  - `StartShoppingSessionUseCase` only starts for an existing stash.
  - `AddToShoppingSessionUseCase` validates product existence and measurement support but only
    mutates the draft aggregate.
  - Draft items merge by `(productId, measurementType)` within the shopping session.
  - `ConfirmShoppingSessionUseCase` persists the draft's `StashFoodRef.Product` directly.

### Consume from stash (`ConsumeFromStashUseCase.consume`)

- **Input:** `itemId`, `mealId`, positive `amountEaten`, optional `dateOverride`
- **Output:** `Result<FoodDiaryEntryId, ConsumeFromStashError>`
- **Writes:** diary entry, updated/deleted `StashEntry`, `DirectConsume` movement linked to the new
  diary entry
- **Invariants:**
  - Item and meal must exist.
  - Product entries consume only in the stored stash unit.
  - Recipe entries may consume by weight even when the stored measurement is serving/package-based,
    using the stored batch context on `StashFoodRef.Recipe`.
  - Product-backed rows may remain as zero rows for later reversal; recipe rows are removed when
    empty.

### Return leftovers and rebalance linked diary history (`ReturnPartialMealToStashUseCase`, `RestoreLinkedDiaryEntryStashUseCase`)

- **Input:** existing linked diary entry plus stash/measurement updates
- **Output:** stash entry IDs or `Result<Unit, RestoreLinkedDiaryEntryStashError>` depending on the
  flow
- **Writes:** stash entry updates/recreations, `ReturnToStash` / `AutoReversalOnEdit` /
  `AutoReversalOnDelete` movements, coordinated diary updates
- **Invariants:**
  - Linked flows prefer reusing the original `foodRef` so restored inventory stays attached to the
    same catalog identity.
  - If a diary entry has no stash-linked source row, the return flow creates a user product and
    references that new catalog entry.
  - Reversal flows fail loudly if an expected retained source row is missing.

### Ingredient subtract while logging a recipe (`AssessRecipeStashAvailabilityUseCase.assess`, `LogRecipeToMealWithStashSubtractionUseCase.log`)

- **Input:** `recipeId`, diary `Measurement`, `mealId`, `date`, subtraction mode (`Auto`, `Partial`, `Skip`)
- **Output:** availability data or `Result<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError>`
- **Writes:** diary recipe entry plus zero or more `IngredientSubtract` movements and stash entry updates
- **Invariants:**
  - Nested recipes are unpacked into product requirements before allocation.
  - Only `StashFoodRef.Product` entries are eligible inventory for ingredient subtraction.
  - Candidate entries are allocated oldest-first (`createdAt` ascending) within matching measurement
    types.
  - `Auto` subtracts only when all ingredients are available; `Partial` subtracts what is
    available; `Skip` logs the diary entry without touching stash.

## Persistence guide

### Room schema

`FoodYouDatabase` version `34` includes the stash schema and converters:

- `MeasurementTypeConverter`
- `StashMovementOperationTypeConverter`

Tables:

| Table | Purpose | Current schema details |
| --- | --- | --- |
| `StashDefinition` | Stash containers | PK `id`; indices on `ownerId` and unique `(ownerId, name)`; sorted by `ordering`, `createdAtEpochSeconds`, `id`. |
| `StashEntry` | Current stash rows | FK `stashId -> StashDefinition(id)` with `ON DELETE CASCADE`; nullable `foodProductId` / `foodRecipeId` live references; `observeItems()` filters `rawValue > 0`; unique indices on `(stashId, foodProductId, measurementType)` and `(stashId, foodRecipeId, measurementType)`; recipe rows additionally store `batchTotalWeight`, `batchTotalAmount`, and `batchTotalAmountType`. |
| `StashMovement` | Audit ledger | FK only to `StashDefinition`; indices on `stashId`, `itemId`, `linkedDiaryEntryId`; `itemId` is intentionally not a foreign key so history survives row merge/recreation. |

### DAO query patterns

```sql
-- Visible stashes for one owner
SELECT *
FROM StashDefinition
WHERE ownerId = :ownerId
ORDER BY ordering ASC, createdAtEpochSeconds ASC, id ASC;

-- Visible contents for one stash
SELECT *
FROM StashEntry
WHERE stashId = :stashId
  AND rawValue > 0
ORDER BY createdAtEpochSeconds DESC, id DESC;

-- Movement history for stash browser
SELECT *
FROM StashMovement
WHERE stashId = :stashId
ORDER BY createdAtEpochSeconds DESC, id DESC;

-- Ledger replay for diary edit/delete reconciliation
SELECT *
FROM StashMovement
WHERE linkedDiaryEntryId = :linkedDiaryEntryId
ORDER BY createdAtEpochSeconds ASC, id ASC;
```

### Repository mapping

`RoomStashRepository` is the only Room-backed implementation of `StashRepository`:

- Converts `StashMeasurementEntity` to `StashEntry`
- Maps `foodProductId` to `StashFoodRef.Product`
- Maps `foodRecipeId` plus batch columns to `StashFoodRef.Recipe`
- Converts `LocalDateTime` to epoch seconds and back
- Wraps inserts/deletes in `TransactionProvider.withTransaction()`

Multi-write use cases (`consume`, `confirm shopping`, `return`, recipe logging, diary reversals) also
open an outer transaction so diary writes and stash writes commit together.

### Migration notes

`StashMeasurementMigration` performs the `33 -> 34` refactor from frozen inline metadata to live
food references:

- The old schema did not store recipe IDs for legacy recipe-style stash rows, so those rows cannot
  be migrated into true recipe references.
- Instead, legacy recipe-style rows and orphaned product references are healed into synthetic user
  products and then mapped to `StashFoodRef.Product`.
- Duplicate product rows are merged before the new unique indices are created.
- `StashMovement.itemId` values are remapped to the surviving `StashEntry` rows after merge.

Representative migration tests:

- `AbstractStashCoreMigrationTest`
- `StashCoreMigrationTest`

## Testing guide

### Unit tests (`commonTest`)

Use fake repositories and fixed collaborators:

- `StashUseCaseTestDoubles.kt`
- `FakeStashRepository`
- `FakeFoodDiaryEntryRepository`
- `FakeTransactionProvider`
- `SnapshottingFakeTransactionProvider`
- `FixedDateProvider`
- `NoOpLogger`

Representative tests:

- `StashEntryTest`
- `StashFoodRefTest`
- `ConsumeFromStashUseCaseTest`
- `AddProductToStashUseCaseTest`
- `ShoppingSessionUseCaseTest`
- `RecipeStashAvailabilityUseCaseTest`

Patterns to keep:

- Prefer AAA structure with concrete domain examples.
- Verify both state (`allItems()`, `allEntries()`) and ledger output (`allMovements()`).
- Use `SnapshottingFakeTransactionProvider` when rollback behavior matters.

### Integration and UI state tests (`commonTest`)

Representative tests:

- `StashDiaryConsistencyIntegrationTest`
- `HomeStashQuickAddViewModelTest`
- `ShoppingSessionStateTest`
- `ShoppingSessionViewModelTest`
- `StashBrowserStateTest`
- `ConsumeStashItemViewModelTest`

Current state: most stash presentation logic is covered in common tests, not device-side UI tests.

### Instrumented tests (`androidInstrumentedTest`)

Representative tests:

- `StashCoreMigrationTest`

Current state: stash-specific instrumented coverage is migration-focused.
