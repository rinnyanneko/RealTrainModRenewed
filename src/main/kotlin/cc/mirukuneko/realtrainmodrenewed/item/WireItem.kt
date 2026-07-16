// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlocks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class WireItem : Item, ModelSelectableItem {
    constructor() : this(Properties())
    constructor(properties: Properties) : super(properties)

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) ClientHooks.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), InstalledObjectCategory.WIRE)
        return InteractionResult.SUCCESS
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        val stack = context.itemInHand
        val selectedId = LegacyItemStackBridge.getSelectedModelId(stack)
        val definition = InstalledObjectRegistry.getById(selectedId)
        if (definition == null || definition.category != InstalledObjectCategory.WIRE) {
            if (level.isClientSide) ClientHooks.openInstalledObjectSelectScreen(player, stack, InstalledObjectCategory.WIRE)
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }

        val clickedPos = context.clickedPos
        val clicked = level.getBlockEntity(clickedPos)
        if (clicked !is InstalledObjectBlockEntity
            || (clicked.category != InstalledObjectCategory.INSULATOR && clicked.category != InstalledObjectCategory.OVERHEAD_LINE_POLE)) {
            if (!level.isClientSide) player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.wire.insulators_only"))
            return InteractionResult.FAIL
        }

        val startTag = stack.get(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get())
        if (startTag == null || !startTag.contains("X")) {
            if (!level.isClientSide) {
                val tag = CompoundTag()
                tag.putInt("X", clickedPos.x); tag.putInt("Y", clickedPos.y); tag.putInt("Z", clickedPos.z)
                stack.set(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get(), tag)
                player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.wire.start_selected"))
            }
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }

        val startPos = BlockPos(NbtCompat.getInt(startTag, "X"), NbtCompat.getInt(startTag, "Y"), NbtCompat.getInt(startTag, "Z"))
        if (startPos == clickedPos) {
            if (!level.isClientSide) {
                stack.remove(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get())
                player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.wire.selection_cleared"))
            }
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }

        val mid = BlockPos((startPos.x + clickedPos.x) shr 1, (startPos.y + clickedPos.y) shr 1, (startPos.z + clickedPos.z) shr 1)
        if (!level.getBlockState(mid).canBeReplaced()) {
            if (!level.isClientSide) player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.wire.obstructed"))
            return InteractionResult.FAIL
        }

        if (!level.isClientSide) {
            level.setBlock(mid, RealTrainModRenewedBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3)
            val be = level.getBlockEntity(mid)
            if (be is InstalledObjectBlockEntity) {
                be.setDefinition(definition.id, InstalledObjectCategory.WIRE, player.yRot)
                be.setWireEndpoints(startPos, clickedPos)
                level.sendBlockUpdated(mid, be.blockState, be.blockState, 3)
            }
            stack.remove(RealTrainModRenewedComponents.WIRE_PLACEMENT_START.get())
            if (!player.abilities.instabuild) stack.shrink(1)
            player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.wire.placed"))
        }
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

    @Deprecated("Overrides Minecraft's deprecated tooltip extension hook")
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: net.minecraft.world.item.component.TooltipDisplay, lines: java.util.function.Consumer<Component>, flag: TooltipFlag) {
        val selectedId = LegacyItemStackBridge.getSelectedModelId(stack)
        if (!selectedId.isNullOrBlank()) {
            val def = InstalledObjectRegistry.getById(selectedId)
            val name = def?.displayName ?: selectedId
            lines.accept(Component.translatable("tooltip.realtrainmodrenewed.model.selected", name).withStyle(ChatFormatting.GRAY))
        } else {
            lines.accept(Component.translatable("tooltip.realtrainmodrenewed.model.none").withStyle(ChatFormatting.DARK_GRAY))
        }
    }

    override fun getSelectableModels(): List<SelectableModelInfo> =
        InstalledObjectRegistry.getByCategory(InstalledObjectCategory.WIRE).map {
            SelectableModelInfo(it.id, it.displayName, it.packName, it.buttonTexture)
        }
}
