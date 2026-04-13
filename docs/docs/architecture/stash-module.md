---
title: Stash module architecture
---

# Stash module architecture

The stash module is a bounded context for local inventory. It owns stash definitions, immutable item
snapshots, quantity math, and the movement ledger. It integrates with food catalog, diary, home, and
settings through explicit use cases and repository boundaries instead of sharing Room details.

## Bounded context and module structure

| Layer | Key files | Responsibility |
| --- | --- | --- |
| Composition root | `stash/StashModule.kt` | Registers stash domain and infrastructure modules. |
| Domain | `stash/domain/entity/*`, `stash/domain/usecase/*`, `stash/domain/repository/StashRepository.kt` | Owns stash policies, value objects, use cases, and repository contracts. |
| Infrastructure | `stash/infrastructure/room/*`, `stash/infrastructure/repository/RoomStashRepository.kt`, `stash/infrastructure/StashInfrastructureModule.kt` | Maps domain models to Room and provides the local owner implementation. |
| UI adapters | `app/ui/stash/*`, `app/ui/home/stash/*`, `app/ui/food/diary/*`, `app/ui/settings/*` | Turns user actions into stash use case calls and reacts to flows/events. |

```text
Food catalog
(ProductRepository, RecipeRepository)
        │  product snapshots / recipe ingredient requirements
        ▼
Home, stash, diary, settings UI
(app.ui.home.stash.*, app.ui.stash.*, app.ui.food.diary.*, app.ui.settings.*)
        │
        ▼
stash.domain.usecase.*
        ├── uses FoodDiaryEntryRepository + MealRepository for consume/return/reversal flows
        ├── uses ProductRepository + RecipeRepository for immutable snapshots and recipe allocation
        └── uses StashRepository + StashOwnerProvider as the stash boundary
                         │
                         ▼
stash.infrastructure.repository.RoomStashRepository
        ├── StashDefinitionDao -> StashDefinition
        ├── StashItemDao       -> StashItem
        └── StashMovementDao   -> StashMovement
```

### Boundary rules

- The stash domain depends on abstractions (`StashRepository`, product/recipe/diary repositories), not
  on Room.
- UI code should enter stash through use cases or read-only repository flows; it should not query Room
  tables directly.
- Food catalog data is copied into stash snapshots. Later product or recipe edits do not mutate
  existing stash items.
- Diary integration is ledger-based through `LinkedDiaryEntryId`, so edit/delete flows can rebalance
  or restore stash state.

## Data model guide

### Core value objects

| Type | Purpose | Important constraints |
| --- | --- | --- |
| `StashDefinitionId`, `StashItemId`, `StashMovementId`, `LinkedDiaryEntryId`, `StashOwnerId` | Strongly typed IDs | `StashOwnerId.Local` is the current offline owner. |
| `StashName` | Human-readable stash name | Trimmed, must not be blank. |
| `StashQuantity` | Amount + unit (`Gram`, `Milliliter`, `Fraction`) | Amount must be finite. Arithmetic only works on matching units. |

### `StashDefinition`

`StashDefinition` is the container metadata:

- `id`: persisted identifier
- `ownerId`: current stash owner
- `name`: unique per owner at the database level
- `createdAt`
- `ordering`: non-negative display order

Current-state invariant: `ordering >= 0`, and Room enforces a unique `(ownerId, name)` pair.

### `StashItem`

`StashItem` is the current inventory row:

- `stashId`: owning stash
- `snapshot`: immutable food snapshot
- `quantity`: current remaining amount in stash units
- `createdAt`

Snapshot variants:

| Snapshot type | Backing source | Notes |
| --- | --- | --- |
| `RawProductSnapshot` | Product catalog or returned diary product | Keeps product metadata (`productId`, brand, barcode, source, nutrition, weights). |
| `AnonymousDishSnapshot` | Recipe snapshot or returned diary recipe | Stores a frozen recipe-like dish with `totalWeight` and `totalAmount`. |

### `StashMovement`

`StashMovement` is the append-only audit trail for stash changes:

- `stashId`, `itemId`
- `operation`
- `quantityChange`
- `linkedDiaryEntryId`
- `note`
- `createdAt`

Current operations:

- `Purchase`
- `CreateSnapshot`
- `DirectConsume`
- `IngredientSubtract`
- `ReturnToStash`
- `ManualAdjust`
- `AutoReversalOnEdit`
- `AutoReversalOnDelete`

