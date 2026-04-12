package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CreateAnonymousDishSnapshotUseCaseTest {
    @Test
    fun when_snapshot_is_created_then_recipe_is_copied_immutably_and_create_snapshot_movement_is_logged() = runBlocking {
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
                recipeId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(1),
                stashId = sampleStash().id,
                totalAmount = StashQuantity.fraction(2.0),
                servings = 8,
            )

        val success = assertIs<Success<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError>>(result)
        assertEquals(sampleStash().id, success.data.stashId)
        assertEquals(1L, success.data.itemId.value)
        val snapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals(800.0, snapshot.totalWeight)
        assertEquals(StashQuantity.fraction(2.0), snapshot.totalAmount)
        assertEquals(recipeRepository.observeRecipe(com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(1)).first()!!.nutritionFacts, snapshot.nutritionFacts)
        assertEquals(StashMovementOperation.CreateSnapshot, stashRepository.allMovements().single().operation)

        recipeRepository.updateRecipe(
            sampleRecipe(servings = 4, totalWeight = 1000.0, name = "Changed pizza")
        )
        val persistedSnapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals("Pizza", persistedSnapshot.name)
        assertEquals(800.0, persistedSnapshot.totalWeight)
        assertEquals(snapshot.nutritionFacts, persistedSnapshot.nutritionFacts)
    }

    @Test
    fun when_no_stash_exists_then_snapshot_is_added_to_default_stash() = runBlocking {
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
                recipeId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(1),
                totalAmount = StashQuantity.fraction(2.0),
                servings = 4,
            )

        val success = assertIs<Success<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError>>(result)
        assertEquals("Stash", stashRepository.allStashes().single().name.value)
        assertEquals(stashRepository.allStashes().single().id, success.data.stashId)
    }

    @Test
    fun when_weight_amount_is_used_then_snapshot_preserves_weight_unit_and_total_weight() = runBlocking {
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
                recipeId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(1),
                stashId = sampleStash().id,
                totalAmount = StashQuantity.grams(250.0),
                servings = 8,
            )

        val success = assertIs<Success<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError>>(result)
        assertEquals(sampleStash().id, success.data.stashId)
        val snapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals(StashQuantity.grams(250.0), snapshot.totalAmount)
        assertEquals(250.0, snapshot.totalWeight)
    }
}
