// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.block.SignalConverterBlock
import cc.mirukuneko.realtrainmodrenewed.electric.SignalConverterType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState

class SignalConverterItem(
    block: SignalConverterBlock,
    private val converterType: SignalConverterType,
    properties: Properties,
) : BlockItem(block, properties) {
    override fun getPlacementState(context: BlockPlaceContext): BlockState? =
        super.getPlacementState(context)?.setValue(SignalConverterBlock.TYPE, converterType.id)
}