Sign convention:

- Positive `quantityChange` adds inventory.
- Negative `quantityChange` removes inventory.

### Immutability and lifecycle constraints

- `RawProductSnapshot.from(product)` and `AnonymousDishSnapshot.from(recipe, ...)` copy data at write
  time. Stored snapshots do not track later catalog edits.
- `StashItem.snapshot` should be treated as immutable historical context, not a live catalog pointer.
- `StashMovement` rows are append-only; reversals write new movements instead of mutating old ones.
- `observeStashContents()` hides rows where `quantity <= 0`, but some zero-quantity rows are retained
  intentionally so linked diary reversals can rebuild metadata.
- Raw product items with a real `productId` can stay at zero quantity. Anonymous dish items and
  synthetic product rows may be deleted when they reach zero.

## Primary use cases

### Add product to stash (`AddProductToStashUseCase.add`)

- **Input:** `productId`, positive `StashQuantity`, optional `stashId`
- **Output:** `Result<AddProductToStashResult, AddProductToStashError>`
- **Writes:** `StashItem` with `RawProductSnapshot`; `Purchase` movement
- **Invariants:**
  - Product must exist.
  - Quantity must be positive and supported by the product (`grams` for solids, `milliliters` for liquids).
  - If `stashId` is omitted and no stashes exist, a default stash named `Stash` is created.
  - Multiple stashes require an explicit selection.

### Create recipe snapshot (`CreateAnonymousDishSnapshotUseCase.create`)

- **Input:** `recipeId`, optional `stashId`, positive `totalAmount`, positive `servings`
- **Output:** `Result<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError>`
- **Writes:** `StashItem` with `AnonymousDishSnapshot`; `CreateSnapshot` movement
- **Invariants:**
  - Recipe must exist.
  - Fraction-based amounts scale `recipe.totalWeight` by produced servings.
  - Weight-based amounts keep `totalWeight == totalAmount.amount`.
  - Snapshot data is frozen at creation time.

### Batch add / shopping session (`StartShoppingSessionUseCase`, `AddToShoppingSessionUseCase`, `ConfirmShoppingSessionUseCase`)

- **Input:** existing `stashId`, per-item `productId` + positive quantity
- **Output:** in-memory `ShoppingSession` during drafting, then `Result<List<StashItemId>, ConfirmShoppingSessionError>` on confirm
- **Writes:** none during draft; on confirm each session item becomes a `StashItem` plus `Purchase` movement
- **Invariants:**
  - `StartShoppingSessionUseCase` only starts for an existing stash.
  - `AddToShoppingSessionUseCase` validates product existence and quantity unit but only mutates the
    session value object.
  - `ConfirmShoppingSessionUseCase` persists all rows in one transaction and leaves the diary untouched.
  - Rollback tests verify partial inserts and movement writes are not left behind.

### Consume from stash (`ConsumeFromStashUseCase.consume`)

- **Input:** `itemId`, `mealId`, positive `amountEaten`, optional `dateOverride`
- **Output:** `Result<FoodDiaryEntryId, ConsumeFromStashError>`
- **Writes:** diary entry, updated/deleted `StashItem`, `DirectConsume` movement linked to the new diary entry
- **Invariants:**
  - Item and meal must exist.
  - Raw products consume only in the stored stash unit.
  - Anonymous dishes may consume by weight even when the stored quantity is `Fraction`; the use case
    converts the requested weight back into a fraction of the original batch.
  - Weight-equivalent consumption requires `snapshot.totalWeight`.
  - Product-backed raw items may remain as zero rows for later reversal; anonymous dish rows are deleted
    when empty.

### Return leftovers to stash (`ReturnPartialMealToStashUseCase.returnToStash`)

- **Input:** `entryId`, positive `quantityToReturn`, optional `mealId`, `date`, and `stashId`
- **Output:** `Result<ReturnPartialMealToStashResult, ReturnPartialMealToStashError>`
- **Writes:** new `StashItem`, `ReturnToStash` movement linked to the diary entry, updated diary entry measurement/date/meal
- **Invariants:**
  - Entry must exist.
  - Returned quantity unit must match the diary entry weight unit.
  - Returned quantity must be smaller than the consumed amount; the diary entry must keep a positive
    remainder.
  - The use case reuses the original linked stash snapshot when possible so returned leftovers keep the
    original product/recipe metadata.
  - Diary measurement is reduced proportionally, preserving serving/package semantics instead of forcing
    a gram-only update.

