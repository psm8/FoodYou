package com.maksimowiczm.foodyou.app.ui.stash.management

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.usecase.StashManagementOverview
import kotlinx.datetime.LocalDateTime

@Immutable
internal data class StashManagementUiState(
    val isLoading: Boolean = true,
    val stashes: List<StashManagementItemUi> = emptyList(),
    val draft: StashNameDraftState = StashNameDraftState(),
) {
    val sortedStashes: List<StashManagementItemUi>
        get() = stashes.sortedWith(compareBy<StashManagementItemUi>({ it.ordering }, { it.id.value }))
}

@Immutable
internal data class StashManagementItemUi(
    val id: StashDefinitionId,
    val name: String,
    val itemCount: Int,
    val lastModifiedAt: LocalDateTime,
    val ordering: Int,
) {
    companion object {
        fun from(overview: StashManagementOverview): StashManagementItemUi =
            StashManagementItemUi(
                id = overview.id,
                name = overview.name.value,
                itemCount = overview.itemCount,
                lastModifiedAt = overview.lastModifiedAt,
                ordering = overview.ordering,
            )
    }
}

@Immutable
internal data class StashNameDraftState(val value: String = "") {
    val normalizedValue: String
        get() = normalizeStashName(value)

    val canSave: Boolean
        get() = normalizedValue.isNotEmpty()
}

fun normalizeStashName(value: String): String = value.trim()

fun canSaveStashName(
    value: String,
    existingNames: Set<String>,
    currentName: String? = null,
): Boolean {
    val normalizedValue = normalizeStashName(value)
    if (normalizedValue.isEmpty()) {
        return false
    }

    val normalizedExistingNames = existingNames.map(::normalizeStashName).toSet()
    return normalizedValue == currentName?.let(::normalizeStashName) || normalizedValue !in normalizedExistingNames
}

internal fun List<StashManagementItemUi>.reordered(
    fromIndex: Int,
    toIndex: Int,
): List<StashManagementItemUi> {
    if (fromIndex !in indices) {
        return this
    }

    if (toIndex !in 0..size) {
        return this
    }

    if (fromIndex == toIndex) {
        return this
    }

    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal fun StashManagementUiState.withLoadedStashes(
    stashes: List<StashManagementOverview>,
): StashManagementUiState =
    copy(
        isLoading = false,
        stashes = stashes.map(StashManagementItemUi::from),
    )
