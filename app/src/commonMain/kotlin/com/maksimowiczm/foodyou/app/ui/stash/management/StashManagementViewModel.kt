package com.maksimowiczm.foodyou.app.ui.stash.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.result.onSuccess
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.DeleteStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.LoadStashManagementStashesUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.RenameStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ReorderStashesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class StashManagementViewModel(
    private val loadStashManagementStashesUseCase: LoadStashManagementStashesUseCase,
    private val createStashUseCase: CreateStashUseCase,
    private val renameStashUseCase: RenameStashUseCase,
    private val deleteStashUseCase: DeleteStashUseCase,
    private val reorderStashesUseCase: ReorderStashesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(StashManagementUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val stashes = loadStashManagementStashesUseCase.load()
            _state.update { current -> current.withLoadedStashes(stashes) }
        }
    }

    fun updateDraft(value: String) {
        _state.update { it.copy(draft = StashNameDraftState(value)) }
    }

    fun createStash() {
        val draft = state.value.draft
        viewModelScope.launch {
            createStashUseCase.create(draft.value).onSuccess {
                refresh()
                updateDraft("")
            }
        }
    }

    fun renameStash(
        stashId: StashDefinitionId,
        name: String,
    ) {
        viewModelScope.launch {
            renameStashUseCase.rename(stashId, name).onSuccess { refresh() }
        }
    }

    fun deleteStash(stashId: StashDefinitionId) {
        viewModelScope.launch {
            deleteStashUseCase.delete(stashId).onSuccess { refresh() }
        }
    }

    fun reorderStashes(order: List<StashDefinitionId>) {
        viewModelScope.launch {
            reorderStashesUseCase.reorder(order).onSuccess { refresh() }
        }
    }
}