### Ingredient subtract while logging a recipe (`AssessRecipeStashAvailabilityUseCase.assess`, `LogRecipeToMealWithStashSubtractionUseCase.log`)

- **Input:** `recipeId`, diary `Measurement`, `mealId`, `date`, subtraction mode (`Auto`, `Partial`, `Skip`)
- **Output:** `Result<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError>`
- **Writes:** diary recipe entry plus zero or more `IngredientSubtract` movements and item quantity updates
- **Invariants:**
  - Availability is computed only from visible raw-product stash items that still have a `productId`.
  - Nested recipes are unpacked into product requirements before allocation.
  - Candidate items are allocated oldest-first (`createdAt` ascending) within matching quantity units.
  - `Auto` subtracts only when all ingredients are available; `Partial` subtracts what is available;
    `Skip` logs the diary entry without touching stash.

### Reversal and rebalance (`RestoreLinkedDiaryEntryStashUseCase`)

- **Input:** original diary entry plus either updated measurement (`rebalanceEditedEntry`) or delete intent (`restoreAll`)
- **Output:** `Result<Unit, RestoreLinkedDiaryEntryStashError>`
- **Writes:** updated/recreated/deleted stash items plus `AutoReversalOnEdit` or `AutoReversalOnDelete` movements
- **Invariants:**
  - Movements are loaded by `LinkedDiaryEntryId`.
  - Edit rebalances scale the net quantity change per item instead of replaying every previous movement.
  - Delete reversals replay the whole linked movement set.
  - Positive reversal quantities may recreate a deleted item from the diary entry snapshot.
  - Negative reversal quantities fail if the current stash row no longer has enough quantity to remove.

## Persistence guide

### Room schema

`FoodYouDatabase` version `34` includes the stash schema and converters:

- `StashQuantityUnitConverter`
- `StashMovementOperationTypeConverter`
- `StashSnapshotTypeConverter`

Tables:

| Table | Purpose | Current schema details |
| --- | --- | --- |
| `StashDefinition` | Stash containers | PK `id`; indices on `ownerId` and unique `(ownerId, name)`; sorted by `ordering`, `createdAtEpochSeconds`, `id`. |
| `StashItem` | Current stash rows | FK `stashId -> StashDefinition(id)` with `ON DELETE CASCADE`; denormalized snapshot columns for both snapshot types; `observeItems()` filters `quantity > 0`. |
| `StashMovement` | Audit ledger | FK only to `StashDefinition`; indices on `stashId`, `itemId`, `linkedDiaryEntryId`; `itemId` is intentionally not a foreign key so history survives item deletion/recreation. |

### DAO query patterns

