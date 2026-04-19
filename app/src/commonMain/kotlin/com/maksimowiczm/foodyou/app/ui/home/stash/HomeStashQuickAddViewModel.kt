package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateManualStashSnapshotError
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateManualStashSnapshotUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class HomeStashQuickAddViewModel(
    preferredStashId: StashDefinitionId?,
    stashRepository: StashRepository,
    stashOwnerProvider: StashOwnerProvider,
    private val createManualStashSnapshotUseCase: CreateManualStashSnapshotUseCase,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val ownerId = stashOwnerProvider.current()
    private val selectedStashId = MutableStateFlow(preferredStashId)
    private val error = MutableStateFlow<HomeStashQuickAddError?>(null)
    private val eventBus = Channel<HomeStashQuickAddEvent>()

    private val stashes =
        stashRepository.observeStashes(ownerId).map { list ->
            list.sortedWith(compareBy({ it.ordering }, { it.id.value })).map {
                HomeStashQuickAddStash(id = it.id, name = it.name.value)
            }
        }

    val events = eventBus.receiveAsFlow()

    val state =
        combine(stashes, selectedStashId, error) { stashes, selectedStashId, error ->
                val resolvedSelectedStashId =
                    when {
                        selectedStashId != null && stashes.any { it.id == selectedStashId } ->
                            selectedStashId
                        stashes.size == 1 -> stashes.single().id
                        else -> null
                    }

                HomeStashQuickAddState(
                    isLoading = false,
                    stashes = stashes,
                    selectedStashId = resolvedSelectedStashId,
                    error = error,
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = HomeStashQuickAddState(),
            )

    fun selectStash(stashId: StashDefinitionId) {
        selectedStashId.value = stashId
        if (error.value == HomeStashQuickAddError.StashSelectionRequired) {
            error.value = null
        }
    }

    fun save(name: String, nutritionFacts: NutritionFacts, measurement: Measurement) {
        val currentState = state.value
        if (currentState.requiresStashSelection && currentState.selectedStashId == null) {
            error.value = HomeStashQuickAddError.StashSelectionRequired
            return
        }

        scope.launch {
            val result =
                createManualStashSnapshotUseCase.create(
                    name = name,
                    nutritionFacts = nutritionFacts,
                    measurement = measurement,
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

    private fun CreateManualStashSnapshotError.toUiError(): HomeStashQuickAddError =
        when (this) {
            CreateManualStashSnapshotError.InvalidMeasurement ->
                HomeStashQuickAddError.InvalidMeasurement
            CreateManualStashSnapshotError.StashSelectionRequired ->
                HomeStashQuickAddError.StashSelectionRequired
            is CreateManualStashSnapshotError.StashNotFound,
            CreateManualStashSnapshotError.InvalidName -> HomeStashQuickAddError.SaveFailed
        }
}

@Immutable
internal data class HomeStashQuickAddState(
    val isLoading: Boolean = true,
    val stashes: List<HomeStashQuickAddStash> = emptyList(),
    val selectedStashId: StashDefinitionId? = null,
    val error: HomeStashQuickAddError? = null,
) {
    val requiresStashSelection: Boolean
        get() = stashes.size > 1
}

@Immutable internal data class HomeStashQuickAddStash(val id: StashDefinitionId, val name: String)

internal enum class HomeStashQuickAddError {
    InvalidMeasurement,
    StashSelectionRequired,
    SaveFailed,
}

internal sealed interface HomeStashQuickAddEvent {
    data class Saved(val stashId: StashDefinitionId) : HomeStashQuickAddEvent
}
