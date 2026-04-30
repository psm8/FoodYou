package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.Meal
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashOwnerId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal class FakeStashRepository(
    initialStashes: List<StashDefinition> = emptyList(),
    initialItems: List<StashItem> = emptyList(),
    initialMovements: List<StashMovement> = emptyList(),
    private val failOnMovementInsertAttempt: Int? = null,
) : StashRepository, SnapshottingFakeTransactionProvider.StateRepository {
    private val stashes = initialStashes.associateBy { it.id.value }.toMutableMap()
    private val items = initialItems.associateBy { it.id.value }.toMutableMap()
    private val movements = initialMovements.associateBy { it.id.value }.toMutableMap()
    private var nextStashId = (stashes.keys.maxOrNull() ?: 0L) + 1L
    private var nextItemId = (items.keys.maxOrNull() ?: 0L) + 1L
    private var nextMovementId = (movements.keys.maxOrNull() ?: 0L) + 1L
    private var movementInsertAttempts = 0

    override fun observeStashes(ownerId: StashOwnerId): Flow<List<StashDefinition>> =
        flowOf(
            stashes.values
                .filter { it.ownerId == ownerId }
                .sortedWith(compareBy<StashDefinition>({ it.ordering }, { it.createdAt }, { it.id.value }))
        )

    override fun observeStashContents(stashId: StashDefinitionId): Flow<List<StashItem>> =
        flowOf(
            items.values
                .filter { it.stashId == stashId }
                .filter { it.quantity.amount > 0.0 }
                .sortedWith(compareByDescending(StashItem::createdAt).thenByDescending { it.id.value })
        )

    override fun observeMovementHistory(stashId: StashDefinitionId): Flow<List<StashMovement>> =
        flowOf(
            movements.values
                .filter { it.stashId == stashId }
                .sortedWith(compareByDescending(StashMovement::createdAt).thenByDescending { it.id.value })
        )

    override suspend fun getItem(id: StashItemId): StashItem? = items[id.value]

    override suspend fun getLinkedDiaryEntryMovements(
        linkedDiaryEntryId: com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
    ): List<StashMovement> =
        movements.values
            .filter { it.linkedDiaryEntryId == linkedDiaryEntryId }
            .sortedBy { it.id.value }

    override suspend fun insertStash(stash: StashDefinition): StashDefinitionId {
        val id = StashDefinitionId(nextStashId++)
        stashes[id.value] = stash.copy(id = id)
        return id
    }

    override suspend fun updateStash(stash: StashDefinition) {
        stashes[stash.id.value] = stash
    }

    override suspend fun deleteStash(id: StashDefinitionId) {
        stashes.remove(id.value)
    }

    override suspend fun insertItem(item: StashItem): StashItemId {
        val id = StashItemId(nextItemId++)
        items[id.value] = item.copy(id = id)
        return id
    }

    override suspend fun updateItem(item: StashItem) {
        items[item.id.value] = item
    }

    override suspend fun deleteItem(id: StashItemId) {
        items.remove(id.value)
    }

    override suspend fun insertMovement(movement: StashMovement): StashMovementId {
        movementInsertAttempts += 1
        if (failOnMovementInsertAttempt == movementInsertAttempts) {
            error("Failed to insert movement on attempt $movementInsertAttempts")
        }
        val id = StashMovementId(nextMovementId++)
        movements[id.value] = movement.copy(id = id)
        return id
    }

    fun allStashes(): List<StashDefinition> = stashes.values.sortedBy { it.id.value }

    fun allItems(): List<StashItem> = items.values.sortedBy { it.id.value }

    fun allMovements(): List<StashMovement> = movements.values.sortedBy { it.id.value }

    override fun snapshotState(): Any =
        FakeStashRepositoryState(
            stashes = stashes.toMap(),
            items = items.toMap(),
            movements = movements.toMap(),
            nextStashId = nextStashId,
            nextItemId = nextItemId,
            nextMovementId = nextMovementId,
            movementInsertAttempts = movementInsertAttempts,
        )

    override fun restoreState(state: Any) {
        val snapshot = state as FakeStashRepositoryState
        stashes.clear()
        stashes.putAll(snapshot.stashes)
        items.clear()
        items.putAll(snapshot.items)
        movements.clear()
        movements.putAll(snapshot.movements)
        nextStashId = snapshot.nextStashId
        nextItemId = snapshot.nextItemId
        nextMovementId = snapshot.nextMovementId
        movementInsertAttempts = snapshot.movementInsertAttempts
    }
}

internal data class FakeStashRepositoryState(
    val stashes: Map<Long, StashDefinition>,
    val items: Map<Long, StashItem>,
    val movements: Map<Long, StashMovement>,
    val nextStashId: Long,
    val nextItemId: Long,
    val nextMovementId: Long,
    val movementInsertAttempts: Int,
)

