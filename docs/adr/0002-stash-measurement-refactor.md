# StashMeasurement Refactor — Replace StashQuantity with a Value Type Wrapping Measurement

## Status

**Accepted** — Implemented across issues #23–#29 (Slices 1–7)

Supersedes: [ADR-0001](./0001-stash-item-quantity-only.md) (which proposed the general concept; this ADR refines and documents the actual implementation)

## Context

### The Problem

The stash module originally used a `StashQuantity` type-safe wrapper that enforced weight-only storage (grams/milliliters). This design had two critical flaws:

1. **Lost user intent**: When a user added "2 servings" to the stash, the system converted it to weight (e.g., "300g") and discarded the original measurement type. This made the UI unable to reflect what the user entered.

2. **Parallel enum maintenance**: To support "quantity-only" storage while remembering the type for UI display, the system maintained a `StashQuantityUnit` enum parallel to the common `MeasurementType`. Any new measurement type (Ounce, FluidOunce) required changes in two places.

3. **Type-unsafe cross-domain coupling**: `StashQuantity.toMeasurement()` performed an implicit lossy conversion at use-case boundaries, making the direction of data flow ambiguous.

### The Opportunity

The diary module already used `Measurement` (a sealed interface with 6 types: Gram, Milliliter, Ounce, FluidOunce, Package, Serving) as its core representation. By adopting the same type in the stash domain, we could:

- Preserve the user's chosen measurement type end-to-end
- Eliminate the parallel enum
- Unify the measurement model across modules
- Make cross-module operations (dish consumption from stash) type-safe and explicit

## Decision

**Replace `StashQuantity` with `StashMeasurement`** — a thin value type that wraps `Measurement` and provides same-type arithmetic (`+`, `-`, `negate()`, `normalize()`).

### Implementation Details

**StashMeasurement.kt:**
```kotlin
data class StashMeasurement(val measurement: Measurement) {
    init {
        require(measurement.rawValue.isFinite()) { "StashMeasurement value must be finite" }
    }

    val type: MeasurementType get() = measurement.type

    operator fun plus(other: StashMeasurement): StashMeasurement {
        requireSameType(other)
        return StashMeasurement(Measurement.from(type, measurement.rawValue + other.measurement.rawValue))
    }

    operator fun minus(other: StashMeasurement): StashMeasurement {
        requireSameType(other)
        return StashMeasurement(Measurement.from(type, measurement.rawValue - other.measurement.rawValue))
    }

    fun negate(): StashMeasurement = 
        StashMeasurement(Measurement.from(type, -measurement.rawValue))

    fun normalize(): StashMeasurement {
        val rawValue = measurement.rawValue
        return if (rawValue > -1e-9 && rawValue < 1e-9) {
            StashMeasurement(Measurement.from(type, 0.0))
        } else {
            this
        }
    }

    private fun requireSameType(other: StashMeasurement) {
        require(type == other.type) {
            "Cannot combine StashMeasurements with different types: $type vs ${other.type}"
        }
    }

    companion object {
        fun grams(amount: Double): StashMeasurement = StashMeasurement(Measurement.Gram(amount))
        fun milliliters(amount: Double): StashMeasurement = StashMeasurement(Measurement.Milliliter(amount))
        fun servings(amount: Double): StashMeasurement = StashMeasurement(Measurement.Serving(amount))
        fun packages(amount: Double): StashMeasurement = StashMeasurement(Measurement.Package(amount))
        fun ounces(amount: Double): StashMeasurement = StashMeasurement(Measurement.Ounce(amount))
        fun fluidOunces(amount: Double): StashMeasurement = StashMeasurement(Measurement.FluidOunce(amount))
    }
}
```

### Key Changes

1. **StashItem** now stores `measurement: StashMeasurement` directly — no conversion to weight
2. **StashMovement** records `measurementChange: StashMeasurement` for audit — same type, no separate rawMeasurement
3. **Merge rule**: When adding a product to the stash, items merge by `(productId, measurementType)`:
   - Same product + same measurement type → quantities combine (e.g., `Gram(300)` + `Gram(200)` → `Gram(500)`)
   - Same product + different measurement type → separate stash items (e.g., `Serving(2)` and `Gram(300)` coexist)
4. **Cross-unit consumption (dishes only)**: An `AnonymousDishSnapshot` stored in servings can be consumed in weight (Gram/Milliliter), with proportional conversion. Raw products support same-type consumption only.
5. **Deleted files**:
   - `StashQuantity.kt` — replaced by StashMeasurement
   - `StashQuantityUnit.kt` — merged into MeasurementType
   - `ProductStashQuantity.kt` — replaced by ProductStashMeasurement
   - `StashQuantityAdjustment.kt` — replaced by StashMeasurementAdjustment
