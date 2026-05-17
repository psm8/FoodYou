package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class CreateManualStashSnapshotUseCaseTest {
    @Test
    fun `when first manual quick add is created then default stash snapshot and audit movement are stored`() =
        runBlocking {
            val stashRepository = FakeStashRepository()
            val useCase =
                CreateManualStashSnapshotUseCase(
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val result =
                useCase.create(
                    name = "Quick yogurt",
                    nutritionFacts =
                        NutritionFacts(
                            energy = 250.0.toNutrientValue(),
                            proteins = 25.0.toNutrientValue(),
                            carbohydrates = 10.0.toNutrientValue(),
                            fats = 5.0.toNutrientValue(),
                        ),
                    measurement = Measurement.Gram(250.0),
                )

            val success =
                assertIs<
                    Success<CreateManualStashSnapshotResult, CreateManualStashSnapshotError>
                >(result)
            val item = stashRepository.allItems().single()
            val snapshot = assertIs<RawProductSnapshot>(item.snapshot)

            assertEquals(StashName.from("Stash"), stashRepository.allStashes().single().name)
            assertEquals(success.data.stashId, stashRepository.allStashes().single().id)
            assertEquals(null, snapshot.productId)
            assertEquals("Quick yogurt", snapshot.name)
            assertEquals(StashQuantity.grams(250.0), item.quantity)
            assertEquals(250.0, snapshot.totalWeight)
            assertEquals(100.0, snapshot.nutritionFacts.energy.value)
            assertEquals(10.0, snapshot.nutritionFacts.proteins.value)
            assertEquals(4.0, snapshot.nutritionFacts.carbohydrates.value)
            assertEquals(2.0, snapshot.nutritionFacts.fats.value)
            assertEquals(
                StashMovementOperation.ManualQuickAdd,
                stashRepository.allMovements().single().operation,
            )
        }

    @Test
    fun `when liquid measurement is used then canonical stash quantity is normalized to milliliters`() =
        runBlocking {
            val stash = sampleStash()
            val stashRepository = FakeStashRepository(initialStashes = listOf(stash))
            val useCase =
                CreateManualStashSnapshotUseCase(
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val result =
                useCase.create(
                    name = "Quick juice",
                    nutritionFacts = nutritionWithEnergy(80.0),
                    measurement = Measurement.FluidOunce(8.0),
                    stashId = stash.id,
                )

            assertIs<Success<CreateManualStashSnapshotResult, CreateManualStashSnapshotError>>(result)
            val item = stashRepository.allItems().single()
            val snapshot = assertIs<RawProductSnapshot>(item.snapshot)

            assertEquals(StashQuantityUnit.Milliliter, item.quantity.unit)
            assertEquals(236.58824, item.quantity.amount, 0.00001)
            assertEquals(true, snapshot.isLiquid)
            val normalizedEnergy = assertNotNull(snapshot.nutritionFacts.energy.value)
            assertEquals(
                80.0 / (item.quantity.amount / 100.0),
                normalizedEnergy,
                0.00001,
            )
        }

    @Test
    fun `when measurement is unsupported then it returns validation error`() = runBlocking {
        val useCase =
            CreateManualStashSnapshotUseCase(
                stashRepository = FakeStashRepository(),
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.create(
                name = "Quick oats",
                nutritionFacts = NutritionFacts.Empty,
                measurement = Measurement.Package(1.0),
            )

        assertEquals(
            CreateManualStashSnapshotError.InvalidMeasurement,
            assertIs<
                Error<CreateManualStashSnapshotResult, CreateManualStashSnapshotError>
            >(result)
                .error,
        )
    }
}
