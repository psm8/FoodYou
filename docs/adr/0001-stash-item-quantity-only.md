# StashItem stores only StashQuantity — no Measurement fields

Stash items carry a single `quantity: StashQuantity` (grams, milliliters, or fraction) and no `Measurement` or `rawMeasurement` fields. The user's original measurement input (e.g., "3 packages" or "2 servings") is converted to a canonical `StashQuantity` at the use-case boundary and discarded.

Stash movements also carry no `rawMeasurement`. The `quantityChange` and `operation` on each movement provide sufficient audit information.

This was an explicit trade-off: we sacrifice the ability to reconstruct the user's original input representation (e.g., "3 packages" displayed as "450g") for simplicity and correctness. The previous approach stored both `measurement` and `rawMeasurement` on `StashItem`, but different add paths populated them inconsistently, making both fields unreliable. Since neither field was ever read by any consumer, removing them eliminates the inconsistency bugs with no functional loss.

Status: accepted