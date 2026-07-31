// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectDefinition
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData
import jp.ngt.mccompat.WorldCompat
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class InstalledObjectBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.INSTALLED_OBJECT.get(), pos, state) {

    companion object {
        private const val TICKET_GATE_OPEN_TICKS = 60
        private const val TICKET_GATE_MOVE_TICKS = 12
        private const val CROSSING_GATE_MOVE_TICKS = 90
        private const val CROSSING_GATE_LIGHT_INTERVAL = 10

        @JvmStatic
        fun tick(level: Level, pos: BlockPos, state: BlockState, be: InstalledObjectBlockEntity) {
            if (level.isClientSide) {
                val def = be.getDefinition()
                if (!def?.runningSound.isNullOrBlank()) ClientHooks.tickCrossingGateSound(be)
                else ClientHooks.stopCrossingGateSound(level, pos)
                if (be.category == InstalledObjectCategory.CROSSING) be.tickCrossingAnimation()
                return
            }
            if (be.category == InstalledObjectCategory.TICKET_GATE) {
                var changed = false
                if (be.powered) {
                    if (be.barMoveCount < 90) { be.barMoveCount = min(90, be.barMoveCount + max(1, 90 / TICKET_GATE_MOVE_TICKS)); changed = true }
                    be.tickCountOnActive++
                    if (be.tickCountOnActive >= TICKET_GATE_OPEN_TICKS) { be.powered = false; be.tickCountOnActive = 0; changed = true }
                } else {
                    if (be.barMoveCount > 0) { be.barMoveCount = max(0, be.barMoveCount - max(1, 90 / TICKET_GATE_MOVE_TICKS)); changed = true }
                }
                if (changed) { be.setChanged(); level.sendBlockUpdated(pos, state, state, 3) }
                return
            }
            if (be.category == InstalledObjectCategory.CROSSING) {
                val wasPowered = be.powered
                be.powered = level.getBestNeighborSignal(pos) > 0
                val animationChanged = be.tickCrossingAnimation()
                if (animationChanged || wasPowered != be.powered) be.setChanged()
                if (wasPowered != be.powered || animationChanged && be.barMoveCount % CROSSING_GATE_LIGHT_INTERVAL == 0) {
                    level.sendBlockUpdated(pos, state, state, 3)
                }
                return
            }
            if (!be.shouldHandleRunningSoundPower()) return
            val powered = level.getBestNeighborSignal(pos) > 0
            if (be.powered != powered) {
                be.powered = powered
                be.setChanged()
                level.sendBlockUpdated(pos, state, state, 3)
            }
        }

        // Legacy compat inner classes
        class ResourceStateCompat(private val be: InstalledObjectBlockEntity) {
            fun getInt(key: String): Int = be.scriptData[key]?.toIntOrNull() ?: 0
            fun getString(key: String): String = be.scriptData[key] ?: ""
            fun getBoolean(key: String): Boolean = be.scriptData[key]?.toBooleanStrictOrNull() ?: false
            fun getFloat(key: String): Float = be.scriptData[key]?.toFloatOrNull() ?: 0f
            fun setInt(key: String, value: Int) { be.scriptData[key] = value.toString() }
            fun setString(key: String, value: String) { be.scriptData[key] = value }
        }
        class ModelSetCompat(private val be: InstalledObjectBlockEntity) {
            fun getConfig(): ResourceStateCompat = ResourceStateCompat(be)
        }
    }

    var definitionId: String = ""; private set
    var category: InstalledObjectCategory = InstalledObjectCategory.LIGHT
        private set
    var yaw: Float = 0f; private set
    var mountPitch: Float = 0f; private set
    var wireStart: BlockPos? = null; private set
    var wireEnd: BlockPos? = null; private set
    var powered: Boolean = false; private set
    var barMoveCount: Int = 0
    private var crossingLightCount: Int = -1
    var tickCountOnActive: Int = 0
    var offsetX: Double = 0.0; private set
    var offsetY: Double = 0.0; private set
    var offsetZ: Double = 0.0; private set
    var signalChannel: Int = -1; private set
    var signalAspect: Int = SignalAspect.STOP.id; private set
    var speakerRange: Int = 32; private set
    val scriptData: MutableMap<String, String> = HashMap()
    @JvmField
    var field_145850_b: WorldCompat? = null
    var isPowered: Boolean get() = powered; set(v) { powered = v; setChanged() }
    val isTicketGateOpen: Boolean get() = category == InstalledObjectCategory.TICKET_GATE && powered
    val isSignal: Boolean get() = category == InstalledObjectCategory.SIGNAL
    val isSpeaker: Boolean get() = category == InstalledObjectCategory.SPEAKER
    val renderOffset: Vec3 get() = Vec3(offsetX, offsetY, offsetZ)

    fun setDefinition(id: String?, cat: InstalledObjectCategory?, yaw: Float) {
        definitionId = id ?: ""; category = cat ?: InstalledObjectCategory.LIGHT; this.yaw = yaw
        if (cat == InstalledObjectCategory.SIGNAL) signalAspect = SignalAspect.STOP.id
        setChanged()
    }

    fun setMountPitch(pitch: Float) { mountPitch = pitch; setChanged() }
    fun setRenderOffset(x: Double, y: Double, z: Double) { offsetX = x; offsetY = y; offsetZ = z; setChanged() }
    fun setWireEndpoints(start: BlockPos?, end: BlockPos?) { wireStart = start; wireEnd = end; setChanged() }
    fun setSignalChannel(ch: Int, updateClient: Boolean) { signalChannel = ch; setChanged(); if (updateClient && level != null) level!!.sendBlockUpdated(worldPosition, blockState, blockState, 3) }
    fun setSignalAspect(aspect: SignalAspect?, updateClient: Boolean) { signalAspect = aspect?.id ?: SignalAspect.STOP.id; setChanged(); if (updateClient && level != null) level!!.sendBlockUpdated(worldPosition, blockState, blockState, 3) }
    fun setSpeakerRange(range: Int) { speakerRange = range.coerceIn(1, 256); setChanged(); if (level != null && !level!!.isClientSide) level!!.sendBlockUpdated(blockPos, blockState, blockState, 3) }
    fun activateTicketGate() {
        activateTicketGateAndReport()
    }

    fun activateTicketGateAndReport(): Boolean {
        if (category != InstalledObjectCategory.TICKET_GATE) return false
        powered = true
        tickCountOnActive = 0
        setChanged()
        if (level != null && !level!!.isClientSide) level!!.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        return true
    }

    fun getDefinition(): InstalledObjectDefinition? = InstalledObjectRegistry.getById(definitionId)
    val modelName: String get() = definitionId.substringAfterLast(':')
    val renderCenter: Vec3 get() = Vec3.atCenterOf(blockPos)

    // Legacy script accessors
    fun getSignal(): Int = max(0, signalAspect.let { SignalAspect.byId(it).legacyValue })
    fun getLegacySignalState(): Int = getSignal()
    fun getLightCount(): Int = if (category == InstalledObjectCategory.CROSSING) crossingLightCount else if (powered) 1 else 0
    fun getResourceState(): ResourceStateCompat = ResourceStateCompat(this)
    fun getModelSet(): ModelSetCompat = ModelSetCompat(this)
    fun getRotation(): Float = 0f
    fun getBlockDirection(): Float = 0f
    fun getX(): Int = blockPos.x; fun getY(): Int = blockPos.y; fun getZ(): Int = blockPos.z
    fun getDir(): Int = Math.floorMod(yaw.roundToInt() / 90, 4)
    fun isConnected(side: Int): Boolean = false
    fun getAttachedSide(): Int = 1
    fun getRandomScale(): Float = 1f

    private fun tickCrossingAnimation(): Boolean {
        val oldBarMoveCount = barMoveCount
        val oldLightCount = crossingLightCount
        val oldTickCount = tickCountOnActive
        if (powered) {
            if (barMoveCount < CROSSING_GATE_MOVE_TICKS) barMoveCount++
            tickCountOnActive = (tickCountOnActive + 1) % 360
            crossingLightCount = when {
                crossingLightCount < 0 -> 0
                tickCountOnActive % CROSSING_GATE_LIGHT_INTERVAL == 0 -> (crossingLightCount + 1) % 2
                else -> crossingLightCount
            }
        } else {
            if (barMoveCount > 0) barMoveCount--
            tickCountOnActive = 0
            crossingLightCount = -1
        }
        return oldBarMoveCount != barMoveCount || oldLightCount != crossingLightCount
    }

    private fun shouldHandleRunningSoundPower(): Boolean =
        category == InstalledObjectCategory.SPEAKER && !getDefinition()?.runningSound.isNullOrBlank()

    override fun onLoad() {
        super.onLoad()
        level?.let { field_145850_b = WorldCompat(it) }
        if (level is ServerLevel && isSignal) SignalNetworkSavedData.get(level as ServerLevel).syncLoadedSignal(level as ServerLevel, this)
    }

    fun getWorldObj(): WorldCompat? = field_145850_b

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        tag.putString("DefinitionId", definitionId)
        tag.putString("Category", category.name)
        tag.putFloat("Yaw", yaw); tag.putFloat("MountPitch", mountPitch)
        wireStart?.let { tag.putInt("WireStartX", it.x); tag.putInt("WireStartY", it.y); tag.putInt("WireStartZ", it.z) }
        wireEnd?.let { tag.putInt("WireEndX", it.x); tag.putInt("WireEndY", it.y); tag.putInt("WireEndZ", it.z) }
        tag.putBoolean("Powered", powered); tag.putInt("BarMoveCount", barMoveCount)
        tag.putInt("LightCount", crossingLightCount); tag.putInt("TickCountOnActive", tickCountOnActive)
        tag.putDouble("OffsetX", offsetX); tag.putDouble("OffsetY", offsetY); tag.putDouble("OffsetZ", offsetZ)
        tag.putInt("SignalChannel", signalChannel); tag.putInt("SignalAspect", signalAspect)
        tag.putInt("SpeakerRange", speakerRange)
        if (scriptData.isNotEmpty()) {
            val sd = CompoundTag(); scriptData.forEach { (k, v) -> sd.putString(k, v) }
            tag.store("ScriptData", CompoundTag.CODEC, sd)
        }
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        definitionId = tag.getStringOr("DefinitionId", "")
        category = try { InstalledObjectCategory.valueOf(tag.getStringOr("Category", InstalledObjectCategory.LIGHT.name)) } catch (_: Exception) { InstalledObjectCategory.LIGHT }
        yaw = tag.getFloatOr("Yaw", 0f); mountPitch = tag.getFloatOr("MountPitch", 0f)
        wireStart = if (tag.getInt("WireStartX").isPresent) BlockPos(tag.getIntOr("WireStartX", 0), tag.getIntOr("WireStartY", 0), tag.getIntOr("WireStartZ", 0)) else null
        wireEnd = if (tag.getInt("WireEndX").isPresent) BlockPos(tag.getIntOr("WireEndX", 0), tag.getIntOr("WireEndY", 0), tag.getIntOr("WireEndZ", 0)) else null
        powered = tag.getBooleanOr("Powered", false); barMoveCount = tag.getIntOr("BarMoveCount", 0)
        crossingLightCount = tag.getIntOr("LightCount", -1)
        tickCountOnActive = tag.getIntOr("TickCountOnActive", 0)
        offsetX = tag.getDoubleOr("OffsetX", 0.0); offsetY = tag.getDoubleOr("OffsetY", 0.0); offsetZ = tag.getDoubleOr("OffsetZ", 0.0)
        signalChannel = tag.getIntOr("SignalChannel", -1); signalAspect = tag.getIntOr("SignalAspect", SignalAspect.STOP.id)
        speakerRange = tag.getIntOr("SpeakerRange", 32)
        scriptData.clear()
        tag.read("ScriptData", CompoundTag.CODEC).ifPresent { sd -> for (key in sd.keySet()) scriptData[key] = sd.getStringOr(key, "") }
    }

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
}
