# Recipe-first stash ingredient resolution

When a recipe ingredient is itself a Recipe, stash matching tries a stash Recipe entry first. Only when no stash Recipe entry exists for that ingredient does it fall back to expanding that ingredient into its product components and matching those against the stash. This resolution is recursive — every nesting level follows the same strategy. When a stash Recipe entry partially satisfies an ingredient, the remaining quantity is unavailable; products are NOT consulted to fill the gap.

## Considered Options

- **Always flatten to products**: Recursively expand all recipe ingredients into `ProductRequirement`s and only match against `StashFoodRef.Product` stash entries (the previous behavior). Rejected because users who batch-cook a sub-recipe and store it in the stash expect it to be consumed when a parent recipe uses it.
- **Partial cascade to products**: When a stash Recipe entry partially satisfies an ingredient, fill the remaining quantity from individual products. Rejected because it creates confusing UX ("I used 1 serving of Pasta Salad from stash and 2 servings worth of flour and eggs?") and adds algorithmic complexity for marginal benefit.
- **Recipe-first with fallback**: The chosen approach. Matches stash Recipe entries first; falls back to product expansion only when no stash Recipe entry exists for that ingredient. Partial recipe matches don't cascade to products.

## Consequences

- Cross-unit consumption (e.g., requesting grams from a serving-based stash recipe entry) uses the same proportional conversion (`totalWeight / totalAmount`) as `ConsumeFromStashUseCase`.
- The `StashMovementOperation.IngredientSubtract` operation is reused for both product and recipe stash entries — the `StashFoodRef` on the item already distinguishes the type.