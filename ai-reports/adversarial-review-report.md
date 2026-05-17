# Adversarial Review Report

**Scope:** Commits `e7e06a86` (refactor: remove measurement fields) and `de0b36ba` (docs: rewrite stash onboarding, add evidence page) on branch `fix/food-stash-fixes`, PR #17, Issue #18.

**Date:** 2026-05-17

---

## Summary

The review covered 8 categories. The refactor is **well-executed**: it removes two unreliable fields (`measurement`, `rawMeasurement`) from `StashItem` and `StashMovement`, consolidates `scaleToQuantityOrFallback` into a single public extension, and adds proper documentation (ADR-0001, onboarding page, evidence page). The main concerns are around the `Fraction→Serving` semantic mapping in `toMeasurement()` and missing test coverage for the new extensions.

---

## Findings by Severity

### Notable (2)

| ID | Category | Title | File | Action |
|----|----------|-------|------|--------|
| ar2-1 | Data Integrity | `mergeWith` falls back to `toMeasurement()` for mixed types, mapping Fraction→Serving | `app/src/commonMain/.../stash/domain/entity/ShoppingSession.kt:91-97` | Consider adding a `toMeasurementOrNull()` that returns null for Fraction and handles the fallback explicitly, or document the semantic gap. |
| ar7-1 | Documentation & Tests | No unit tests for `toMeasurement()` or `scaleToQuantityOrFallback()` | `app/src/commonTest/.../stash/domain/entity/StashQuantityTest.kt` | Add tests for both extensions, especially Fraction→Serving mapping and zero-quantity fallback. |

### Low (3)

| ID | Category | Title | File | Action |
|----|----------|-------|------|--------|
| ar1-4 | Architecture | `toMeasurement()` maps Fraction→Serving, a semantic mismatch | `app/src/commonMain/.../stash/domain/entity/StashQuantity.kt:48-53` | Consider renaming to `toDiaryMeasurement()` or adding a conversion parameter to make the intent explicit. |
| ar2-2 | Data Integrity | `ConfirmShoppingSessionUseCase` doesn't merge same-product items | `app/src/commonMain/.../stash/domain/usecase/ConfirmShoppingSessionUseCase.kt:59-80` | Currently safe because `ShoppingSession.add()` merges, but add a comment or assertion documenting this dependency. |
| ar3-1 | Error Handling | `scaleToQuantityOrFallback` doesn't preserve measurement type for zero-quantity items | `app/src/commonMain/.../stash/domain/entity/StashQuantity.kt:55-65` | When `previousQuantity.amount <= 0.0`, falls back to `toMeasurement()` which may change type. Document the edge case or preserve the previous measurement type. |
| ar7-2 | Documentation & Tests | No test for `anonymousDishPlan` weight-based consumption path | `app/src/commonTest/.../stash/domain/usecase/` | Add integration test for Fraction→weight cross-unit consumption. |

### Informational (7)

| ID | Category | Title | Notes |
|----|----------|-------|-------|
| ar1-3 | Architecture | Evidence page script-runs have abbreviated paths | Steps 6–8 in `guidelines/index.html` use `...`; not verifiable per EB-01 |
| ar2-3 | Data Integrity | Evidence page script-runs not actually executed | Same as ar1-3 but from data-integrity angle |
| ar3-2 | Error Handling | `anonymousDishPlan` divides by `totalWeight` with distant guard | Guard at line 112, division at 199 — correct but not co-located |
| ar3-3 | Error Handling | `StashQuantity` allows negative amounts | Valid for movements, but no type-level distinction for item vs. movement quantities |
| ar7-3 | Documentation | ADR-0001 is well-written | Positive note — clearly documents trade-off |
| ar8-1 | Dependency & Compatibility | No stale references to removed fields | Clean removal — full codebase search confirms |
| ar8-2 | Dependency & Compatibility | Schema version regression 36→34 needs main-branch coordination | Accepted risk since branch never shipped |

---

## Removed During Review (Accepted Risks)

| ID | Severity | Title | Reason |
|----|----------|-------|--------|
| ar1-1 | Notable | DB version downgrade 36→34 could crash | Feature branch never shipped; main is still at 34 |
| ar1-2 | Low | StashMovement lost rawMeasurement audit trail | Explicitly accepted per ADR-0001 |
| ar4-1 | Informational | getItem/getLinkedDiaryEntryMovements don't enforce owner | Single-user local app; removed as noise |
| ar5-1 | Informational | StashMovement logs lose measurement type context | Accepted per ADR-0001 |
| ar6-0 | None | Performance — no findings | — |

---

## Recommendations (Priority Order)

1. **Add tests for `toMeasurement()` and `scaleToQuantityOrFallback()`** — These are the exact functions where semantic mismatches exist. Test coverage is the highest-value fix.
2. **Consider renaming `toMeasurement()` → `toDiaryMeasurement()`** — Makes the conversion intent explicit (Fraction→Serving is for diary entry creation, not general-purpose).
3. **Fix or document the evidence page script-runs** — Either re-run the actual commands and capture verbatim output, or replace `<pre class="script-run">` with regular `<pre>` blocks.
4. **Add a comment to `ConfirmShoppingSessionUseCase`** about the 1:1 mapping dependency on `ShoppingSession.add()` merging same-product items.
5. **Consider a `toMeasurementOrNull()` for Fraction** — Returns null for Fraction instead of mapping to Serving, forcing callers to handle the ambiguity explicitly.