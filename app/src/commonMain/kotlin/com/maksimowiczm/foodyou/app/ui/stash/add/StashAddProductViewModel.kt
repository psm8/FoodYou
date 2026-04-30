package com.maksimowiczm.foodyou.app.ui.stash.add

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveMeasurementSuggestionsUseCase
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.AddProductToStashError
import com.maksimowiczm.foodyou.stash.domain.usecase.AddProductToStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.toStashQuantityOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class StashAddProductViewModel(
    private val productId: FoodId.Product,
    preferredStashId: StashDefinitionId?,
    initialMeasurement: Measurement?,
    productRepository: ProductRepository,
    stashRepository: StashRepository,
    stashOwnerProvider: StashOwnerProvider,
    observeMeasurementSuggestionsUseCase: ObserveMeasurementSuggestionsUseCase,
    private val addProductToStashUseCase: AddProductToStashUseCase,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val ownerId = stashOwnerProvider.current()
    private val selectedStashId = MutableStateFlow(preferredStashId)
    private val error = MutableStateFlow<StashAddProductError?>(null)
    private val eventBus = Channel<StashAddProductEvent>()

    private val product = productRepository.observeProduct(productId)
    private val stashes =
        stashRepository.observeStashes(ownerId).map { list ->
            list.sortedWith(compareBy({ it.ordering }, { it.id.value })).map {
                StashAddProductStash(id = it.id, name = it.name.value)
            }
        }
    private val suggestions = observeMeasurementSuggestionsUseCase.observe(productId, limit = 5)

    val events = eventBus.receiveAsFlow()

    val state =
        combine(product, stashes, suggestions, selectedStashId, error) {
                product,
                stashes,
                suggestions,
                selectedStashId,
                error ->
                val resolvedSelectedStashId =
                    when {
                        selectedStashId != null && stashes.any { it.id == selectedStashId } ->
                            selectedStashId
                        stashes.size == 1 -> stashes.single().id
                        else -> null
                    }

                StashAddProductState(
                    productId = productId,
                    productName = product?.headline.orEmpty(),
                    isLoading = false,
                    isProductMissing = product == null,
                    suggestions = suggestions,
                    possibleMeasurementTypes = product?.possibleMeasurementTypes().orEmpty(),
                    selectedMeasurement =
                        product.resolveSelectedMeasurement(initialMeasurement, suggestions),
                    stashes = stashes,
                    selectedStashId = resolvedSelectedStashId,
                    error = if (product == null) StashAddProductError.ProductNotFound else error,
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = StashAddProductState(productId = productId),
            )

    fun selectStash(stashId: StashDefinitionId) {
        selectedStashId.value = stashId
        if (error.value == StashAddProductError.StashSelectionRequired) {
            error.value = null
        }
    }

    fun save(measurement: Measurement) {
        val currentState = state.value
        if (currentState.isLoading) {
            return
        }
        if (currentState.isProductMissing) {
            error.value = StashAddProductError.ProductNotFound
            return
        }
        if (currentState.requiresStashSelection && currentState.selectedStashId == null) {
            error.value = StashAddProductError.StashSelectionRequired
            return
        }

        scope.launch {
            val result =
                addProductToStashUseCase.add(
                    productId = productId,
                    measurement = measurement,
                    stashId = currentState.selectedStashId,
                )

            when (result) {
                is com.maksimowiczm.foodyou.common.result.Result.Success -> {
                    error.value = null
                    eventBus.send(StashAddProductEvent.Saved(result.data.stashId))
                }

                is com.maksimowiczm.foodyou.common.result.Result.Error -> {
                    error.value = result.error.toUiError()
                }
            }
        }
    }

    private fun AddProductToStashError.toUiError(): StashAddProductError =
        when (this) {
            is AddProductToStashError.ProductNotFound -> StashAddProductError.ProductNotFound
            is AddProductToStashError.StashNotFound -> StashAddProductError.SaveFailed
            AddProductToStashError.StashSelectionRequired ->
                StashAddProductError.StashSelectionRequired
            AddProductToStashError.InvalidMeasurement -> StashAddProductError.InvalidMeasurement
        }
}

@Immutable
internal data class StashAddProductState(
    val productId: FoodId.Product,
    val productName: String = "",
    val isLoading: Boolean = true,
    val isProductMissing: Boolean = false,
    val suggestions: List<Measurement> = emptyList(),
    val possibleMeasurementTypes: List<MeasurementType> = emptyList(),
    val selectedMeasurement: Measurement = Measurement.Gram(Measurement.Gram.DEFAULT),
    val stashes: List<StashAddProductStash> = emptyList(),
    val selectedStashId: StashDefinitionId? = null,
    val error: StashAddProductError? = null,
) {
    val requiresStashSelection: Boolean
        get() = stashes.size > 1
}

@Immutable internal data class StashAddProductStash(val id: StashDefinitionId, val name: String)

internal enum class StashAddProductError {
    ProductNotFound,
    InvalidMeasurement,
    StashSelectionRequired,
    SaveFailed,
}

internal sealed interface StashAddProductEvent {
    data class Saved(val stashId: StashDefinitionId) : StashAddProductEvent
}

private fun Product.possibleMeasurementTypes(): List<MeasurementType> =
    MeasurementType.entries.filter { type ->
        when (type) {
            MeasurementType.Gram -> !isLiquid
            MeasurementType.Ounce -> !isLiquid
            MeasurementType.Milliliter -> isLiquid
            MeasurementType.FluidOunce -> isLiquid
            MeasurementType.Package -> totalWeight != null
            MeasurementType.Serving -> servingWeight != null
        }
    }

private fun Product.defaultMeasurement(): Measurement =
    when {
        servingWeight != null -> Measurement.Serving(1.0)
        totalWeight != null -> Measurement.Package(1.0)
        isLiquid -> Measurement.Milliliter(100.0)
        else -> Measurement.Gram(100.0)
    }

private fun Product?.resolveSelectedMeasurement(
    initialMeasurement: Measurement?,
    suggestions: List<Measurement>,
): Measurement =
    when {
        this == null -> Measurement.Gram(Measurement.Gram.DEFAULT)
        initialMeasurement != null && toStashQuantityOrNull(initialMeasurement) != null ->
            initialMeasurement
        suggestions.isNotEmpty() -> suggestions.first()
        else -> defaultMeasurement()
    }
