package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ReturnPartialMealToStashUseCaseTest {
    @Test
    fun when_returning_part_of_a_consumed_raw_product_then_existing_linked_item_is_reused() = runBlocking {
        val product = sampleProduct()
        val consumedItem = sampleRawProductItem(id = 1, measurement = StashMeasurement.grams(0.0), product = product)
        val entry = sampleProductEntry(product = product, measurement = Measurement.Gram(500.0))
        val entryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(entry))
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(consumedItem),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = consumedItem.stashId,
                            itemId = consumedItem.id,
                            operation = StashMovementOperation.DirectConsume,
                            measurementChange = StashMeasurement.grams(-500.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                            createdAt = FIXED_NOW,
                        ),
                    ),
            )
        val useCase =
            ReturnPartialMealToStashUseCase(
                entryRepository = entryRepository,
                productRepository = FakeProductRepository(listOf(product)),
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.returnToStash(
                entryId = entry.id,
                measurementToReturn = StashMeasurement.grams(200.0),
            )

        val success = assertIs<Success<ReturnPartialMealToStashResult, ReturnPartialMealToStashError>>(result)
        assertEquals(sampleStash().id, success.data.stashId)
        assertEquals(consumedItem.id, success.data.itemId)
        assertEquals(listOf(StashMeasurement.grams(200.0)), stashRepository.allItems().map { it.measurement })
        assertEquals(Measurement.Gram(300.0), entryRepository.allEntries().single().measurement)
        assertEquals(StashMovementOperation.ReturnToStash, stashRepository.allMovements().last().operation)
        assertEquals(StashFoodRef.Product(product.id), stashRepository.allItems().single().foodRef)
    }

    @Test
    fun when_returning_part_of_a_recipe_entry_then_recipe_food_ref_and_batch_context_are_preserved() = runBlocking {
        val recipe = sampleRecipe(id = 1, name = "Soup", servings = 2, totalWeight = 500.0, isLiquid = false)
        val consumedItem =
            sampleAnonymousDishItem(
                id = 1,
                measurement = StashMeasurement.servings(0.0),
                recipe = recipe,
                totalAmount = Measurement.Serving(2.0),
                servingsMade = 2,
            )
        val entry =
            FoodDiaryEntry(
                id = FoodDiaryEntryId(1),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
                measurement = Measurement.Serving(2.0),
                food = recipe.toDiaryFood(),
                createdAt = FIXED_NOW,
                updatedAt = FIXED_NOW,
            )
        val entryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(entry))
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(consumedItem),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = consumedItem.stashId,
                            itemId = consumedItem.id,
                            operation = StashMovementOperation.DirectConsume,
                            measurementChange = StashMeasurement.servings(-2.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                            createdAt = FIXED_NOW,
                        ),
                    ),
            )
        val useCase =
            ReturnPartialMealToStashUseCase(
                entryRepository = entryRepository,
                productRepository = FakeProductRepository(),
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.returnToStash(
                entryId = entry.id,
                measurementToReturn = StashMeasurement.grams(200.0),
            )

        assertIs<Success<ReturnPartialMealToStashResult, ReturnPartialMealToStashError>>(result)
        assertEquals(Measurement.Serving(1.2), entryRepository.allEntries().single().measurement)
        assertEquals(StashMeasurement.servings(0.8), stashRepository.allItems().single().measurement)
        assertEquals(
            StashFoodRef.Recipe(
                recipeId = recipe.id,
                totalWeight = 500.0,
                totalAmount = Measurement.Serving(2.0),
            ),
            stashRepository.allItems().single().foodRef,
        )
    }

    private fun sampleProductEntry(
        product: com.maksimowiczm.foodyou.food.domain.entity.Product,
        measurement: Measurement,
    ): FoodDiaryEntry =
        FoodDiaryEntry(
            id = FoodDiaryEntryId(1),
            mealId = sampleMeal().id,
            date = FIXED_NOW.date,
            measurement = measurement,
            food =
                DiaryFoodProduct(
                    name = product.headline,
                    nutritionFacts = product.nutritionFacts,
                    servingWeight = product.servingWeight,
                    totalWeight = product.totalWeight,
                    isLiquid = product.isLiquid,
                    source = product.source,
                    note = product.note,
                ),
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
}
