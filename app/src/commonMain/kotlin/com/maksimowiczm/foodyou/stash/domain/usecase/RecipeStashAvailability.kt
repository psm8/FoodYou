package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
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
    val matchedMeasurement: StashMeasurement = measurement,
)

data class RecipeIngredientStashAvailability(
    val foodId: FoodId,
    val foodName: String,
    val requiredMeasurement: StashMeasurement,
    val allocations: List<IngredientStashAllocation>,
) {
    val availableMeasurement: StashMeasurement =
        allocations.fold(requiredMeasurement.zero()) { acc, allocation -> acc + allocation.matchedMeasurement }

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
    val recipe: Recipe,
) : IngredientRequirement

internal sealed interface IngredientRequirement {
    val foodId: FoodId
    val foodName: String
    val measurement: StashMeasurement
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
                visitedRecipeIds = setOf(recipe.id),
            )
        }

    return RecipeStashAvailability(ingredientAvailabilities)
}

private fun RecipeIngredient.toAvailabilities(
    itemsByProductId: Map<FoodId.Product, List<StashEntry>>,
    itemsByRecipeId: Map<FoodId.Recipe, List<StashEntry>>,
    visitedRecipeIds: Set<FoodId.Recipe>,
): List<RecipeIngredientStashAvailability> {
    val requirements =
        toRequirements(
            itemsByRecipeId = itemsByRecipeId,
            visitedRecipeIds = visitedRecipeIds,
        )
            .groupRequirements()

    return requirements.map { requirement ->
        requirement.toAvailability(requirement.candidateItems(itemsByProductId, itemsByRecipeId))
    }
}

private fun RecipeIngredient.toRequirements(
    itemsByRecipeId: Map<FoodId.Recipe, List<StashEntry>>,
    visitedRecipeIds: Set<FoodId.Recipe>,
): List<IngredientRequirement> {
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

        is Recipe ->
            ingredientFood.toRequirements(
                requiredMeasurement = measurement,
                consumedWeight = ingredientWeight,
                itemsByRecipeId = itemsByRecipeId,
                visitedRecipeIds = visitedRecipeIds,
            )
    }
}

private fun Recipe.toRequirements(
    requiredMeasurement: Measurement,
    consumedWeight: Double,
    itemsByRecipeId: Map<FoodId.Recipe, List<StashEntry>>,
    visitedRecipeIds: Set<FoodId.Recipe>,
): List<IngredientRequirement> {
    val recipeRequirement =
        RecipeRequirement(
            foodId = id,
            foodName = headline,
            measurement = StashMeasurement(requiredMeasurement),
            recipe = this,
        )
    val matchingRecipeItems =
        itemsByRecipeId[id]
            .orEmpty()
            .compatibleWith(recipeRequirement)

    if (matchingRecipeItems.isNotEmpty()) {
        return listOf(recipeRequirement)
    }
    if (id in visitedRecipeIds) {
        return listOf(recipeRequirement)
    }

    return unpack(consumedWeight)
        .flatMap { ingredient ->
            ingredient.toRequirements(
                itemsByRecipeId = itemsByRecipeId,
                visitedRecipeIds = visitedRecipeIds + id,
            )
        }.groupRequirements()
}

private fun List<IngredientRequirement>.groupRequirements(): List<IngredientRequirement> =
    groupBy { requirement -> requirement.foodId to requirement.measurement.type }
        .map { (_, requirements) -> requirements.merge() }

private fun List<IngredientRequirement>.merge(): IngredientRequirement {
    val firstRequirement = first()
    val totalMeasurement =
        fold(firstRequirement.measurement.zero()) { acc, requirement ->
            acc + requirement.measurement
        }

    return when (firstRequirement) {
        is ProductRequirement ->
            firstRequirement.copy(measurement = totalMeasurement)

        is RecipeRequirement ->
            firstRequirement.copy(measurement = totalMeasurement)
    }
}

private fun IngredientRequirement.candidateItems(
    itemsByProductId: Map<FoodId.Product, List<StashEntry>>,
    itemsByRecipeId: Map<FoodId.Recipe, List<StashEntry>>,
): List<StashEntry> =
    when (this) {
        is ProductRequirement -> itemsByProductId[foodId].orEmpty()
        is RecipeRequirement -> itemsByRecipeId[foodId].orEmpty()
    }