6. **Deleted test files**:
   - `StashQuantityTest.kt`
   - `StashQuantityDisplayTest.kt` (old; new StashMeasurementDisplayTest added)

## Consequences

### Positive

1. **User intent preserved**: The measurement type the user entered is stored and displayed end-to-end. "2 servings" stays as servings, not converted to weight.

2. **Single measurement model**: Stash and diary now share the same `Measurement` type. New measurement types (future: "bundles", "liters") only need implementation in one place.

3. **Type-safe arithmetic**: `StashMeasurement` enforces same-type operations at compile time. Attempting `Gram(300) + Serving(2)` fails immediately with a clear error message, not silently during domain logic.

4. **Explicit cross-module semantics**: Consuming a dish from stash (weight-unit conversion) is now explicit: `CreateAnonymousDishSnapshot` documents when and how proportional conversion occurs. Raw products never cross units.

5. **Simpler audit trail**: `StashMovement` records only what changed (`measurementChange`) and why (`operation`). No redundant `rawMeasurement` field to keep in sync.

6. **Reduced cognitive load**: Developers read one measurement type (`Measurement`) everywhere, not two (`Measurement` + `StashQuantityUnit`).

### Negative (Trade-offs)

1. **Different-type items don't auto-convert**: If a user adds "2 servings" and later "500g" of the same product, the stash now shows two separate items. The previous weight-only system would merge them (after conversion). This is intentional: it respects user choice and avoids silent conversions, but requires UI/UX clarity about why separate items exist.

2. **Increased stash size in memory/DB**: Stashes with mixed-unit products (e.g., "3 servings + 500g" of chicken) now store two items instead of one merged item. For typical user stashes (dozens to hundreds of items), the impact is negligible.

3. **Cross-module consumption is documented, not automatic**: The stash module now requires the food module (for dish consumption conversion). This is the correct dependency direction (stash is lower-level infrastructure consuming food services), but it means weight conversion logic is scattered across modules, not centralized.

## Alternatives Considered

### 1. **Keep StashQuantity with a Type Parameter**
```kotlin
sealed interface StashQuantityValue
data class StashQuantity<T : StashQuantityValue>(val amount: Double, val type: T)
```

**Rejected**: Generic type parameters add complexity without addressing the core problem (unit preservation). StashQuantity would still need to convert to/from Measurement at boundaries, leaving the ambiguity.

### 2. **Use Sealed Classes for Measurement Variants**
```kotlin
sealed class StashMeasurement {
    data class Grams(val amount: Double) : StashMeasurement()
    data class Servings(val amount: Double) : StashMeasurement()
    // ...
}
```

**Rejected**: Functionally equivalent to `Measurement`, but duplicates the entire sealed hierarchy. Increases maintenance burden and requires synchronization whenever diary/common adds a new type.

### 3. **Add Arithmetic to Measurement Directly**
```kotlin
// Add plus/minus/negate to Measurement sealed interface
data class Gram(val amount: Double) : Measurement {
    operator fun plus(other: Gram) = Gram(amount + other.amount)
}
```

**Rejected**: Pollutes the common domain type with stash-specific concerns. `Measurement` is used by many modules (diary, recipes, food); not all of them need or want arithmetic. The responsibility for safe arithmetic belongs with the stash module.

### 4. **Weight-Only Stash (Previous Approach)**
Store all items in Gram/Milliliter, discard original unit type.

**Rejected**: Loses user intent and forces a parallel `StashQuantityUnit` enum. Superseded by this decision.

## Related Issues and ADRs

- **Issue #23**: Introduce StashMeasurement value type
- **Issue #24**: Migrate StashItem to use StashMeasurement
- **Issue #25**: Update merge rule to (productId, measurementType)
- **Issue #26**: Update use cases (AddProductToStash, AdjustQuantity, etc.)
- **Issue #27**: Update UI layer and StashQuantityText
- **Issue #28**: Database migration and schema alignment
- **Issue #29** (Slice 7): Documentation, ADRs, and final cleanup
- **[ADR-0001](./0001-stash-item-quantity-only.md)**: Proposed the original "quantity-only" concept; superseded by this more concrete decision

## References

- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/stash/domain/entity/StashMeasurement.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/stash/domain/entity/StashItem.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/stash/domain/usecase/AddProductToStashUseCase.kt` (merge rule implementation)
- `CONTEXT.md` (stash module language and design)
