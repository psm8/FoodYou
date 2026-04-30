package com.maksimowiczm.foodyou.stash.domain

import com.maksimowiczm.foodyou.stash.domain.usecase.AddProductToStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.AddToShoppingSessionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.AdjustStashItemQuantityUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.AssessRecipeStashAvailabilityUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ConfirmShoppingSessionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ConsumeFromStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateAnonymousDishSnapshotUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateManualStashSnapshotUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.DeleteStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.LoadStashManagementStashesUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.LogRecipeToMealWithStashSubtractionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.MoveStashItemUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ObserveHomeStashSummaryUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.RemoveStashItemUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.RenameStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ReorderStashesUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.RestoreLinkedDiaryEntryStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ReturnPartialMealToStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.SelectStashItemToConsumeUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.StartShoppingSessionUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf

fun Module.stashDomainModule() {
    factoryOf(::CreateStashUseCase)
    factoryOf(::RenameStashUseCase)
    factoryOf(::ReorderStashesUseCase)
    factoryOf(::DeleteStashUseCase)
    factoryOf(::LoadStashManagementStashesUseCase)
    factoryOf(::ObserveHomeStashSummaryUseCase)
    factoryOf(::AddProductToStashUseCase)
    factoryOf(::StartShoppingSessionUseCase)
    factoryOf(::AddToShoppingSessionUseCase)
    factoryOf(::ConfirmShoppingSessionUseCase)
    factoryOf(::CreateAnonymousDishSnapshotUseCase)
    factoryOf(::CreateManualStashSnapshotUseCase)
    factoryOf(::AssessRecipeStashAvailabilityUseCase)
    factoryOf(::LogRecipeToMealWithStashSubtractionUseCase)
    factoryOf(::RestoreLinkedDiaryEntryStashUseCase)
    factoryOf(::ReturnPartialMealToStashUseCase)
    factoryOf(::SelectStashItemToConsumeUseCase)
    factoryOf(::ConsumeFromStashUseCase)
    factoryOf(::AdjustStashItemQuantityUseCase)
    factoryOf(::RemoveStashItemUseCase)
    factoryOf(::MoveStashItemUseCase)
}
