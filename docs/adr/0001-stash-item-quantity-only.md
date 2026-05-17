# StashItem stores only StashQuantity — no Measurement fields

## Status

**Superseded** by [ADR-0002](./0002-stash-measurement-refactor.md)

This ADR proposed the original concept of "quantity-only" storage and rejection of measurement duplication. However, it did not specify the implementation type. ADR-0002 implements the concept using `StashMeasurement` (a value type wrapping `Measurement`) rather than the weight-only `StashQuantity`, preserving the user's original measurement type end-to-end.

## Original Decision

Stash items carry a single `quantity: StashQuantity` (grams, milliliters, or fraction) and no `Measurement` or `rawMeasurement` fields. The user's original measurement input (e.g., "3 packages" or "2 servings") is converted to a canonical `StashQuantity` at the use-case boundary and discarded.

Stash movements also carry no `rawMeasurement`. The `quantityChange` and `operation` on each movement provide sufficient audit information.

This was an explicit trade-off: we sacrifice the ability to reconstruct the user's original input representation (e.g., "3 packages" displayed as "450g") for simplicity and correctness. The previous approach stored both `measurement` and `rawMeasurement` on `StashItem`, but different add paths populated them inconsistently, making both fields unreliable. Since neither field was ever read by any consumer, removing them eliminates the inconsistency bugs with no functional loss.

## Resolution

ADR-0002 refines this decision by preserving the user's original measurement type, not discarding it. The implementation uses `StashMeasurement` (wrapping `Measurement`) instead of weight-only `StashQuantity`, and applies a same-type merge rule: `(productId, measurementType)`. This way, "2 servings" remains "2 servings" instead of being converted to "300g".