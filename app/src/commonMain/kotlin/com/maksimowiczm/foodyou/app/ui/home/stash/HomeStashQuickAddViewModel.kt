package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.AddProductToStashError
import com.maksimowiczm.foodyou.stash.domain.usecase.AddProductToStashUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class HomeStashQuickAddViewModel(
    private val productId: FoodId.Product,
    preferredStashId: StashDefinitionId?,
    productRepository: ProductRepository,
    stashRepository: StashRepository,
    stashOwnerProvider: StashOwnerProvider,
    private val addProductToStashUseCase: AddProductToStashUseCase,
) : ViewModel() {
    private val ownerId = stashOwnerProvider.current()
    private val amount = MutableStateFlow("")
    private val selectedStashId = MutableStateFlow(preferredStashId)
    private val error = MutableStateFlow<HomeStashQuickAddError?>(null)
    private val eventBus = Channel<HomeStashQuickAddEvent>()

    private val product = productRepository.observeProduct(productId)
    private val stashes =
        stashRepository.observeStashes(ownerId).map { list ->
            list.sortedWith(compareBy({ it.ordering }, { it.id.value })).map {
                HomeStashQuickAddStash(id = it.id, name = it.name.value)
            }
        }

    val events = eventBus.receiveAsFlow()

    val state =
        combine(product, stashes, amount, selectedStashId, error) { product, stashes, amount, selectedStashId, error ->
            val resolvedSelectedStashId =
                when {
                    selectedStashId != null && stashes.any { it.id == selectedStashId } -> selectedStashId
                    stashes.size == 1 -> stashes.single().id
                    else -> null
                }

            HomeStashQuickAddState(
                productId = productId,
                productName = product?.headline.orEmpty(),
                isLoading = false,
                isProductMissing = product == null,
                amount = amount,
                unit = if (product?.isLiquid == true) StashQuantityUnit.Milliliter else StashQuantityUnit.Gram,
                stashes = stashes,
                selectedStashId = resolvedSelectedStashId,
                error = if (product == null) HomeStashQuickAddError.ProductNotFound else error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = HomeStashQuickAddState(productId = productId),
        )

    fun updateAmount(value: String) {
        amount.value = value
        if (error.value == HomeStashQuickAddError.InvalidAmount ||
            error.value == HomeStashQuickAddError.SaveFailed
        ) {
            error.value = null
        }
    }

    fun selectStash(stashId: StashDefinitionId) {
        selectedStashId.value = stashId
        if (error.value == HomeStashQuickAddError.StashSelectionRequired) {
            error.value = null
        }
    }

    fun save() {
        val currentState = state.value
        if (currentState.isLoading) {
            return
        }
        if (currentState.isProductMissing) {
            error.value = HomeStashQuickAddError.ProductNotFound
            return
        }
        val quantity =
            currentState.parsedQuantity ?: run {
                error.value = HomeStashQuickAddError.InvalidAmount
                return
            }
        if (currentState.requiresStashSelection && currentState.selectedStashId == null) {
            error.value = HomeStashQuickAddError.StashSelectionRequired
            return
        }

        viewModelScope.launch {
            val result =
                addProductToStashUseCase.add(
                    productId = productId,
                    quantity = quantity,
                    stashId = currentState.selectedStashId,
                )

            when (result) {
                is Result.Success -> {
                    error.value = null
                    eventBus.send(HomeStashQuickAddEvent.Saved(result.data.stashId))
                }

                is Result.Error -> {
                    error.value = result.error.toUiError()
                }
            }
        }
    }

    private fun AddProductToStashError.toUiError(): HomeStashQuickAddError =
        when (this) {
            is AddProductToStashError.ProductNotFound -> HomeStashQuickAddError.ProductNotFound
            is AddProductToStashError.StashNotFound -> HomeStashQuickAddError.SaveFailed
            AddProductToStashError.StashSelectionRequired -> HomeStashQuickAddError.StashSelectionRequired
            AddProductToStashError.NonPositiveQuantity,
            AddProductToStashError.InvalidQuantityUnit -> HomeStashQuickAddError.InvalidAmount
        }
}

@Immutable
internal data class HomeStashQuickAddState(
    val productId: FoodId.Product,
    val productName: String = "",
    val isLoading: Boolean = true,
    val isProductMissing: Boolean = false,
    val amount: String = "",
    val unit: StashQuantityUnit = StashQuantityUnit.Gram,
    val stashes: List<HomeStashQuickAddStash> = emptyList(),
    val selectedStashId: StashDefinitionId? = null,
    val error: HomeStashQuickAddError? = null,
) {
    val requiresStashSelection: Boolean
        get() = stashes.size > 1

    val parsedQuantity: StashQuantity?
        get() {
            val parsedAmount = amount.toDoubleOrNull() ?: return null
            if (parsedAmount <= 0.0) {
                return null
            }

            return when (unit) {
                StashQuantityUnit.Gram -> StashQuantity.grams(parsedAmount)
                StashQuantityUnit.Milliliter -> StashQuantity.milliliters(parsedAmount)
                StashQuantityUnit.Fraction -> StashQuantity.fraction(parsedAmount)
            }
        }

    val canSave: Boolean
        get() =
            !isLoading &&
                !isProductMissing &&
                parsedQuantity != null &&
                (!requiresStashSelection || selectedStashId != null)
}

@Immutable
internal data class HomeStashQuickAddStash(val id: StashDefinitionId, val name: String)

internal enum class HomeStashQuickAddError {
    ProductNotFound,
    InvalidAmount,
    StashSelectionRequired,
    SaveFailed,
}

internal sealed interface HomeStashQuickAddEvent {
    data class Saved(val stashId: StashDefinitionId) : HomeStashQuickAddEvent
}