internal class FakeProductRepository(
    initialProducts: List<Product> = emptyList(),
) : ProductRepository {
    private val products = initialProducts.associateBy { it.id.id }.toMutableMap()
    private var nextId = (products.keys.maxOrNull() ?: 0L) + 1L

    override fun observeProduct(id: FoodId.Product): Flow<Product?> = flowOf(products[id.id])

    override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> =
        flowOf(products.values.sortedBy { it.id.id }.drop(offset).take(limit))

    override suspend fun insertProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
    ): FoodId.Product {
        val id = FoodId.Product(nextId++)
        products[id.id] =
            Product(
                id = id,
                name = name,
                brand = brand,
                barcode = barcode,
                note = note,
                isLiquid = isLiquid,
                packageWeight = packageWeight,
                servingWeight = servingWeight,
                source = source,
                nutritionFacts = nutritionFacts,
            )
        return id
    }

    override suspend fun insertUniqueProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
    ): FoodId.Product? = insertProduct(
        name = name,
        brand = brand,
        barcode = barcode,
        note = note,
        isLiquid = isLiquid,
        packageWeight = packageWeight,
        servingWeight = servingWeight,
        source = source,
        nutritionFacts = nutritionFacts,
    )

    override suspend fun updateProduct(product: Product) {
        products[product.id.id] = product
    }

    override suspend fun deleteProduct(product: Product) {
        products.remove(product.id.id)
    }
}

internal class FakeRecipeRepository(
    initialRecipes: List<Recipe> = emptyList(),
) : RecipeRepository {
    private val recipes = initialRecipes.associateBy { it.id.id }.toMutableMap()
    private var nextId = (recipes.keys.maxOrNull() ?: 0L) + 1L

    override fun observeRecipe(recipeId: FoodId.Recipe): Flow<Recipe?> = flowOf(recipes[recipeId.id])

    override suspend fun insertRecipe(
        name: String,
        servings: Int,
        note: String?,
        isLiquid: Boolean,
        ingredients: List<RecipeIngredient>,
    ): FoodId.Recipe {
        val id = FoodId.Recipe(nextId++)
        recipes[id.id] =
            Recipe(
                id = id,
                name = name,
                servings = servings,
                ingredients = ingredients,
                note = note,
                isLiquid = isLiquid,
            )
        return id
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        recipes[recipe.id.id] = recipe
    }

    override suspend fun deleteRecipe(recipe: Recipe) {
        recipes.remove(recipe.id.id)
    }
}

