package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSession
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlinx.coroutines.flow.first

sealed interface AddToShoppingSessionError {
    data class ProductNotFound(val id: FoodId.Product) : AddToShoppingSessionError

    data object NonPositiveQuantity : AddToShoppingSessionError

    data object InvalidQuantityUnit : AddToShoppingSessionError
}

class AddToShoppingSessionUseCase(
    private val productRepository: ProductRepository,
    private val logger: Logger,
) {
    suspend fun add(
        session: ShoppingSession,
        productId: FoodId.Product,
        quantity: StashQuantity,
    ): Result<ShoppingSession, AddToShoppingSessionError> {
        if (quantity.amount <= 0.0) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddToShoppingSessionError.NonPositiveQuantity,
                message = { "Shopping session quantity must be greater than 0." },
            )
        }

        val product = productRepository.observeProduct(productId).first()
        if (product == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddToShoppingSessionError.ProductNotFound(productId),
                message = { "Product with id $productId not found." },
            )
        }

        if (!product.supportsStashQuantity(quantity)) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddToShoppingSessionError.InvalidQuantityUnit,
                message = { "Product ${product.id} does not support quantity unit ${quantity.unit}." },
            )
        }

        return Ok(session.add(productId = product.id, snapshot = RawProductSnapshot.from(product), quantity = quantity))
    }

    private companion object {
        const val TAG = "AddToShoppingSessionUseCase"
    }
}
