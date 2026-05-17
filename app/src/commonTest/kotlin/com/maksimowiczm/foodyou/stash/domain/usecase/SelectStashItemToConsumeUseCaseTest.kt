package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SelectStashItemToConsumeUseCaseTest {
    @Test
    fun when_filtering_consumable_items_then_only_matching_available_items_are_returned() = runBlocking {
        val useCase =
            SelectStashItemToConsumeUseCase(
                stashRepository =
                    FakeStashRepository(
                        initialItems =
                            listOf(
                                sampleRawProductItem(id = 1),
                                sampleAnonymousDishItem(id = 2),
                                sampleRawProductItem(id = 3, measurement = StashMeasurement.grams(0.0)),
                            )
                    )
            )

        val items =
            useCase.observe(
                stashId = sampleStash().id,
                type = ConsumableStashItemType.RawProduct,
            ).first()

        assertEquals(listOf(1L), items.map { it.id.value })
    }
}
