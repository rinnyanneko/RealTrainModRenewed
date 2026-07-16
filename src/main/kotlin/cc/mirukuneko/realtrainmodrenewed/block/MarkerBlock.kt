// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.rail.math.CurveMath
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlocks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import cc.mirukuneko.realtrainmodrenewed.item.MarkerItem
import cc.mirukuneko.realtrainmodrenewed.item.RailItem
import cc.mirukuneko.realtrainmodrenewed.item.WrenchItem
import cc.mirukuneko.realtrainmodrenewed.rail.RailDefinition
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMapBasic
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMaker
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailProperties
import cc.mirukuneko.realtrainmodrenewed.rail.util.SwitchType
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs

class MarkerBlock(val isSwitch: Boolean, properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {
    companion object {
        val CODEC: MapCodec<MarkerBlock> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.BOOL.fieldOf("is_switch").forGetter { it.isSwitch },
                propertiesCodec()
            ).apply(instance) { sw, props -> MarkerBlock(sw, props) }
        }
        val FACING: IntegerProperty = IntegerProperty.create("facing", 0, 7)
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0)
        const val SEARCH_DISTANCE = 80
        const val SEARCH_HEIGHT = 10

        @JvmStatic fun computeFacing(player: Player): Int = computeFacing(player, false)
        @JvmStatic fun computeFacing(player: Player, diagonal: Boolean): Int {
            val yaw = Mth.positiveModulo(player.yRot + 180f, 360f)
            val base = if (diagonal) Mth.floor(yaw / 90f) and 3 else Mth.floor(yaw / 90f + 0.5f) and 3
            return base + if (diagonal) 4 else 0
        }
        @JvmStatic fun getMarkerDir(facing: Int): Int {
            val i0 = facing and 3
            var i1 = ((6 - i0) and 3) * 2
            if ((facing and 4) != 0) i1 = (i1 + 7) and 7
            return i1
        }

        @JvmStatic
        fun placeRailFromItem(level: Level, pos: BlockPos, player: Player, stack: ItemStack, selectedModelId: String?): Boolean {
            var previewStack = stack
            var startTag = stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get())
            if (startTag == null) {
                val altStack = WrenchItem.findPlayerPreviewStack(player)
                val altTag = if (altStack.isEmpty) null else altStack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get())
                if (altTag != null && NbtCompat.getBoolean(altTag, "WrenchMode")) { previewStack = altStack; startTag = altTag }
            }
            if (startTag == null || !startTag.contains("X")) {
                val mBlock = level.getBlockState(pos).block as? MarkerBlock ?: return false
                if (mBlock.searchAllMarkers(level, pos).size >= 2) {
                    val created = mBlock.onMarkerActivated(level, pos, player, true, selectedModelId)
                    if (created) player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.connected"))
                    return created
                }
                player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.not_enough_markers"))
                return false
            }

            val startPos = BlockPos(NbtCompat.getInt(startTag, "X"), NbtCompat.getInt(startTag, "Y"), NbtCompat.getInt(startTag, "Z"))
            val branchMode = NbtCompat.getBoolean(startTag, "BranchMode")
            val wrenchMode = NbtCompat.getBoolean(startTag, "WrenchMode") && (startTag.contains("EndRP") || startTag.contains("RailSegments"))
            if (startPos == pos && !wrenchMode) { previewStack.remove(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get()); player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.preview_cleared")); return false }

            val endBe = level.getBlockEntity(pos)
            val startBe = level.getBlockEntity(startPos)
            if (!wrenchMode && endBe !is MarkerBlockEntity) { player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.marker_missing")); return false }
            if (wrenchMode && startBe !is MarkerBlockEntity && startBe !is LargeRailCoreBlockEntity && !startTag.contains("StartRP")) { player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.copy_source_missing")); return false }

            val start = resolvePreviewStart(startBe, startTag) ?: return false
            val prop = createRailProperties(player, selectedModelId)
            val created = if (wrenchMode) {
                createRailsFromWrenchPreview(level, startPos, start, startTag, prop, player.abilities.instabuild, selectedModelId)
            } else {
                val end = (endBe as MarkerBlockEntity).markerRP ?: return false
                val adjustedEnd = applyPreviewOffset(end, startTag)
                start.addHeight((prop.blockHeight - 0.0625f).toDouble())
                adjustedEnd.addHeight((prop.blockHeight - 0.0625f).toDouble())
                if (branchMode) createOrAppendBranchRail(level, startPos, copyRailPosition(start), copyRailPosition(adjustedEnd), prop, player.abilities.instabuild, selectedModelId)
                else createRail(level, startPos, listOf(copyRailPosition(start), copyRailPosition(adjustedEnd)), prop, true, player.abilities.instabuild, selectedModelId)
            }
            if (created) player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.connected"))
            else player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.cannot_place"))
            return created
        }

        @JvmStatic fun placeCopiedRailAt(level: Level, placePos: BlockPos, player: Player, stack: ItemStack, selectedId: String?): Boolean =
            placeRailFromItem(level, placePos, player, stack, selectedId)

        @JvmStatic
        fun buildRailForScript(
            level: Level,
            railPositions: List<RailPosition>,
            selectedModelId: String?,
            isCreative: Boolean = false,
            canEdit: ((BlockPos) -> Boolean)? = null,
        ): Boolean {
            if (railPositions.size < 2) return false
            val prop = RailProperties.createDefault()
            selectedModelId?.let { RailRegistry.getById(it) }?.let { prop.ballastWidth = it.ballastWidth }
            val core = BlockPos(railPositions[0].blockX, railPositions[0].blockY, railPositions[0].blockZ)
            return createRail(level, core, railPositions, prop, true, isCreative, selectedModelId, canEdit)
        }

        private fun resolvePreviewStart(startBe: BlockEntity?, tag: CompoundTag): RailPosition? = when {
            startBe is MarkerBlockEntity -> startBe.markerRP
            startBe is LargeRailCoreBlockEntity -> startBe.firstRailPosition
            tag.contains("StartRP") -> RailPosition.readFromNBT(NbtCompat.getCompound(tag, "StartRP"))
            else -> null
        }

        private fun applyPreviewOffset(raw: RailPosition, tag: CompoundTag?): RailPosition {
            val copy = copyRailPosition(raw); if (tag == null) return copy
            copy.posX += NbtCompat.getInt(tag, "OffsetX") / 16.0; copy.posY += NbtCompat.getInt(tag, "OffsetY") / 16.0; copy.posZ += NbtCompat.getInt(tag, "OffsetZ") / 16.0
            return copy
        }

        private fun copyRailPosition(source: RailPosition): RailPosition = RailPosition.readFromNBT(source.writeToNBT())!!

        private fun applyOffsetToRailPosition(rp: RailPosition, tag: CompoundTag): RailPosition {
            val copy = copyRailPosition(rp)
            val offX = NbtCompat.getInt(tag, "OffsetX")
            val offY = NbtCompat.getInt(tag, "OffsetY")
            val offZ = NbtCompat.getInt(tag, "OffsetZ")
            // Apply the preview offset to posX/posY/posZ.
            copy.posX += offX / 16.0
            copy.posY += offY / 16.0
            copy.posZ += offZ / 16.0
            // Save the exact shifted positions before setHeight recomputes posY.
            val shiftedPosX = copy.posX
            val shiftedPosY = copy.posY
            val shiftedPosZ = copy.posZ
            // Shift block coordinates using floor so placementCorePos is correct.
            copy.blockX = CurveMath.floor(shiftedPosX)
            copy.blockY = CurveMath.floor(shiftedPosY)
            copy.blockZ = CurveMath.floor(shiftedPosZ)
            // Set height via setHeight() so the private field is updated correctly.
            copy.setHeight(((shiftedPosY - copy.blockY) / 0.0625 - 1.0).let { Math.round(it).toInt().toByte() })
            // Set precise overrides after setHeight so init() uses the exact shifted values.
            copy.precisePosX = shiftedPosX
            copy.precisePosY = shiftedPosY
            copy.precisePosZ = shiftedPosZ
            // init() respects precise overrides, keeping the exact shifted posX/Y/Z.
            copy.init()
            return copy
        }

        private fun createRailProperties(player: Player, selectedModelId: String?): RailProperties {
            val prop = RailProperties.createDefault()
            val def = selectedModelId?.let { RailRegistry.getById(it) }
            if (def != null) prop.ballastWidth = def.ballastWidth
            return prop
        }

        @JvmStatic
        fun createRail(
            level: Level,
            corePos: BlockPos,
            rps: List<RailPosition>,
            prop: RailProperties,
            setRail: Boolean,
            isCreative: Boolean,
            selectedModelId: String?,
            canEdit: ((BlockPos) -> Boolean)? = null,
        ): Boolean {
            if (rps.size < 2) return false
            val maker = RailMaker(rps.toTypedArray())
            val switch = maker.getSwitch()
            val maps = if (switch != null) switch.allRailMap.toList() else {
                val result = mutableListOf<RailMap>()
                var i = 0
                while (i + 1 < rps.size) {
                    result.add(RailMapBasic(rps[i], rps[i + 1]))
                    i += 2
                }
                result
            }
            if (canEdit != null && !canEdit(corePos)) return false
            for (map in maps) {
                if (!map.canPlaceRail(level, isCreative, prop)) return false
                if (canEdit != null && map.getRailBlockList(prop, true).any { rail ->
                        !canEdit(BlockPos(rail[0], rail[1], rail[2]))
                    }
                ) return false
            }
            if (!setRail) return true
            val prev = RailMap.suppressRailRemoval.get(); RailMap.suppressRailRemoval.set(true)
            try {
                level.setBlock(corePos, RealTrainModRenewedBlocks.LARGE_RAIL_CORE.get().defaultBlockState(), Block.UPDATE_ALL)
                val core = level.getBlockEntity(corePos) as? LargeRailCoreBlockEntity
                if (core != null) {
                    core.setRailPositions(rps.toTypedArray())
                    core.setRailMaps(maps.toTypedArray())
                    if (switch != null) core.setSwitchType(switch)
                    if (!selectedModelId.isNullOrBlank()) core.setRailDefinitionId(selectedModelId)
                    for (map in maps) map.setRail(level, RealTrainModRenewedBlocks.BALLAST.get(), corePos.x, corePos.y, corePos.z, prop)
                }
            } finally { RailMap.suppressRailRemoval.set(prev) }
            return true
        }

        @JvmStatic fun createOrAppendBranchRail(level: Level, corePos: BlockPos, start: RailPosition, end: RailPosition, prop: RailProperties, isCreative: Boolean, selectedModelId: String?): Boolean {
            val existingCore = level.getBlockEntity(corePos) as? LargeRailCoreBlockEntity
            if (existingCore != null && existingCore.allRailMaps.isNotEmpty()) {
                val prev = RailMap.suppressRailRemoval.get(); RailMap.suppressRailRemoval.set(true)
                try {
                    existingCore.appendRailSegment(start, end)
                    RailMapBasic(start, end).setRail(level, RealTrainModRenewedBlocks.BALLAST.get(), corePos.x, corePos.y, corePos.z, prop)
                } finally { RailMap.suppressRailRemoval.set(prev) }
                return true
            }
            return createRail(level, corePos, listOf(start, end), prop, true, isCreative, selectedModelId)
        }

         @JvmStatic fun createRailsFromWrenchPreview(level: Level, corePos: BlockPos, start: RailPosition, tag: CompoundTag, prop: RailProperties, isCreative: Boolean, selectedModelId: String?): Boolean {
            val segments = WrenchItem.getSegmentList(tag)

            // Apply offset to every copied RailPosition
            val offsetSegments = segments.map { seg -> applyOffsetToRailPosition(seg, tag) }

            if (offsetSegments.size >= 2) {
                // Derive placement core position from shifted first start position
                val shiftedFirst = offsetSegments[0]
                val placementCorePos = BlockPos(shiftedFirst.blockX, shiftedFirst.blockY, shiftedFirst.blockZ)
                return createRail(level, placementCorePos, offsetSegments, prop, true, isCreative, selectedModelId)
            }

            // Legacy fallback: no RailSegments — offset both start and EndRP, use shifted core pos
            val legacyStart = applyOffsetToRailPosition(start, tag)
            val legacyEnd = RailPosition.readFromNBT(NbtCompat.getCompound(tag, "EndRP"))
            val legacyEndOffset = legacyEnd?.let { applyOffsetToRailPosition(it, tag) } ?: return false
            val legacyCorePos = BlockPos(legacyStart.blockX, legacyStart.blockY, legacyStart.blockZ)
            return createOrAppendBranchRail(level, legacyCorePos, legacyStart, legacyEndOffset, prop, isCreative, selectedModelId)
        }
    }

    constructor(isSwitch: Boolean) : this(isSwitch, Properties.of().sound(SoundType.STONE).strength(1.0f, 1.0f).noOcclusion().noCollision())

    init { registerDefaultState(stateDefinition.any().setValue(FACING, 0)) }

    override fun codec(): MapCodec<out MarkerBlock> = CODEC
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(FACING) }
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = SHAPE
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape = Shapes.empty()

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val player = context.player ?: return defaultBlockState().setValue(FACING, 0)
        val diagonal = shouldPlaceDiagonal(context.itemInHand.item is MarkerItem && (context.itemInHand.item as MarkerItem).diagonal, player)
        return defaultBlockState().setValue(FACING, computeFacing(player, diagonal))
    }

    private fun shouldPlaceDiagonal(forced: Boolean, player: Player): Boolean {
        if (forced) return true
        val yaw = Mth.positiveModulo(player.yRot + 180f, 360f)
        val rem = Mth.positiveModulo(yaw, 90f)
        return rem >= 22.5f && rem < 67.5f
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        if (stack.item is MarkerItem) { if (level.isClientSide) ClientHooks.openMarkerConfigScreen(pos); return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER }
        if (stack.item is RailItem && !level.isClientSide) {
            val selectedId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get())
            val count = searchAllMarkers(level, pos).size
            if (count < 2) { player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.not_enough_markers_count", 2)); return InteractionResult.SUCCESS_SERVER }
            val created = onMarkerActivated(level, pos, player, true, selectedId)
            if (created) { if (!player.abilities.instabuild) stack.shrink(1); player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.connected")) }
            else player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.rail.cannot_place_check"))
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND
    }

    @JvmOverloads
    fun onMarkerActivated(level: Level, pos: BlockPos, player: Player?, makeRail: Boolean = true, selectedModelId: String? = null): Boolean {
        val rps = searchAllMarkers(level, pos)
        if (rps.size < 2 || !makeRail) return false
        val switchList = rps.filter { it.switchType == 1.toByte() }
        val normalList = rps.filter { it.switchType != 1.toByte() }
        val maker = RailMaker(rps)
        val switch = maker.getSwitch()
        val maps = if (switch != null) switch.allRailMap.toList() else {
            if (normalList.size >= 2) listOf(RailMapBasic(normalList[0], normalList[1])) else emptyList()
        }
        if (maps.isEmpty()) return false
        val prop = createRailProperties(player ?: return false, selectedModelId)
        return createRail(level, pos, rps, prop, true, player.abilities.instabuild, selectedModelId)
    }

    fun searchAllMarkers(level: Level, pos: BlockPos): List<RailPosition> {
        val found = LinkedHashSet<RailPosition>()
        val queue = ArrayDeque<BlockPos>(); queue.add(pos)
        val visited = HashSet<BlockPos>(); visited.add(pos)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val be = level.getBlockEntity(cur)
            if (be is MarkerBlockEntity) {
                val rp = be.markerRP
                if (rp != null && rp.posX.let { it * it } + rp.posZ.let { it * it } > 0.001) found.add(rp)
            }
            for (dx in -SEARCH_DISTANCE..SEARCH_DISTANCE) for (dy in -SEARCH_HEIGHT..SEARCH_HEIGHT) for (dz in -SEARCH_DISTANCE..SEARCH_DISTANCE) {
                if (abs(dx) > SEARCH_DISTANCE || abs(dz) > SEARCH_DISTANCE) continue
                val np = cur.offset(dx, dy, dz); if (visited.add(np) && level.getBlockEntity(np) is MarkerBlockEntity) queue.add(np)
            }
        }
        return found.toList()
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? = null
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = MarkerBlockEntity(pos, state)
}
