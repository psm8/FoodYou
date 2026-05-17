package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement

enum class IngredientAvailabilityStatus {
    Available,
    PartiallyAvailable,
    Unavailable,
}

enum class StashSubtractionMode {
    Auto,
    Partial,
    Skip,
}

data class IngredientStashAllocation(
    val item: StashItem,
    val measurement: StashMeasurement,
)

data class RecipeIngredientStashAvailability(
    val productId: FoodId.Product,
    val productName: String,
    val requiredMeasurement: StashMeasurement,
    val allocations: List<IngredientStashAllocation>,
) {
    val availableMeasurement: StashMeasurement =
        allocations.fold(requiredMeasurement.zero()) { acc, allocation -> acc + allocation.measurement }

    val status: IngredientAvailabilityStatus
        get() =
            when {
                availableMeasurement.measurement.rawValue <= EPSILON -> IngredientAvailabilityStatus.Unavailable
                availableMeasurement.measurement.rawValue + EPSILON >= requiredMeasurement.measurement.rawValue ->
                    IngredientAvailabilityStatus.Available

                else -> IngredientAvailabilityStatus.PartiallyAvailable
            }

    private companion object {
        const val EPSILON = 0.000001
    }
}

data class RecipeStashAvailability(
    val ingredients: List<RecipeIngredientStashAvailability>,
) {
    val availableIngredientsCount: Int =
        ingredients.count { it.status == IngredientAvailabilityStatus.Available }

    val partiallyAvailableIngredientsCount: Int =
        ingredients.count { it.status == IngredientAvailabilityStatus.PartiallyAvailable }

    val unavailableIngredientsCount: Int =
        ingredients.count { it.status == IngredientAvailabilityStatus.Unavailable }

    val areAllIngredientsAvailable: Boolean = ingredients.all {
        it.status == IngredientAvailabilityStatus.Available
    }

    val hasAnyAvailableIngredients: Boolean = ingredients.any {
        it.status != IngredientAvailabilityStatus.Unavailable
    }
}

internal data class ProductRequirement(
    val productId: FoodId.Product,
    val productName: String,
    val measurement: StashMeasurement,
)

internal fun Recipe.toProductRequirements(measurement: Measurement): List<ProductRequirement> {
    val consumedWeight = weight(measurement)
    return toProductRequirements(consumedWeight)
}

private fun Recipe.toProductRequirements(consumedWeight: Double): List<ProductRequirement> =
    unpack(consumedWeight)
        .flatMap(RecipeIngredient::toProductRequirements)
        .groupBy { requirement -> requirement.productId to requirement.measurement.type }
        .map { (key, requirements) ->
            val measurement = requirements.fold(requirements.first().measurement.zero()) { acc, requirement ->
                acc + requirement.measurement
            }
            ProductRequirement(
                productId = key.first,
                productName = requirements.first().productName,
                measurement = measurement,
            )
        }

private fun RecipeIngredient.toProductRequirements(): List<ProductRequirement> {
    val ingredientWeight = weight ?: return emptyList()
    return when (val ingredientFood = food) {
        is Product ->
            listOf(
                ProductRequirement(
                    productId = ingredientFood.id,
                    productName = ingredientFood.headline,
                    measurement = ingredientFood.toStashMeasurement(ingredientWeight),
                )
            )

        is Recipe -> ingredientFood.toProductRequirements(ingredientWeight)
    }
}

internal fun buildRecipeStashAvailability(
    recipe: Recipe,
    measurement: Measurement,
    candidateItems: List<StashItem>,
): RecipeStashAvailability {
    val itemsByProductId =
        candidateItems
            .mapNotNull { item ->
                val snapshot = item.snapshot
                if (snapshot is RawProductSnapshot) {
                    snapshot.productId?.let { productId -> productId to item }
                } else {
                    null
                }
            }.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )

    val ingredientAvailabilities =
        recipe.toProductRequirements(measurement).map { requirement ->
            requirement.toAvailability(
                candidateItems = itemsByProductId[requirement.productId].orEmpty(),
            )
        }

    return RecipeStashAvailability(ingredientAvailabilities)
}

private fun ProductRequirement.toAvailability(
    candidateItems: List<StashItem>,
): RecipeIngredientStashAvailability {
    var remainingRawValue = measurement.measurement.rawValue
    val allocations = mutableListOf<IngredientStashAllocation>()

    candidateItems
        .filter { item -> item.measurement.type == measurement.type }
        .sortedBy(StashItem::createdAt)
        .forEach { item ->
            if (remainingRawValue <= EPSILON) {
                return@forEach
            }

            val allocatedRawValue = minOf(item.measurement.measurement.rawValue, remainingRawValue)
            if (allocatedRawValue <= EPSILON) {
                return@forEach
            }

            allocations +=
                IngredientStashAllocation(
                    item = item,
                    measurement = StashMeasurement(Measurement.from(measurement.type, allocatedRawValue)),
                )
            remainingRawValue -= allocatedRawValue
        }

    return RecipeIngredientStashAvailability(
        productId = productId,
        productName = productName,
        requiredMeasurement = measurement,
        allocations = allocations,
    )
}

private fun Product.toStashMeasurement(weight: Double): StashMeasurement =
    if (isLiquid) {
        StashMeasurement.milliliters(weight)
    } else {
        StashMeasurement.grams(weight)
    }

private fun StashMeasurement.zero(): StashMeasurement =
    StashMeasurement(Measurement.from(type, 0.0))

private const val EPSILON = 0.000001

