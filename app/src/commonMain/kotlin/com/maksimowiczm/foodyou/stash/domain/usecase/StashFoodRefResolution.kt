package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import kotlinx.coroutines.flow.first

internal suspend fun StashFoodRef.resolveDiaryFood(
    productRepository: ProductRepository,
    recipeRepository: RecipeRepository,
): DiaryFood? =
    when (this) {
        is StashFoodRef.Product -> productRepository.observeProduct(productId).first()?.toDiaryFood()
        is StashFoodRef.Recipe -> recipeRepository.observeRecipe(recipeId).first()?.toDiaryFood()
    }

internal fun Product.toDiaryFood(): DiaryFoodProduct =
    DiaryFoodProduct(
        name = headline,
        nutritionFacts = nutritionFacts,
        servingWeight = servingWeight,
        totalWeight = totalWeight,
        isLiquid = isLiquid,
        source = source,
        note = note,
    )

internal fun Recipe.toDiaryFood(): DiaryFoodRecipe =
    DiaryFoodRecipe(
        name = headline,
        servings = servings,
        ingredients = ingredients.map(RecipeIngredient::toDiaryFoodIngredient),
        isLiquid = isLiquid,
        note = note,
    )

private fun RecipeIngredient.toDiaryFoodIngredient(): DiaryFoodRecipeIngredient =
    DiaryFoodRecipeIngredient(food = food.toDiaryFood(), measurement = measurement)

private fun com.maksimowiczm.foodyou.food.domain.entity.Food.toDiaryFood(): DiaryFood =
    when (this) {
        is Product -> toDiaryFood()
        is Recipe -> toDiaryFood()
    }

internal suspend fun DiaryFoodProduct.ensureProductId(
    productRepository: ProductRepository,
): FoodId.Product =
    productRepository.insertProduct(
        name = name,
        brand = null,
        barcode = null,
        note = note,
        isLiquid = isLiquid,
        packageWeight = totalWeight,
        servingWeight = servingWeight,
        source = source,
        nutritionFacts = nutritionFacts,
    )