private fun IngredientRequirement.toAvailability(
    candidateItems: List<StashEntry>,
): RecipeIngredientStashAvailability {
    var remainingMeasurement = measurement
    val allocations = mutableListOf<IngredientStashAllocation>()

    candidateItems
        .sortedBy(StashEntry::createdAt)
        .forEach { item ->
            if (remainingMeasurement.measurement.rawValue <= EPSILON) {
                return@forEach
            }

            val allocation = allocateFrom(item, remainingMeasurement)
            if (allocation == null) {
                return@forEach
            }

            allocations += allocation
            remainingMeasurement = (remainingMeasurement - allocation.matchedMeasurement).normalize()
        }

    return RecipeIngredientStashAvailability(
        foodId = foodId,
        foodName = foodName,
        requiredMeasurement = measurement,
        allocations = allocations,
    )
}

private fun List<StashEntry>.compatibleWith(requirement: IngredientRequirement): List<StashEntry> =
    filter { item -> requirement.allocateFrom(item) != null }

private fun IngredientRequirement.allocateFrom(
    item: StashEntry,
    requiredMeasurement: StashMeasurement = measurement,
): IngredientStashAllocation? =
    when (this) {
        is ProductRequirement -> item.allocateFor(requiredMeasurement)
        is RecipeRequirement -> item.allocateFor(requiredMeasurement, recipe)
    }

private fun StashEntry.allocateFor(
    requiredMeasurement: StashMeasurement,
    recipe: Recipe? = null,
): IngredientStashAllocation? {
    if (measurement.type == requiredMeasurement.type) {
        val allocatedRawValue = minOf(measurement.measurement.rawValue, requiredMeasurement.measurement.rawValue)
        if (allocatedRawValue <= EPSILON) {
            return null
        }

        val allocatedMeasurement = StashMeasurement(Measurement.from(measurement.type, allocatedRawValue))
        return IngredientStashAllocation(
            item = this,
            measurement = allocatedMeasurement,
        )
    }

    val recipeFoodRef = foodRef as? StashFoodRef.Recipe ?: return null
    return recipeFoodRef.allocateConvertedMeasurement(
        item = this,
        availableMeasurement = measurement,
        requiredMeasurement = requiredMeasurement,
        recipe = recipe,
    )
}

private fun StashFoodRef.Recipe.allocateConvertedMeasurement(
    item: StashEntry,
    availableMeasurement: StashMeasurement,
    requiredMeasurement: StashMeasurement,
    recipe: Recipe?,
): IngredientStashAllocation? {
    if (requiredMeasurement.type.isWeightType() && availableMeasurement.type.isPortionType()) {
        if (totalAmount.type != availableMeasurement.type) {
            return null
        }
        if (totalWeight <= EPSILON) {
            return null
        }

        val availableRequiredRawValue =
            availableMeasurement.measurement.rawValue * (totalWeight / totalAmount.rawValue)
        val matchedRequiredRawValue =
            minOf(availableRequiredRawValue, requiredMeasurement.measurement.rawValue)
        if (matchedRequiredRawValue <= EPSILON) {
            return null
        }

        val allocatedItemRawValue = totalAmount.rawValue * (matchedRequiredRawValue / totalWeight)
        return IngredientStashAllocation(
            item = item,
            measurement = StashMeasurement(Measurement.from(availableMeasurement.type, allocatedItemRawValue)),
            matchedMeasurement = StashMeasurement(Measurement.from(requiredMeasurement.type, matchedRequiredRawValue)),
        )
    }

    if (requiredMeasurement.type.isPortionType() && availableMeasurement.type.isWeightType()) {
        val liveRecipe = recipe ?: return null
        val requiredWeight = liveRecipe.weight(requiredMeasurement.measurement)
        if (requiredWeight <= EPSILON) {
            return null
        }

        val allocatedItemRawValue =
            minOf(availableMeasurement.measurement.rawValue, requiredWeight)
        if (allocatedItemRawValue <= EPSILON) {
            return null
        }

        val matchedRequiredRawValue =
            requiredMeasurement.measurement.rawValue * (allocatedItemRawValue / requiredWeight)
        if (matchedRequiredRawValue <= EPSILON) {
            return null
        }

        return IngredientStashAllocation(
            item = item,
            measurement = StashMeasurement(Measurement.from(availableMeasurement.type, allocatedItemRawValue)),
            matchedMeasurement = StashMeasurement(Measurement.from(requiredMeasurement.type, matchedRequiredRawValue)),
        )
    }

    return null
}

private fun MeasurementType.isWeightType(): Boolean =
    when (this) {
        MeasurementType.Gram,
        MeasurementType.Milliliter,
        MeasurementType.Ounce,
        MeasurementType.FluidOunce,
        -> true

        MeasurementType.Package,
        MeasurementType.Serving,
        -> false
    }

private fun MeasurementType.isPortionType(): Boolean =
    when (this) {
        MeasurementType.Package,
        MeasurementType.Serving,
        -> true

        MeasurementType.Gram,
        MeasurementType.Milliliter,
        MeasurementType.Ounce,
        MeasurementType.FluidOunce,
        -> false
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
