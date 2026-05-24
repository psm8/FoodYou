package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
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
    fun `when starting manual adjust action, it pre-fills the amount from the item and defaults to SetTo`() {
        val item = browserItem(id = 1L, quantity = StashMeasurement.grams(250.0))

        val state = browserState().showManualAdjustDialog(item)

        val dialog = assertIs<StashBrowserActionDialog.ManualAdjust>(state.actionDialog)
        assertEquals("250.0", dialog.amount)
        assertEquals(StashBrowserAdjustMode.SetTo, dialog.mode)
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
    fun `when switching mode from SetTo to ChangeBy, amount resets to empty`() {
        val item = browserItem(id = 1L, quantity = StashMeasurement.grams(250.0))
        val state = browserState().showManualAdjustDialog(item)

        val updated = state.updateManualAdjustMode(StashBrowserAdjustMode.ChangeBy)

        val dialog = assertIs<StashBrowserActionDialog.ManualAdjust>(updated.actionDialog)
        assertEquals(StashBrowserAdjustMode.ChangeBy, dialog.mode)
        assertEquals("", dialog.amount)
    }

    @Test
    fun `when switching mode from ChangeBy to SetTo, amount resets to item current quantity`() {
        val item = browserItem(id = 1L, quantity = StashMeasurement.grams(250.0))
        val state = browserState()
            .showManualAdjustDialog(item)
            .updateManualAdjustMode(StashBrowserAdjustMode.ChangeBy)

        val updated = state.updateManualAdjustMode(StashBrowserAdjustMode.SetTo)

        val dialog = assertIs<StashBrowserActionDialog.ManualAdjust>(updated.actionDialog)
        assertEquals(StashBrowserAdjustMode.SetTo, dialog.mode)
        assertEquals("250.0", dialog.amount)
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