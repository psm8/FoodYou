package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StashFoodRefTest {
    @Test
    fun `product variant keeps referenced product id`() {
        val foodRef = StashFoodRef.Product(FoodId.Product(11))

        assertEquals(FoodId.Product(11), foodRef.productId)
    }

    @Test
    fun `recipe variant created from servings scales total weight`() {
        val recipe = sampleRecipe()

        val foodRef =
            StashFoodRef.Recipe.from(
                recipe = recipe,
                totalAmount = Measurement.Serving(1.5),
                servingsMade = 3,
            )

        assertEquals(recipe.id, foodRef.recipeId)
        assertEquals(300.0, foodRef.totalWeight)
        assertEquals(1.5, foodRef.totalAmount.rawValue)
    }

    @Test
    fun `recipe variant rejects non positive batch amount`() {
        assertFailsWith<IllegalArgumentException> {
            StashFoodRef.Recipe(
                recipeId = FoodId.Recipe(7),
                totalWeight = 100.0,
                totalAmount = Measurement.Gram(0.0),
            )
        }
    }
}

private fun sampleRecipe(): Recipe =
    Recipe(
        id = FoodId.Recipe(7),
        name = "Soup",
        servings = 2,
        ingredients =
            listOf(
                RecipeIngredient(
                    food =
                        Product(
                            id = FoodId.Product(1),
                            name = "Water",
                            brand = null,
                            barcode = null,
                            note = null,
                            isLiquid = true,
                            packageWeight = 200.0,
                            servingWeight = 200.0,
                            source = com.maksimowiczm.foodyou.common.domain.food.FoodSource(
                                com.maksimowiczm.foodyou.common.domain.food.FoodSource.Type.User
                            ),
                            nutritionFacts = com.maksimowiczm.foodyou.common.domain.food.NutritionFacts.Empty,
                        ),
                    measurement = Measurement.Gram(200.0),
                )
            ),
        note = null,
        isLiquid = true,
    )
