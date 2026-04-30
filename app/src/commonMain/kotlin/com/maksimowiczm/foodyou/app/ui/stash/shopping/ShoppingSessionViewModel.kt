package com.maksimowiczm.foodyou.app.ui.stash.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSession
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.AddToShoppingSessionError
import com.maksimowiczm.foodyou.stash.domain.usecase.AddToShoppingSessionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ConfirmShoppingSessionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.StartShoppingSessionError
import com.maksimowiczm.foodyou.stash.domain.usecase.StartShoppingSessionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ShoppingSessionViewModel(
    private val stashId: StashDefinitionId?,
    private val startShoppingSessionUseCase: StartShoppingSessionUseCase,
    private val addToShoppingSessionUseCase: AddToShoppingSessionUseCase,
    private val confirmShoppingSessionUseCase: ConfirmShoppingSessionUseCase,
    stashRepository: StashRepository,
    stashOwnerProvider: StashOwnerProvider,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val ownerId = stashOwnerProvider.current()
    private val sessionHeader = MutableStateFlow<ShoppingSessionHeader?>(null)
    private val selectedStashId = MutableStateFlow(stashId)
    private val selectedProduct = MutableStateFlow<ShoppingSessionSelectedProduct?>(null)
    private val pendingMeasurement = MutableStateFlow<Measurement?>(null)
    private val items = MutableStateFlow<List<ShoppingSessionListItem>>(emptyList())
    private val error = MutableStateFlow<ShoppingSessionUiError?>(null)
    private val isLoading = MutableStateFlow(true)
    private val isConfirming = MutableStateFlow(false)
    private val eventBus = Channel<ShoppingSessionEvent>()
    private var nextItemId = 0L

    private val stashOptions =
        stashRepository.observeStashes(ownerId).map { stashes ->
            stashes
                .sortedWith(compareBy({ it.ordering }, { it.id.value }))
                .map { ShoppingSessionStashOption(id = it.id, name = it.name.value) }
        }

    val events = eventBus.receiveAsFlow()

    val state =
        combine(
            stashOptions,
            isLoading,
            isConfirming,
            selectedStashId,
            selectedProduct,
            pendingMeasurement,
            items,
            error,
        ) { values: Array<Any?> ->
            val stashOptions = values[0] as List<ShoppingSessionStashOption>
            val isLoading = values[1] as Boolean
            val isConfirming = values[2] as Boolean
            val selectedStashId = values[3] as StashDefinitionId?
            val selectedProduct = values[4] as ShoppingSessionSelectedProduct?
            val pendingMeasurement = values[5] as Measurement?
            val items = values[6] as List<ShoppingSessionListItem>
            val error = values[7] as ShoppingSessionUiError?

            ShoppingSessionUiState(
                isLoading = isLoading,
                isConfirming = isConfirming,
                stashOptions = stashOptions,
                selectedStashId = resolveSelectedStash(stashOptions, selectedStashId),
                selectedProduct = selectedProduct,
                pendingMeasurement = pendingMeasurement,
                items = items,
                error = error,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ShoppingSessionUiState(),
        )

    init {
        scope.launch {
            stashOptions.collect { options ->
                val resolvedStashId = resolveSelectedStash(options, selectedStashId.value)
                selectedStashId.value = resolvedStashId
                isLoading.value = false

                if (resolvedStashId != null && sessionHeader.value?.stashId != resolvedStashId) {
                    startSession(resolvedStashId)
                }
            }
        }
    }

    fun selectStash(stashId: StashDefinitionId) {
        if (selectedStashId.value == stashId) {
            return
        }

        selectedStashId.value = stashId
        clearDraft()
        scope.launch { startSession(stashId) }
    }

    fun selectProduct(product: FoodSearch.Product) {
        val selectedProduct = ShoppingSessionSelectedProduct.from(product)
        this.selectedProduct.value = selectedProduct
        pendingMeasurement.value = selectedProduct.suggestedMeasurement
        clearError(ShoppingSessionUiError.ProductSelectionRequired)
    }

    fun updatePendingMeasurement(measurement: Measurement) {
        pendingMeasurement.value = measurement
        clearError(ShoppingSessionUiError.InvalidQuantity)
        clearError(ShoppingSessionUiError.AddFailed)
    }

    fun addPendingProduct() {
        val currentState = state.value
        val selectedStashId =
            currentState.selectedStashId ?: run {
                error.value = ShoppingSessionUiError.StashSelectionRequired
                return
            }
        val selectedProduct =
            currentState.selectedProduct ?: run {
                error.value = ShoppingSessionUiError.ProductSelectionRequired
                return
            }
        val measurement =
            currentState.pendingMeasurement ?: run {
                error.value = ShoppingSessionUiError.InvalidQuantity
                return
            }
        if (currentState.pendingQuantityValue == null) {
            error.value = ShoppingSessionUiError.InvalidQuantity
            return
        }

        scope.launch {
            if (!ensureSession(selectedStashId)) {
                return@launch
            }

            val result =
                addToShoppingSessionUseCase.add(
                    session = currentSession(selectedStashId),
                    productId = selectedProduct.id,
                    measurement = measurement,
                )

            when (result) {
                is Result.Success -> {
                    items.value = result.data.items.map(::toPreviewItem)
                    pendingMeasurement.value = null
                    this@ShoppingSessionViewModel.selectedProduct.value = null
                    error.value = null
                }

                is Result.Error -> {
                    error.value = result.error.toUiError()
                }
            }
        }
    }

    fun updateItemQuantity(
        itemId: ShoppingSessionListItemId,
        value: String,
    ) {
        items.update { currentItems ->
            currentItems.map { item ->
                if (item.id == itemId) {
                    item.updateQuantity(value)
                } else {
                    item
                }
            }
        }
        clearError(ShoppingSessionUiError.InvalidQuantity)
    }

    fun removeItem(itemId: ShoppingSessionListItemId) {
        items.update { currentItems -> currentItems.filterNot { it.id == itemId } }
    }

    fun confirm() {
        val currentState = state.value
        val selectedStashId =
            currentState.selectedStashId ?: run {
                error.value = ShoppingSessionUiError.StashSelectionRequired
                return
            }
        if (!currentState.canConfirm) {
            error.value = ShoppingSessionUiError.InvalidQuantity
            return
        }

        scope.launch {
            if (!ensureSession(selectedStashId)) {
                return@launch
            }

            isConfirming.value = true
            val result = confirmShoppingSessionUseCase.confirm(currentSession(selectedStashId))
            isConfirming.value = false

            when (result) {
                is Result.Success -> {
                    clearDraft()
                    sessionHeader.value = null
                    error.value = null
                    eventBus.send(ShoppingSessionEvent.Finished(selectedStashId))
                }

                is Result.Error -> {
                    error.value = ShoppingSessionUiError.ConfirmFailed
                }
            }
        }
    }

    private fun currentSession(
        stashId: StashDefinitionId,
        sessionItems: List<ShoppingSessionListItem> = items.value,
    ): ShoppingSession {
        val header = sessionHeader.value ?: ShoppingSessionHeader(ShoppingSessionId("pending-${stashId.value}"), stashId)
        return ShoppingSession(
            id = header.id,
            stashId = header.stashId,
            items = sessionItems.map(ShoppingSessionListItem::sessionItem),
        )
    }

    private suspend fun ensureSession(stashId: StashDefinitionId): Boolean {
        if (sessionHeader.value?.stashId == stashId) {
            return true
        }

        return startSession(stashId)
    }

    private suspend fun startSession(stashId: StashDefinitionId): Boolean =
        when (val result = startShoppingSessionUseCase.start(stashId)) {
            is Result.Success -> {
                sessionHeader.value = ShoppingSessionHeader(id = result.data.id, stashId = result.data.stashId)
                true
            }

            is Result.Error -> {
                error.value = result.error.toUiError()
                false
            }
        }

    private fun resolveSelectedStash(
        options: List<ShoppingSessionStashOption>,
        currentSelection: StashDefinitionId?,
    ): StashDefinitionId? {
        if (currentSelection != null && options.any { it.id == currentSelection }) {
            return currentSelection
        }

        if (stashId != null && options.any { it.id == stashId }) {
            return stashId
        }

        if (options.size == 1) {
            return options.single().id
        }

        return null
    }

    private fun clearDraft() {
        items.value = emptyList()
        selectedProduct.value = null
        pendingMeasurement.value = null
    }

    private fun nextItemId(): ShoppingSessionListItemId {
        nextItemId += 1
        return ShoppingSessionListItemId("shopping-session-item-$nextItemId")
    }

    private fun toPreviewItem(sessionItem: com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem): ShoppingSessionListItem {
        val existingItem = items.value.firstOrNull { it.sessionItem.id == sessionItem.id }
        return ShoppingSessionListItem.from(
            id = existingItem?.id ?: nextItemId(),
            sessionItem = sessionItem,
        )
    }

    private fun clearError(target: ShoppingSessionUiError) {
        if (error.value == target) {
            error.value = null
        }
    }

    private fun StartShoppingSessionError.toUiError(): ShoppingSessionUiError =
        when (this) {
            is StartShoppingSessionError.StashNotFound -> ShoppingSessionUiError.StartFailed
        }

    private fun AddToShoppingSessionError.toUiError(): ShoppingSessionUiError =
        when (this) {
            is AddToShoppingSessionError.ProductNotFound -> ShoppingSessionUiError.AddFailed
            AddToShoppingSessionError.NonPositiveQuantity -> ShoppingSessionUiError.InvalidQuantity
            AddToShoppingSessionError.InvalidQuantityUnit -> ShoppingSessionUiError.InvalidQuantity
        }

    private data class ShoppingSessionHeader(
        val id: ShoppingSessionId,
        val stashId: StashDefinitionId,
    )
}
