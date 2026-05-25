package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class CreateManualStashSnapshotUseCaseTest {
    @Test
    fun `when manual quick add succeeds then it creates catalog product stash item and movement`() = runBlocking {
        val productRepository = FakeProductRepository()
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
        val useCase =
            CreateManualStashSnapshotUseCase(
                productRepository = productRepository,
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.create(
                name = "Quick yogurt",
                nutritionFacts = NutritionFacts.Empty,
                measurement = Measurement.Gram(250.0),
                stashId = sampleStash().id,
            )

        val success =
            assertIs<Success<CreateManualStashSnapshotResult, CreateManualStashSnapshotError>>(result)
        assertEquals(sampleStash().id, success.data.stashId)
        assertEquals(1L, success.data.itemId.value)
        val createdProduct = productRepository.allProducts().single()
        assertEquals(FoodId.Product(1L), createdProduct.id)
        assertEquals("Quick yogurt", createdProduct.name)
        assertEquals(null, createdProduct.brand)
        assertEquals(null, createdProduct.barcode)
        assertEquals(null, createdProduct.note)
        assertEquals(false, createdProduct.isLiquid)
        assertEquals(null, createdProduct.packageWeight)
        assertEquals(null, createdProduct.servingWeight)
        assertEquals(com.maksimowiczm.foodyou.common.domain.food.FoodSource.Type.User, createdProduct.source.type)
        assertEquals(
            StashFoodRef.Product(FoodId.Product(1L)),
            stashRepository.allItems().single().foodRef,
        )
        assertEquals(StashMeasurement.grams(250.0), stashRepository.allItems().single().measurement)
        assertEquals(StashMovementOperation.ManualQuickAdd, stashRepository.allMovements().single().operation)
    }

    @Test
    fun `when manual quick add uses unsupported measurement then it fails before creating product`() = runBlocking {
        val productRepository = FakeProductRepository()
        val useCase =
            CreateManualStashSnapshotUseCase(
                productRepository = productRepository,
                stashRepository = FakeStashRepository(),
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.create(
                name = "Quick yogurt",
                nutritionFacts = NutritionFacts.Empty,
                measurement = Measurement.Package(1.0),
            )

        assertEquals(
            CreateManualStashSnapshotError.InvalidMeasurement,
            assertIs<Error<CreateManualStashSnapshotResult, CreateManualStashSnapshotError>>(result).error,
        )
        assertEquals(emptyList(), productRepository.allProducts())
    }
}
