package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateAnonymousDishSnapshotError
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateAnonymousDishSnapshotUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class HomeStashRecipeSnapshotViewModel(
    private val recipeId: FoodId.Recipe,
    preferredStashId: StashDefinitionId?,
    recipeRepository: RecipeRepository,
    stashRepository: StashRepository,
    stashOwnerProvider: StashOwnerProvider,
    private val createAnonymousDishSnapshotUseCase: CreateAnonymousDishSnapshotUseCase,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val ownerId = stashOwnerProvider.current()
    private val totalAmount = MutableStateFlow("")
    private val servingsMade = MutableStateFlow("")
    private val amountUnit = MutableStateFlow(StashQuantityUnit.Fraction)
    private val selectedStashId = MutableStateFlow(preferredStashId)
    private val error = MutableStateFlow<HomeStashRecipeSnapshotError?>(null)
    private val eventBus = Channel<HomeStashRecipeSnapshotEvent>()

    private val recipe = recipeRepository.observeRecipe(recipeId)
    private val stashes =
        stashRepository.observeStashes(ownerId).map { list ->
            list.sortedWith(compareBy({ it.ordering }, { it.id.value })).map {
                HomeStashRecipeSnapshotStash(id = it.id, name = it.name.value)
            }
        }
    private val formInputs =
        combine(totalAmount, servingsMade, amountUnit) { totalAmount, servingsMade, amountUnit ->
            RecipeSnapshotFormInputs(
                totalAmount = totalAmount,
                servingsMade = servingsMade,
                amountUnit = amountUnit,
            )
        }

    val events = eventBus.receiveAsFlow()

    val state =
        combine(recipe, stashes, formInputs, selectedStashId) {
                recipe,
                stashes,
                formInputs,
                selectedStashId ->
                val resolvedSelectedStashId =
                    when {
                        selectedStashId != null && stashes.any { it.id == selectedStashId } ->
                            selectedStashId
                        stashes.size == 1 -> stashes.single().id
                        else -> null
                    }
                val availableUnits = recipe?.supportedAmountUnits() ?: listOf(StashQuantityUnit.Fraction)
                val resolvedAmountUnit =
                    formInputs.amountUnit.takeIf(availableUnits::contains) ?: availableUnits.first()
                val resolvedServingsMade =
                    formInputs.servingsMade.ifBlank { recipe?.servings?.toString().orEmpty() }
                val parsedQuantity = formInputs.totalAmount.toQuantityOrNull(resolvedAmountUnit)
                val parsedServings = resolvedServingsMade.toPositiveIntOrNull()
                val previewSnapshot =
                    if (recipe != null && parsedQuantity != null && parsedServings != null) {
                        AnonymousDishSnapshot.from(
                            recipe = recipe,
                            totalAmount = parsedQuantity,
                            servingsMade = parsedServings,
                        )
                    } else {
                        null
                    }

                HomeStashRecipeSnapshotState(
                    recipeId = recipeId,
                    recipeName = recipe?.name.orEmpty(),
                    isLoading = false,
                    isRecipeMissing = recipe == null,
                    totalAmount = formInputs.totalAmount,
                    servingsMade = resolvedServingsMade,
                    amountUnit = resolvedAmountUnit,
                    availableUnits = availableUnits,
                    stashes = stashes,
                    selectedStashId = resolvedSelectedStashId,
                    previewNutritionFacts = previewSnapshot?.totalNutritionFacts,
                )
            }
            .combine(error) { state, error ->
                state.copy(
                    error =
                        if (state.isRecipeMissing) {
                            HomeStashRecipeSnapshotError.RecipeNotFound
                        } else {
                            error
                        }
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = HomeStashRecipeSnapshotState(recipeId = recipeId),
            )

    fun updateTotalAmount(value: String) {
        totalAmount.value = value
        clearRecoverableError(HomeStashRecipeSnapshotError.InvalidAmount)
    }

    fun updateServingsMade(value: String) {
        servingsMade.value = value
        clearRecoverableError(HomeStashRecipeSnapshotError.InvalidServings)
    }

    fun selectAmountUnit(unit: StashQuantityUnit) {
        amountUnit.value = unit
        clearRecoverableError(HomeStashRecipeSnapshotError.InvalidAmount)
    }

    fun selectStash(stashId: StashDefinitionId) {
        selectedStashId.value = stashId
        clearRecoverableError(HomeStashRecipeSnapshotError.StashSelectionRequired)
    }

    fun save() {
        val currentState = state.value
        if (currentState.isLoading) {
            return
        }
        if (currentState.isRecipeMissing) {
            error.value = HomeStashRecipeSnapshotError.RecipeNotFound
            return
        }
        val quantity =
            currentState.parsedQuantity ?: run {
                error.value = HomeStashRecipeSnapshotError.InvalidAmount
                return
            }
        val servings =
            currentState.parsedServings ?: run {
                error.value = HomeStashRecipeSnapshotError.InvalidServings
                return
            }

        scope.launch {
            val result =
                createAnonymousDishSnapshotUseCase.create(
                    recipeId = recipeId,
                    stashId = currentState.selectedStashId,
                    totalAmount = quantity,
                    servings = servings,
                )

            when (result) {
                is Result.Success -> {
                    error.value = null
                    eventBus.send(HomeStashRecipeSnapshotEvent.Saved(result.data.stashId))
                }

                is Result.Error -> {
                    error.value = result.error.toUiError()
                }
            }
        }
    }

    private fun clearRecoverableError(candidate: HomeStashRecipeSnapshotError) {
        if (error.value == candidate || error.value == HomeStashRecipeSnapshotError.SaveFailed) {
            error.value = null
        }
    }

    private fun CreateAnonymousDishSnapshotError.toUiError(): HomeStashRecipeSnapshotError =
        when (this) {
            is CreateAnonymousDishSnapshotError.RecipeNotFound ->
                HomeStashRecipeSnapshotError.RecipeNotFound
            is CreateAnonymousDishSnapshotError.StashNotFound ->
                HomeStashRecipeSnapshotError.SaveFailed
            CreateAnonymousDishSnapshotError.StashSelectionRequired ->
                HomeStashRecipeSnapshotError.StashSelectionRequired
            CreateAnonymousDishSnapshotError.NonPositiveQuantity ->
                HomeStashRecipeSnapshotError.InvalidAmount
            CreateAnonymousDishSnapshotError.NonPositiveServings ->
                HomeStashRecipeSnapshotError.InvalidServings
        }
}

@Immutable
internal data class HomeStashRecipeSnapshotState(
    val recipeId: FoodId.Recipe,
    val recipeName: String = "",
    val isLoading: Boolean = true,
    val isRecipeMissing: Boolean = false,
    val totalAmount: String = "",
    val servingsMade: String = "",
    val amountUnit: StashQuantityUnit = StashQuantityUnit.Fraction,
    val availableUnits: List<StashQuantityUnit> = listOf(StashQuantityUnit.Fraction),
    val stashes: List<HomeStashRecipeSnapshotStash> = emptyList(),
    val selectedStashId: StashDefinitionId? = null,
    val previewNutritionFacts: NutritionFacts? = null,
    val error: HomeStashRecipeSnapshotError? = null,
) {
    val requiresStashSelection: Boolean
        get() = stashes.size > 1

    val parsedQuantity: StashQuantity?
        get() = totalAmount.toQuantityOrNull(amountUnit)

    val parsedServings: Int?
        get() = servingsMade.toPositiveIntOrNull()

    val canSave: Boolean
        get() =
            !isLoading &&
                !isRecipeMissing &&
                parsedQuantity != null &&
                parsedServings != null &&
                (!requiresStashSelection || selectedStashId != null)
}

@Immutable
internal data class HomeStashRecipeSnapshotStash(val id: StashDefinitionId, val name: String)

internal enum class HomeStashRecipeSnapshotError {
    RecipeNotFound,
    InvalidAmount,
    InvalidServings,
    StashSelectionRequired,
    SaveFailed,
}

internal sealed interface HomeStashRecipeSnapshotEvent {
    data class Saved(val stashId: StashDefinitionId) : HomeStashRecipeSnapshotEvent
}

private val AnonymousDishSnapshot.totalNutritionFacts: NutritionFacts
    get() = nutritionFacts * (totalWeight / 100.0)

private fun String.toQuantityOrNull(unit: StashQuantityUnit): StashQuantity? {
    val parsedAmount = toDoubleOrNull() ?: return null
    if (parsedAmount <= 0.0) {
        return null
    }

    return when (unit) {
        StashQuantityUnit.Gram -> StashQuantity.grams(parsedAmount)
        StashQuantityUnit.Milliliter -> StashQuantity.milliliters(parsedAmount)
        StashQuantityUnit.Fraction -> StashQuantity.fraction(parsedAmount)
    }
}

private fun String.toPositiveIntOrNull(): Int? {
    val parsedValue = toIntOrNull() ?: return null
    return parsedValue.takeIf { it > 0 }
}

private fun Recipe.supportedAmountUnits(): List<StashQuantityUnit> =
    listOf(
        StashQuantityUnit.Fraction,
        if (isLiquid) {
            StashQuantityUnit.Milliliter
        } else {
            StashQuantityUnit.Gram
        },
    )

private data class RecipeSnapshotFormInputs(
    val totalAmount: String,
    val servingsMade: String,
    val amountUnit: StashQuantityUnit,
)
