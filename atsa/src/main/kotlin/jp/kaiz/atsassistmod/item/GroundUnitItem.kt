// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.item

import jp.kaiz.atsassistmod.block.GroundUnitBlock
import jp.kaiz.atsassistmod.block.GroundUnitType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * One creative item per ground-unit variant. Places the ground-unit block with
 * [GroundUnitBlock.TYPE] set.
 */
open class GroundUnitItem(
    block: Block,
    private val type: GroundUnitType,
    properties: Properties,
) : BlockItem(block, properties) {
    fun getType(): GroundUnitType = type

    override fun getPlacementState(context: BlockPlaceContext): BlockState? =
        super.getPlacementState(context)?.setValue(GroundUnitBlock.TYPE, type.id)
}
