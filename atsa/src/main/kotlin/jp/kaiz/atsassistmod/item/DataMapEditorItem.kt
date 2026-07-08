// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.item

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * DataMap editor tool. The original opened a GUI when sneak-right-clicking a block.
 * The editor screen is wired in the GUI stage (TODO).
 */
open class DataMapEditorItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        if (context.level.isClientSide && context.player != null && context.player!!.isShiftKeyDown) {
            // TODO(gui): open GUIDataMapEditor for the clicked position.
        }
        return InteractionResult.PASS
    }
}