```sql
-- Visible stashes for one owner
SELECT *
FROM StashDefinition
WHERE ownerId = :ownerId
ORDER BY ordering ASC, createdAtEpochSeconds ASC, id ASC;

-- Visible contents for one stash
SELECT *
FROM StashItem
WHERE stashId = :stashId
  AND quantity > 0
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

- Converts `LocalDateTime` to epoch seconds and back
- Maps `RawProductSnapshot` / `AnonymousDishSnapshot` to the flat `StashItemEntity` schema
- Uses `upsertStashItem()` for item updates
- Wraps inserts/deletes in `TransactionProvider.withTransaction()`

Multi-write use cases (`consume`, `confirm shopping`, `return`, recipe logging, diary reversals) also
open an outer transaction so diary writes and stash writes commit together.

### Migration pattern

Current migrations:

- `StashCoreMigration` (`32 -> 33`) creates the three stash tables and indices
- `StashMovementNoteMigration` (`33 -> 34`) adds the nullable `note` column to `StashMovement`

Follow the existing pattern for future changes:

1. Add an explicit `Migration` object in `app.infrastructure.room.migration`.
2. Register it in `FoodYouDatabase.migrations`.
3. Add a reusable abstract migration test in `commonTest`.
4. Add a concrete Android instrumented wrapper in `androidInstrumentedTest`.

## Testing guide

### Unit tests (domain logic, `commonTest`)

Use fake repositories and fixed collaborators:

- `StashUseCaseTestDoubles.kt`
- `FakeStashRepository`
- `FakeFoodDiaryEntryRepository`
- `FakeTransactionProvider`
- `SnapshottingFakeTransactionProvider`
- `FixedDateProvider`
- `NoOpLogger`

Representative tests:

- `ConsumeFromStashUseCaseTest`
- `ManageStashItemsUseCaseTest`
- `CreateAnonymousDishSnapshotUseCaseTest`
- `ShoppingSessionUseCaseTest`
- `LogRecipeToMealWithStashSubtractionUseCaseTest`

Patterns to keep:

- Prefer AAA structure with concrete domain examples.
- Verify both state (`allItems()`, `allEntries()`) and ledger output (`allMovements()`).
- Use `SnapshottingFakeTransactionProvider` when rollback behavior matters.

### Integration tests (cross-boundary logic, `commonTest`)

Representative test:

- `StashDiaryConsistencyIntegrationTest`

This pattern exercises multiple use cases against shared fake repositories to verify that diary-linked
stash state remains consistent across add/consume/edit flows.

### UI state and view-model tests (current stash UI coverage, `commonTest`)

Representative tests:

- `ConsumeStashItemViewModelTest`
- `HomeStashQuickAddViewModelTest`
- `ShoppingSessionViewModelTest`
- `StashBrowserActionsTest`
- `StashManagementStateTest`

Current state: most stash presentation logic is covered in common tests, not device-side UI tests.

### Instrumented tests (`androidInstrumentedTest`)

Representative tests:

- `StashCoreMigrationTest`
- `StashMovementNoteMigrationTest`

Current state: stash-specific instrumented coverage is migration-focused. There are no stash Compose UI
instrumented tests yet, so migration validation is the primary device-level guardrail.

## Integration checklist

- [x] **Diary create flow:** `AddEntryViewModel` uses `AssessRecipeStashAvailabilityUseCase` and
  `LogRecipeToMealWithStashSubtractionUseCase` for stash-aware recipe logging.
- [x] **Diary update flow:** `UpdateEntryScreen` and `UpdateFoodDiaryEntryViewModel` can return
  leftovers to stash through `ReturnPartialMealToStashUseCase`.
- [x] **Diary edit/delete consistency:** `UpdateFoodDiaryEntryUseCase` rebalances linked stash
  movements; `DeleteFoodDiaryEntryUseCase` restores them on delete.
- [x] **Direct consume flow:** `ConsumeStashItemScreen/ViewModel` writes a diary entry from a stash row.
- [x] **Food catalog integration:** `ProductRepository` and `RecipeRepository` provide immutable
  snapshots and recipe ingredient data, but there is currently no direct stash button on product or
  recipe edit screens; stash entry points are home quick-add/snapshot and diary recipe logging.
- [x] **Home integration:** `HomeStashCardViewModel`, `HomeStashQuickAddViewModel`,
  `HomeStashRecipeSnapshotViewModel`, and `FoodYouAppNavHost` expose browse/add/consume flows from the
  home surface.
- [x] **Settings integration:** `SettingsScreen` exposes `StashSettingsListItem`, and navigation routes
  into `StashManagementScreen`.
- [x] **Home personalization:** `HomePersonalizationViewModel` shows or hides the stash card based on
  both settings and whether any stash exists.

## Example walkthrough: consume from stash

1. `FoodYouAppNavHost` routes from `HomeScreen` or `StashBrowserScreen` to
   `ConsumeStashItemScreen(itemId)`.
2. `ConsumeStashItemScreen` binds user input to `ConsumeStashItemViewModel`.
3. `ConsumeStashItemViewModel.consume()` parses the entered amount into `StashQuantity`, resolves meal
   and date, and calls `ConsumeFromStashUseCase.consume(...)`.
4. `ConsumeFromStashUseCase` opens a transaction, loads the stash item and meal, computes a
   `ConsumptionPlan`, and inserts the linked diary entry.
5. The same transaction updates or deletes the stash item and appends a `DirectConsume` movement with
   `linkedDiaryEntryId = LinkedDiaryEntryId(entryId.value)`.
6. `RoomStashRepository` maps domain models to `StashItemEntity` / `StashMovementEntity`, delegates to
   the DAOs, and Room notifies flows. `observeStashContents()` hides any zero-quantity restore rows, so
   the browser/home UI refreshes automatically.

## Developer notes

- Prefer extending stash through a new use case before adding new direct repository calls to UI code.
- Preserve the ledger model: new behavior should usually append a `StashMovement`, not mutate old
  history.
- If you add a new snapshot or movement concept, update the Room converters, schema migration, and test
  doubles in the same change.
