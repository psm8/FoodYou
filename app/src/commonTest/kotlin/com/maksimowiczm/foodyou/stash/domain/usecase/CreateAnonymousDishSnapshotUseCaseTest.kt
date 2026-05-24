package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class CreateAnonymousDishSnapshotUseCaseTest {
    @Test
    fun when_recipe_entry_is_created_then_recipe_ref_and_create_movement_are_saved() = runBlocking {
        val recipeRepository = FakeRecipeRepository(listOf(sampleRecipe(servings = 4, totalWeight = 400.0)))
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
        val useCase =
            CreateAnonymousDishSnapshotUseCase(
                recipeRepository = recipeRepository,
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.create(
                recipeId = FoodId.Recipe(1),
                stashId = sampleStash().id,
                totalAmount = StashMeasurement.servings(2.0),
                servings = 8,
            )

        val success = assertIs<Success<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError>>(result)
        assertEquals(sampleStash().id, success.data.stashId)
        assertEquals(1L, success.data.itemId.value)
        assertEquals(
            StashFoodRef.Recipe(
                recipeId = FoodId.Recipe(1),
                totalWeight = 800.0,
                totalAmount = Measurement.Serving(2.0),
            ),
            stashRepository.allItems().single().foodRef,
        )
        assertEquals(StashMeasurement.servings(2.0), stashRepository.allItems().single().measurement)
        assertEquals(StashMovementOperation.CreateSnapshot, stashRepository.allMovements().single().operation)
        assertEquals(StashMeasurement.servings(2.0), stashRepository.allMovements().single().measurementChange)
    }

    @Test
    fun when_no_stash_exists_then_recipe_entry_is_added_to_default_stash() = runBlocking {
        val recipeRepository = FakeRecipeRepository(listOf(sampleRecipe()))
        val stashRepository = FakeStashRepository()
        val useCase =
            CreateAnonymousDishSnapshotUseCase(
                recipeRepository = recipeRepository,
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.create(
                recipeId = FoodId.Recipe(1),
                totalAmount = StashMeasurement.servings(2.0),
                servings = 4,
            )

        val success = assertIs<Success<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError>>(result)
        assertEquals("Stash", stashRepository.allStashes().single().name.value)
        assertEquals(stashRepository.allStashes().single().id, success.data.stashId)
    }

    @Test
    fun when_weight_amount_is_used_then_recipe_ref_keeps_weight_unit_and_batch_weight() = runBlocking {
        val recipeRepository = FakeRecipeRepository(listOf(sampleRecipe(totalWeight = 400.0)))
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
        val useCase =
            CreateAnonymousDishSnapshotUseCase(
                recipeRepository = recipeRepository,
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.create(
                recipeId = FoodId.Recipe(1),
                stashId = sampleStash().id,
                totalAmount = StashMeasurement.grams(250.0),
                servings = 8,
            )

        val success = assertIs<Success<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError>>(result)
        assertEquals(sampleStash().id, success.data.stashId)
        assertEquals(
            StashFoodRef.Recipe(
                recipeId = FoodId.Recipe(1),
                totalWeight = 250.0,
                totalAmount = Measurement.Gram(250.0),
            ),
            stashRepository.allItems().single().foodRef,
        )
    }
}
