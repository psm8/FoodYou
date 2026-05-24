package com.maksimowiczm.foodyou.app.ui.stash.consume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.stash.StashFoodDisplay
import com.maksimowiczm.foodyou.app.ui.stash.observeStashFoodDisplay
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.extension.combine
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.result.onError
import com.maksimowiczm.foodyou.common.result.onSuccess
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.ConsumeFromStashError
import com.maksimowiczm.foodyou.stash.domain.usecase.ConsumeFromStashUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal class ConsumeStashItemViewModel(
    private val itemId: StashEntryId,
    private val stashRepository: StashRepository,
    mealRepository: MealRepository,
    dateProvider: DateProvider,
    private val observeFoodUseCase: ObserveFoodUseCase,
    private val consumeFromStashUseCase: ConsumeFromStashUseCase,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val initialDate = dateProvider.now().date
    private val item = MutableStateFlow<StashEntry?>(null)
    private val itemLoaded = MutableStateFlow(false)
    private val amount = MutableStateFlow("")
    private val selectedMealId = MutableStateFlow<Long?>(null)
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val isSaving = MutableStateFlow(false)
    private val error = MutableStateFlow<ConsumeStashItemError?>(null)
    private val eventChannel = Channel<ConsumeStashItemEvent>()

    val events = eventChannel.receiveAsFlow()

    private val itemDisplay =
        item
            .flatMapLatest { loadedItem ->
                loadedItem?.let { observeFoodUseCase.observeStashFoodDisplay(it.foodRef) } ?: flowOf(null)
            }.stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    private val meals =
        mealRepository
            .observeMeals()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    private val today =
        dateProvider
            .observeDate()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = initialDate,
            )

    private val stateInputs =
        combine(item, itemLoaded, amount, itemDisplay, meals, selectedMealId) { item, itemLoaded, amount, itemDisplay, meals, selectedMealId ->
            ConsumeStashItemStateInputs(
                item = item,
                itemLoaded = itemLoaded,
                amount = amount,
                itemDisplay = itemDisplay,
                meals = meals,
                selectedMealId = selectedMealId,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue =
                ConsumeStashItemStateInputs(
                    item = null,
                    itemLoaded = false,
                    amount = "",
                    itemDisplay = null,
                    meals = emptyList(),
                    selectedMealId = null,
                ),
        )

    val state: StateFlow<ConsumeStashItemState> =
        kotlinx.coroutines.flow.combine(stateInputs, selectedDate, today, isSaving, error) { inputs, selectedDate, today, isSaving, error ->
            ConsumeStashItemState(
                itemName = inputs.itemDisplay?.name.orEmpty(),
                remainingQuantity = inputs.item?.measurement,
                amount = inputs.amount,
                meals = inputs.meals.map { ConsumeStashItemMeal(id = it.id, name = it.name) },
                selectedMealId = inputs.selectedMealId,
                today = today,
                selectedDate = selectedDate ?: today,
                isLoading = !inputs.itemLoaded || (inputs.item != null && (inputs.itemDisplay == null || inputs.itemDisplay.isLoading)),
                isSaving = isSaving,
                error = error,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue =
                ConsumeStashItemState(
                    today = initialDate,
                    selectedDate = initialDate,
                ),
        )

    init {
        loadItem()
        observeDefaultMeal()
        observeDefaultDate()
    }

    fun updateAmount(value: String) {
        amount.value = value
        error.value = null
    }

    fun selectMeal(mealId: Long) {
        selectedMealId.value = mealId
        error.value = null
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        error.value = null
    }

    fun consume() {
        if (isSaving.value) {
            return
        }
        val currentState = state.value
        val mealId =
            currentState.selectedMealId ?: run {
                error.value = ConsumeStashItemError.MealNotFound
                return
            }
        val quantity =
            currentState.parsedAmount ?: run {
                error.value = ConsumeStashItemError.InvalidAmount
                return
            }
        val date = currentState.selectedDate

        scope.launch {
            isSaving.value = true
            error.value = null

            consumeFromStashUseCase
                .consume(
                    itemId = itemId,
                    mealId = mealId,
                    amountEaten = quantity,
                    dateOverride = date,
                ).onSuccess {
                    eventChannel.send(ConsumeStashItemEvent.Consumed)
                }.onError {
                    error.value = it.toUiError()
                }

            isSaving.value = false
        }
    }

    private fun loadItem() {
        scope.launch {
            val loadedItem = stashRepository.getItem(itemId)
            item.value = loadedItem
            itemLoaded.value = true

            if (loadedItem == null) {
                error.value = ConsumeStashItemError.ItemNotFound
                return@launch
            }

            if (amount.value.isBlank()) {
                amount.value = loadedItem.measurement.measurement.rawValue.formatClipZeros()
            }
        }
    }

    private fun observeDefaultMeal() {
        scope.launch {
            meals.collect { availableMeals ->
                val currentSelection = selectedMealId.value
                if (currentSelection != null && availableMeals.any { it.id == currentSelection }) {
                    return@collect
                }

                selectedMealId.value = availableMeals.firstOrNull()?.id
            }
        }
    }

    private fun observeDefaultDate() {
        scope.launch {
            today.collect { currentToday ->
                selectedDate.update { it ?: currentToday }
            }
        }
    }

    private fun ConsumeFromStashError.toUiError(): ConsumeStashItemError =
        when (this) {
            is ConsumeFromStashError.ItemNotFound -> ConsumeStashItemError.ItemNotFound
            ConsumeFromStashError.MealNotFound -> ConsumeStashItemError.MealNotFound
            ConsumeFromStashError.NonPositiveAmount,
            ConsumeFromStashError.InvalidAmount,
            ConsumeFromStashError.MissingRecipeBatchWeight,
            is ConsumeFromStashError.FoodNotFound,
            -> ConsumeStashItemError.InvalidAmount
            is ConsumeFromStashError.InsufficientQuantity ->
                ConsumeStashItemError.InsufficientQuantity(
                    available = this.available,
                    requested = this.requested,
                )
        }
}

internal sealed interface ConsumeStashItemEvent {
    data object Consumed : ConsumeStashItemEvent
}

private data class ConsumeStashItemStateInputs(
    val item: StashEntry?,
    val itemLoaded: Boolean,
    val amount: String,
    val itemDisplay: StashFoodDisplay?,
    val meals: List<Meal>,
    val selectedMealId: Long?,
)
