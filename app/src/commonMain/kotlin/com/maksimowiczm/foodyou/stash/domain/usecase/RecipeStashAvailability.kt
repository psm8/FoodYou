package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity

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
    val quantity: StashQuantity,
)

data class RecipeIngredientStashAvailability(
    val productId: FoodId.Product,
    val productName: String,
    val requiredQuantity: StashQuantity,
    val allocations: List<IngredientStashAllocation>,
) {
    val availableQuantity: StashQuantity =
        allocations.fold(requiredQuantity.zero()) { acc, allocation -> acc + allocation.quantity }

    val status: IngredientAvailabilityStatus
        get() =
            when {
                availableQuantity.amount <= EPSILON -> IngredientAvailabilityStatus.Unavailable
                availableQuantity.amount + EPSILON >= requiredQuantity.amount ->
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
    val quantity: StashQuantity,
)

internal fun Recipe.toProductRequirements(measurement: Measurement): List<ProductRequirement> {
    val consumedWeight = weight(measurement)
    return toProductRequirements(consumedWeight)
}

private fun Recipe.toProductRequirements(consumedWeight: Double): List<ProductRequirement> =
    unpack(consumedWeight)
        .flatMap(RecipeIngredient::toProductRequirements)
        .groupBy { requirement -> requirement.productId to requirement.quantity.unit }
        .map { (key, requirements) ->
            val quantity = requirements.fold(requirements.first().quantity.zero()) { acc, requirement ->
                acc + requirement.quantity
            }
            ProductRequirement(
                productId = key.first,
                productName = requirements.first().productName,
                quantity = quantity,
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
                    quantity = ingredientFood.toStashQuantity(ingredientWeight),
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
    var remainingAmount = quantity.amount
    val allocations = mutableListOf<IngredientStashAllocation>()

    candidateItems
        .filter { item -> item.quantity.unit == quantity.unit }
        .sortedBy(StashItem::createdAt)
        .forEach { item ->
            if (remainingAmount <= EPSILON) {
                return@forEach
            }

            val allocatedAmount = minOf(item.quantity.amount, remainingAmount)
            if (allocatedAmount <= EPSILON) {
                return@forEach
            }

            allocations += IngredientStashAllocation(item = item, quantity = quantity.copy(amount = allocatedAmount))
            remainingAmount -= allocatedAmount
        }

    return RecipeIngredientStashAvailability(
        productId = productId,
        productName = productName,
        requiredQuantity = quantity,
        allocations = allocations,
    )
}

private fun Product.toStashQuantity(weight: Double): StashQuantity =
    if (isLiquid) {
        StashQuantity.milliliters(weight)
    } else {
        StashQuantity.grams(weight)
    }

private fun StashQuantity.zero(): StashQuantity = copy(amount = 0.0)

private const val EPSILON = 0.000001
