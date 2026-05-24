package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
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
    val item: StashEntry,
    val measurement: StashMeasurement,
)

data class RecipeIngredientStashAvailability(
    val foodId: FoodId,
    val foodName: String,
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
    override val foodId: FoodId.Product,
    override val foodName: String,
    override val measurement: StashMeasurement,
) : IngredientRequirement

internal data class RecipeRequirement(
    override val foodId: FoodId.Recipe,
    override val foodName: String,
    override val measurement: StashMeasurement,
) : IngredientRequirement

internal sealed interface IngredientRequirement {
    val foodId: FoodId
    val foodName: String
    val measurement: StashMeasurement
}

internal fun Recipe.toProductRequirements(measurement: Measurement): List<ProductRequirement> {
    val consumedWeight = weight(measurement)
    return toProductRequirements(consumedWeight)
}

private fun Recipe.toProductRequirements(consumedWeight: Double): List<ProductRequirement> =
    unpack(consumedWeight)
        .flatMap(RecipeIngredient::toProductRequirements)
        .groupBy { requirement -> requirement.foodId to requirement.measurement.type }
        .map { (key, requirements) ->
            val measurement = requirements.fold(requirements.first().measurement.zero()) { acc, requirement ->
                acc + requirement.measurement
            }
            ProductRequirement(
                foodId = key.first,
                foodName = requirements.first().foodName,
                measurement = measurement,
            )
        }

private fun RecipeIngredient.toProductRequirements(): List<ProductRequirement> {
    val ingredientWeight = weight ?: return emptyList()
    return when (val ingredientFood = food) {
        is Product ->
            listOf(
                ProductRequirement(
                    foodId = ingredientFood.id,
                    foodName = ingredientFood.headline,
                    measurement = ingredientFood.toStashMeasurement(ingredientWeight),
                )
            )

        is Recipe -> ingredientFood.toProductRequirements(ingredientWeight)
    }
}

internal fun buildRecipeStashAvailability(
    recipe: Recipe,
    measurement: Measurement,
    candidateItems: List<StashEntry>,
): RecipeStashAvailability {
    val itemsByProductId =
        candidateItems
            .mapNotNull { item ->
                when (val foodRef = item.foodRef) {
                    is StashFoodRef.Product -> foodRef.productId to item
                    is StashFoodRef.Recipe -> null
                }
            }.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
    val itemsByRecipeId =
        candidateItems
            .mapNotNull { item ->
                when (val foodRef = item.foodRef) {
                    is StashFoodRef.Product -> null
                    is StashFoodRef.Recipe -> foodRef.recipeId to item
                }
            }.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )

    val ingredientAvailabilities =
        recipe.unpack(recipe.weight(measurement)).flatMap { ingredient ->
            ingredient.toAvailabilities(
                itemsByProductId = itemsByProductId,
                itemsByRecipeId = itemsByRecipeId,
            )
        }

    return RecipeStashAvailability(ingredientAvailabilities)
}

private fun RecipeIngredient.toAvailabilities(
    itemsByProductId: Map<FoodId.Product, List<StashEntry>>,
    itemsByRecipeId: Map<FoodId.Recipe, List<StashEntry>>,
): List<RecipeIngredientStashAvailability> {
    val ingredientWeight = weight ?: return emptyList()

    return when (val ingredientFood = food) {
        is Product ->
            listOf(
                ProductRequirement(
                    foodId = ingredientFood.id,
                    foodName = ingredientFood.headline,
                    measurement = ingredientFood.toStashMeasurement(ingredientWeight),
                ).toAvailability(itemsByProductId[ingredientFood.id].orEmpty())
            )

        is Recipe -> {
            val requiredMeasurement = StashMeasurement(measurement)
            val matchingRecipeItems =
                itemsByRecipeId[ingredientFood.id]
                    .orEmpty()
                    .compatibleWith(requiredMeasurement)

            if (matchingRecipeItems.isNotEmpty()) {
                listOf(
                    RecipeRequirement(
                        foodId = ingredientFood.id,
                        foodName = ingredientFood.headline,
                        measurement = requiredMeasurement,
                    ).toAvailability(matchingRecipeItems)
                )
            } else {
                ingredientFood.toProductRequirements(ingredientWeight).map { requirement ->
                    requirement.toAvailability(itemsByProductId[requirement.foodId].orEmpty())
                }
            }
        }
    }
}

private fun IngredientRequirement.toAvailability(
    candidateItems: List<StashEntry>,
): RecipeIngredientStashAvailability {
    var remainingRawValue = measurement.measurement.rawValue
    val allocations = mutableListOf<IngredientStashAllocation>()

    candidateItems
        .filter { item -> item.measurement.type == measurement.type }
        .sortedBy(StashEntry::createdAt)
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
        foodId = foodId,
        foodName = foodName,
        requiredMeasurement = measurement,
        allocations = allocations,
    )
}

private fun List<StashEntry>.compatibleWith(requiredMeasurement: StashMeasurement): List<StashEntry> =
    filter { item -> item.measurement.type == requiredMeasurement.type }

private fun Product.toStashMeasurement(weight: Double): StashMeasurement =
    if (isLiquid) {
        StashMeasurement.milliliters(weight)
    } else {
        StashMeasurement.grams(weight)
    }

private fun StashMeasurement.zero(): StashMeasurement =
    StashMeasurement(Measurement.from(type, 0.0))

private const val EPSILON = 0.000001
