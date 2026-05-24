package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.usecase.ManualStashAction
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StashBrowserStateActionTest {
    @Test
    fun `when starting remove action, it opens remove dialog for the item`() {
        val item = browserItem(id = 1L, name = "Bread")

        val state = browserState().showRemoveDialog(item)

        val dialog = assertIs<StashBrowserActionDialog.Remove>(state.actionDialog)
        assertEquals(item.id, dialog.item.id)
    }

    @Test
    fun `when starting manual adjust action, it pre-fills the amount from the item`() {
        val item = browserItem(id = 1L, quantity = StashMeasurement.grams(250.0))

        val state = browserState().showManualAdjustDialog(item)

        val dialog = assertIs<StashBrowserActionDialog.ManualAdjust>(state.actionDialog)
        assertEquals("250.0", dialog.amount)
        assertEquals(StashBrowserAdjustMode.ChangeBy, dialog.mode)
        assertEquals("", dialog.reason)
    }

    @Test
    fun `when starting move action, it selects the first available target by default`() {
        val item = browserItem(id = 1L)
        val target = StashBrowserMoveTarget(id = StashDefinitionId(2L), name = "Pantry")

        val state = browserState(moveTargets = listOf(target)).showMoveDialog(item)

        val dialog = assertIs<StashBrowserActionDialog.Move>(state.actionDialog)
        assertEquals(target.id, dialog.targetStashId)
    }

    @Test
    fun `when building manual adjust action, it keeps entered reason and note`() {
        val dialog =
            StashBrowserActionDialog.ManualAdjust(
                item = browserItem(id = 1L),
                reason = " Pantry recount ",
                note = " Settled after cleanup ",
            )

        val action = dialog.toManualAction()

        assertEquals(ManualStashAction(reason = "Pantry recount", note = "Settled after cleanup"), action)
    }

    @Test
    fun `when building move action without reason, it defaults to moving to selected stash`() {
        val dialog =
            StashBrowserActionDialog.Move(
                item = browserItem(id = 1L),
                targetStashId = StashDefinitionId(2L),
                note = "Top shelf",
            )

        val action = dialog.toManualAction(targetStashName = "Pantry")

        assertEquals(ManualStashAction(reason = "Move to Pantry", note = "Top shelf"), action)
    }

    private fun browserState(moveTargets: List<StashBrowserMoveTarget> = emptyList()) =
        StashBrowserState(stashId = sampleRawProductItem().stashId, moveTargets = moveTargets)

    private fun browserItem(
        id: Long = 1L,
        name: String = "Item",
        quantity: StashMeasurement = StashMeasurement.grams(1.0),
    ) =
        StashBrowserItem(
            id = com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId(id),
            name = name,
            isNameLoading = false,
            quantity = quantity,
            createdAt = com.maksimowiczm.foodyou.stash.domain.usecase.FIXED_NOW,
            stashName = "Stash",
            type = StashBrowserItemType.Product,
        )
}
