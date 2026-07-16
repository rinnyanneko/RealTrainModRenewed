// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.item

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.block.Block
import java.util.function.Consumer

class MarkerItem : BlockItem {
    @JvmField val diagonal: Boolean

    constructor(block: Block, diagonal: Boolean) : this(block, diagonal, Properties())
    constructor(block: Block, diagonal: Boolean, properties: Properties) : super(block, properties) {
        this.diagonal = diagonal
    }

    @Deprecated("Overrides Minecraft's deprecated tooltip extension hook")
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: net.minecraft.world.item.component.TooltipDisplay, tooltip: Consumer<Component>, flag: TooltipFlag) {
        tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.marker.place_pair").withStyle(ChatFormatting.GRAY))
        tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.marker.configure").withStyle(ChatFormatting.DARK_GRAY))
        tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.marker.build").withStyle(ChatFormatting.DARK_GRAY))
    }
}