internal class FakeFoodDiaryEntryRepository(
    initialEntries: List<FoodDiaryEntry> = emptyList(),
) : FoodDiaryEntryRepository, SnapshottingFakeTransactionProvider.StateRepository {
    private val entries = initialEntries.associateBy { it.id.value }.toMutableMap()
    private var nextId = (entries.keys.maxOrNull() ?: 0L) + 1L

    override fun observe(id: FoodDiaryEntryId): Flow<FoodDiaryEntry?> = flowOf(entries[id.value])

    override fun observeAll(mealId: Long, date: LocalDate): Flow<List<FoodDiaryEntry>> =
        flowOf(entries.values.filter { it.mealId == mealId && it.date == date }.sortedBy { it.id.value })

    override suspend fun insert(
        measurement: Measurement,
        mealId: Long,
        date: LocalDate,
        food: DiaryFood,
        createdAt: LocalDateTime,
    ): FoodDiaryEntryId {
        val id = FoodDiaryEntryId(nextId++)
        entries[id.value] =
            FoodDiaryEntry(
                id = id,
                mealId = mealId,
                date = date,
                measurement = measurement,
                food = food,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        return id
    }

    override suspend fun update(entry: FoodDiaryEntry) {
        entries[entry.id.value] = entry
    }

    override suspend fun delete(id: FoodDiaryEntryId) {
        entries.remove(id.value)
    }

    fun allEntries(): List<FoodDiaryEntry> = entries.values.sortedBy { it.id.value }

    override fun snapshotState(): Any = FakeFoodDiaryEntryRepositoryState(entries.toMap(), nextId)

    override fun restoreState(state: Any) {
        val snapshot = state as FakeFoodDiaryEntryRepositoryState
        entries.clear()
        entries.putAll(snapshot.entries)
        nextId = snapshot.nextId
    }
}

internal data class FakeFoodDiaryEntryRepositoryState(
    val entries: Map<Long, FoodDiaryEntry>,
    val nextId: Long,
)

internal class FakeMealRepository(
    initialMeals: List<Meal> = emptyList(),
) : MealRepository {
    private val meals = initialMeals.associateBy { it.id }.toMutableMap()

    override fun observeMeal(mealId: Long): Flow<Meal?> = flowOf(meals[mealId])

    override fun observeMeals(): Flow<List<Meal>> = flowOf(meals.values.sortedBy(Meal::rank))

    override suspend fun insertMealWithLastRank(name: String, from: LocalTime, to: LocalTime) = Unit

    override suspend fun deleteMeal(mealId: Long) {
        meals.remove(mealId)
    }

    override suspend fun updateMeal(id: Long, name: String, from: LocalTime, to: LocalTime) = Unit

    override suspend fun reorderMeals(order: List<Long>) = Unit
}

internal class FixedDateProvider(
    private val now: LocalDateTime = FIXED_NOW,
) : DateProvider {
    override fun nowInstant(): Instant = now.toInstant(TimeZone.UTC)

    override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(nowInstant())

    override fun observeDate(timeZone: TimeZone): Flow<LocalDate> = flowOf(now.date)
}

internal class FakeTransactionProvider : TransactionProvider {
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
        try {
            block(FakeTransactionScope())
        } catch (exception: RollbackException) {
            exception.result as T
        }

    private class FakeTransactionScope<T> : TransactionScope<T> {
        override suspend fun rollback(result: T): Nothing = throw RollbackException(result)
    }

    private class RollbackException(val result: Any?) : RuntimeException()
}

internal class SnapshottingFakeTransactionProvider(
    private vararg val repositories: StateRepository,
) : TransactionProvider {
    interface StateRepository {
        fun snapshotState(): Any

        fun restoreState(state: Any)
    }

    private val mutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T =
        mutex.withLock {
            val snapshots = repositories.associateWith(StateRepository::snapshotState)
            try {
                block(FakeTransactionScope())
            } catch (exception: RollbackException) {
                snapshots.forEach { (repository, state) -> repository.restoreState(state) }
                exception.result as T
            } catch (exception: Throwable) {
                snapshots.forEach { (repository, state) -> repository.restoreState(state) }
                throw exception
            }
        }

    private class FakeTransactionScope<T> : TransactionScope<T> {
        override suspend fun rollback(result: T): Nothing = throw RollbackException(result)
    }

    private class RollbackException(val result: Any?) : RuntimeException()
}

internal object NoOpLogger : Logger {
    override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

    override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

    override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

    override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
}

internal fun localOwnerProvider(): StashOwnerProvider = StashOwnerProvider { StashOwnerId.Local }

internal fun sampleMeal(id: Long = 1L): Meal =
    Meal(
        id = id,
        name = "Lunch",
        from = LocalTime(12, 0),
        to = LocalTime(13, 0),
        rank = 1,
    )

internal fun sampleProduct(
    id: Long = 1L,
    isLiquid: Boolean = false,
    packageWeight: Double? = 1000.0,
    servingWeight: Double? = 250.0,
    name: String = "Skyr",
    brand: String? = "FoodYou",
    nutritionFacts: NutritionFacts = NutritionFacts.Empty,
): Product =
    Product(
        id = FoodId.Product(id),
        name = name,
        brand = brand,
        barcode = "1234567890",
        note = "Fresh",
        isLiquid = isLiquid,
        packageWeight = packageWeight,
        servingWeight = servingWeight,
        source = FoodSource(FoodSource.Type.User),
        nutritionFacts = nutritionFacts,
    )

internal fun sampleRecipe(
    id: Long = 1L,
    name: String = "Pizza",
    servings: Int = 4,
    totalWeight: Double = 400.0,
    isLiquid: Boolean = false,
): Recipe {
    val product =
        sampleProduct(
            id = 100L,
            packageWeight = totalWeight,
            servingWeight = totalWeight / servings,
            name = "Flour",
            brand = null,
            isLiquid = isLiquid,
        )
    return Recipe(
        id = FoodId.Recipe(id),
        name = name,
        servings = servings,
        ingredients = listOf(RecipeIngredient(product, Measurement.Gram(totalWeight))),
        note = "Bake hot",
        isLiquid = isLiquid,
    )
}

internal fun sampleStash(
    id: Long = 1L,
    name: String = "Stash",
    ordering: Int = 0,
): StashDefinition =
    StashDefinition(
        id = StashDefinitionId(id),
        ownerId = StashOwnerId.Local,
        name = StashName.from(name),
        createdAt = FIXED_NOW,
        ordering = ordering,
    )

internal fun sampleRawProductItem(
    id: Long = 1L,
    stashId: Long = 1L,
    quantity: StashQuantity = StashQuantity.grams(500.0),
    product: Product = sampleProduct(),
): StashItem =
    StashItem(
        id = StashItemId(id),
        stashId = StashDefinitionId(stashId),
        snapshot = RawProductSnapshot.from(product),
        measurement = null,
        quantity = quantity,
        createdAt = FIXED_NOW,
    )

internal fun sampleAnonymousDishItem(
    id: Long = 1L,
    stashId: Long = 1L,
    quantity: StashQuantity = StashQuantity.fraction(2.0),
    recipe: Recipe = sampleRecipe(),
    totalAmount: StashQuantity = StashQuantity.fraction(2.0),
    servingsMade: Int = 8,
): StashItem =
    StashItem(
        id = StashItemId(id),
        stashId = StashDefinitionId(stashId),
        snapshot =
            AnonymousDishSnapshot.from(
                recipe = recipe,
                totalAmount = totalAmount,
                servingsMade = servingsMade,
            ),
        measurement = null,
        quantity = quantity,
        createdAt = FIXED_NOW,
    )

internal val FIXED_NOW: LocalDateTime = LocalDateTime(2025, 1, 1, 12, 0)

internal fun nutritionWithEnergy(energy: Double): NutritionFacts =
    NutritionFacts(
        energy = NutrientValue.Complete(energy),
    )
