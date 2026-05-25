package com.maksimowiczm.foodyou.app.ui.stash.add

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.stashAddModule() {
    viewModel {
        (
            productId: FoodId.Product,
            preferredStashId: StashDefinitionId?,
            initialMeasurement: Measurement?) ->
        StashAddProductViewModel(
            productId = productId,
            preferredStashId = preferredStashId,
            initialMeasurement = initialMeasurement,
            productRepository = get(),
            stashRepository = get(),
            stashOwnerProvider = get(),
            observeMeasurementSuggestionsUseCase = get(),
            addProductToStashUseCase = get(),
        )
    }
}
