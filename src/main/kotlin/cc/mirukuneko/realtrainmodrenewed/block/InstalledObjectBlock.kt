// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.block

import com.mojang.serialization.MapCodec
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.installedobject.SpeakerSoundConfig
import cc.mirukuneko.realtrainmodrenewed.network.SpeakerPlayPayload
import cc.mirukuneko.realtrainmodrenewed.network.SpeakerStopPayload
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData
import cc.mirukuneko.realtrainmodrenewed.electric.ElectricSignalNetwork
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
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
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.neoforged.neoforge.network.PacketDistributor

class InstalledObjectBlock : BaseEntityBlock {
    companion object {
        val CODEC: MapCodec<InstalledObjectBlock> = simpleCodec { InstalledObjectBlock() }
        private val RTM_SELECTION_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        private val EMPTY_SHAPE = Shapes.empty()
        private const val TICKET_GATE_SOUND_RANGE = 32.0

        private fun stopSpeakerSoundOnRemove(level: Level, pos: BlockPos) {
            if (level !is ServerLevel) return
            val stop = SpeakerStopPayload(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            for (p in level.players()) PacketDistributor.sendToPlayer(p, stop)
        }

        private fun removeSignalLink(level: Level, pos: BlockPos) {
            if (level !is ServerLevel) return
            val be = level.getBlockEntity(pos)
            if (be !is InstalledObjectBlockEntity || !be.isSignal) return
            SignalNetworkSavedData.get(level).removeSignal(level, pos, be.signalChannel)
        }

        private fun removeAttachedWires(level: Level, pos: BlockPos) {
            val be = level.getBlockEntity(pos)
            if (be !is InstalledObjectBlockEntity || be.category == InstalledObjectCategory.WIRE) return
            ElectricSignalNetwork.removeWiresAtEndpoint(level, pos)
        }

        private fun playTicketGateSound(level: ServerLevel, pos: BlockPos, be: InstalledObjectBlockEntity) {
            val sound = be.getDefinition()?.activationSound?.takeIf(String::isNotBlank) ?: return
            val cx = pos.x + 0.5
            val cy = pos.y + 0.5
            val cz = pos.z + 0.5
            val payload = SpeakerPlayPayload(cx, cy, cz, sound, 1.0f, 1.0f)
            val rangeSq = TICKET_GATE_SOUND_RANGE * TICKET_GATE_SOUND_RANGE
            for (player in level.players()) {
                if (player.distanceToSqr(cx, cy, cz) <= rangeSq) PacketDistributor.sendToPlayer(player, payload)
            }
        }
    }

    constructor() : this(Properties.of().sound(SoundType.METAL).strength(0.4f, 2.0f).noOcclusion())
    constructor(properties: BlockBehaviour.Properties) : super(properties)

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getLightEmission(state: BlockState, level: BlockGetter, pos: BlockPos): Int {
        val be = level.getBlockEntity(pos)
        if (be is InstalledObjectBlockEntity && be.category == InstalledObjectCategory.LIGHT && be.isPowered) return 15
        return super.getLightEmission(state, level, pos)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape {
        val be = level.getBlockEntity(pos)
        if (be is InstalledObjectBlockEntity && be.wireStart != null && be.wireEnd != null) return EMPTY_SHAPE
        return RTM_SELECTION_SHAPE
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, ctx: CollisionContext): VoxelShape {
        val be = level.getBlockEntity(pos)
        if (be is InstalledObjectBlockEntity && be.category == InstalledObjectCategory.TICKET_GATE && !be.isTicketGateOpen)
            return RTM_SELECTION_SHAPE
        return EMPTY_SHAPE
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = InstalledObjectBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        createTickerHelper(type, RealTrainModRenewedBlockEntities.INSTALLED_OBJECT.get(), InstalledObjectBlockEntity::tick)

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        val be = level.getBlockEntity(pos) as? InstalledObjectBlockEntity
        if (stack.`is`(RealTrainModRenewedItems.IC_CARD_ITEM.get()) && be?.category == InstalledObjectCategory.TICKET_GATE) {
            if (level is ServerLevel && be.activateTicketGateAndReport()) playTicketGateSound(level, pos, be)
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val be = level.getBlockEntity(pos)
        if (be is InstalledObjectBlockEntity && be.isSpeaker) {
            if (level.isClientSide) ClientHooks.openSpeakerScreen(pos)
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }
        return super.useWithoutItem(state, level, pos, player, hit)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        if (!level.isClientSide) updatePoweredState(level, pos)
        super.onPlace(state, level, pos, oldState, isMoving)
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, orientation: net.minecraft.world.level.redstone.Orientation?, isMoving: Boolean) {
        if (!level.isClientSide) updatePoweredState(level, pos)
        super.neighborChanged(state, level, pos, block, orientation, isMoving)
    }

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, isMoving: Boolean) {
        InstalledObjectBlock.removeSignalLink(level, pos)
        InstalledObjectBlock.removeAttachedWires(level, pos)
        InstalledObjectBlock.stopSpeakerSoundOnRemove(level, pos)
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving)
    }



    private fun updatePoweredState(level: Level, pos: BlockPos) {
        val be = level.getBlockEntity(pos) as? InstalledObjectBlockEntity ?: return
        val cat = be.category
        if (cat == InstalledObjectCategory.SPEAKER && !hasDefinitionRunningSound(be)) { updateSpeaker(level, pos, be); return }
        if (cat == InstalledObjectCategory.LIGHT) {
            val powered = level.hasNeighborSignal(pos)
            if (be.isPowered != powered) { be.isPowered = powered; level.lightEngine.checkBlock(pos); level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3) }
            return
        }
        if (cat != InstalledObjectCategory.CROSSING && !hasDefinitionRunningSound(be)) return
        val powered = level.getBestNeighborSignal(pos) > 0
        be.isPowered = powered
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3)
    }

    private fun hasDefinitionRunningSound(be: InstalledObjectBlockEntity): Boolean {
        val def = InstalledObjectRegistry.getById(be.definitionId)
        return !def?.runningSound.isNullOrBlank()
    }

    private fun updateSpeaker(level: Level, pos: BlockPos, be: InstalledObjectBlockEntity) {
        val signal = level.getBestNeighborSignal(pos)
        val wasPowered = be.isPowered
        val nowPowered = signal > 0
        be.isPowered = nowPowered
        if (level is ServerLevel) {
            val cx = pos.x + 0.5; val cy = pos.y + 0.5; val cz = pos.z + 0.5
            if (nowPowered && !wasPowered) {
                val sound = SpeakerSoundConfig.getSound(signal) ?: return
                val range = be.speakerRange
                val volume = maxOf(1.0f, range / 16.0f)
                val payload = SpeakerPlayPayload(cx, cy, cz, sound, volume, 1.0f)
                val rangeSq = (range * range).toDouble()
                for (p in level.players()) if (p.distanceToSqr(cx, cy, cz) <= rangeSq) PacketDistributor.sendToPlayer(p, payload)
            } else if (!nowPowered && wasPowered) {
                val stop = SpeakerStopPayload(cx, cy, cz)
                for (p in level.players()) PacketDistributor.sendToPlayer(p, stop)
            }
        }
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3)
    }
}
