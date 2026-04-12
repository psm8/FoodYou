package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        assertEquals(1L, success.data.itemId.value)
        val snapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals(800.0, snapshot.totalWeight)
        assertEquals(StashQuantity.fraction(2.0), snapshot.totalAmount)
        assertEquals(StashMovementOperation.CreateSnapshot, stashRepository.allMovements().single().operation)

        recipeRepository.updateRecipe(sampleRecipe(servings = 4, totalWeight = 1000.0, name = "Changed pizza"))
        val persistedSnapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals("Pizza", persistedSnapshot.name)
        assertEquals(800.0, persistedSnapshot.totalWeight)
    }
}
