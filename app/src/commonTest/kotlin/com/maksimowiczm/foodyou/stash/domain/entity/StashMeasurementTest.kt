package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StashMeasurementTest {

    // --- Arithmetic: same-type operations ---

    @Test
    fun when_adding_grams_then_returns_sum_in_grams() {
        val result = StashMeasurement.grams(250.0) + StashMeasurement.grams(50.0)
        assertEquals(MeasurementType.Gram, result.type)
        assertEquals(300.0, result.measurement.rawValue)
    }

    @Test
    fun when_subtracting_milliliters_then_returns_difference_in_milliliters() {
        val result = StashMeasurement.milliliters(500.0) - StashMeasurement.milliliters(200.0)
        assertEquals(MeasurementType.Milliliter, result.type)
        assertEquals(300.0, result.measurement.rawValue)
    }

    @Test
    fun when_adding_servings_then_returns_sum_in_servings() {
        val result = StashMeasurement.servings(1.0) + StashMeasurement.servings(2.0)
        assertEquals(MeasurementType.Serving, result.type)
        assertEquals(3.0, result.measurement.rawValue)
    }

    @Test
    fun when_adding_packages_then_returns_sum_in_packages() {
        val result = StashMeasurement.packages(1.0) + StashMeasurement.packages(1.5)
        assertEquals(MeasurementType.Package, result.type)
        assertEquals(2.5, result.measurement.rawValue)
    }

    @Test
    fun when_adding_ounces_then_returns_sum_in_ounces() {
        val result = StashMeasurement.ounces(8.0) + StashMeasurement.ounces(4.0)
        assertEquals(MeasurementType.Ounce, result.type)
        assertEquals(12.0, result.measurement.rawValue)
    }

    @Test
    fun when_adding_fluid_ounces_then_returns_sum_in_fluid_ounces() {
        val result = StashMeasurement.fluidOunces(8.0) + StashMeasurement.fluidOunces(8.0)
        assertEquals(MeasurementType.FluidOunce, result.type)
        assertEquals(16.0, result.measurement.rawValue)
    }

    // --- Type mismatch: cross-type arithmetic throws ---

    @Test
    fun when_adding_grams_to_milliliters_then_throws() {
        assertFailsWith<IllegalArgumentException> {
            StashMeasurement.grams(100.0) + StashMeasurement.milliliters(100.0)
        }
    }

    @Test
    fun when_subtracting_servings_from_grams_then_throws() {
        assertFailsWith<IllegalArgumentException> {
            StashMeasurement.grams(100.0) - StashMeasurement.servings(1.0)
        }
    }

    // --- Factory methods ---

    @Test
    fun factory_grams_produces_gram_measurement() {
        val sm = StashMeasurement.grams(42.0)
        assertEquals(Measurement.Gram(42.0), sm.measurement)
    }

    @Test
    fun factory_milliliters_produces_milliliter_measurement() {
        val sm = StashMeasurement.milliliters(250.0)
        assertEquals(Measurement.Milliliter(250.0), sm.measurement)
    }

    @Test
    fun factory_servings_produces_serving_measurement() {
        val sm = StashMeasurement.servings(2.0)
        assertEquals(Measurement.Serving(2.0), sm.measurement)
    }

    @Test
    fun factory_packages_produces_package_measurement() {
        val sm = StashMeasurement.packages(3.0)
        assertEquals(Measurement.Package(3.0), sm.measurement)
    }

    @Test
    fun factory_ounces_produces_ounce_measurement() {
        val sm = StashMeasurement.ounces(16.0)
        assertEquals(Measurement.Ounce(16.0), sm.measurement)
    }

    @Test
    fun factory_fluidOunces_produces_fluidOunce_measurement() {
        val sm = StashMeasurement.fluidOunces(8.0)
        assertEquals(Measurement.FluidOunce(8.0), sm.measurement)
    }

    // --- Negate ---

    @Test
    fun negate_returns_negative_measurement() {
        val result = StashMeasurement.grams(100.0).negate()
        assertEquals(MeasurementType.Gram, result.type)
        assertEquals(-100.0, result.measurement.rawValue)
    }

    @Test
    fun negate_of_negative_returns_positive() {
        val result = StashMeasurement.milliliters(-50.0).negate()
        assertEquals(50.0, result.measurement.rawValue)
    }

    // --- Normalize ---

    @Test
    fun normalize_near_zero_normalizes_to_exact_zero() {
        val tiny = StashMeasurement.grams(1e-10)
        val result = tiny.normalize()
        assertEquals(0.0, result.measurement.rawValue)
    }

    @Test
    fun normalize_negative_near_zero_normalizes_to_exact_zero() {
        val tiny = StashMeasurement.grams(-1e-10)
        val result = tiny.normalize()
        assertEquals(0.0, result.measurement.rawValue)
    }

    @Test
    fun normalize_non_zero_stays_as_is() {
        val sm = StashMeasurement.grams(100.0)
        val result = sm.normalize()
        assertEquals(100.0, result.measurement.rawValue)
    }

    // --- Edge cases ---

    @Test
    fun zero_value_is_valid() {
        val sm = StashMeasurement.grams(0.0)
        assertEquals(0.0, sm.measurement.rawValue)
    }

    @Test
    fun negative_value_is_valid() {
        val sm = StashMeasurement.servings(-1.5)
        assertEquals(-1.5, sm.measurement.rawValue)
    }

    @Test
    fun nan_throws_on_construction() {
        assertFailsWith<IllegalArgumentException> {
            StashMeasurement.grams(Double.NaN)
        }
    }

    @Test
    fun positive_infinity_throws_on_construction() {
        assertFailsWith<IllegalArgumentException> {
            StashMeasurement.grams(Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun negative_infinity_throws_on_construction() {
        assertFailsWith<IllegalArgumentException> {
            StashMeasurement.grams(Double.NEGATIVE_INFINITY)
        }
    }

    // --- Same-type enforcement across all types ---

    @Test
    fun same_type_enforcement_packages_vs_servings() {
        assertFailsWith<IllegalArgumentException> {
            StashMeasurement.packages(1.0) + StashMeasurement.servings(1.0)
        }
    }

    @Test
    fun same_type_enforcement_ounces_vs_fluidOunces() {
        assertFailsWith<IllegalArgumentException> {
            StashMeasurement.ounces(1.0) - StashMeasurement.fluidOunces(1.0)
        }
    }

    @Test
    fun subtraction_same_type_succeeds() {
        val result = StashMeasurement.packages(5.0) - StashMeasurement.packages(2.0)
        assertEquals(3.0, result.measurement.rawValue)
    }

    // --- Type property ---

    @Test
    fun type_property_returns_correct_measurement_type() {
        assertEquals(MeasurementType.Gram, StashMeasurement.grams(1.0).type)
        assertEquals(MeasurementType.Milliliter, StashMeasurement.milliliters(1.0).type)
        assertEquals(MeasurementType.Serving, StashMeasurement.servings(1.0).type)
        assertEquals(MeasurementType.Package, StashMeasurement.packages(1.0).type)
        assertEquals(MeasurementType.Ounce, StashMeasurement.ounces(1.0).type)
        assertEquals(MeasurementType.FluidOunce, StashMeasurement.fluidOunces(1.0).type)
    }
}