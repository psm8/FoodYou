# FoodYou

An Android food tracking app (KMP/Compose Multiplatform) with a diary module and a stash module for managing food inventory.

## Language

**StashItem**: A food item tracked in a stash. Carries a `snapshot` (product or dish metadata) and a `quantity` (the canonical amount in grams, milliliters, or fraction). Does NOT carry a `Measurement` — the canonical `StashQuantity` is the sole source of truth.

**StashMovement**: An audit ledger entry recording how and why a stash item's quantity changed. Carries `quantityChange` (signed) and `operation` (the lifecycle event type).

**StashQuantity**: A canonical unit-and-amount pair (`Gram(450)`, `Milliliter(500)`, `Fraction(2)`). All stash arithmetic uses this.

**Measurement**: A user-facing measurement input (`Package(3)`, `Serving(2)`, `Gram(150)`). Converted to `StashQuantity` at the boundary before entering stash domain logic.

**ShoppingSession**: An in-memory draft of items to be confirmed into stash items. Uses `Measurement` for UI interaction but converts to `StashQuantity` for persistence.

**RawMeasurement**: Removed. Previously stored the user's original `Measurement` input alongside the canonical `StashQuantity`. Eliminated because it was write-only (never consumed) and caused inconsistency bugs across add paths. The canonical `StashQuantity` is sufficient — users add items via `Measurement`, which is immediately converted and discarded.

## Relationships

- A **Measurement** is converted to a **StashQuantity** at the use-case boundary
- A **StashItem** has one **StashQuantity** (canonical amount)
- A **StashMovement** records changes to **StashItem** quantities with an **operation** type
- A **ShoppingSession** confirms into **StashItem**s and **StashMovement**s

## Flagged ambiguities

- "rawMeasurement" vs "measurement" on StashItem — resolved: both removed. The fields were write-only and inconsistently populated. StashItem carries only quantity.
- "rawMeasurement" on StashMovement — removed. Write-only audit data. If needed in future, a migration can re-add it.