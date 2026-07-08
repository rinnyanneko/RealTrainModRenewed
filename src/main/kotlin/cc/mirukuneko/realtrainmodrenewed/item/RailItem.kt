package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import cc.mirukuneko.realtrainmodrenewed.rail.RailDefinition
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class RailItem : Item {
    constructor() : this(Properties())
    constructor(properties: Properties) : super(properties)

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        if (stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get()) != null)
            return InteractionResult.PASS
        if (level.isClientSide) ClientHooks.openRailSelectScreen(player, stack)
        return InteractionResult.SUCCESS
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val stack = context.itemInHand
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        val selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get())
        if (level.getBlockEntity(context.clickedPos) is MarkerBlockEntity) {
            if (!level.isClientSide) {
                val created = MarkerBlock.placeRailFromItem(level, context.clickedPos, player, stack, selectedId)
                if (created && !player.abilities.instabuild) stack.shrink(1)
                return if (created) InteractionResult.SUCCESS_SERVER else InteractionResult.FAIL
            }
            return InteractionResult.SUCCESS
        }

        if (stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get()) == null)
            return InteractionResult.PASS
        if (!level.isClientSide) {
            val placePos = context.clickedPos.relative(context.clickedFace)
            val created = MarkerBlock.placeCopiedRailAt(level, placePos, player, stack, selectedId)
            if (created && !player.abilities.instabuild) stack.shrink(1)
            return if (created) InteractionResult.SUCCESS else InteractionResult.FAIL
        }
        return InteractionResult.SUCCESS
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: net.minecraft.world.item.component.TooltipDisplay, lines: java.util.function.Consumer<Component>, flag: TooltipFlag) {
        val selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get())
        if (!selectedId.isNullOrBlank()) {
            val def = RailRegistry.getById(selectedId)
            val name = def?.displayName ?: selectedId
            lines.accept(Component.translatable("tooltip.realtrainmodrenewed.model.selected", name).withStyle(ChatFormatting.GRAY))
        } else {
            lines.accept(Component.translatable("tooltip.realtrainmodrenewed.model.none").withStyle(ChatFormatting.DARK_GRAY))
        }
        lines.accept(Component.translatable("tooltip.realtrainmodrenewed.rail.marker_use").withStyle(ChatFormatting.GRAY))
        lines.accept(Component.translatable("tooltip.realtrainmodrenewed.rail.preview_adjust").withStyle(ChatFormatting.DARK_GRAY))
    }
}
