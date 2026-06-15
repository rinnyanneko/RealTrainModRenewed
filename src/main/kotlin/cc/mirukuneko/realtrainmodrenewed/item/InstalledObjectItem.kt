package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlocks
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class InstalledObjectItem(
    @JvmField val category: InstalledObjectCategory, properties: Properties = Properties().stacksTo(1)
) : Item(properties), ModelSelectableItem {
    companion object {
        private const val WALL_MOUNT_RAISE = 0.5
        private const val UPSIDE_DOWN_RAISE = 1.0
    }

    init {
        // Properties handled by super()
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) ClientHooks.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), category)
        return InteractionResult.SUCCESS
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        val stack = context.itemInHand
        val selectedId = LegacyItemStackBridge.getSelectedModelId(stack)
        val definition = InstalledObjectRegistry.getById(selectedId)
        if (definition == null || definition.category != category) {
            if (level.isClientSide) ClientHooks.openInstalledObjectSelectScreen(player, stack, category)
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }

        val clickedFace = context.clickedFace
        val placePos = context.clickedPos.relative(clickedFace)
        val state = level.getBlockState(placePos)
        if (!state.canBeReplaced()) return InteractionResult.FAIL

        var placeYaw = player.yRot
        var placeMountPitch = 0.0f
        var wallMounted = false
        var upsideDown = false
        if (category != InstalledObjectCategory.WIRE && category != InstalledObjectCategory.SIGNAL) {
            when {
                clickedFace == Direction.DOWN -> {
                    upsideDown = true; placeMountPitch = 180.0f
                }
                clickedFace.axis.isHorizontal -> {
                    wallMounted = true; placeYaw = clickedFace.opposite.toYRot(); placeMountPitch = 90.0f
                }
            }
        }
        if (!level.isClientSide) {
            level.setBlock(placePos, RealTrainModRenewedBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3)
            val be = level.getBlockEntity(placePos)
            if (be is InstalledObjectBlockEntity) {
                be.setDefinition(definition.id, category, placeYaw)
                be.setMountPitch(placeMountPitch)
                if (category == InstalledObjectCategory.SIGNAL) {
                    val yawRad = Math.toRadians(player.yRot.toDouble())
                    val faceX = clickedFace.stepX.toDouble()
                    val faceZ = clickedFace.stepZ.toDouble()
                    val facingDot = abs((-sin(yawRad) * faceX) + (cos(yawRad) * faceZ))
                    val embedDepth = if (facingDot < 0.85) 0.905 else 0.92
                    be.setRenderOffset(-faceX * embedDepth, -clickedFace.stepY.toDouble() * embedDepth, -faceZ * embedDepth)
                } else if (upsideDown) {
                    be.setRenderOffset(0.0, UPSIDE_DOWN_RAISE, 0.0)
                } else if (wallMounted) {
                    be.setRenderOffset(0.0, WALL_MOUNT_RAISE, 0.0)
                } else {
                    be.setRenderOffset(0.0, 0.0, 0.0)
                }
                level.sendBlockUpdated(placePos, be.blockState, be.blockState, 3)
            }
            if (!player.abilities.instabuild) stack.shrink(1)
        }
        return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
    }

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
        InstalledObjectRegistry.getByCategory(category).map {
            SelectableModelInfo(it.id, it.displayName, it.packName, it.buttonTexture)
        }
}
