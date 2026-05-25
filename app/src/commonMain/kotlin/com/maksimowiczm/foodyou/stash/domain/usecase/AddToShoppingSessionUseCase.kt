package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSession
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionProductDetails
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import kotlinx.coroutines.flow.first

sealed interface AddToShoppingSessionError {
    data class ProductNotFound(val id: FoodId.Product) : AddToShoppingSessionError

    data object UnsupportedMeasurement : AddToShoppingSessionError
}

class AddToShoppingSessionUseCase(
    private val productRepository: ProductRepository,
    private val logger: Logger,
) {
    suspend fun add(
        session: ShoppingSession,
        productId: FoodId.Product,
        measurement: Measurement,
    ): Result<ShoppingSession, AddToShoppingSessionError> {
        val product = productRepository.observeProduct(productId).first()
        if (product == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddToShoppingSessionError.ProductNotFound(productId),
                message = { "Product with id $productId not found." },
            )
        }

        if (!product.supportsStashMeasurement(measurement)) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddToShoppingSessionError.UnsupportedMeasurement,
                message = { "Product ${product.id} does not support measurement type ${measurement.type}." },
            )
        }

        val stashMeasurement = product.toStashMeasurementOrNull(measurement)
        if (stashMeasurement == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddToShoppingSessionError.UnsupportedMeasurement,
                message = { "Product ${product.id} measurement $measurement is not valid." },
            )
        }

        return Ok(
            session.add(
                productId = product.id,
                foodRef = StashFoodRef.Product(product.id),
                productDetails = ShoppingSessionProductDetails.from(product),
                measurement = stashMeasurement,
            )
        )
    }

    private companion object {
        const val TAG = "AddToShoppingSessionUseCase"
    }
}
