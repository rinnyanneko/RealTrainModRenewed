// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.entity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedEntities
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.block.BallastBlock
import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.loadServerScriptForVehicle
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.loadSoundScriptForVehicle
import cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager
import cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager.tickJsonRunningSound
import cc.mirukuneko.realtrainmodrenewed.entity.formation.Formation
import cc.mirukuneko.realtrainmodrenewed.entity.formation.FormationEntry
import cc.mirukuneko.realtrainmodrenewed.entity.formation.FormationManager.Companion.getInstance
import cc.mirukuneko.realtrainmodrenewed.network.TrainScriptDataPayload
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailMap
import cc.mirukuneko.realtrainmodrenewed.rail.util.RailPosition
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry.getById
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry.getSelected
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import javax.script.ScriptEngine
import kotlin.math.*

class TrainEntity(type: EntityType<*>, level: Level) : Entity(type, level) {
    private val seatAssignments: MutableMap<UUID?, Int?> = HashMap<UUID?, Int?>()
    private val scriptData: MutableMap<String, String> = HashMap<String, String>()
    private var scriptDataDirty = false
    private var coupledFollowerUuid: UUID? = null
    private var coupledLeaderUuid: UUID? = null
    private var coupledFollowerThisSide = -1
    private var coupledFollowerOtherSide = 1
    private var formation: Formation? = null
    private var travelStallTicks = 0
    private val lastTravelStallLogTick = Long.MIN_VALUE
    private var railGuidanceFailureTicks = 0
    private var centerGuidanceFallbackTicks = 0
    private var customButtonValues: IntArray? = null
    var scriptEngine: ScriptEngine? = null
    private var soundScriptEngine: ScriptEngine? = null
    private var attemptedSoundScriptLoad = false
    private var serverScriptEngine: ScriptEngine? = null
    private var attemptedServerScriptLoad = false
    @JvmField
    val field_70170_p: WorldCompat = WorldCompat(this)
    @JvmField
    var field_70173_aa: Int = 0
    @JvmField
    var field_70177_z: Float = 0f
    private val serverLevel: ServerLevel
        get() = level() as ServerLevel

    /** The rail this train currently sits on (used by placement to allow side-by-side trains on separate rails).  */
    var activeRailMap: RailMap? = null
        private set
    private var activeRailSplit = 0
    private var activeRailIndex = -1
    private var activeRailDirection = 1
    private var activeRailBodyDirection = 1
    private var activeRailPosition = -1.0
    private var frontRailAnchor: RailAnchor? = null
    private var rearRailAnchor: RailAnchor? = null
    private val bogiePrevMaps = arrayOfNulls<RailMap>(2)
    private val bogiePrevSplits = IntArray(2)
    private val bogiePrevSampleIndex = intArrayOf(-1, -1)
    private val bogieYawMemory = FloatArray(2)
    private val bogiePitchMemory = FloatArray(2)

    // クライアント描画で台車をレール追従させる際の RailMap キャッシュ(全探索を避ける)。
    // bogieIndex を 0/1 の extreme side に正規化したインデックスで保持。
    private val clientBogieRailMap = arrayOfNulls<RailMap>(2)

    // 台車レール追従の水平オフセット(本体ローカル)を平滑化して保持。探索が一瞬失敗しても
    // 直前値を維持し、台車が一瞬剛体位置へ戻って「外れて見える」のを防ぐ。
    private var activeDriverUuid: UUID? = null
    private var activeDriverTicks = 0
    private val interpolationHandler = InterpolationHandler(this, 3)

    // クライアント: 同期された端台車オフセット(エンティティ相対)の前tick/現tick値。
    // tick段付きを無くすため、描画時に partialTicks で補間する(本家RTMの台車補間に相当)。
    private var clientRearBogieOffPrev = Vec3.ZERO
    private var clientRearBogieOffCurr = Vec3.ZERO
    private var clientFrontBogieOffPrev = Vec3.ZERO
    private var clientFrontBogieOffCurr = Vec3.ZERO
    private var clientBogieOffInit = false

    // クライアント: 端台車(0=後/1=前)のレール接線ヨーの前tick/現tick値。
    // 毎フレーム生計算すると微振動(ガクガク)、減衰平滑すると遅延する。tick値を partialTicks で
    // 補間して「滑らか＋遅延なし(RTM同等)」にする。
    private val clientBogieYawPrev = floatArrayOf(Float.NaN, Float.NaN)
    private val clientBogieYawCurr = floatArrayOf(Float.NaN, Float.NaN)
    private val clientBogieYawRejectCount = intArrayOf(0, 0)

    // 位置(同期オフセット)のレール継ぎ目グリッチ除去用。0=後/1=前。
    private val clientBogieOffRejectCount = intArrayOf(0, 0)
    private var interactionHitboxRefreshCooldown = 0

    /** 診断用: STALL ログのスパム防止クールダウン(tick)。  */
    private var stallLogCooldown = 0
    private var rotationRoll = 0f
    private var prevRotationRoll = 0f
    @JvmField
    var doorMoveL: Float = 0f
    @JvmField
    var doorMoveR: Float = 0f
    var mainReservoirPressure: Float = MAIN_RESERVOIR_NORMAL
        get() = entityData.get<Float>(MAIN_RESERVOIR_PRESSURE)!!
        private set(pressure) {
            field = Mth.clamp(
                pressure,
                0.0f,
                MAIN_RESERVOIR_NORMAL
            )
            entityData.set<Float>(
                MAIN_RESERVOIR_PRESSURE,
                field
            )
        }
    var brakePipePressure: Float = BRAKE_PIPE_NORMAL
        get() = entityData.get<Float>(BRAKE_PIPE_PRESSURE)!!
        private set(pressure) {
            field = Mth.clamp(
                pressure,
                0.0f,
                BRAKE_PIPE_NORMAL
            )
            entityData.set<Float>(
                BRAKE_PIPE_PRESSURE,
                field
            )
        }
    var brakeCylinderPressure: Float = 0.0f
        get() = entityData.get<Float>(BRAKE_CYLINDER_PRESSURE)!!
        private set(pressure) {
            field = Mth.clamp(
                pressure,
                0.0f,
                BRAKE_CYLINDER_EMERGENCY_MAX
            )
            entityData.set<Float>(
                BRAKE_CYLINDER_PRESSURE,
                field
            )
        }
    @JvmField
    var pantograph_F: Float = 40.0f
    @JvmField
    var pantograph_B: Float = 40.0f
    @JvmField
    var seatRotation: Float = 0f
    private val bogieHitboxUuids: MutableMap<Int, UUID?> = HashMap<Int, UUID?>()
    private val seatHitboxUuids: MutableMap<Int?, UUID> = HashMap<Int?, UUID>()

    fun initializeOnRail(map: RailMap?, split: Int, index: Int) {
        if (map == null || split <= 0) {
            return
        }
        activeRailMap = map
        activeRailSplit = getMovementSplitForMap(map)
        val normalized = Mth.clamp(index.toDouble() / split.toDouble(), 0.0, 1.0)
        activeRailIndex = Mth.clamp(Math.round(normalized * activeRailSplit).toInt(), 0, activeRailSplit)
        activeRailPosition = normalized * activeRailSplit
        activeRailBodyDirection = getBodyDirectionOnRail(map, activeRailSplit, activeRailIndex, getYRot())
        activeRailDirection = activeRailBodyDirection
        this.railProgress = activeRailIndex / activeRailSplit.toFloat()

        val requestedCenter = sampleRail(map, activeRailSplit, activeRailIndex)
        val pair = findBestAnchorPairForCenter(
            map,
            activeRailSplit,
            activeRailPosition,
            requestedCenter,
            activeRailBodyDirection
        )
        frontRailAnchor = pair.front
        rearRailAnchor = pair.rear
        val front = pair.frontSample
        val rear = pair.rearSample
        val yaw =
            getRailYawForBody(map, activeRailSplit, activeRailIndex.toDouble(), activeRailBodyDirection, getYRot())
        val pitch = getRailPitchForBody(map, activeRailSplit, activeRailIndex.toDouble(), activeRailBodyDirection)
        applyPoseFromBogieSamples(front, rear, yaw, pitch, true)
        syncBogieOrientationMemory(front, rear, yaw, pitch)
        updateStoredBogieState()
        setDeltaMovement(Vec3.ZERO)
        this.speed = 0.0f
        this.notch = 0
        // レール整列後の本体位置を前tick位置にも反映し、初回フレームの補間ズレ
        // (台車が一瞬ズレて見える)を防ぐ。
        setOldPosAndRot()
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define<String>(VEHICLE_ID, "")
        builder.define<Float>(SPEED, 0.0f)
        builder.define<Float>(TRAIN_DISTANCE, 4.5f)
        builder.define<Int>(NOTCH, 0)
        builder.define<Boolean>(HEADLIGHT_ON, false)
        builder.define<Boolean>(DOOR_OPEN, false)
        builder.define<Boolean>(DOOR_LEFT_OPEN, false)
        builder.define<Boolean>(DOOR_RIGHT_OPEN, false)
        builder.define<Int>(LIGHT_MODE, 0)
        builder.define<Boolean>(PANTOGRAPH_UP, true)
        builder.define<Boolean>(REVERSE, false)
        builder.define<Int>(REVERSER, 1)
        builder.define<Int>(DESTINATION_INDEX, 0)
        builder.define<Int>(SOUND_INDEX, 0)
        builder.define<Float>(BODY_ROLL, 0.0f)
        builder.define<Float>(MAIN_RESERVOIR_PRESSURE, MAIN_RESERVOIR_NORMAL)
        builder.define<Float>(BRAKE_PIPE_PRESSURE, BRAKE_PIPE_NORMAL)
        builder.define<Float>(BRAKE_CYLINDER_PRESSURE, 0.0f)
        builder.define<Int>(CUSTOM_BUTTON_BITS, 0)
        builder.define<Float>(RAIL_PROGRESS, 0.0f)
        builder.define<Int>(SIGNAL, 0)
        builder.define<String>(SEAT_ASSIGNMENTS, "")
        builder.define<String>(COUPLED_FOLLOWER, "")
        builder.define<String>(COUPLED_LEADER, "")
        builder.define<Boolean>(INTERIOR_LIGHT_ON, false)
        builder.define<Float>(FRONT_BOGIE_DX, 0.0f)
        builder.define<Float>(FRONT_BOGIE_DY, 0.0f)
        builder.define<Float>(FRONT_BOGIE_DZ, 0.0f)
        builder.define<Float>(REAR_BOGIE_DX, 0.0f)
        builder.define<Float>(REAR_BOGIE_DY, 0.0f)
        builder.define<Float>(REAR_BOGIE_DZ, 0.0f)
        builder.define<Boolean>(BOGIE_SYNC_VALID, false)
        builder.define<Float>(FRONT_BOGIE_YAW, 0.0f)
        builder.define<Float>(REAR_BOGIE_YAW, 0.0f)
    }

    var vehicleId: String?
        get() = entityData.get<String>(VEHICLE_ID)
        set(id) {
            entityData.set<String>(
                VEHICLE_ID,
                if (id != null) id else ""
            )
        }
    var speed: Float
        get() = entityData.get<Float>(SPEED)!!
        set(speed) {
            entityData.set<Float>(SPEED, speed)
        }

    /** 動輪/ロッドの累積回転角(度, 0-360)。毎tickの移動距離で加算。スクリプトの getWheelRotationR が参照。  */
    var wheelRotationDegrees: Float = 0.0f
        private set
    var trainDistance: Float
        get() = entityData.get<Float>(TRAIN_DISTANCE)!!
        set(distance) {
            entityData.set<Float>(
                TRAIN_DISTANCE,
                max(2.5f, distance)
            )
        }
    var notch: Int
        get() = entityData.get<Int>(NOTCH)!!
        set(notch) {
            entityData.set<Int>(
                NOTCH,
                Mth.clamp(notch, -this.maxBrakeNotch, this.maxPowerNotch)
            )
        }
    val maxPowerNotch: Int
        get() = getMaxPowerNotch(
            getById(
                this.vehicleId
            )
        )
    val maxBrakeNotch: Int
        get() = getMaxBrakeNotch(getById(this.vehicleId))
    var isHeadlightOn: Boolean
        get() = entityData.get<Boolean>(HEADLIGHT_ON)!!
        set(value) {
            this.lightMode = if (value) 1 else 0
        }
    var lightMode: Int
        get() = entityData.get<Int>(LIGHT_MODE)!!
        set(value) {
            val mode = if (value == 3) 2 else Mth.clamp(value, 0, 2)
            entityData.set<Int>(LIGHT_MODE, mode)
            entityData.set<Boolean>(
                HEADLIGHT_ON,
                mode == 1 || mode == 2
            )
        }

    fun setLightModeForFormation(value: Int) {
        if (level().isClientSide()) {
            this.lightMode = value
            return
        }
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train!!.lightMode = value
        })
    }

    var isInteriorLightOn: Boolean
        get() = entityData.get<Boolean>(INTERIOR_LIGHT_ON)!!
        set(value) {
            entityData.set<Boolean>(
                INTERIOR_LIGHT_ON,
                value
            )
        }

    fun setInteriorLightOnForFormation(value: Boolean) {
        if (level().isClientSide()) {
            this.isInteriorLightOn = value
            return
        }
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train!!.isInteriorLightOn = value
        })
    }

    var isDoorOpen: Boolean
        get() = entityData.get<Boolean>(DOOR_OPEN)!!
        set(value) {
            entityData.set<Boolean>(
                DOOR_OPEN,
                value
            )
            entityData.set<Boolean>(
                DOOR_LEFT_OPEN,
                value
            )
            entityData.set<Boolean>(
                DOOR_RIGHT_OPEN,
                value
            )
        }
    var isDoorLeftOpen: Boolean
        get() = entityData.get<Boolean>(DOOR_LEFT_OPEN)!!
        set(value) {
            entityData.set<Boolean>(
                DOOR_LEFT_OPEN,
                value
            )
            entityData.set<Boolean>(
                DOOR_OPEN,
                value || this.isDoorRightOpen
            )
        }
    var isDoorRightOpen: Boolean
        get() = entityData.get<Boolean>(DOOR_RIGHT_OPEN)!!
        set(value) {
            entityData.set<Boolean>(
                DOOR_RIGHT_OPEN,
                value
            )
            entityData.set<Boolean>(
                DOOR_OPEN,
                value || this.isDoorLeftOpen
            )
        }

    fun toggleDoorForFormation() {
        setDoorOpenForFormation(!this.isDoorOpen)
    }

    fun setDoorOpenForFormation(value: Boolean) {
        if (level().isClientSide()) {
            this.isDoorOpen = value
            return
        }
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train!!.isDoorOpen = value
        })
    }

    fun toggleDoorSideForFormation(left: Boolean) {
        toggleDoorSideForFormation(left, this)
    }

    fun toggleDoorSideForFormation(left: Boolean, referenceTrain: TrainEntity?) {
        val reference = referenceTrain ?: this
        val next = !isDoorSideOpenForFormation(left, reference)
        if (level().isClientSide()) {
            setDoorSideOpenFromReference(left, next, reference)
            return
        }
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train?.setDoorSideOpenFromReference(left, next, reference)
        })
    }

    fun isDoorSideOpenForFormation(left: Boolean, referenceTrain: TrainEntity?): Boolean {
        val reference = referenceTrain ?: this
        return if (isOppositeFormationDirection(reference)) {
            if (left) this.isDoorRightOpen else this.isDoorLeftOpen
        } else {
            if (left) this.isDoorLeftOpen else this.isDoorRightOpen
        }
    }

    private fun setDoorSideOpenFromReference(left: Boolean, value: Boolean, referenceTrain: TrainEntity) {
        val targetLeft = if (isOppositeFormationDirection(referenceTrain)) !left else left
        if (targetLeft) {
            this.isDoorLeftOpen = value
        } else {
            this.isDoorRightOpen = value
        }
    }

    private fun isOppositeFormationDirection(referenceTrain: TrainEntity): Boolean {
        val thisDir = formationEntryDirection(this)
        val referenceDir = formationEntryDirection(referenceTrain)
        return thisDir != referenceDir
    }

    private fun formationEntryDirection(train: TrainEntity?): Int {
        if (train == null) {
            return 0
        }
        val currentFormation = train.formation ?: return 0
        val entry = currentFormation.getEntry(train) ?: return 0
        return if (entry.dir != 0) 1 else 0
    }

    var isPantographUp: Boolean
        get() = entityData.get<Boolean>(PANTOGRAPH_UP)!!
        set(value) {
            entityData.set<Boolean>(
                PANTOGRAPH_UP,
                value
            )
        }

    fun setPantographUpForFormation(value: Boolean) {
        if (level().isClientSide()) {
            this.isPantographUp = value
            return
        }
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train!!.isPantographUp = value
        })
    }

    var reverser: Int
        get() = entityData.get<Int>(REVERSER)!!
        set(value) {
            val clamped = Mth.clamp(value, -1, 1)
            entityData.set<Int>(
                REVERSER,
                clamped
            )
            entityData.set<Boolean>(
                REVERSE,
                clamped < 0
            )
        }
    var isReverse: Boolean
        get() = this.reverser < 0
        set(value) {
            this.reverser = if (value) -1 else 1
        }
    var destinationIndex: Int
        get() = entityData.get<Int>(DESTINATION_INDEX)!!
        set(value) {
            entityData.set<Int>(
                DESTINATION_INDEX,
                max(0, value)
            )
        }

    fun setDestinationIndexForFormation(value: Int) {
        val index = max(0, value)
        if (level().isClientSide()) {
            this.destinationIndex = index
            return
        }
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train!!.destinationIndex = index
        })
    }

    var soundIndex: Int
        get() = entityData.get<Int>(SOUND_INDEX)!!
        set(value) {
            entityData.set<Int>(
                SOUND_INDEX,
                max(0, value)
            )
        }
    var bodyRoll: Float
        get() = entityData.get<Float>(BODY_ROLL)!!
        set(value) {
            entityData.set<Float>(
                BODY_ROLL,
                value
            )
            this.rotationRoll = value
        }

    fun getVisualRoll(partialTicks: Float): Float {
        return Mth.lerp(partialTicks, prevRotationRoll, rotationRoll)
    }

    var customButtonBits: Int
        get() = entityData.get<Int>(CUSTOM_BUTTON_BITS)!!
        set(bits) {
            entityData.set<Int>(
                CUSTOM_BUTTON_BITS,
                bits
            )
        }
    var railProgress: Float
        get() = entityData.get<Float>(RAIL_PROGRESS)!!
        set(progress) {
            entityData.set<Float>(
                RAIL_PROGRESS,
                Mth.clamp(progress, 0.0f, 1.0f)
            )
        }

    private fun setLegacySignalState(signal: Int) {
        entityData.set<Int>(SIGNAL, Mth.clamp(signal, -1, 15))
    }

    private var seatAssignmentsData: String?
        get() = entityData.get<String>(SEAT_ASSIGNMENTS)
        private set(data) {
            entityData.set<String>(
                SEAT_ASSIGNMENTS,
                if (data == null) "" else data
            )
        }

    private fun setCoupledFollowerUuid(uuid: UUID?) {
        coupledFollowerUuid = uuid
        entityData.set<String>(COUPLED_FOLLOWER, if (uuid == null) "" else uuid.toString())
    }

    private fun setCoupledLeaderUuid(uuid: UUID?) {
        coupledLeaderUuid = uuid
        entityData.set<String>(COUPLED_LEADER, if (uuid == null) "" else uuid.toString())
    }

    fun isCustomButtonOn(index: Int): Boolean {
        if (index < 0 || index >= 31) return false
        return (this.customButtonBits and (1 shl index)) != 0
    }

    fun setCustomButton(index: Int, on: Boolean) {
        if (index < 0 || index >= 31) return
        val bits = this.customButtonBits
        val mask = 1 shl index
        this.customButtonBits = if (on) (bits or mask) else (bits and mask.inv())
    }

    fun toggleCustomButton(index: Int) {
        if (index < 0 || index >= 31) return
        setCustomButton(index, !isCustomButtonOn(index))
    }

    fun getScriptDataValue(key: String?): String? {
        if (key == null || key.isBlank()) {
            return ""
        }
        return scriptData.getOrDefault(key, "")
    }

    fun setScriptDataValue(key: String?, value: String?) {
        if (key == null || key.isBlank()) {
            return
        }
        scriptData.put(key, if (value == null) "" else value)
        scriptDataDirty = true
    }

    fun applyScriptDataSync(data: Map<String, String>?) {
        if (data == null || data.isEmpty()) return
        scriptData.putAll(data)
        for (entry in data.entries) {
            val key = entry.key
            if (key == null || !key.startsWith("Button")) continue
            try {
                val index = key.substring("Button".length).toInt()
                if (index >= 0 && index < 16) {
                    if (customButtonValues == null) customButtonValues = IntArray(16)
                    customButtonValues!![index] = entry.value!!.toInt()
                }
            } catch (ignored: Exception) {
            }
        }
    }

    fun getSoundScriptEngine(): ScriptEngine? {
        return this.soundScriptEngine
    }

    fun setSoundScriptEngine(soundScriptEngine: ScriptEngine?) {
        this.soundScriptEngine = soundScriptEngine
        this.attemptedSoundScriptLoad = true
    }

    private fun ensureServerScriptLoaded() {
        if (attemptedServerScriptLoad) return
        val id = this.vehicleId
        if (id == null || id.isBlank()) return
        val def = getById(id)
        if (def == null || !def.hasServerScript()) {
            attemptedServerScriptLoad = true
            return
        }
        attemptedServerScriptLoad = true
        try {
            serverScriptEngine = loadServerScriptForVehicle(def)
        } catch (t: Throwable) {
            RealTrainModRenewed.LOGGER.warn("Failed to load train server script for {}: {}", id, t.toString())
        }
    }

    override fun isPickable(): Boolean {
        return false
    }

    override fun canBeCollidedWith(other: Entity?): Boolean {
        return false
    }

    override fun getPickRadius(): Float {
        return 0.1f
    }

    override fun getDimensions(pose: Pose): EntityDimensions {
        return EntityDimensions.scalable(BODY_HITBOX_SIZE, BODY_HITBOX_SIZE)
    }

    private fun makeTrainBoundingBox(): AABB {
        val x = getX()
        val y = getY()
        val z = getZ()
        val half: Double = BODY_HITBOX_SIZE * 0.5
        return AABB(x - half, y - half, z - half, x + half, y + half, z + half)
    }

    val bodyHalfLengthForPlacement: Double
        get() = this.trainHalfLength

    val bodyHalfWidthForPlacement: Double
        get() = this.trainHalfWidth

    private val trainHalfLength: Double
        get() {
            val def =
                getById(this.vehicleId)
            // trainDistance stores center-to-end distance (half-length), not total length.
            var maxZ = max(1.75, this.trainDistance.toDouble())
            if (def != null) {
                for (bogie in def.getBogies()) {
                    maxZ = max(maxZ, abs(bogie.position().z) + 0.95)
                }
            }
            return maxZ
        }

    private val trainHalfWidth: Double
        get() {
            val def =
                getById(this.vehicleId)
            var maxX: Double = DEFAULT_HALF_WIDTH
            if (def != null) {
                for (bogie in def.getBogies()) {
                    maxX = max(maxX, abs(bogie.position().x) + 1.0)
                }
            }
            return maxX
        }

    private val trainHalfHeight: Double
        get() {
            val def =
                getById(this.vehicleId)
            var maxY: Double = DEFAULT_HALF_HEIGHT
            if (def != null) {
                for (seat in def.getAllSeatPositions()) {
                    maxY = max(maxY, seat.y + 1.8)
                }
            }
            return maxY
        }

    override fun tick() {
        super.tick()
        field_70173_aa = tickCount
        field_70177_z = getYRot()
        field_70170_p.field_72995_K = level().isClientSide()
        prevRotationRoll = rotationRoll
        rotationRoll = entityData.get<Float>(BODY_ROLL)!!

        // 動輪/ロッドの回転角を「毎tickの移動距離」で累積する。
        // 旧実装(tickCount × 現在速度)は速度が少しでも変わるたびに全履歴が再スケールされ、
        // tickCount が大きいほど巨大な回転ジャンプ→「空転しまくり」になっていた。
        // 正しくは各tickに進んだ距離分だけ回す(= 速度/円周 × 360)。
        run {
            val distThisTick = abs(this.speed)
            val wheelCircumference = (2.0 * Math.PI * 0.43).toFloat()
            wheelRotationDegrees = (wheelRotationDegrees + (distThisTick / wheelCircumference) * 360.0f) % 360.0f
        }

        // スクリプトのtick関数を呼び出す
        updateTrainAnimationState()
        updateBrakeAirState()
        if (level().isClientSide() && soundScriptEngine == null && !attemptedSoundScriptLoad) {
            attemptedSoundScriptLoad = true
            val soundVehicle = getById(
                this.vehicleId
            )
            if (soundVehicle != null && soundVehicle.hasSoundScript()) {
                setSoundScriptEngine(loadSoundScriptForVehicle(soundVehicle))
            }
        }
        val runScriptTick = !level().isClientSide() || shouldRunClientVisualScriptThisTick()
        if (level().isClientSide() && soundScriptEngine != null &&
            !TrainScriptSystem.isLegacyScriptDisabled(soundScriptEngine)
        ) {
            TrainScriptSystem.invokeScriptTick(soundScriptEngine, this, true)
            TrainScriptSystem.invokeScriptUpdate(soundScriptEngine, this, 1.0f)
        } else {
            if (level().isClientSide()) {
                if (soundScriptEngine != null) {
                    LegacyScriptSoundManager.stopAll(this)
                    setSoundScriptEngine(null)
                }
                tickJsonRunningSound(this)
            }
            // 音は毎tick更新するが、重い描画スクリプトは既存の間引き設定を尊重する。
            if (runScriptTick && scriptEngine != null) {
                TrainScriptSystem.invokeScriptTick(scriptEngine, this)
            }
        }

        if (level().isClientSide()) {
            if (interpolationHandler.hasActiveInterpolation()) {
                interpolationHandler.interpolate()
            } else {
                reapplyPosition()
                setRot(getYRot(), getXRot())
            }
            updateClientBogieOffsetInterpolation()
            return
        }

        // SD8200 等の serverScriptPath（Server_sd8200_1.js など）を毎tick実行。
        // 方向幕(pck_maku) や buildData 等の DataMap を書き換える処理がここで走る。
        ensureServerScriptLoaded()
        if (serverScriptEngine != null) {
            TrainScriptSystem
                .invokeServerScriptOnUpdate(serverScriptEngine, this)
        }

        if (scriptDataDirty && !scriptData.isEmpty()) {
            scriptDataDirty = false
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                this, TrainScriptDataPayload(
                    this.getId(), HashMap<String, String>(scriptData)
                )
            )
        }

        if (!level().isClientSide()) {
            pruneSeatAssignments()
            for (passenger in getPassengers().toList()) {
                if (passenger is Player && passenger.isShiftKeyDown()) {
                    forceDismountPassenger(passenger)
                }
            }
            if (activeDriverTicks > 0) {
                activeDriverTicks--
            } else {
                activeDriverUuid = null
            }
            // Lazy formation init for head/solo cars
            if (!level().isClientSide() && formation == null && coupledLeaderUuid == null) {
                rebuildFormationFromUuidChain()
            }
            refreshFormationDirectionFromActiveDriver()
            val isFormationFollower = (formation != null && !formation!!.isFrontCar(this))
                    || (formation == null && coupledLeaderUuid != null)
            if (isFormationFollower) {
                setDeltaMovement(Vec3.ZERO)
                this.hurtMarked = true
                this.hurtMarked = true
                // 後続車もここで早期returnするため、台車位置の同期を必ず行う。
                // (アンカーは編成側 formation.updateTrainMovement が設定済み。)
                // これをしないと後続車の台車がクライアントで剛体描画になりカーブでズレる。
                syncBogieRenderOffsets()
                refreshInteractionHitboxes(force = true)
                return
            }

            var speed = this.speed
            var notch = this.notch

            // ATSA(別mod)は NeoForge の EntityTickEvent 経由で notch を制御するため、
            // ここでの直接呼び出しは行わない。ATSA未導入時は何も起きない。
            val formationDriver =
                this.formationDriver
            val controller = if (formationDriver != null) formationDriver.controller else null
            val cabTrain = if (formationDriver != null) formationDriver.cabTrain else this

            if (this.reverser == 0 && notch > 0) {
                notch = 0
            }

            speed = applyNotchPhysics(speed, notch)

            this.notch = notch

            if (notch <= 0) {
                val drag: Float = DRAG_BASE + abs(speed) * DRAG_SPEED_FACTOR
                speed = approachZero(speed, drag)
            }
            speed *= FRICTION
            if (abs(speed) < 0.001f && notch <= 0) speed = 0.0f

            this.speed = speed

            // 先に支え(レール)の有無を判定する。レールが壊れて支えが無い時に travelAlongRail を
            // 走らせると、キャッシュ済みアンカー(壊れたレール位置)へスナップし続けて空中に浮くため、
            // 支え無しならレール追従をスキップして重力に任せる。
            val unsupported = this.isUnsupportedInAir
            val onRail = !unsupported && travelAlongRail(speed, controller, cabTrain)
            if (!onRail || unsupported) {
                stopFormationAtRailBoundary()
                setNoGravity(false)
                val dm = getDeltaMovement()
                var vy = dm.y
                vy -= 0.08 // 重力加速度
                vy *= 0.98 // 空気抵抗
                val vx = dm.x * 0.98
                val vz = dm.z * 0.98
                setDeltaMovement(vx, vy, vz)
                move(MoverType.SELF, getDeltaMovement())
                if (onGround() || verticalCollision) {
                    setDeltaMovement(getDeltaMovement().multiply(0.6, 0.0, 0.6))
                }
                this.hurtMarked = true
                this.hurtMarked = true
            } else {
                if (!isNoGravity()) {
                    setNoGravity(true)
                }
            }

            if (formation != null && formation!!.size() > 1) {
                formation!!.updateTrainMovement()
            }
            updateLegacySignalFromRail()
            scanNearbyCouplerContacts()
            tryCompletePendingCoupling()

            // 端台車のワールド位置をクライアントへ同期(カーブで台車をレール上に正確に描くため)。
            syncBogieRenderOffsets()
            refreshInteractionHitboxes(force = !isNoGravity)

            hurtMarked = abs(speed) > 0.001f
        }
    }

    override fun getInterpolation(): InterpolationHandler = interpolationHandler

    private fun applyNotchPhysics(speed: Float, notch: Int): Float {
        val def = getById(
            this.vehicleId
        )
        val accelBase = getConfiguredAcceleration(def)

        if (notch > 0) {
            if (this.reverser == 0) {
                return speed
            }
            val maxSpeed = getConfiguredMaxSpeed(def, notch)
            val absSpeed = abs(speed)
            val speedRatio = Mth.clamp(absSpeed / maxSpeed, 0.0f, 1.0f)
            if (absSpeed >= maxSpeed) {
                return speed
            }
            // 力行: JSON の acceleration は P5 の基準加速度として扱う。
            val notchFactor = notch / getMaxPowerNotch(def).toFloat()
            val tractionCurve = 1.0f - speedRatio.toDouble().pow(3.0).toFloat()
            val accelCurve = accelBase * (0.35f + notchFactor * 0.65f) * tractionCurve
            val next = speed + max(0.0f, accelCurve)
            return if (abs(next) > maxSpeed) maxSpeed.withSign(next) else next
        }

        if (notch < 0) {
            // Brake force follows the gradually changing cylinder pressure.
            // This keeps the HUD air gauges and actual deceleration tied together.
            val maxPressure: Float = if (-notch >= getMaxBrakeNotch(def))
                BRAKE_CYLINDER_EMERGENCY_MAX
            else
                BRAKE_CYLINDER_SERVICE_MAX
            val ratio = Mth.clamp(this.brakeCylinderPressure / max(1.0f, maxPressure), 0.0f, 1.0f)
            val serviceDecel = getConfiguredBrakeDeceleration(def, -notch)
            val decel = max(0.00018f, serviceDecel * ratio)
            return approachZero(speed, decel)
        }

        return speed
    }

    private fun getConfiguredMaxSpeed(def: VehicleDefinition?, notch: Int): Float {
        if (def != null && notch > 0 && !def.getNotchMaxSpeeds().isEmpty()) {
            val index = Mth.clamp(notch - 1, 0, def.getNotchMaxSpeeds().size - 1)
            val configured = def.getNotchMaxSpeeds().get(index)
            if (configured > 0.0f) {
                return configured
            }
        }
        // 本家RTM(EnumNotch): 力行ノッチごとに最高速を制限する(P_n = 0.36*n)。
        // これで低ノッチは低速で頭打ち、高ノッチほど伸びる本家挙動になる。
        if (notch > 0) {
            return RTM_POWER_SPEED_PER_NOTCH * min(notch, MAX_POWER_NOTCH)
        }
        return MAX_SPEED
    }

    private fun getConfiguredAcceleration(def: VehicleDefinition?): Float {
        if (def != null && def.getAcceleration() > 0.0f) {
            return Mth.clamp(def.getAcceleration(), 0.0002f, 0.0060f)
        }
        return 0.001736f
    }

    private fun getConfiguredBrakeDeceleration(def: VehicleDefinition?, brakeNotch: Int): Float {
        if (def != null && !def.getBrakeDecelerations().isEmpty()) {
            val index = Mth.clamp(brakeNotch, 0, def.getBrakeDecelerations().size - 1)
            val configured = abs(def.getBrakeDecelerations().get(index))
            if (configured > 0.0f) {
                return configured
            }
        }
        return if (brakeNotch >= getMaxBrakeNotch(def)) 0.0100f else 0.0035f
    }

    private fun getMaxBrakeNotch(def: VehicleDefinition?): Int {
        if (def != null && !def.getBrakeDecelerations().isEmpty()) {
            return Mth.clamp(def.getBrakeDecelerations().size - 1, 1, 12)
        }
        return MAX_BRAKE_NOTCH
    }

    val legacyBrakeAirCount: Float
        get() {
            val ratio = Mth.clamp(
                this.brakeCylinderPressure / BRAKE_CYLINDER_EMERGENCY_MAX,
                0.0f,
                1.0f
            )
            return LEGACY_MAX_AIR_COUNT - ratio * (LEGACY_MAX_AIR_COUNT - LEGACY_MIN_AIR_COUNT)
        }

    private fun shouldRunClientVisualScriptThisTick(): Boolean {
        if (!level().isClientSide()) {
            return true
        }
        val mc = Minecraft.getInstance()
        if (mc.player != null && mc.player!!.getVehicle() === this) {
            return true
        }
        val cameraPos = mc.gameRenderer.getMainCamera().position()
        val distanceSq = cameraPos.distanceToSqr(getX(), getY() + 1.5, getZ())
        if (distanceSq > 140.0 * 140.0) {
            return (tickCount and 7) == 0
        }
        if (distanceSq > 80.0 * 80.0) {
            return (tickCount and 3) == 0
        }
        if (distanceSq > 40.0 * 40.0) {
            return (tickCount and 1) == 0
        }
        return true
    }

    private fun updateTrainAnimationState() {
        doorMoveL = approach(doorMoveL, if (this.isDoorLeftOpen) 60.0f else 0.0f, 1.0f)
        doorMoveR = approach(doorMoveR, if (this.isDoorRightOpen) 60.0f else 0.0f, 1.0f)
        // 本家RTM準拠: pantograph movement(=pantograph_F/40) は「下降量」。DOWN で 40(=1.0)、UP で 0。
        // (RTMU は従来 UP で 40 にしており、パンタ上で下降表示・下で上昇表示と逆になっていた。)
        pantograph_F = approach(pantograph_F, if (this.isPantographUp) 0.0f else 40.0f, 1.0f)
        pantograph_B = approach(pantograph_B, if (this.isPantographUp) 0.0f else 40.0f, 1.0f)
        val reverserDir = this.reverser
        // 座席 script 側は -45〜45 度前提なので、進行方向へゆっくり寄せていく。
        if (reverserDir < 0 && seatRotation > -45.0f) {
            seatRotation -= 1.0f
        } else if (reverserDir > 0 && seatRotation < 45.0f) {
            seatRotation += 1.0f
        }
        seatRotation = Mth.clamp(seatRotation, -45.0f, 45.0f)
    }

    private fun updateBrakeAirState() {
        val brakeNotch = max(0, -this.notch)
        val ratio = Mth.clamp(brakeNotch / max(1, this.maxBrakeNotch).toFloat(), 0.0f, 1.0f)
        val emergency = brakeNotch >= this.maxBrakeNotch
        val targetPipe: Float = BRAKE_PIPE_NORMAL - ratio * BRAKE_PIPE_SERVICE_DROP
        val targetCylinder: Float = if (emergency)
            BRAKE_CYLINDER_EMERGENCY_MAX
        else
            ratio * BRAKE_CYLINDER_SERVICE_MAX
        val targetReservoir: Float = MAIN_RESERVOIR_NORMAL - min(35.0f, targetCylinder * 0.06f)

        this.brakePipePressure = approach(
            this.brakePipePressure,
            targetPipe,
            BRAKE_PIPE_RATE
        )
        this.brakeCylinderPressure = approach(
            this.brakeCylinderPressure,
            targetCylinder,
            if (targetCylinder > this.brakeCylinderPressure) BRAKE_APPLY_RATE else BRAKE_RELEASE_RATE
        )
        this.mainReservoirPressure = approach(
            this.mainReservoirPressure,
            targetReservoir,
            MAIN_RESERVOIR_RATE
        )
    }

    private fun canTravelOnRail(worldPos: Vec3): Boolean {
        val base = BlockPos.containing(worldPos.x, worldPos.y - 0.2, worldPos.z)
        for (dy in -1..1) {
            val pos = base.offset(0, dy, 0)
            val block = level().getBlockState(pos).getBlock()
            if (block is RailCollisionBlock || block is LargeRailCoreBlock) {
                return true
            }
        }
        return false
    }

    private fun updateLegacySignalFromRail() {
        if (level().isClientSide() || activeRailMap == null || activeRailSplit <= 0 || activeRailPosition < 0.0) {
            return
        }
        val core = findActiveRailCore()
        if (core != null) {
            setRailSignal(core.lastSignalStrength)
        }
    }

    private fun setRailSignal(signal: Int) {
        if (this.signal != -1) {
            setSignal2(max(0, signal))
        }
    }

    private fun findActiveRailCore(): LargeRailCoreBlockEntity? {
        val sample = sampleRail(activeRailMap!!, activeRailSplit, activeRailPosition)
        val base = BlockPos.containing(sample.x, sample.y, sample.z)
        val direct = railCoreAt(base)
        if (direct != null) {
            return direct
        }
        for (dy in -1..1) {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val core = railCoreAt(base.offset(dx, dy, dz))
                    if (core != null) {
                        return core
                    }
                }
            }
        }
        return null
    }

    private fun railCoreAt(pos: BlockPos): LargeRailCoreBlockEntity? {
        val blockEntity = level().getBlockEntity(pos)
        if (blockEntity is LargeRailCoreBlockEntity && blockEntity.isLoaded) {
            return blockEntity
        }
        if (blockEntity is RailCollisionBlockEntity) {
            val corePos = blockEntity.getCorePos()
            val core = if (corePos == null) null else level().getBlockEntity(corePos)
            if (core is LargeRailCoreBlockEntity && core.isLoaded) {
                return core
            }
        }
        return null
    }

    private fun syncCoupledFollower() {
        if (coupledFollowerUuid == null || level().isClientSide()) {
            return
        }
        val followerRaw = (level() as ServerLevel).getEntity(coupledFollowerUuid!!)
        if (followerRaw !is TrainEntity || !followerRaw.isAlive()) {
            setCoupledFollowerUuid(null)
            coupledFollowerThisSide = -1
            coupledFollowerOtherSide = 1
            return
        }

        followerRaw.setCoupledLeaderUuid(this.getUUID())
        followerRaw.notch = this.notch
        followerRaw.speed = this.speed
        followerRaw.reverser = this.reverser

        val gap: Double = getCoupledGap(this, followerRaw)
        if (!placeCoupledFollowerOnRail(followerRaw, coupledFollowerThisSide, coupledFollowerOtherSide)) {
            placeCoupledFollowerFallback(followerRaw, coupledFollowerThisSide, coupledFollowerOtherSide, gap)
        }
        followerRaw.hurtMarked = true
        followerRaw.hurtMarked = true
    }

    private fun placeCoupledFollowerFallback(follower: TrainEntity?, thisSide: Int, followerSide: Int, gap: Double) {
        if (follower == null) {
            return
        }
        val currentSide: Int = normalizeCouplerSide(thisSide)
        val otherSide: Int = normalizeCouplerSide(followerSide)
        var forward = localToWorld(Vec3(0.0, 0.0, 1.0)).subtract(position()).normalize()
        if (forward.lengthSqr() < 1.0E-6) {
            val yawRad = Math.toRadians(-getYRot().toDouble())
            forward = Vec3(-sin(yawRad), 0.0, cos(yawRad))
        }
        // 連結間隔は当たり判定用の膨張長ではなく実車体端(連結面)で配置する(短車両の間隔過大対策)。
        val thisHalf = this.couplingHalfLength
        val followerHalf = follower.couplingHalfLength
        val followerDirection = -currentSide * otherSide
        val coupler = position().add(forward.scale(currentSide * (thisHalf + COUPLED_CLEARANCE)))
        val center = coupler.subtract(forward.scale(followerDirection * otherSide * followerHalf))
        val yaw = if (followerDirection < 0) Mth.wrapDegrees(getYRot() + 180.0f) else getYRot()
        follower.setPos(center.x, getY(), center.z)
        follower.setRot(yaw, follower.getXRot())
    }

    private fun syncCoupledChain() {
        if (level() !is ServerLevel) {
            return
        }

        var current = this
        var guard = 0
        while (current.coupledFollowerUuid != null && guard++ < 16) {
            val nextRaw: Entity? = serverLevel.getEntity(current.coupledFollowerUuid!!)
            if (nextRaw !is TrainEntity || !nextRaw.isAlive()) {
                current.setCoupledFollowerUuid(null)
                current.coupledFollowerThisSide = -1
                current.coupledFollowerOtherSide = 1
                break
            }

            nextRaw.setCoupledLeaderUuid(current.getUUID())
            nextRaw.notch = current.notch
            nextRaw.speed = current.speed
            nextRaw.reverser = current.reverser
            nextRaw.setDeltaMovement(Vec3.ZERO)

            val gap: Double = getCoupledGap(current, nextRaw)
            if (!current.placeCoupledFollowerOnRail(
                    nextRaw,
                    current.coupledFollowerThisSide,
                    current.coupledFollowerOtherSide
                )
            ) {
                current.placeCoupledFollowerFallback(
                    nextRaw,
                    current.coupledFollowerThisSide,
                    current.coupledFollowerOtherSide,
                    gap
                )
            }
            nextRaw.hurtMarked = true
            nextRaw.hurtMarked = true

            current = nextRaw
        }
    }

    private fun syncCoupledFormationFromHead() {
        if (level() !is ServerLevel) {
            syncCoupledChain()
            return
        }

        var head = this
        var guard = 0
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            val leaderRaw: Entity? = serverLevel.getEntity(head.coupledLeaderUuid!!)
            if (leaderRaw is TrainEntity && leaderRaw.isAlive()) {
                head = leaderRaw
            } else {
                head.setCoupledLeaderUuid(null)
                break
            }
        }
        head.syncCoupledChain()
    }

    private fun placeCoupledFollowerOnRail(follower: TrainEntity?, thisSide: Int, followerSide: Int): Boolean {
        if (follower == null || activeRailMap == null || activeRailSplit <= 0 || activeRailPosition < 0.0) {
            return false
        }
        val currentSide: Int = normalizeCouplerSide(thisSide)
        val otherSide: Int = normalizeCouplerSide(followerSide)
        val bodyDirection = if (activeRailBodyDirection == 0) 1 else activeRailBodyDirection
        val gap: Double = getCoupledGap(this, follower)

        // Use anchor-based traversal instead of index arithmetic so rail joints are
        // handled correctly without relying on resolveRailSample's single-crossing limit.
        // offset = currentSide * gap: bodyDirection is already encoded in travelDirection,
        // so negative offset flips direction toward the follower regardless of rail orientation.
        val leaderCenter = TrainEntity.RailAnchor(
            activeRailMap!!, activeRailSplit,
            Mth.clamp(activeRailPosition, 0.0, activeRailSplit.toDouble()),
            bodyDirection
        )
        val followerCenter = advanceAnchorAlongPath(leaderCenter, currentSide.toDouble() * gap)
        if (!isRailAnchorUsable(followerCenter)) {
            return false
        }

        val desiredFollowerBodyDirection = bodyDirection * (-currentSide * otherSide)
        val pair = follower.createAnchorPairFromCenter(
            followerCenter!!.map,
            followerCenter.split,
            followerCenter.index,
            desiredFollowerBodyDirection
        )
        if (!isRailAnchorUsable(pair.front) || !isRailAnchorUsable(pair.rear)) {
            return false
        }
        follower.frontRailAnchor = pair.front
        follower.rearRailAnchor = pair.rear
        follower.activeRailMap = followerCenter.map
        follower.activeRailSplit = followerCenter.split
        follower.activeRailPosition = Mth.clamp(followerCenter.index, 0.0, followerCenter.split.toDouble())
        follower.activeRailIndex =
            Mth.clamp(Math.round(follower.activeRailPosition).toInt(), 0, follower.activeRailSplit)
        follower.activeRailBodyDirection = desiredFollowerBodyDirection
        follower.activeRailDirection = activeRailDirection * (-currentSide * otherSide)
        follower.railProgress = follower.activeRailIndex / follower.activeRailSplit.toFloat()

        val yaw = follower.getRailYawForBody(
            follower.activeRailMap,
            follower.activeRailSplit,
            follower.activeRailPosition,
            follower.activeRailBodyDirection,
            follower.getYRot()
        )
        val pitch = follower.getRailPitchForBody(
            follower.activeRailMap,
            follower.activeRailSplit,
            follower.activeRailPosition,
            follower.activeRailBodyDirection
        )
        follower.applyPoseFromBogieSamples(pair.frontSample, pair.rearSample, yaw, pitch, false)
        follower.setDeltaMovement(Vec3.ZERO)
        return true
    }

    private val couplingHalfLength: Double
        /**
         * 連結間隔に使う「中心→連結面(車体端)」距離。trainDistance が center-to-end。
         * getTrainHalfLength は台車/座席位置で膨張した当たり判定用の長さで、短い車体だと
         * 実車体より長くなり連結間隔が伸びすぎる。連結にはこの実車体端を使う。
         */
        get() = max(1.0, this.trainDistance.toDouble())

    private fun forEachFormationTrain(action: Consumer<TrainEntity?>?) {
        if (action == null) {
            return
        }
        if (formation != null) {
            formation!!.trainStream().forEach(action)
            return
        }
        if (level() !is ServerLevel) {
            action.accept(this)
            return
        }

        var head = this
        var guard = 0
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            val leaderRaw: Entity? = serverLevel.getEntity(head.coupledLeaderUuid!!)
            if (leaderRaw is TrainEntity && leaderRaw.isAlive()) {
                head = leaderRaw
            } else {
                head.setCoupledLeaderUuid(null)
                break
            }
        }

        var current = head
        guard = 0
        while (current != null && guard++ < 16) {
            action.accept(current)
            if (current.coupledFollowerUuid == null) {
                break
            }
            val followerRaw: Entity? = serverLevel.getEntity(current.coupledFollowerUuid!!)
            if (followerRaw is TrainEntity && followerRaw.isAlive()) {
                current = followerRaw
            } else {
                current.setCoupledFollowerUuid(null)
                break
            }
        }
    }

    val formationHead: TrainEntity
        get() {
            if (formation != null) {
                val front: FormationEntry? =
                    formation!!.getFrontEntry()
                if (front != null && front.train != null && front.train.isAlive()) {
                    return front.train
                }
            }
            if (level() !is ServerLevel) {
                return this
            }

        var head = this
        var guard = 0
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            val leaderRaw: Entity? = serverLevel.getEntity(head.coupledLeaderUuid!!)
                if (leaderRaw is TrainEntity && leaderRaw.isAlive()) {
                    head = leaderRaw
                } else {
                    head.setCoupledLeaderUuid(null)
                    break
                }
            }
            return head
        }

    private val formationTail: TrainEntity
        get() {
            if (level() !is ServerLevel) {
                return this
            }

        var tail = this.formationHead
        var guard = 0
        while (tail.coupledFollowerUuid != null && guard++ < 16) {
            val followerRaw: Entity? = serverLevel.getEntity(tail.coupledFollowerUuid!!)
                if (followerRaw is TrainEntity && followerRaw.isAlive()) {
                    tail = followerRaw
                } else {
                    tail.setCoupledFollowerUuid(null)
                    break
                }
            }
            return tail
        }

    private val formationDriver: FormationDriver?
        get() {
            if (level().isClientSide()) {
                var driver = this.driverPassenger
                if (driver == null) {
                    driver = this.firstAssignedPassenger
                }
                return if (driver == null) null else FormationDriver(
                    driver,
                    this
                )
            }

            val result =
                arrayOfNulls<FormationDriver>(1)
            forEachFormationTrain(Consumer { train: TrainEntity? ->
                if (result[0] != null || train!!.activeDriverUuid == null) {
                    return@Consumer
                }
                val driver = train.findAssignedPassenger(train.activeDriverUuid)
                if (driver != null) {
                    result[0] = FormationDriver(driver, train)
                }
            })
            if (result[0] != null) {
                return result[0]
            }
            forEachFormationTrain(Consumer { train: TrainEntity? ->
                if (result[0] != null) {
                    return@Consumer
                }
                val driver = train!!.driverPassenger
                if (driver != null) {
                    result[0] = FormationDriver(driver, train)
                }
            })
            if (result[0] != null) {
                return result[0]
            }
            forEachFormationTrain(Consumer { train: TrainEntity? ->
                if (result[0] != null) {
                    return@Consumer
                }
                val passenger = train!!.firstAssignedPassenger
                if (passenger is Player) {
                    result[0] = FormationDriver(passenger, train)
                }
            })
            return result[0]
        }

    @JvmRecord
    private data class RailFollowContext(
        val map: RailMap,
        val split: Int,
        val nearestIndex: Int,
        val distanceSq: Double
    )

    @JvmRecord
    private data class FormationDriver(val controller: Entity?, val cabTrain: TrainEntity?)

    @JvmRecord
    private data class RailResolvedSample(
        val sample: RailSample,
        val map: RailMap?,
        val split: Int,
        val index: Double,
        val bodyDirection: Int
    )

    @JvmRecord
    private data class RailAnchor(val map: RailMap, val split: Int, val index: Double, val travelDirection: Int)

    @JvmRecord
    private data class RailAnchorPair(
        val front: RailAnchor?,
        val rear: RailAnchor?,
        val frontSample: RailSample,
        val rearSample: RailSample,
        val distanceSq: Double
    )

    @JvmRecord
    private data class RailConnection(
        val map: RailMap,
        val split: Int,
        val index: Double,
        val travelDirection: Int,
        val score: Double
    )

    @JvmRecord
    private data class RailSample(val x: Double, val y: Double, val z: Double)

    private val isUnsupportedInAir: Boolean
        /**
         * 列車真下に支えとなるブロック / レールが存在するかをチェック。
         * 体下方 3 ブロックがすべて air なら未支持 = 重力で落下対象とみなす。
         */
        get() {
            if (level() == null) return false
            // 列車を支えるのは「地面の任意ブロック」ではなく「レール」。レール系ブロック(当たり判定/コア/道床)が
            // 近くに無ければ脱線=支え無しとみなし重力で落下させる。
            // 車体中心だけ見ると、カーブでは車体中心がレール中心線から内側へずれて当たり判定ブロックを外し、
            // 誤って「非支持」と判定→重力で地面に埋まる。前後台車はレール上にあるので、台車位置周辺も見る。
            // (レールが折れた場合は台車位置の当たり判定ブロックも消えるため、落下判定は従来どおり機能する。)
            if (hasRailSupportNear(blockPosition())) {
                return false
            }
            if (isRailAnchorUsable(frontRailAnchor)) {
                val s =
                    sampleBogieRail(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
                if (s != null && hasRailSupportNear(BlockPos.containing(s.x, s.y, s.z))) {
                    return false
                }
            }
            if (isRailAnchorUsable(rearRailAnchor)) {
                val s =
                    sampleBogieRail(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
                if (s != null && hasRailSupportNear(BlockPos.containing(s.x, s.y, s.z))) {
                    return false
                }
            }
            return true
        }

    private fun hasRailSupportNear(base: BlockPos): Boolean {
        for (dy in -2..1) {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val b =
                        level().getBlockState(base.offset(dx, dy, dz)).getBlock()
                    if (b is RailCollisionBlock
                        || b is LargeRailCoreBlock
                        || b is BallastBlock
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun travelAlongRail(speed: Float, controller: Entity?, cabTrain: TrainEntity?): Boolean {
        val startX = getX()
        val startY = getY()
        val startZ = getZ()
        // Direction of travel is driven purely by the reverser.
        // Cab direction (seat Z position) is a UI concept only — reading it here caused
        // the train to violently reverse the moment a player mounted a rear seat.
        var controllerDirection = Integer.compare(this.reverser, 0)
        if (controllerDirection == 0 && abs(speed) > 0.0f) {
            controllerDirection = if (activeRailDirection != 0) activeRailDirection else (if (speed >= 0.0f) 1 else -1)
        }
        var distance = abs(speed).toDouble()
        if (distance > 0.0 && controllerDirection == 0) {
            this.speed = 0.0f
            distance = 0.0
        }

        if (!ensureBogieAnchors()) {
            this.speed = 0.0f
            setDeltaMovement(Vec3.ZERO)
            return false
        }
        if (activeRailBodyDirection == 0) {
            activeRailBodyDirection = if (controllerDirection == 0) 1 else controllerDirection
        }
        syncActiveRailStateFromAnchors(activeRailBodyDirection)
        if (activeRailMap == null || activeRailSplit <= 0) {
            val context =
                this.activeRailContext
            if (context == null) {
                return false
            }
            activeRailMap = context.map
            activeRailSplit = context.split
            if (activeRailPosition < 0.0) {
                activeRailPosition = context.nearestIndex.toDouble()
            }
            activeRailIndex = Mth.clamp(Math.round(activeRailPosition).toInt(), 0, activeRailSplit)
            if (activeRailBodyDirection == 0) {
                activeRailBodyDirection =
                    getBodyDirectionOnRail(activeRailMap, activeRailSplit, activeRailIndex, getYRot())
            }
        }

        if (distance > 0.0) {
            // ワープ検出用に、前進前のアンカー・方向・本体位置を保存しておく。
            val preFrontAnchor = frontRailAnchor
            val preRearAnchor = rearRailAnchor
            val preBodyDirection = activeRailBodyDirection
            val preX = getX()
            val preY = getY()
            val preZ = getZ()
            if (advanceBogiePairAlongPath(distance, controllerDirection)) {
                var front = sampleBogieRail(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
                var rear = sampleBogieRail(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
                if (front == null || rear == null) {
                    if (!restabilizeBogieAnchors(controllerDirection)) {
                        this.speed = 0.0f
                        setDeltaMovement(Vec3.ZERO)
                        return false
                    }
                    front = sampleBogieRail(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
                    rear = sampleBogieRail(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
                }
                // ワープ・ガード: 逆向きに繋がったレール継ぎ目等で本体中心が1tickに
                // 物理的にあり得ない距離(速度×tickを大きく超える)ジャンプする不具合がある
                // (bodyDir が毎tick反転→前進方向が反転→2レール間を往復ワープ→速度0/1・めり込み)。
                // この場合は更新を棄却し、直前のアンカー・方向・位置を維持して振動を止める。
                // 通常走行・カーブは移動量が小さくしきい値に達しないため一切影響しない。
                val candidateCenter = resolveBodyCenterSample(front, rear)
                val jumpDx = candidateCenter.x - preX
                val jumpDz = candidateCenter.z - preZ
                val jumpSq = jumpDx * jumpDx + jumpDz * jumpDz
                val allowed: Double = distance + RAIL_TELEPORT_TOLERANCE
                if (jumpSq > allowed * allowed && (preX != 0.0 || preZ != 0.0)) {
                    // 復帰試行: スパン逆算が膨らみレール等で遠点を拾った場合でも永久停止しないよう、
                    // 前台車は素直に前進、後台車は「前回後台車位置の近傍」で前台車からスパン直線距離の
                    // 点に補正する(連続性優先=膨らみの遠点を拾わない)。結果がしきい値内なら採用して進む。
                    var recovered = false
                    run {
                        // 前台車は advanceBogiePairAlongPath が選んだ進行先(分岐切替時は分岐先)をそのまま維持し、
                        // 後台車だけ「前回後台車位置の近傍」で前台車から台車間隔の点に補正する。これで
                        // 分岐ルートを維持したまま、後台車がレールの膨らみの遠点を拾うのを防ぐ。
                        val bz = this.bogieRailOffsets
                        val span = abs(bz[1] - bz[0])
                        val recRear = refineAnchorByStraightDistance(preRearAnchor, front, span)
                        if (isRailAnchorUsable(recRear)) {
                            val rr = sampleBogieRail(recRear!!.map, recRear.split, recRear.index)
                            if (rr != null) {
                                val rc = resolveBodyCenterSample(front, rr)
                                val rjx = rc.x - preX
                                val rjz = rc.z - preZ
                                if (rjx * rjx + rjz * rjz <= allowed * allowed) {
                                    rearRailAnchor = recRear
                                    rear = rr
                                    recovered = true
                                }
                            }
                        }
                    }
                    if (!recovered) {
                        // ワープを棄却: 元のアンカー/方向に戻し、本体は動かさない(速度は維持)。
                        frontRailAnchor = preFrontAnchor
                        rearRailAnchor = preRearAnchor
                        activeRailBodyDirection = preBodyDirection
                        syncActiveRailStateFromAnchors(if (preBodyDirection == 0) 1 else preBodyDirection)
                        setDeltaMovement(Vec3.ZERO)
                        if (stallLogCooldown <= 0) {
                            stallLogCooldown = 20
                            val fm = frontRailAnchor!!.map
                            val rm = rearRailAnchor!!.map
                            RealTrainModRenewed.LOGGER.debug(
                                "[RTM-DBG] TELEPORT-REJECT veh={} jump={} allowed={} from=({},{}) to=({},{})",
                                this.vehicleId, sqrt(jumpSq).toFloat(), allowed.toFloat(),
                                preX.toFloat(), preZ.toFloat(), candidateCenter.x.toFloat(), candidateCenter.z.toFloat()
                            )
                            RealTrainModRenewed.LOGGER.debug(
                                "[RTM-DBG]   front: pos=({},{}) idx={}/{} dir={} map={} | rear: pos=({},{}) idx={}/{} dir={} map={}",
                                front.x.toFloat(),
                                front.z.toFloat(),
                                frontRailAnchor!!.index.toFloat(),
                                frontRailAnchor!!.split,
                                frontRailAnchor!!.travelDirection,
                                if (fm == null) "null" else (fm.javaClass.getSimpleName() + railEndpoints(fm)),
                                rear.x.toFloat(),
                                rear.z.toFloat(),
                                rearRailAnchor!!.index.toFloat(),
                                rearRailAnchor!!.split,
                                rearRailAnchor!!.travelDirection,
                                if (rm == null) "null" else (rm.javaClass.getSimpleName() + railEndpoints(rm))
                            )
                        }
                        return true
                    }
                }
                val appliedYaw = applyPoseFromBogieSamples(front, rear, getYRot(), getXRot(), true)
                syncBogieOrientationMemory(front, rear, appliedYaw, getXRot())
                activeRailBodyDirection = chooseStableBodyDirection(
                    activeRailMap,
                    activeRailSplit,
                    activeRailIndex,
                    appliedYaw,
                    if (activeRailBodyDirection == 0) 1 else activeRailBodyDirection
                )
            } else {
                stopFormationAtRailBoundary()
                setDeltaMovement(Vec3.ZERO)
                if (!restabilizeBogieAnchors(controllerDirection)) {
                    return false
                }
            }
        }

        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)) {
            if (!restabilizeBogieAnchors(controllerDirection)) {
                this.speed = 0.0f
                setDeltaMovement(Vec3.ZERO)
                return false
            }
        }

        var front = sampleBogieRail(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
        var rear = sampleBogieRail(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
        if (front == null || rear == null) {
            if (!restabilizeBogieAnchors(controllerDirection)) {
                this.speed = 0.0f
                setDeltaMovement(Vec3.ZERO)
                return false
            }
            front = sampleBogieRail(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
            rear = sampleBogieRail(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
        }
        val appliedYaw = applyPoseFromBogieSamples(front, rear, getYRot(), getXRot(), false)
        syncBogieOrientationMemory(front, rear, appliedYaw, getXRot())
        activeRailBodyDirection = chooseStableBodyDirection(
            activeRailMap,
            activeRailSplit,
            activeRailIndex,
            appliedYaw,
            if (activeRailBodyDirection == 0) 1 else activeRailBodyDirection
        )
        if (abs(this.speed) <= 1.0E-6f) {
            setDeltaMovement(Vec3.ZERO)
        } else {
            setDeltaMovement(getX() - startX, getY() - startY, getZ() - startZ)
        }
        activeRailIndex = Mth.clamp(Math.round(activeRailPosition).toInt(), 0, activeRailSplit)
        activeRailDirection =
            if (controllerDirection == 0) activeRailDirection else controllerDirection * (if (activeRailBodyDirection == 0) 1 else activeRailBodyDirection)
        this.railProgress = activeRailIndex / activeRailSplit.toFloat()
        return true
    }

    private fun stopFormationAtRailBoundary() {
        forEachFormationTrain(Consumer { train ->
            train ?: return@Consumer
            train.speed = 0.0f
            train.notch = 0
            train.setDeltaMovement(Vec3.ZERO)
            train.hurtMarked = true
        })
    }

    /**
     * 本家RTM EntityBogie.updateBogiePos 準拠: refSample からの直線距離が targetDist に最も近い
     * レール点を baseAnchor の map 上で探して返す。2台車の弦長を台車間隔に保ち、カーブでも
     * 台車が実レール点(±bogiePos)へ乗る(アーク配置による弦-弧オーバーシュートを解消)。
     */
    /**
     * 本家RTM準拠の後続台車の決め方。先頭台車(leadingSample)から台車間隔(span)の弦距離になる点を、
     * まず「前回の後続台車位置(prevTrailing)の近傍」で探す(連続性優先=レールの膨らみ等で遠点へ飛ばない)。
     * 近傍に妥当な点が無い(レール端を越える等)場合だけ、先頭からアーク逆算した点に補正してフォールバックする。
     */
    private fun deriveTrailingAnchor(
        prevTrailing: RailAnchor?,
        leadingSample: RailSample?,
        span: Double,
        leading: RailAnchor?
    ): RailAnchor? {
        val cont = refineAnchorByStraightDistance(prevTrailing, leadingSample, span)
        if (chordWithinTolerance(cont, leadingSample, span)) {
            return cont
        }
        val arc = advanceAnchorAlongPath(leading, -abs(span))
        if (isRailAnchorUsable(arc)) {
            val refined = refineAnchorByStraightDistance(arc, leadingSample, span)
            if (isRailAnchorUsable(refined)) {
                return refined
            }
        }
        return cont
    }

    /** baseAnchor の位置が leadingSample から span 弦距離(±1m)になっているか。  */
    private fun chordWithinTolerance(a: RailAnchor?, leadingSample: RailSample?, span: Double): Boolean {
        if (!isRailAnchorUsable(a) || leadingSample == null) {
            return false
        }
        val s = sampleBogieRail(a!!.map, a.split, a.index)
        if (s == null) {
            return false
        }
        val dx = s.x - leadingSample.x
        val dz = s.z - leadingSample.z
        val d = sqrt(dx * dx + dz * dz)
        return abs(d - abs(span)) <= 1.0
    }

    private fun refineAnchorByStraightDistance(
        baseAnchor: RailAnchor?,
        refSample: RailSample?,
        targetDist: Double
    ): RailAnchor? {
        if (!isRailAnchorUsable(baseAnchor) || refSample == null) {
            return baseAnchor
        }
        val map = baseAnchor!!.map
        val split = baseAnchor.split
        // 探索窓は弦-弧差(数十cm〜数m)を吸収できる小さめに固定する。台車間隔(targetDist)でスケール
        // させると窓が非常に広くなり、端点を大きくはみ出す不正レールで遠い張り出し点を拾ってしまい、
        // 本体中心が1tickで瞬間移動→ワープ棄却で列車が停止する(ユーザー報告: 分岐で止まる)。
        val searchInc = max(8, (3.0 * BOGIE_SPLITS_PER_METER.toDouble()).toInt())
        val center = Mth.clamp(Math.round(baseAnchor.index).toInt(), 0, split)
        val min = max(0, center - searchInc)
        val max = min(split, center + searchInc)
        val targetSq = targetDist * targetDist
        var bestIndex = -1
        var bestDiff = Double.MAX_VALUE
        for (i in min..max) {
            val p = map.getRailPos(split, i) // p[1]=x, p[0]=z
            val ddx = p[1] - refSample.x
            val ddz = p[0] - refSample.z
            val dsq = ddx * ddx + ddz * ddz
            val diff = abs(dsq - targetSq)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = i
            }
        }
        if (bestIndex < 0) {
            return baseAnchor
        }
        return RailAnchor(map, split, bestIndex.toDouble(), baseAnchor.travelDirection)
    }

    private fun advanceBogiePairAlongPath(distanceMeters: Double, controllerDirection: Int): Boolean {
        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor) || controllerDirection == 0) {
            return false
        }
        val bogieZ = this.bogieRailOffsets
        val span = bogieZ[1] - bogieZ[0]
        if (abs(span) < 1.0E-4) {
            return false
        }
        val bodyDirection = if (activeRailBodyDirection == 0) 1 else activeRailBodyDirection
        // Each anchor stores the direction it travels on ITS OWN rail:
        //   frontRailAnchor.travelDirection() = forward direction on front's rail
        //   rearRailAnchor.travelDirection()  = backward direction on rear's rail
        //                                       (placed by going backward from front)
        // When bogies span a rail joint, the two rails may have opposite orientations,
        // so a single global pathDirection from the front anchor is wrong for the rear.
        // Use per-anchor directions to handle cross-rail cases correctly.
        val frontPathDirection = if (frontRailAnchor!!.travelDirection != 0)
            frontRailAnchor!!.travelDirection
        else
            bodyDirection
        val rearPathDirection = if (rearRailAnchor!!.travelDirection != 0)
            rearRailAnchor!!.travelDirection
        else
            -bodyDirection
        if (controllerDirection > 0) {
            val movedFront = advanceBogieAnchor(frontRailAnchor, distanceMeters, frontPathDirection)
            if (!isRailAnchorUsable(movedFront)) {
                RealTrainModRenewed.LOGGER.debug("[RTM-DBG] PAIR-FAIL movedFront unusable")
                return false
            }
            val movedFrontSample = sampleBogieRail(movedFront!!.map, movedFront.split, movedFront.index)
            // 本家RTM式: 後台車は「前回位置の近傍」で前台車から台車間隔の弦距離になる点を探す(連続性優先・
            // 遠点回避)。前回位置近傍に無い(レール端越え等)場合だけアーク逆算にフォールバックする。
            val bestRear = deriveTrailingAnchor(rearRailAnchor, movedFrontSample, abs(span), movedFront)
            if (!isRailAnchorUsable(bestRear)) {
                RealTrainModRenewed.LOGGER.debug("[RTM-DBG] PAIR-FAIL bestRear")
                return false
            }
            frontRailAnchor = movedFront
            rearRailAnchor = bestRear
            syncActiveRailStateFromAnchors(bodyDirection)
            updateStoredBogieState()
            return true
        }

        val movedRear = advanceBogieAnchor(rearRailAnchor, distanceMeters, rearPathDirection)
        if (!isRailAnchorUsable(movedRear)) {
            RealTrainModRenewed.LOGGER.debug("[RTM-DBG] PAIR-FAIL movedRear unusable")
            return false
        }
        val movedRearSample = sampleBogieRail(movedRear!!.map, movedRear.split, movedRear.index)
        // 本家RTM式: 前台車は「前回位置の近傍」で後台車から台車間隔の弦距離になる点を探す(連続性優先)。
        val bestFront = deriveTrailingAnchor(frontRailAnchor, movedRearSample, abs(span), movedRear)
        if (!isRailAnchorUsable(bestFront)) {
            RealTrainModRenewed.LOGGER.debug("[RTM-DBG] PAIR-FAIL bestFront")
            return false
        }
        rearRailAnchor = movedRear
        frontRailAnchor = bestFront
        syncActiveRailStateFromAnchors(bodyDirection)
        updateStoredBogieState()
        return true
    }

    private fun restabilizeBogieAnchors(controllerDirection: Int): Boolean {
        var center = this.activeRailContext
        if (center == null) {
            center = findRailContextNearAny(position(), null)
        }
        if (center == null) {
            return false
        }
        val bodyDirection = if (activeRailBodyDirection == 0) getBodyDirectionOnRail(
            center.map,
            center.split,
            center.nearestIndex,
            getYRot()
        ) else activeRailBodyDirection
        val pair = createAnchorPairFromCenter(center.map, center.split, center.nearestIndex.toDouble(), bodyDirection)
        if (!isRailAnchorUsable(pair.front) || !isRailAnchorUsable(pair.rear)) {
            return false
        }
        frontRailAnchor = pair.front
        rearRailAnchor = pair.rear
        updateStoredBogieState()
        activeRailMap = center.map
        activeRailSplit = center.split
        activeRailPosition = center.nearestIndex.toDouble()
        activeRailIndex = center.nearestIndex
        activeRailBodyDirection = bodyDirection
        return true
    }

    private fun createAnchorPairFromCenter(
        map: RailMap,
        split: Int,
        centerIndex: Double,
        bodyDirection: Int
    ): RailAnchorPair {
        val bogieZ = this.bogieRailOffsets
        val dir = if (bodyDirection == 0) 1 else bodyDirection
        val centerAnchor = RailAnchor(map, split, Mth.clamp(centerIndex, 0.0, split.toDouble()), dir)
        val frontAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[1])
        var rearAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[0])
        if (!isRailAnchorUsable(frontAnchor) || !isRailAnchorUsable(rearAnchor)) {
            val requestedCenter = sampleRail(map, split, centerIndex)
            return findBestAnchorPairForCenter(map, split, centerIndex, requestedCenter, dir)
        }
        // 後台車を前台車からの直線距離=台車間隔に補正(本家RTM準拠・弦-弧ズレ解消)。
        val frontSample = sampleBogieRail(frontAnchor!!.map, frontAnchor.split, frontAnchor.index)
        rearAnchor = refineAnchorByStraightDistance(rearAnchor!!, frontSample, abs(bogieZ[1] - bogieZ[0]))
        val rearSample = sampleBogieRail(rearAnchor!!.map, rearAnchor.split, rearAnchor.index)
        return RailAnchorPair(
            frontAnchor,
            rearAnchor,
            frontSample,
            rearSample,
            0.0
        )
    }

    private fun syncActiveRailStateFromAnchors(fallbackBodyDirection: Int) {
        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)) {
            return
        }

        val bogieZ = this.bogieRailOffsets
        val bodyDirection = if (fallbackBodyDirection == 0) 1 else fallbackBodyDirection
        // Go backward from front bogie by bogieZ[1] to reach center (bogieZ[1] > 0, so negate).
        // Go forward from rear bogie by |bogieZ[0]| to reach center; bogieZ[0] < 0 so use it
        // directly — the negative offset flips rearAnchor's backward travelDirection to forward.
        val centerFromFront = advanceAnchorAlongPath(frontRailAnchor, -bogieZ[1])
        val centerFromRear = advanceAnchorAlongPath(rearRailAnchor, bogieZ[0])
        val centerAnchor = chooseCenterAnchor(centerFromFront, centerFromRear, bodyDirection)
        if (!isRailAnchorUsable(centerAnchor)) {
            return
        }
        activeRailMap = centerAnchor!!.map
        activeRailSplit = centerAnchor.split
        activeRailPosition = Mth.clamp(centerAnchor.index, 0.0, activeRailSplit.toDouble())
        activeRailIndex = Mth.clamp(Math.round(activeRailPosition).toInt(), 0, activeRailSplit)
        activeRailBodyDirection = chooseStableBodyDirection(
            activeRailMap,
            activeRailSplit,
            activeRailIndex,
            getYRot(),
            bodyDirection
        )
    }

    private fun chooseCenterAnchor(
        centerFromFront: RailAnchor?,
        centerFromRear: RailAnchor?,
        bodyDirection: Int
    ): RailAnchor? {
        if (isRailAnchorUsable(centerFromFront) && isRailAnchorUsable(centerFromRear)) {
            if (centerFromFront!!.map === centerFromRear!!.map && centerFromFront.split == centerFromRear.split) {
                return RailAnchor(
                    centerFromFront.map,
                    centerFromFront.split,
                    (centerFromFront.index + centerFromRear.index) * 0.5,
                    bodyDirection
                )
            }
            val frontSample = sampleRail(centerFromFront.map, centerFromFront.split, centerFromFront.index)
            val rearSample = sampleRail(centerFromRear.map, centerFromRear.split, centerFromRear.index)
            val midpoint = position()
            val frontDist = Vec3(frontSample.x, frontSample.y, frontSample.z).distanceToSqr(midpoint)
            val rearDist = Vec3(rearSample.x, rearSample.y, rearSample.z).distanceToSqr(midpoint)
            return if (frontDist <= rearDist) centerFromFront else centerFromRear
        }
        if (isRailAnchorUsable(centerFromFront)) {
            return centerFromFront
        }
        return centerFromRear
    }

    private fun applyPoseFromBogieSamples(
        front: RailSample,
        rear: RailSample,
        fallbackYaw: Float,
        fallbackPitch: Float,
        move: Boolean
    ): Float {
        val dx = front.x - rear.x
        val dy = front.y - rear.y
        val dz = front.z - rear.z
        val horizontal = sqrt(dx * dx + dz * dz)
        var yaw = if (horizontal > 1.0E-4) Math.toDegrees(atan2(dx, dz)).toFloat() else
            fallbackYaw
        // 前後台車の微小なY差(分岐マップの縦ベジェのわずかな膨らみ等)で小さなピッチが付き、本体が
        // 跳ねて見える。Y差が小さいうち(0.15ブロック未満)はピッチに反映しない(デッドゾーン)。実際の
        // 勾配はこれより大きなY差になるので従来通り正確に追従する。
        val pitchDy = if (abs(dy) < 0.15) 0.0 else dy
        var pitch = if (horizontal > 1.0E-4) Math.toDegrees(atan2(pitchDy, horizontal)).toFloat() else
            fallbackPitch
        // 分岐境界などで前後台車のY差が急変したとき、本体ピッチが瞬間的に振れて跳ねるのを抑える。
        // 1tickのピッチ変化量を制限し急なジョルトだけ平滑化する(通常走行・カーブ・坂は無影響)。
        if (move) {
            val prevPitch = getXRot()
            val maxPitchDelta = 6.0f
            pitch = Mth.clamp(pitch, prevPitch - maxPitchDelta, prevPitch + maxPitchDelta)
        }
        yaw = keepNearestYaw(yaw, getYRot())
        val centerSample = resolveBodyCenterSample(front, rear)
        val center = Vec3(centerSample.x, centerSample.y + TRAIN_BODY_HEIGHT_OFFSET, centerSample.z)
        if (move) {
            setPos(center.x, center.y, center.z)
            setRot(yaw, pitch)
        } else {
            setPos(center.x, center.y, center.z)
        }
        setYRot(yaw)
        setXRot(pitch)
        setYHeadRot(yaw)
        setYBodyRot(yaw)
        return yaw
    }

    private fun computeBodyRoll(front: RailSample?, rear: RailSample?): Float {
        if (isRailAnchorUsable(frontRailAnchor) && isRailAnchorUsable(rearRailAnchor)) {
            val frontRoll = sampleRailRoll(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
            val rearRoll = sampleRailRoll(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
            return (frontRoll + rearRoll) * 0.5f
        }
        if (activeRailMap != null && activeRailSplit > 0) {
            return sampleRailRoll(activeRailMap, activeRailSplit, activeRailPosition)
        }
        return 0.0f
    }

    private fun applyBodyCenterOffset(centerSample: RailSample, yaw: Float, roll: Float): Vec3 {
        val x = centerSample.x
        val y = centerSample.y
        val z = centerSample.z
        val rollRad = Math.toRadians(-roll.toDouble())
        val offsetX: Double = sin(rollRad) * RTM_VEHICLE_Y_OFFSET
        val offsetY: Double = cos(rollRad) * RTM_VEHICLE_Y_OFFSET - RTM_VEHICLE_Y_OFFSET
        val yawRad = Math.toRadians(yaw.toDouble())
        val rotatedX = sin(yawRad) * offsetX
        val rotatedZ = cos(yawRad) * offsetX
        return Vec3(
            x + rotatedX,
            y + offsetY,
            z + rotatedZ
        )
    }

    private fun resolveBodyCenterSample(front: RailSample, rear: RailSample): RailSample {
        val bogieZ = this.bogieRailOffsets
        val frontZ = bogieZ[1]
        val rearZ = bogieZ[0]
        val span = abs(frontZ - rearZ)
        if (span > 1.0E-4) {
            val frontWeight = abs(rearZ) / span
            val rearWeight = abs(frontZ) / span
            return RailSample(
                front.x * frontWeight + rear.x * rearWeight,
                (front.y + rear.y) * 0.5,
                front.z * frontWeight + rear.z * rearWeight
            )
        }
        return RailSample(
            (front.x + rear.x) * 0.5,
            (front.y + rear.y) * 0.5,
            (front.z + rear.z) * 0.5
        )
    }

    private fun getRailYawForBody(map: RailMap?, split: Int, index: Double, bodyDirection: Int, baseYaw: Float): Float {
        if (map == null || split <= 0) {
            return baseYaw
        }
        var yaw = sampleRailYaw(map, split, index)
        if (bodyDirection < 0) {
            yaw = Mth.wrapDegrees(yaw + 180.0f)
        }
        return keepNearestYaw(yaw, baseYaw)
    }

    private fun getRailPitchForBody(map: RailMap?, split: Int, index: Double, bodyDirection: Int): Float {
        if (map == null || split <= 0) {
            return getXRot()
        }
        val pitch = sampleRailPitch(map, split, index)
        return if (bodyDirection < 0) -pitch else pitch
    }

    private fun findBestAnchorPairForCenter(
        map: RailMap,
        split: Int,
        centerIndex: Double,
        requestedCenter: RailSample,
        bodyDirection: Int
    ): RailAnchorPair {
        val bogieZ = this.bogieRailOffsets
        val trackLen = max(0.001, map.getLength())
        val sampleStep = trackLen / split
        val frontOffset = bogieZ[1] / sampleStep
        val rearOffset = bogieZ[0] / sampleStep
        val minCenter = max(0.0, -min(frontOffset, rearOffset))
        val maxCenter = min(split.toDouble(), split - max(frontOffset, rearOffset))
        val hasFullBogieRange = minCenter <= maxCenter
        val spanSamples = abs(bogieZ[1] - bogieZ[0]) / sampleStep
        val searchRadius = Mth.clamp(ceil(spanSamples * 0.35).toInt(), 4, 48)
        var best: RailAnchorPair? = null

        for (offset in -searchRadius..searchRadius) {
            val candidateCenter = if (hasFullBogieRange)
                Mth.clamp(centerIndex + offset, minCenter, maxCenter)
            else
                Mth.clamp(centerIndex + offset, 0.0, split.toDouble())
            val centerAnchor = RailAnchor(map, split, candidateCenter, bodyDirection)
            val frontAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[1])
            var rearAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[0])
            if (!isRailAnchorUsable(frontAnchor) || !isRailAnchorUsable(rearAnchor)) {
                continue
            }
            val front = sampleBogieRail(frontAnchor!!.map, frontAnchor.split, frontAnchor.index)
            // 後台車を前台車からの直線距離=台車間隔に補正(本家RTM準拠・弦-弧ズレ解消)。
            rearAnchor = refineAnchorByStraightDistance(rearAnchor!!, front, abs(bogieZ[1] - bogieZ[0]))
            val rear = sampleBogieRail(rearAnchor!!.map, rearAnchor.split, rearAnchor.index)
            val centerX = (front.x + rear.x) * 0.5
            val centerY = (front.y + rear.y) * 0.5
            val centerZ = (front.z + rear.z) * 0.5
            val dx = centerX - requestedCenter.x
            val dz = centerZ - requestedCenter.z
            val distanceSq = dx * dx + dz * dz
            if (best == null || distanceSq < best.distanceSq) {
                best = RailAnchorPair(
                    frontAnchor,
                    rearAnchor,
                    front,
                    rear,
                    distanceSq
                )
            }
        }

        if (best != null) {
            return best
        }
        val fallbackCenter = if (hasFullBogieRange) Mth.clamp(centerIndex, minCenter, maxCenter) else Mth.clamp(
            centerIndex,
            0.0,
            split.toDouble()
        )
        val fallbackAnchor = RailAnchor(map, split, fallbackCenter, bodyDirection)
        val frontAnchor = advanceAnchorAlongPath(fallbackAnchor, bogieZ[1])
        val rearAnchor = advanceAnchorAlongPath(fallbackAnchor, bogieZ[0])
        return RailAnchorPair(
            frontAnchor,
            rearAnchor,
            sampleBogieRail(frontAnchor!!.map, frontAnchor.split, frontAnchor.index),
            sampleBogieRail(rearAnchor!!.map, rearAnchor.split, rearAnchor.index),
            0.0
        )
    }

    private fun normalizeAnchorOrientation(anchor: RailAnchor?, bodyDirection: Int): RailAnchor? {
        if (anchor == null || anchor.map == null || anchor.split <= 0) {
            return anchor
        }
        val normalized = if (bodyDirection == 0) 1 else bodyDirection
        return RailAnchor(anchor.map, anchor.split, anchor.index, normalized)
    }

    private fun advanceAnchorAlongPath(anchor: RailAnchor?, offsetMeters: Double): RailAnchor? {
        if (anchor == null || anchor.map == null || anchor.split <= 0) {
            return null
        }
        var map = anchor.map
        var split = anchor.split
        var index = Mth.clamp(anchor.index, 0.0, split.toDouble())
        var travelDirection = if (anchor.travelDirection == 0) 1 else anchor.travelDirection
        var remaining = abs(offsetMeters)
        if (offsetMeters < 0.0) {
            travelDirection *= -1
        }

        var guard = 0
        while (remaining > 1.0E-5 && guard++ < 8) {
            val step = max(0.001, map.getLength()) / split
            val samplesToBoundary = if (travelDirection > 0) split - index else index
            val metersToBoundary = samplesToBoundary * step
            if (remaining <= metersToBoundary) {
                index += travelDirection * (remaining / step)
                remaining = 0.0
                break
            }

            remaining -= max(0.0, metersToBoundary)
            val boundaryIndex = if (travelDirection > 0) split else 0
            val next = findConnectedRailContext(map, split, boundaryIndex, travelDirection)
            if (next == null) {
                val snapped = findRailContextBeyondBoundary(map, split, boundaryIndex, travelDirection)
                if (snapped == null) {
                    val bSample = sampleRail(map, split, boundaryIndex)
                    RealTrainModRenewed.LOGGER.debug(
                        "[RTM-DBG] AAP no-conn: boundary={} dir={} rem={} pos=({},{},{})",
                        boundaryIndex, travelDirection, remaining.toFloat(),
                        bSample.x.toFloat(), bSample.y.toFloat(), bSample.z.toFloat()
                    )
                    index = boundaryIndex.toDouble()
                    break
                }
                map = snapped.map
                split = snapped.split
                index = snapped.nearestIndex.toDouble()
                travelDirection = getBodyDirectionOnRail(
                    map,
                    split,
                    Math.round(index).toInt(),
                    getYRot()
                ) * (if (travelDirection > 0) 1 else -1)
                continue
            }
            map = next.map
            split = next.split
            index = next.index
            travelDirection = if (next.travelDirection == 0) travelDirection else next.travelDirection
        }

        return RailAnchor(map, split, Mth.clamp(index, 0.0, split.toDouble()), travelDirection)
    }

    private fun findBestFollowerAnchor(
        predictedAnchor: RailAnchor?,
        leadingSample: RailSample?,
        targetDistanceSq: Double,
        bodyDirection: Int,
        referenceYaw: Float,
        distanceMeters: Double,
        followerSide: Int
    ): RailAnchor? {
        if (!isRailAnchorUsable(predictedAnchor) || leadingSample == null || predictedAnchor!!.split <= 0) {
            return null
        }

        val map = predictedAnchor.map
        val split = predictedAnchor.split
        val predictedIndex = Mth.clamp(predictedAnchor.index, 0.0, split.toDouble())
        var searchCenter = predictedIndex
        if (followerSide >= 0 && followerSide < 2 && bogiePrevMaps[followerSide] === map && bogiePrevSplits[followerSide] == split && bogiePrevSampleIndex[followerSide] >= 0) {
            searchCenter = Mth.clamp(bogiePrevSampleIndex[followerSide].toDouble(), 0.0, split.toDouble())
        }
        val targetDistanceMeters = sqrt(max(targetDistanceSq, 0.0))
        var indexInc = max(
            48,
            (max(abs(distanceMeters) + 0.25, targetDistanceMeters * 0.50) * BOGIE_SPLITS_PER_METER.toDouble()).toInt()
        )
        indexInc = min(indexInc, max(16, split))
        val indexMin = max(floor(searchCenter).toInt() - indexInc, 0)
        val indexMax = min(ceil(searchCenter).toInt() + indexInc, split)
        var best: RailAnchor? = null
        var bestScore = Double.MAX_VALUE

        for (i in indexMin..indexMax) {
            val resolved = resolveRailSample(map, split, i.toDouble(), bodyDirection)
            val sample = resolved.sample
            val dx = leadingSample.x - sample.x
            val dy = leadingSample.y - sample.y
            val dz = leadingSample.z - sample.z
            val distSq = dx * dx + dy * dy + dz * dz
            val score =
                (abs(distSq - targetDistanceSq) + abs(i - searchCenter) * 0.001 + (if (resolved.map === map) 0.0 else 0.08))
            if (best == null || score < bestScore) {
                bestScore = score
                best = TrainEntity.RailAnchor(resolved.map!!, resolved.split, resolved.index, resolved.bodyDirection)
            }
        }

        return best
    }

    private fun distanceToRailMeters(a: RailSample?, b: RailAnchor?): Double {
        if (a == null || b == null || !isRailAnchorUsable(b)) {
            return 0.0
        }
        val sample = sampleRail(b.map, b.split, b.index)
        val dx = a.x - sample.x
        val dy = a.y - sample.y
        val dz = a.z - sample.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun getMovementSplitForMap(map: RailMap?): Int {
        if (map == null) {
            return 0
        }
        var split = ceil(map.getLength() * BOGIE_SPLITS_PER_METER.toDouble()).toInt()
        split = max(split, 2)
        return min(split, 32768)
    }

    private fun ensureBogieAnchors(): Boolean {
        val bogieZ = this.bogieRailOffsets
        if (isRailAnchorUsable(frontRailAnchor) && isRailAnchorUsable(rearRailAnchor)) {
            return true
        }

        var center = this.activeRailContext
        if (center == null) {
            val front = findRailContextNearAny(localToWorld(Vec3(0.0, 0.0, bogieZ[1])), null)
            val rear = findRailContextNearAny(localToWorld(Vec3(0.0, 0.0, bogieZ[0])), null)
            if (front == null && rear == null) {
                return false
            }
            val seed: RailFollowContext = (if (front != null) front else rear)!!
            activeRailMap = seed.map
            activeRailSplit = getMovementSplitForMap(seed.map)
            activeRailIndex = seed.nearestIndex
            activeRailPosition = seed.nearestIndex.toDouble()
            center = seed
        }
        val bodyDirection = getBodyDirectionOnRail(center.map, center.split, center.nearestIndex, getYRot())
        val pair = createAnchorPairFromCenter(center.map, center.split, center.nearestIndex.toDouble(), bodyDirection)
        if (!isRailAnchorUsable(pair.front) || !isRailAnchorUsable(pair.rear)) {
            return false
        }
        frontRailAnchor = pair.front
        rearRailAnchor = pair.rear
        updateStoredBogieState()
        activeRailMap = center.map
        activeRailSplit = getMovementSplitForMap(center.map)
        activeRailIndex = center.nearestIndex
        activeRailPosition = center.nearestIndex.toDouble()
        activeRailBodyDirection = bodyDirection
        return true
    }

    private fun isRailAnchorUsable(anchor: RailAnchor?): Boolean {
        return anchor != null && anchor.map != null && anchor.split > 0
    }

    private fun advanceBogieAnchor(anchor: RailAnchor?, distanceMeters: Double, pathDirection: Int): RailAnchor? {
        if (anchor == null || anchor.map == null || anchor.split <= 0) {
            return null
        }
        if (distanceMeters <= 0.0) {
            return anchor
        }

        var map = anchor.map
        var split = anchor.split
        var index = Mth.clamp(anchor.index, 0.0, split.toDouble())
        var travelDirection = if (pathDirection != 0)
            pathDirection
        else
            (if (anchor.travelDirection == 0) 1 else anchor.travelDirection)
        var remaining = distanceMeters
        var guard = 0

        while (remaining > 1.0E-5 && guard++ < 8) {
            val step = max(0.001, map.getLength()) / split
            val samplesToBoundary = if (travelDirection > 0) split - index else index
            val metersToBoundary = samplesToBoundary * step
            if (remaining <= metersToBoundary) {
                index += travelDirection * (remaining / step)
                remaining = 0.0
                break
            }

            remaining -= max(0.0, metersToBoundary)
            val boundaryIndex = if (travelDirection > 0) split else 0
            val next = findConnectedRailContext(map, split, boundaryIndex, travelDirection)
            if (next == null) {
                val snapped = findRailContextBeyondBoundary(map, split, boundaryIndex, travelDirection)
                if (snapped == null) {
                    RealTrainModRenewed.LOGGER.debug(
                        "[RTM-DBG] ABA no-conn: boundary={} dir={} rem={}",
                        boundaryIndex, travelDirection, remaining.toFloat()
                    )
                    return null
                }
                map = snapped.map
                split = snapped.split
                index = snapped.nearestIndex.toDouble()
                travelDirection = if (pathDirection != 0) pathDirection else travelDirection
                continue
            }

            map = next.map
            split = next.split
            index = next.index
            travelDirection = next.travelDirection
        }

        if (index < -0.001 || index > split + 0.001) {
            return null
        }
        return RailAnchor(map, split, Mth.clamp(index, 0.0, split.toDouble()), travelDirection)
    }

    private fun keepNearestYaw(targetYaw: Float, currentYaw: Float): Float {
        var targetYaw = targetYaw
        val diff = Mth.wrapDegrees(targetYaw - currentYaw)
        if (abs(diff) > 120.0f) {
            targetYaw = Mth.wrapDegrees(targetYaw + 180.0f)
        }
        return targetYaw
    }

    private fun resolveRailSample(map: RailMap, split: Int, index: Double, bodyDirection: Int): RailResolvedSample {
        if (index >= 0.0 && index <= split) {
            return RailResolvedSample(sampleRail(map, split, index), map, split, index, bodyDirection)
        }

        val direction = if (index > split) 1 else -1
        val boundaryIndex = if (direction > 0) split else 0
        val next = findConnectedRailContext(map, split, boundaryIndex, direction)
        if (next == null) {
            val clamped = Mth.clamp(index, 0.0, split.toDouble())
            return RailResolvedSample(sampleRail(map, split, clamped), map, split, clamped, bodyDirection)
        }

        val nextBodyDirection =
            chooseStableBodyDirection(next.map, next.split, Math.round(next.index).toInt(), getYRot(), bodyDirection)
        val overflow = abs(if (index < 0.0) index else index - split)
        var nextIndex = next.index + next.travelDirection * overflow
        nextIndex = Mth.clamp(nextIndex, 0.0, next.split.toDouble())
        return RailResolvedSample(
            sampleRail(next.map, next.split, nextIndex),
            next.map,
            next.split,
            nextIndex,
            nextBodyDirection
        )
    }

    private fun findConnectedRailContext(
        currentMap: RailMap?,
        currentSplit: Int,
        boundaryIndex: Int,
        travelDirection: Int
    ): RailConnection? {
        if (currentMap == null || currentSplit <= 0) {
            return null
        }

        val boundary = sampleRail(currentMap, currentSplit, boundaryIndex)
        val boundaryPos = Vec3(boundary.x, boundary.y, boundary.z)
        var outgoingYaw = currentMap.getRailYaw(currentSplit, Mth.clamp(boundaryIndex, 0, currentSplit))
        if (travelDirection < 0) {
            outgoingYaw = Mth.wrapDegrees(outgoingYaw + 180.0f)
        }

        val endpoint =
            if (boundaryIndex <= 0) currentMap.startRP else currentMap.endRP

        val center = BlockPos.containing(boundary.x, boundary.y, boundary.z)
        val radius = 24
        // 1st pass: アクティブ分岐のみ(トラフ側でスイッチ通り正しい分岐を選ぶ)。
        // 2nd pass: 見つからなければ全分岐へフォールバック。これにより「非アクティブな分岐の
        // 終端から突入」「走行中にスイッチが切替わって現在の分岐が非アクティブ化」した場合でも
        // 物理的に存在するレールへ接続でき、弾かれて前に進めなくなるのを防ぐ。
        var best: RailConnection? = null
        var pass = 0
        while (pass < 2 && best == null) {
            railLookupIncludeAllSegments = pass == 1
            for (dx in -radius..radius) {
                for (dy in -4..4) {
                    for (dz in -radius..radius) {
                        val maps = getConnectionCandidateMapsAt(center.offset(dx, dy, dz), currentMap)
                        if (maps.size == 0) continue

                        for (map in maps) {
                            if (map == null || map === currentMap || map.equals(currentMap)) {
                                continue
                            }
                            val split = getMovementSplitForMap(map)
                            best = betterConnection(
                                best,
                                evaluateRailEndpointConnection(map, split, 0, endpoint, boundaryPos, outgoingYaw)
                            )
                            best = betterConnection(
                                best,
                                evaluateRailEndpointConnection(map, split, split, endpoint, boundaryPos, outgoingYaw)
                            )
                        }
                    }
                }
            }
            pass++
        }
        railLookupIncludeAllSegments = false
        if (best == null) {
            RealTrainModRenewed.LOGGER.debug(
                "[RTM-DBG] findConn FAIL: bpos=({},{},{}) outYaw={}",
                boundary.x.toFloat(), boundary.y.toFloat(), boundary.z.toFloat(), outgoingYaw
            )
        }
        return best
    }

    private fun evaluateRailEndpointConnection(
        map: RailMap,
        split: Int,
        endpointIndex: Int,
        currentEndpoint: RailPosition?,
        boundaryPos: Vec3,
        outgoingYaw: Float
    ): RailConnection? {
        val candidateEndpoint =
            if (endpointIndex <= 0) map.startRP else map.endRP
        val sameEndpoint = candidateEndpoint != null && sameRailEndpoint(candidateEndpoint, currentEndpoint)
        val sample = sampleRail(map, split, endpointIndex)
        val distSq = Vec3(sample.x, sample.y, sample.z).distanceToSqr(boundaryPos)
        if (!sameEndpoint && distSq > RAIL_CONNECTION_MAX_DISTANCE_SQ) {
            if (distSq < 4.0) RealTrainModRenewed.LOGGER.debug(
                "[RTM-DBG] eval reject dist: distSq={} pos=({},{},{}) bpos=({},{},{})",
                distSq.toFloat(), sample.x.toFloat(), sample.y.toFloat(), sample.z.toFloat(),
                boundaryPos.x.toFloat(), boundaryPos.y.toFloat(), boundaryPos.z.toFloat()
            )
            return null
        }

        val nextTravelDirection = if (endpointIndex <= 0) 1 else -1
        var candidateYaw = map.getRailYaw(split, endpointIndex)
        if (nextTravelDirection < 0) {
            candidateYaw = Mth.wrapDegrees(candidateYaw + 180.0f)
        }
        val yawDiff = abs(Mth.wrapDegrees(outgoingYaw - candidateYaw))
        // 端点共有(sameEndpoint)でも「逆走(yaw差>90°)」の接続は拒否する。分岐器のトランクでは
        // 兄弟分岐(直進↔カーブ)が同じトランク端点を共有するため、出ていく方向と約180°逆向きの
        // 兄弟分岐へ前台車がU字に乗り移ってしまい、後台車が別分岐に残って車体が裂け弾かれていた。
        // 正しい継続は進行方向が揃う(yaw差小)。逆走接続はスイッチ内への逆流なので常に不可とする。
        val reversal = yawDiff > 90.0f
        if ((!sameEndpoint && yawDiff > RAIL_CONNECTION_MAX_YAW_DIFF) || reversal) {
            if (distSq < 1.0) RealTrainModRenewed.LOGGER.debug(
                "[RTM-DBG] eval reject yaw: yawDiff={} outYaw={} candYaw={} distSq={} same={}",
                yawDiff, outgoingYaw, candidateYaw, distSq.toFloat(), sameEndpoint
            )
            return null
        }
        val score = distSq + yawDiff * 0.02 + (if (sameEndpoint) -200.0 else 0.0)
        return RailConnection(map, split, endpointIndex.toDouble(), nextTravelDirection, score)
    }

    private fun betterConnection(current: RailConnection?, candidate: RailConnection?): RailConnection? {
        if (candidate == null) {
            return current
        }
        if (current == null || candidate.score < current.score) {
            return candidate
        }
        return current
    }

    /** true の間はスイッチの全分岐をレール探索候補にする(アクティブ分岐で見つからない時のフォールバック)。  */
    private var railLookupIncludeAllSegments = false

    private fun switchCandidateMaps(core: LargeRailCoreBlockEntity, currentMap: RailMap?): Array<RailMap?> {
        if (railLookupIncludeAllSegments || shouldInspectAllSegments(core, currentMap)) {
            return core.allRailMaps.map { it as RailMap? }.toTypedArray()
        }
        return core.activeRailMaps.map { it as RailMap? }.toTypedArray()
    }

    private fun getConnectionCandidateMapsAt(pos: BlockPos, currentMap: RailMap?): Array<RailMap?> {
        val blockEntity = level().getBlockEntity(pos)
        if (blockEntity is LargeRailCoreBlockEntity && blockEntity.isLoaded) {
            return switchCandidateMaps(blockEntity, currentMap)
        }
        if (blockEntity is RailCollisionBlockEntity) {
            val corePos = blockEntity.getCorePos()
            val core = if (corePos == null) null else level().getBlockEntity(corePos)
            if (core is LargeRailCoreBlockEntity && core.isLoaded) {
                return switchCandidateMaps(core, currentMap)
            }
        }
        return arrayOfNulls<RailMap>(0)
    }

    private fun shouldInspectAllSegments(core: LargeRailCoreBlockEntity?, currentMap: RailMap?): Boolean {
        if (core == null || currentMap == null) {
            return false
        }
        for (map in core.allRailMaps) {
            if (sameRailShape(map, currentMap)) {
                return true
            }
        }
        return false
    }

    private fun sameRailShape(a: RailMap?, b: RailMap?): Boolean {
        if (a == null || b == null) {
            return false
        }
        return sameRailEndpoint(a.startRP, b.startRP) && sameRailEndpoint(a.endRP, b.endRP)
                || sameRailEndpoint(a.startRP, b.endRP) && sameRailEndpoint(a.endRP, b.startRP)
    }

    private fun sameRailEndpoint(a: RailPosition?, b: RailPosition?): Boolean {
        if (a == null || b == null) {
            return false
        }
        return a.blockX == b.blockX && a.blockY == b.blockY && a.blockZ == b.blockZ
    }

    private fun transitionToConnectedRail(currentMap: RailMap, currentSplit: Int, direction: Int): Boolean {
        val boundaryIndex = if (direction > 0) currentSplit else 0
        val boundary = sampleRail(currentMap, currentSplit, boundaryIndex)
        val next = findRailContextNear(Vec3(boundary.x, boundary.y, boundary.z), currentMap)
        if (next == null) {
            return false
        }

        activeRailMap = next.map
        activeRailSplit = next.split
        activeRailIndex = next.nearestIndex
        activeRailPosition = activeRailIndex.toDouble()
        activeRailBodyDirection =
            chooseStableBodyDirection(next.map, next.split, next.nearestIndex, getYRot(), activeRailBodyDirection)
        activeRailDirection = if (next.nearestIndex <= max(4, next.split / 8)) 1 else -1
        this.railProgress = activeRailIndex / activeRailSplit.toFloat()
        return true
    }

    private fun getBodyDirectionOnRail(map: RailMap?, split: Int, index: Int, bodyYaw: Float): Int {
        if (map == null || split <= 0) {
            return if (activeRailDirection == 0) 1 else activeRailDirection
        }
        val clamped = Mth.clamp(index, 0, split)
        val yawAtRail = map.getRailYaw(split, clamped)
        return if (abs(Mth.wrapDegrees(bodyYaw - yawAtRail)) <= 90.0f) 1 else -1
    }

    private fun chooseStableBodyDirection(map: RailMap?, split: Int, index: Int, bodyYaw: Float, fallback: Int): Int {
        if (map == null || split <= 0) {
            return if (fallback == 0) 1 else fallback
        }
        val byYaw = getBodyDirectionOnRail(map, split, index, bodyYaw)
        if (fallback == 0) {
            return byYaw
        }
        var railYaw = map.getRailYaw(split, Mth.clamp(index, 0, split))
        if (fallback < 0) {
            railYaw = Mth.wrapDegrees(railYaw + 180.0f)
        }
        val diff = abs(Mth.wrapDegrees(bodyYaw - railYaw))
        return if (diff <= 120.0f) fallback else byYaw
    }

    private fun sampleRail(map: RailMap, split: Int, index: Int): RailSample {
        return sampleRail(map, split, index.toDouble())
    }

    private fun sampleRail(map: RailMap, split: Int, index: Double): RailSample {
        val clamped = Mth.clamp(index, 0.0, split.toDouble())
        val low = Mth.clamp(floor(clamped).toInt(), 0, split)
        val high = Mth.clamp(ceil(clamped).toInt(), 0, split)
        if (low == high) {
            val pos = map.getRailPos(split, low)
            return RailSample(pos[1], map.getRailHeight(split, low), pos[0])
        }
        val t = clamped - low
        val a = map.getRailPos(split, low)
        val b = map.getRailPos(split, high)
        val yA: Double = map.getRailHeight(split, low)
        val yB: Double = map.getRailHeight(split, high)
        return RailSample(
            Mth.lerp(t, a[1], b[1]),
            Mth.lerp(t, yA, yB),
            Mth.lerp(t, a[0], b[0])
        )
    }

    private fun sampleBogieRail(map: RailMap, split: Int, index: Double): RailSample {
        val clamped = Mth.clamp(index, 0.0, split.toDouble())
        val low = Mth.clamp(floor(clamped).toInt(), 0, split)
        val high = Mth.clamp(ceil(clamped).toInt(), 0, split)
        if (low == high) {
            val pos = map.getRailPos(split, low)
            return RailSample(pos[1], map.getRailHeight(split, low), pos[0])
        }
        val t = clamped - low
        val a = map.getRailPos(split, low)
        val b = map.getRailPos(split, high)
        val yA: Double = map.getRailHeight(split, low)
        val yB: Double = map.getRailHeight(split, high)
        return RailSample(
            Mth.lerp(t, a[1], b[1]),
            Mth.lerp(t, yA, yB),
            Mth.lerp(t, a[0], b[0])
        )
    }

    private fun sampleRailIndex(map: RailMap, split: Int, index: Int): RailSample {
        val clamped = Mth.clamp(index, 0, split)
        val pos = map.getRailPos(split, clamped)
        return RailSample(pos[1], map.getRailHeight(split, clamped), pos[0])
    }

    private fun sampleRailYaw(map: RailMap?, split: Int, index: Double): Float {
        if (map == null || split <= 0) {
            return getYRot()
        }
        val clamped = Mth.clamp(index, 0.0, split.toDouble())
        val low = Mth.clamp(floor(clamped).toInt(), 0, split)
        val high = Mth.clamp(ceil(clamped).toInt(), 0, split)
        if (low == high) {
            return map.getRailYaw(split, low)
        }
        val yawLow = map.getRailYaw(split, low)
        val yawHigh = keepNearestYaw(map.getRailYaw(split, high), yawLow)
        return Mth.lerp((clamped - low).toFloat(), yawLow, yawHigh)
    }

    private fun sampleRailPitch(map: RailMap?, split: Int, index: Double): Float {
        if (map == null || split <= 0) {
            return getXRot()
        }
        val clamped = Mth.clamp(index, 0.0, split.toDouble())
        val low = Mth.clamp(floor(clamped).toInt(), 0, split)
        val high = Mth.clamp(ceil(clamped).toInt(), 0, split)
        if (low == high) {
            return map.getRailPitch(split, low)
        }
        val pitchLow = map.getRailPitch(split, low)
        val pitchHigh = map.getRailPitch(split, high)
        return Mth.lerp((clamped - low).toFloat(), pitchLow, pitchHigh)
    }

    private fun sampleRailRoll(map: RailMap?, split: Int, index: Double): Float {
        if (map == null || split <= 0) {
            return 0.0f
        }
        val clamped = Mth.clamp(index, 0.0, split.toDouble())
        val low = Mth.clamp(floor(clamped).toInt(), 0, split)
        val high = Mth.clamp(ceil(clamped).toInt(), 0, split)
        if (low == high) {
            return map.getRailRoll(split, low)
        }
        val rollLow = map.getRailRoll(split, low)
        val rollHigh = map.getRailRoll(split, high)
        return Mth.lerp((clamped - low).toFloat(), rollLow, rollHigh)
    }

    private val bogieRailOffsets: DoubleArray
        get() {
            val def =
                getById(this.vehicleId)
            if (def == null || def.getBogies().isEmpty()) {
                val distance = max(2.0, this.trainDistance * 0.7)
                return doubleArrayOf(-distance, distance)
            }
            var rear = Double.POSITIVE_INFINITY
            var front = Double.NEGATIVE_INFINITY
            for (bogie in def.getBogies()) {
                rear = min(rear, bogie.position().z)
                front = max(front, bogie.position().z)
            }
            if (!java.lang.Double.isFinite(rear) || !java.lang.Double.isFinite(front) || abs(front - rear) < 0.5) {
                val distance = max(2.0, this.trainDistance * 0.7)
                return doubleArrayOf(-distance, distance)
            }
            val midpoint = (front + rear) * 0.5
            val halfSpan = abs(front - rear) * 0.5
            if (abs(midpoint) > max(
                    0.75,
                    halfSpan * 0.35
                ) && shouldCenterAsymmetricBogieAnchors(
                    def
                )
            ) {
                return doubleArrayOf(-halfSpan, halfSpan)
            }
            return doubleArrayOf(rear, front)
        }

    private fun getExtremeBogieIndices(def: VehicleDefinition?): IntArray {
        if (def == null || def.getBogies().isEmpty()) {
            return intArrayOf(0, 1)
        }
        var rearIndex = 0
        var frontIndex = 0
        var rearZ = Double.POSITIVE_INFINITY
        var frontZ = Double.NEGATIVE_INFINITY
        for (i in def.getBogies().indices) {
            val z = def.getBogies().get(i).position().z
            if (z < rearZ) {
                rearZ = z
                rearIndex = i
            }
            if (z > frontZ) {
                frontZ = z
                frontIndex = i
            }
        }
        return intArrayOf(rearIndex, frontIndex)
    }

    fun getBogieYawOffset(bogie: VehicleDefinition.BogieDefinition?): Float {
        return getBogieYawOffset(bogie, getYRot())
    }

    fun getBogieYawOffset(bogieIndex: Int, bogie: VehicleDefinition.BogieDefinition?, baseYaw: Float): Float {
        return getBogieYawOffset(bogieIndex, bogie, baseYaw, 1.0f)
    }

    fun getBogieYawOffset(
        bogieIndex: Int,
        bogie: VehicleDefinition.BogieDefinition?,
        baseYaw: Float,
        partialTicks: Float
    ): Float {
        val anchor = resolveRenderAnchorForBogie(bogieIndex)
        if (isRailAnchorUsable(anchor)) {
            return relativeBogieYaw(getAnchorRailYaw(anchor, baseYaw), baseYaw)
        }
        // クライアント: tick記録した端台車ヨーを partialTicks で補間(滑らか＋遅延なし=RTM同等)。
        if (level().isClientSide()) {
            val side = resolveExtremeSideForBogieIndex(bogieIndex)
            if (side >= 0 && side < 2 && !java.lang.Float.isNaN(clientBogieYawCurr[side])) {
                val prev =
                    if (java.lang.Float.isNaN(clientBogieYawPrev[side])) clientBogieYawCurr[side] else clientBogieYawPrev[side]
                val interp = Mth.rotLerp(Mth.clamp(partialTicks, 0.0f, 1.0f), prev, clientBogieYawCurr[side])
                return relativeBogieYaw(interp, baseYaw)
            }
        }
        // フォールバック: 台車位置のレール接線を直接計算(ラグ無し)。スクリプト車両と同じ経路。
        val railYaw = computeClientBogieRailYaw(bogieIndex)
        if (!java.lang.Float.isNaN(railYaw)) {
            return relativeBogieYaw(railYaw, baseYaw)
        }
        return getBogieYawOffset(bogie, baseYaw)
    }

    fun getBogieYawOffset(bogie: VehicleDefinition.BogieDefinition?, baseYaw: Float): Float {
        if (bogie == null) {
            return 0.0f
        }
        val anchor = getNearestAnchorForBogie(bogie)
        if (isRailAnchorUsable(anchor)) {
            return relativeBogieYaw(getAnchorRailYaw(anchor, baseYaw), baseYaw)
        }
        if (activeRailMap != null && activeRailIndex >= 0 && activeRailSplit > 0) {
            val trackLen = max(0.001, activeRailMap!!.getLength())
            val sampleStep = trackLen / activeRailSplit
            val bodyDirection = if (activeRailBodyDirection == 0) 1 else activeRailBodyDirection
            val bogieIndex = activeRailPosition + bodyDirection * (bogie.position().z / sampleStep)
            val resolved = resolveRailSample(activeRailMap!!, activeRailSplit, bogieIndex, bodyDirection)
            val clampedIndex = Mth.clamp(Math.round(resolved.index).toInt(), 0, resolved.split)
            var railYaw = resolved.map!!.getRailYaw(resolved.split, clampedIndex)
            if (resolved.bodyDirection < 0) {
                railYaw = Mth.wrapDegrees(railYaw + 180.0f)
            }
            return relativeBogieYaw(railYaw, baseYaw)
        }

        val context = findRailContextNearAny(localToWorld(bogie.position()), null)
        if (context == null) {
            return 0.0f
        }
        val bodyDirection = getBodyDirectionOnRail(context.map, context.split, context.nearestIndex, baseYaw)
        var railYaw = context.map.getRailYaw(context.split, context.nearestIndex)
        if (bodyDirection < 0) {
            railYaw = Mth.wrapDegrees(railYaw + 180.0f)
        }
        return relativeBogieYaw(railYaw, baseYaw)
    }

    fun getBogieRenderOffset(
        bogieIndex: Int,
        bogie: VehicleDefinition.BogieDefinition?,
        baseYaw: Float,
        partialTicks: Float
    ): Vec3 {
        if (bogie == null) {
            return Vec3.ZERO
        }
        val def = getById(
            this.vehicleId
        )
        if (def == null) {
            return bogie.position()
        }
        // クライアントはサーバー同期の台車ワールド位置を partialTicks 補間で使う(本家RTMの台車補間相当)。
        val clientSynced = level().isClientSide() && entityData.get<Boolean>(BOGIE_SYNC_VALID)
        var railWorldY =
            if (clientSynced) clientSyncedBogieWorld(bogieIndex, partialTicks) else getBogieWorldPosition(bogieIndex)
        if (railWorldY == null) {
            railWorldY = getBogieWorldPosition(bogieIndex)
        }
        // 本家RTM準拠の台車高さ:
        //   RailMap.getRailHeight はすでにワールド軌面Yを返す。台車モデルは TrainEntityRenderer の
        //   車体座標系内で描くため、ワールドYは車体中心基準(RTM_VEHICLE_Y_OFFSET)へ戻してから
        //   JSON の bogiePos[i].y と最小限の visual lift を足す。
        // カーブ上では車体中心線とレール中心線が一致しないため、X/Z はレール位置へ追従。
        val anchor = getAnchorForRenderedBogie(bogieIndex)
        if (clientSynced || isRailAnchorUsable(anchor)) {
            val bodyRefY: Double =
                railWorldY.y + TRAIN_BODY_HEIGHT_OFFSET + bogie.position().y + BOGIE_RENDER_LIFT
            // 本体ポーズ(yaw/pitch/bank/modelOffset/scale)を厳密に逆変換して、カーブのバンクでも
            // 台車がレール上の正しい位置に描画されるようにする。
            val railLocal = worldToBogieLocalForRender(Vec3(railWorldY.x, bodyRefY, railWorldY.z), partialTicks)
            return Vec3(railLocal.x, railLocal.y, railLocal.z)
        }
        // レール非追従時(浮いている等)は本家 updatePosAndRotationClient と同様、
        // bogiePos をそのまま車体相対オフセットとして使う。
        return bogie.position()
    }

    fun getBogieRenderOffset(bogie: VehicleDefinition.BogieDefinition?, baseYaw: Float): Vec3 {
        if (bogie == null) {
            return Vec3.ZERO
        }
        val def = getById(
            this.vehicleId
        )
        if (def == null) {
            return bogie.position()
        }
        val index = def.getBogies().indexOf(bogie)
        if (index < 0) {
            return bogie.position()
        }
        return getBogieRenderOffset(index, bogie, baseYaw, 1.0f)
    }

    fun getBogiePitch(bogieIndex: Int): Float {
        val side = resolveExtremeSideForBogieIndex(bogieIndex)
        val anchor = if (side == 0) rearRailAnchor else frontRailAnchor
        if (isRailAnchorUsable(anchor)) {
            return bogiePitchMemory[side]
        }
        return bogiePitchMemory[side]
    }

    fun getBogieRoll(bogieIndex: Int): Float {
        val anchor = getAnchorForRenderedBogie(bogieIndex)
        if (isRailAnchorUsable(anchor)) {
            return sampleRailRoll(anchor!!.map, anchor.split, anchor.index)
        }
        return this.bodyRoll
    }

    private fun resolveRenderAnchorForBogie(bogieIndex: Int): RailAnchor? {
        val side = resolveExtremeSideForBogieIndex(bogieIndex)
        return if (side == 0) rearRailAnchor else frontRailAnchor
    }

    private fun resolveExtremeSideForBogieIndex(bogieIndex: Int): Int {
        val def = getById(
            this.vehicleId
        )
        if (def != null && !def.getBogies().isEmpty()) {
            val extremes = getExtremeBogieIndices(def)
            if (bogieIndex >= 0 && bogieIndex < def.getBogies().size) {
                if (bogieIndex == extremes[0]) {
                    return 0
                }
                if (bogieIndex == extremes[1]) {
                    return 1
                }
            }
        }
        return Mth.clamp(bogieIndex, 0, 1)
    }

    fun getBogieWorldYaw(bogieIndex: Int): Float {
        val side = resolveExtremeSideForBogieIndex(bogieIndex)
        val anchor = if (side == 0) rearRailAnchor else frontRailAnchor
        if (isRailAnchorUsable(anchor)) {
            // サーバー: レールアンカーの接線。
            val reference = if (bogieYawMemory[side] == 0.0f) getYRot() else bogieYawMemory[side]
            return sampleAnchorTangentYaw(anchor, reference)
        }
        // クライアント: アンカー/bogieYawMemory が無いので、台車位置のレール接線を直接求める。
        // これを返さないと getYRot()(車体ヨー)になり、スクリプトの台車相対角が 0 → 台車が
        // 車体と一緒に回ってしまう(独立しない)。本家RTMは台車エンティティの接線ヨーを返す。
        val railYaw = computeClientBogieRailYaw(bogieIndex)
        if (!java.lang.Float.isNaN(railYaw)) {
            return railYaw
        }
        return if (bogieYawMemory[side] == 0.0f) getYRot() else bogieYawMemory[side]
    }

    /**
     * クライアントで台車のレール接線ワールドヨーを求める。サーバー同期された台車エンティティの
     * 向きを最優先で使い、無ければ台車取付位置の近傍レールから接線を計算する。見つからなければ NaN。
     */
    private fun computeClientBogieRailYaw(bogieIndex: Int): Float {
        // 注意: 同期された台車エンティティの向き(clientBogieEntityYaw)はラグがあり、
        // 走行中に台車が一瞬古い向きを指して横ずれして見える。そこでクライアントでは
        // 台車取付位置のレール接線を毎フレーム直接計算する(ラグ無し・描画位置と一致)。
        val side = resolveExtremeSideForBogieIndex(bogieIndex)
        val def = getById(
            this.vehicleId
        )
        if (def == null) {
            return Float.NaN
        }
        // 同期された実台車ワールド位置でレール接線を求める(剛体マウントだと弦上=レールずれ位置で
        // 接線を取ってしまうため)。同期が無い時は従来どおり剛体マウント位置にフォールバック。
        val mount = getBogieWorldPosition(bogieIndex)
        var map = clientBogieRailMap[side]
        // キャッシュ済みレールが取付位置から遠ければ(別レールへ移った)再探索。
        if (map == null || farFromRail(map, mount)) {
            val ctx = findRailContextNearAny(mount, null)
            if (ctx == null || ctx.map == null) {
                return Float.NaN
            }
            map = ctx.map
            clientBogieRailMap[side] = map
        }
        val split = getMovementSplitForMap(map)
        if (split <= 0) {
            return Float.NaN
        }
        // サーバー(sampleAnchorTangentYaw)と同じ規約: 最近点の前後を少しずらしてサンプルし、
        // atan2(dx,dz) で接線ヨーを求める。getRailYaw より滑らかで規約も一致する。
        val nearest = Mth.clamp(map.getNearlestPoint(split, mount.x, mount.z), 0, split)
        val delta = max(1.0, split * 0.0060)
        val beforeIndex = Mth.clamp(nearest - delta, 0.0, split.toDouble())
        val afterIndex = Mth.clamp(nearest + delta, 0.0, split.toDouble())
        val before = sampleRail(map, split, beforeIndex)
        val after = sampleRail(map, split, afterIndex)
        val dx = after.x - before.x
        val dz = after.z - before.z
        val tangentYaw = if (dx * dx + dz * dz < 1.0E-6)
            sampleRailYaw(map, split, nearest.toDouble())
        else Math.toDegrees(atan2(dx, dz)).toFloat()
        // 本家RTM EntityBogie.fixBogieYaw と同じ: 車体向きと 90°以上離れていれば 180°反転。
        return fixBogieYaw(getYRot(), tangentYaw)
    }

    /** map 上で worldPos の最近点が 4 ブロック超離れているか(=別レールへ移った)。  */
    private fun farFromRail(map: RailMap, worldPos: Vec3): Boolean {
        val split = getMovementSplitForMap(map)
        if (split <= 0) {
            return true
        }
        val nearest = Mth.clamp(map.getNearlestPoint(split, worldPos.x, worldPos.z), 0, split)
        val p = map.getRailPos(split, nearest)
        val dx = p[1] - worldPos.x
        val dz = p[0] - worldPos.z
        return dx * dx + dz * dz > 4.0 * 4.0
    }

    fun getBogieWorldPosition(bogieIndex: Int): Vec3 {
        // クライアントは movement を走らせずレールも持たないため、サーバーが同期した端台車の
        // ワールド位置を使う(カーブで台車をレール上に正確に描く)。中間台車は前後で補間。
        if (level().isClientSide() && entityData.get<Boolean>(BOGIE_SYNC_VALID)) {
            // partialTicks 無しの呼び出し(ヨー計算等)は現tick値(pt=1)を使う。描画位置の補間は
            // getBogieRenderOffset 側が partialTicks 付きで clientSyncedBogieWorld を呼ぶ。
            val synced = clientSyncedBogieWorld(bogieIndex, 1.0f)
            if (synced != null) {
                return synced
            }
        }
        val anchor = getAnchorForRenderedBogie(bogieIndex)
        if (isRailAnchorUsable(anchor)) {
            val sample = sampleBogieRail(anchor!!.map, anchor.split, anchor.index)
            return Vec3(sample.x, sample.y, sample.z)
        }
        val def = getById(
            this.vehicleId
        )
        val local = getBogieLocalPosition(bogieIndex, def)
        return localToWorld(local)
    }

    /** クライアント: 毎tick、同期された端台車オフセットを prev/curr へ取り込む(描画補間用)。  */
    private fun updateClientBogieOffsetInterpolation() {
        val rear = Vec3(
            entityData.get<Float>(REAR_BOGIE_DX)!!.toDouble(),
            entityData.get<Float>(REAR_BOGIE_DY)!!.toDouble(),
            entityData.get<Float>(
                REAR_BOGIE_DZ
            )!!.toDouble()
        )
        val front = Vec3(
            entityData.get<Float>(FRONT_BOGIE_DX)!!.toDouble(),
            entityData.get<Float>(FRONT_BOGIE_DY)!!.toDouble(),
            entityData.get<Float>(
                FRONT_BOGIE_DZ
            )!!.toDouble()
        )
        if (!clientBogieOffInit) {
            clientRearBogieOffCurr = rear
            clientRearBogieOffPrev = clientRearBogieOffCurr
            clientFrontBogieOffCurr = front
            clientFrontBogieOffPrev = clientFrontBogieOffCurr
            clientBogieOffInit = true
            return
        }
        clientRearBogieOffPrev = clientRearBogieOffCurr
        clientFrontBogieOffPrev = clientFrontBogieOffCurr
        clientRearBogieOffCurr = filterOffsetJump(0, clientRearBogieOffCurr, rear)
        clientFrontBogieOffCurr = filterOffsetJump(1, clientFrontBogieOffCurr, front)
        // 端台車のレール接線ヨーを tick 単位で記録(描画時に partialTicks 補間=滑らか＋遅延なし)。
        // ヨーはサーバーのアンカー接線を同期した値を使う(クライアント探索の誤方向/180°反転を回避)。
        val def = getById(
            this.vehicleId
        )
        if (def != null && !def.getBogies().isEmpty()) {
            if (entityData.get<Boolean>(BOGIE_SYNC_VALID)) {
                updateTickBogieYaw(0, entityData.get<Float>(REAR_BOGIE_YAW)!!)
                updateTickBogieYaw(1, entityData.get<Float>(FRONT_BOGIE_YAW)!!)
            } else {
                val ext = getExtremeBogieIndices(def)
                updateTickBogieYaw(0, computeClientBogieRailYaw(ext[0]))
                updateTickBogieYaw(1, computeClientBogieRailYaw(ext[1]))
            }
        }
    }

    /**
     * レール継ぎ目で同期台車位置が一瞬飛ぶグリッチを除去する。明らかな大ジャンプ(>6ブロック/tick、
     * 遠レール誤取得や180°反転に伴う反対側への飛び等)だけを弾き前回値を維持する。
     * 台車オフセットはカーブで回転するため急カーブ+高速では1tick変化が数ブロックになる。しきい値を
     * 低くすると正当なカーブ変化を弾いて「追従が外れて一瞬戻る」原因になるため 6 と高めにする。
     * 連続したら本物の変化として採用(固着回避)。
     */
    private fun filterOffsetJump(side: Int, cur: Vec3?, target: Vec3?): Vec3 {
        if (cur == null || target == null) {
            return target!!
        }
        if (target.distanceToSqr(cur) > 6.0 * 6.0 && clientBogieOffRejectCount[side] < 2) {
            clientBogieOffRejectCount[side]++
            return cur
        }
        clientBogieOffRejectCount[side] = 0
        return target
    }

    /** side(0=後/1=前)のレール接線ヨーを prev/curr へ記録(NaNは前回値維持、初回は即セット)。  */
    private fun updateTickBogieYaw(side: Int, target: Float) {
        if (java.lang.Float.isNaN(target)) {
            return
        }
        if (java.lang.Float.isNaN(clientBogieYawCurr[side])) {
            clientBogieYawCurr[side] = target
            clientBogieYawPrev[side] = clientBogieYawCurr[side]
            clientBogieYawRejectCount[side] = 0
            return
        }
        // グリッチ除去: 明らかな誤値(fixBogieYaw の約180°反転や別レール接線の拾い間違い)だけを弾く。
        // 急カーブ＋高速の正当な1tick変化(最大~50°程度)を棄却しないよう、しきい値は 100° と高めにする。
        // (45°など低くすると急カーブで正当変化を弾き「追従が外れて一瞬戻る」原因になる。)
        // 大ジャンプが連続したら本物の向き変化(=固着回避)として採用する。
        if (abs(Mth.wrapDegrees(target - clientBogieYawCurr[side])) > 100.0f
            && clientBogieYawRejectCount[side] < 2
        ) {
            clientBogieYawRejectCount[side]++
            return
        }
        clientBogieYawRejectCount[side] = 0
        clientBogieYawPrev[side] = clientBogieYawCurr[side]
        clientBogieYawCurr[side] = target
    }

    /**
     * サーバーが同期した端台車ワールド位置から、指定台車のワールド位置を返す(中間は前後で補間)。
     * 本家RTMの台車補間に倣い、エンティティ位置・台車オフセットとも partialTicks で前tick↔現tick
     * 補間して滑らかに追従させる(tick段付き防止)。
     */
    private fun clientSyncedBogieWorld(bogieIndex: Int, partialTicks: Float): Vec3? {
        val def = getById(
            this.vehicleId
        )
        if (def == null || def.getBogies().isEmpty()) {
            return null
        }
        val pt = Mth.clamp(partialTicks, 0.0f, 1.0f)
        val ex = Mth.lerp(pt.toDouble(), this.xo, getX())
        val ey = Mth.lerp(pt.toDouble(), this.yo, getY())
        val ez = Mth.lerp(pt.toDouble(), this.zo, getZ())
        val rearOff = clientRearBogieOffPrev.lerp(clientRearBogieOffCurr, pt.toDouble())
        val frontOff = clientFrontBogieOffPrev.lerp(clientFrontBogieOffCurr, pt.toDouble())
        val rearWorld = Vec3(ex + rearOff.x, ey + rearOff.y, ez + rearOff.z)
        val frontWorld = Vec3(ex + frontOff.x, ey + frontOff.y, ez + frontOff.z)
        val ext = getExtremeBogieIndices(def)
        if (bogieIndex == ext[0]) {
            return rearWorld
        }
        if (bogieIndex == ext[1]) {
            return frontWorld
        }
        // 中間台車: z位置の比率で前後台車間を補間。
        val rearZ = def.getBogies().get(Mth.clamp(ext[0], 0, def.getBogies().size - 1)).position().z
        val frontZ = def.getBogies().get(Mth.clamp(ext[1], 0, def.getBogies().size - 1)).position().z
        val z = def.getBogies().get(Mth.clamp(bogieIndex, 0, def.getBogies().size - 1)).position().z
        val denom = frontZ - rearZ
        val t = if (abs(denom) < 1.0E-6) 0.5 else Mth.clamp((z - rearZ) / denom, 0.0, 1.0)
        return Vec3(
            Mth.lerp(t, rearWorld.x, frontWorld.x),
            Mth.lerp(t, rearWorld.y, frontWorld.y),
            Mth.lerp(t, rearWorld.z, frontWorld.z)
        )
    }

    /** サーバー専用: 端台車のワールド位置をエンティティ相対オフセットでクライアントへ同期する。  */
    private fun syncBogieRenderOffsets() {
        if (level().isClientSide()) {
            return
        }
        val def = getById(
            this.vehicleId
        )
        if (def == null || def.getBogies().isEmpty()
            || !isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)
        ) {
            if (entityData.get<Boolean>(BOGIE_SYNC_VALID)) {
                entityData.set<Boolean>(BOGIE_SYNC_VALID, false)
            }
            return
        }
        val ext = getExtremeBogieIndices(def)
        val frontWorld = getBogieWorldPosition(ext[1])
        val rearWorld = getBogieWorldPosition(ext[0])
        entityData.set<Float>(FRONT_BOGIE_DX, (frontWorld.x - getX()).toFloat())
        entityData.set<Float>(FRONT_BOGIE_DY, (frontWorld.y - getY()).toFloat())
        entityData.set<Float>(FRONT_BOGIE_DZ, (frontWorld.z - getZ()).toFloat())
        entityData.set<Float>(REAR_BOGIE_DX, (rearWorld.x - getX()).toFloat())
        entityData.set<Float>(REAR_BOGIE_DY, (rearWorld.y - getY()).toFloat())
        entityData.set<Float>(REAR_BOGIE_DZ, (rearWorld.z - getZ()).toFloat())
        // 台車のレール接線ヨーもサーバーのアンカーから求めて同期(クライアントの探索計算より安定=誤方向なし)。
        entityData.set<Float>(FRONT_BOGIE_YAW, getBogieWorldYaw(ext[1]))
        entityData.set<Float>(REAR_BOGIE_YAW, getBogieWorldYaw(ext[0]))
        entityData.set<Boolean>(BOGIE_SYNC_VALID, true)
    }

    fun getBogieEntityWorldPosition(bogieIndex: Int): Vec3 {
        return getBogieWorldPosition(bogieIndex)
    }

    fun getBogieVisualWorldPosition(bogieIndex: Int, partialTicks: Float): Vec3 {
        val def = getById(
            this.vehicleId
        )
        val local = getBogieLocalPosition(bogieIndex, def)
        return projectLocalBogiePositionToWorld(local, partialTicks)
    }

    private fun projectLocalBogiePositionToWorld(local: Vec3, partialTicks: Float): Vec3 {
        val def = getById(
            this.vehicleId
        )
        val modelOffset = if (def != null) def.getModelOffset() else Vec3.ZERO
        val scale = if (def != null) def.getModelScale().toDouble() else 1.0

        val px = modelOffset.x + local.x * scale
        val py = modelOffset.y + local.y * scale
        val pz = modelOffset.z + local.z * scale

        // スポーン直後はクライアント側エンティティの前tick位置(xo/yo/zo)が原点(0,0,0)の
        // まま(最初のクライアントtickまで)。そのまま補間すると台車位置が原点側へ大きく
        // ズレる(=「列車を置いた瞬間だけ台車がズレる/動かすと戻る」)。前tick位置が現在位置
        // から物理的にあり得ない距離(列車は最大2/tick)離れていたら未初期化とみなし、補間
        // せず現在値を使う。通常走行では xo は近接しているため一切影響しない。
        val dxo = this.getX() - this.xo
        val dyo = this.getY() - this.yo
        val dzo = this.getZ() - this.zo
        val staleOldPos = (dxo * dxo + dyo * dyo + dzo * dzo) > 64.0

        val trainPitch = if (staleOldPos) this.getXRot() else Mth.lerp(partialTicks, this.xRotO, this.getXRot())
        val pitchRad = Math.toRadians(-trainPitch.toDouble())
        val pitchedY = cos(pitchRad) * py - sin(pitchRad) * pz
        val pitchedZ = sin(pitchRad) * py + cos(pitchRad) * pz

        val trainYaw = if (staleOldPos) this.getYRot() else Mth.rotLerp(partialTicks, this.yRotO, this.getYRot())
        val yawRad = Math.toRadians(-trainYaw.toDouble())
        val rotatedX = cos(yawRad) * px - sin(yawRad) * pitchedZ
        val rotatedZ = sin(yawRad) * px + cos(yawRad) * pitchedZ

        val trainX = if (staleOldPos) this.getX() else Mth.lerp(partialTicks.toDouble(), this.xo, this.getX())
        val trainY = if (staleOldPos) this.getY() else Mth.lerp(partialTicks.toDouble(), this.yo, this.getY())
        val trainZ = if (staleOldPos) this.getZ() else Mth.lerp(partialTicks.toDouble(), this.zo, this.getZ())
        return Vec3(trainX + rotatedX, trainY + pitchedY, trainZ + rotatedZ)
    }

    fun getScriptBogieWorldYaw(bogieIndex: Int): Float {
        return getBogieWorldYaw(bogieIndex)
    }

    private fun getAnchorForBogieIndex(def: VehicleDefinition?, bogieIndex: Int): RailAnchor? {
        if (def == null || def.getBogies().isEmpty()) {
            return null
        }
        val clamped = Mth.clamp(bogieIndex, 0, def.getBogies().size - 1)
        val extremes = getExtremeBogieIndices(def)
        if (clamped == extremes[0]) {
            return rearRailAnchor
        }
        if (clamped == extremes[1]) {
            return frontRailAnchor
        }
        val z = def.getBogies().get(clamped).position().z
        val rearZ = def.getBogies().get(extremes[0]).position().z
        val frontZ = def.getBogies().get(extremes[1]).position().z
        if (abs(z - frontZ) <= abs(z - rearZ)) {
            return frontRailAnchor
        }
        return rearRailAnchor
    }

    private fun getAnchorRailYaw(anchor: RailAnchor?, baseYaw: Float): Float {
        if (!isRailAnchorUsable(anchor)) {
            return baseYaw
        }
        return sampleAnchorTangentYaw(anchor, baseYaw)
    }

    private fun syncBogieOrientationMemory(
        front: RailSample?,
        rear: RailSample?,
        fallbackYaw: Float,
        fallbackPitch: Float
    ) {
        val anchors = arrayOf<RailAnchor?>(rearRailAnchor, frontRailAnchor)
        val samples = arrayOf<RailSample?>(rear, front)
        for (side in anchors.indices) {
            val anchor = anchors[side]
            val sample = samples[side]
            if (isRailAnchorUsable(anchor) && sample != null) {
                val referenceYaw = if (bogieYawMemory[side] == 0.0f) fallbackYaw else bogieYawMemory[side]
                val bogieYaw = sampleAnchorTangentYaw(anchor, referenceYaw)
                val railPitch = sampleRailPitch(anchor!!.map, anchor.split, anchor.index)
                bogieYawMemory[side] = bogieYaw
                bogiePitchMemory[side] = if (anchor.travelDirection < 0) -railPitch else railPitch
            } else {
                bogieYawMemory[side] = fallbackYaw
                bogiePitchMemory[side] = fallbackPitch
            }
        }
    }

    private fun getBogieReferenceYaw(bogieIndex: Int, baseYaw: Float): Float {
        return baseYaw
    }

    private fun getNearestAnchorForBogie(bogie: VehicleDefinition.BogieDefinition?): RailAnchor? {
        if (bogie == null) {
            return null
        }
        val bogieWorld = localToWorld(bogie.position())
        var best: RailAnchor? = null
        var bestDistance = Double.POSITIVE_INFINITY
        if (isRailAnchorUsable(frontRailAnchor)) {
            val sample = sampleRail(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
            bestDistance = Vec3(sample.x, sample.y, sample.z).distanceToSqr(bogieWorld)
            best = frontRailAnchor
        }
        if (isRailAnchorUsable(rearRailAnchor)) {
            val sample = sampleRail(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
            val distance = Vec3(sample.x, sample.y, sample.z).distanceToSqr(bogieWorld)
            if (distance < bestDistance) {
                best = rearRailAnchor
            }
        }
        return best
    }

    private fun relativeBogieYaw(railYaw: Float, baseYaw: Float): Float {
        val directDiff = Mth.wrapDegrees(railYaw - baseYaw)
        val flippedDiff = Mth.wrapDegrees(directDiff + 180.0f)
        val diff = if (abs(directDiff) <= abs(flippedDiff)) directDiff else flippedDiff

        return Mth.clamp(diff, -85.0f, 85.0f)
    }

    private fun updateStoredBogieState() {
        val anchors = arrayOf<RailAnchor?>(rearRailAnchor, frontRailAnchor)
        for (side in anchors.indices) {
            val anchor = anchors[side]
            if (anchor == null) {
                bogiePrevMaps[side] = null
                bogiePrevSplits[side] = 0
                bogiePrevSampleIndex[side] = -1
                continue
            }
            bogiePrevMaps[side] = anchor.map
            bogiePrevSplits[side] = anchor.split
            bogiePrevSampleIndex[side] = Mth.clamp(Math.round(anchor.index).toInt(), 0, max(0, anchor.split))
        }
    }

    private fun sampleAnchorTangentYaw(anchor: RailAnchor?, fallbackYaw: Float): Float {
        if (anchor == null || anchor.map == null || anchor.split <= 0) {
            return fallbackYaw
        }
        val delta = max(1.0, anchor.split * 0.0060)
        val beforeIndex = Mth.clamp(anchor.index - delta, 0.0, anchor.split.toDouble())
        val afterIndex = Mth.clamp(anchor.index + delta, 0.0, anchor.split.toDouble())
        val before = sampleRail(anchor.map, anchor.split, beforeIndex)
        val after = sampleRail(anchor.map, anchor.split, afterIndex)
        val dx = after.x - before.x
        val dz = after.z - before.z
        if (dx * dx + dz * dz < 1.0E-6) {
            val yaw = sampleRailYaw(anchor.map, anchor.split, anchor.index)
            return if (anchor.travelDirection < 0) Mth.wrapDegrees(yaw + 180.0f) else yaw
        }
        var tangentYaw = Math.toDegrees(atan2(dx, dz)).toFloat()
        if (anchor.travelDirection < 0) {
            tangentYaw = Mth.wrapDegrees(tangentYaw + 180.0f)
        }
        return keepNearestYaw(tangentYaw, fallbackYaw)
    }

    private val activeRailContext: RailFollowContext?
        get() {
            if (activeRailMap != null && activeRailIndex >= 0 && activeRailSplit > 0) {
                val index = Mth.clamp(activeRailIndex, 0, activeRailSplit)
                val p = activeRailMap!!.getRailPos(activeRailSplit, index)
                val py: Double = activeRailMap!!.getRailHeight(
                    activeRailSplit,
                    index
                ) + TRAIN_BODY_HEIGHT_OFFSET
                val distSq = distanceToSqr(p[1], py, p[0])
                // 交差部では別レールとの距離がかなり近くなるので、少し離れても
                // 現在のレールを優先して掴み続ける。
                if (distSq < 400.0) {
                    return TrainEntity.RailFollowContext(
                        activeRailMap!!,
                        activeRailSplit,
                        index,
                        distSq
                    )
                }
            }

            val nearest =
                findNearestRailContext()
            if (nearest != null) {
                activeRailMap = nearest.map
                activeRailSplit = getMovementSplitForMap(nearest.map)
                activeRailIndex = nearest.nearestIndex
                activeRailPosition = activeRailIndex.toDouble()
                val yawAtRail = activeRailMap!!.getRailYaw(activeRailSplit, activeRailIndex)
                val yawDiff = abs(Mth.wrapDegrees(getYRot() - yawAtRail))
                val mapForwardSign = if (yawDiff <= 90.0f) 1 else -1
                activeRailBodyDirection = mapForwardSign
                val formationDriver =
                    this.formationDriver
                val controller =
                    if (formationDriver != null) formationDriver.controller else null
                val cabTrain =
                    if (formationDriver != null) formationDriver.cabTrain else this
                val cabDirection = getCabDirectionSign(controller, cabTrain)
                activeRailDirection = if (abs(cabDirection) < 0.5f)
                    mapForwardSign
                else
                    mapForwardSign * (if (cabDirection > 0.0f) 1 else -1)
            }
            return nearest
        }

    private fun findRailContextNear(worldPos: Vec3, exclude: RailMap?): RailFollowContext? {
        return findRailContextNear(worldPos, exclude, true)
    }

    private fun findRailContextNearAny(worldPos: Vec3, exclude: RailMap?): RailFollowContext? {
        return findRailContextNear(worldPos, exclude, false)
    }

    private fun findRailContextNear(worldPos: Vec3, exclude: RailMap?, endpointOnly: Boolean): RailFollowContext? {
        val center = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z)
        var best: RailFollowContext? = null
        val radius = 20
        val referenceYaw = getYRot()

        // 1st pass: アクティブ分岐のみ。見つからなければ 2nd pass で全分岐へフォールバック
        // (非アクティブ分岐の終端突入やスイッチ切替で現在分岐が非アクティブ化した際に弾かれないように)。
        var pass = 0
        while (pass < 2 && best == null) {
            railLookupIncludeAllSegments = pass == 1
            for (dx in -radius..radius) {
                for (dy in -4..4) {
                    for (dz in -radius..radius) {
                        val pos = center.offset(dx, dy, dz)
                        val maps = getRailMapsAt(pos)
                        if (maps.size == 0) continue

                        for (map in maps) {
                            if (map == null || map === exclude) continue
                            val split = getMovementSplitForMap(map)
                            // RailMap#getNearlestPoint expects world X/Z in that order.
                            var nearest = map.getNearlestPoint(split, worldPos.x, worldPos.z)
                            nearest = Mth.clamp(nearest, 0, split)
                            val p = map.getRailPos(split, nearest)
                            val py: Double = map.getRailHeight(split, nearest) + TRAIN_BODY_HEIGHT_OFFSET
                            var distSq = Vec3(p[1], py, p[0]).distanceToSqr(worldPos)
                            val nearEndpoint = nearest <= max(6, split / 6) || nearest >= split - max(6, split / 6)
                            val maxDistSq = if (endpointOnly) 81.0 else 144.0
                            if ((endpointOnly && !nearEndpoint) || distSq > maxDistSq) {
                                continue
                            }
                            distSq += railYawPenalty(map, split, nearest, referenceYaw)
                            if (activeRailMap != null && map === activeRailMap) {
                                distSq -= if (endpointOnly) 2.0 else 6.0
                            }
                            if (best == null || distSq < best.distanceSq) {
                                best = RailFollowContext(map, split, nearest, distSq)
                            }
                        }
                    }
                }
            }
            pass++
        }
        railLookupIncludeAllSegments = false

        return best
    }

    private fun findNearestRailContext(): RailFollowContext? {
        val center = blockPosition()
        var best: RailFollowContext? = null
        val radius = RAIL_SEARCH_RADIUS.toInt()
        val referenceYaw = getYRot()

        // 1st pass: アクティブ分岐のみ。見つからなければ 2nd pass で全分岐へフォールバック
        // (分岐器の非アクティブ側に触れて再取得が走った時に弾かれないように)。
        var pass = 0
        while (pass < 2 && best == null) {
            railLookupIncludeAllSegments = pass == 1
            for (dx in -radius..radius) {
                for (dy in -2..2) {
                    for (dz in -radius..radius) {
                        val pos = center.offset(dx, dy, dz)
                        val maps = getRailMapsAt(pos)
                        if (maps.size == 0) continue

                        for (map in maps) {
                            if (map == null) continue
                            val split = getMovementSplitForMap(map)
                            var nearest = map.getNearlestPoint(split, getX(), getZ())
                            nearest = Mth.clamp(nearest, 0, split)
                            val p = map.getRailPos(split, nearest)
                            val py: Double = map.getRailHeight(split, nearest) + TRAIN_BODY_HEIGHT_OFFSET
                            var distSq = distanceToSqr(p[1], py, p[0])

                            distSq += railYawPenalty(map, split, nearest, referenceYaw)
                            if (activeRailMap != null && map === activeRailMap) {
                                distSq -= 6.0
                            }
                            if (best == null || distSq < best.distanceSq) {
                                best = RailFollowContext(map, split, nearest, distSq)
                            }
                        }
                    }
                }
            }
            pass++
        }
        railLookupIncludeAllSegments = false

        return best
    }

    private fun railYawPenalty(map: RailMap, split: Int, index: Int, referenceYaw: Float): Double {
        val railYaw = map.getRailYaw(split, index)
        val diffForward = abs(Mth.wrapDegrees(referenceYaw - railYaw))
        val diffReverse = abs(Mth.wrapDegrees(referenceYaw - (railYaw + 180.0f)))
        val diff = min(diffForward, diffReverse)
        return diff * 0.06
    }

    private fun findRailContextBeyondBoundary(
        currentMap: RailMap?,
        currentSplit: Int,
        boundaryIndex: Int,
        travelDirection: Int
    ): RailFollowContext? {
        if (currentMap == null || currentSplit <= 0) {
            return null
        }
        val boundary = sampleRail(currentMap, currentSplit, boundaryIndex)
        var boundaryYaw = currentMap.getRailYaw(currentSplit, boundaryIndex)
        if (travelDirection < 0) {
            boundaryYaw = Mth.wrapDegrees(boundaryYaw + 180.0f)
        }
        val yawRad = Math.toRadians(-boundaryYaw.toDouble())
        val forward = Vec3(-sin(yawRad), 0.0, cos(yawRad))
        val boundaryPos = Vec3(boundary.x, boundary.y, boundary.z)
        val endpoint = if (boundaryIndex <= 0) currentMap.startRP else currentMap.endRP
        val center = BlockPos.containing(boundary.x, boundary.y, boundary.z)
        var best: RailFollowContext? = null
        val radius = 20
        for (dx in -radius..radius) {
            for (dy in -4..4) {
                for (dz in -radius..radius) {
                    val maps = getConnectionCandidateMapsAt(center.offset(dx, dy, dz), currentMap)
                    if (maps.size == 0) {
                        continue
                    }
                    for (map in maps) {
                        if (map == null || map === currentMap || map.equals(currentMap)) {
                            continue
                        }
                        val split = getMovementSplitForMap(map)
                        best = betterFollowContext(
                            best,
                            evaluateBoundaryFallback(map, split, 0, endpoint, boundaryPos, boundaryYaw)
                        )
                        best = betterFollowContext(
                            best,
                            evaluateBoundaryFallback(map, split, split, endpoint, boundaryPos, boundaryYaw)
                        )
                    }
                }
            }
        }
        return best
    }

    private fun evaluateBoundaryFallback(
        map: RailMap,
        split: Int,
        endpointIndex: Int,
        currentEndpoint: RailPosition?,
        boundaryPos: Vec3,
        outgoingYaw: Float
    ): RailFollowContext? {
        val candidateEndpoint = if (endpointIndex <= 0) map.startRP else map.endRP
        val sameEndpoint = candidateEndpoint != null && sameRailEndpoint(candidateEndpoint, currentEndpoint)
        val sample = sampleRail(map, split, endpointIndex)
        val distSq = Vec3(sample.x, sample.y, sample.z).distanceToSqr(boundaryPos)
        if (!sameEndpoint && distSq > RAIL_CONNECTION_MAX_DISTANCE_SQ) {
            return null
        }

        val nextTravelDirection = if (endpointIndex <= 0) 1 else -1
        var candidateYaw = map.getRailYaw(split, endpointIndex)
        if (nextTravelDirection < 0) {
            candidateYaw = Mth.wrapDegrees(candidateYaw + 180.0f)
        }
        val yawDiff = abs(Mth.wrapDegrees(outgoingYaw - candidateYaw))
        // 端点共有でも逆走(yaw差>90°)接続は拒否(分岐器トランクでの兄弟分岐へのU字乗り移り防止)。
        val reversal = yawDiff > 90.0f
        if ((!sameEndpoint && yawDiff > RAIL_CONNECTION_MAX_YAW_DIFF) || reversal) {
            return null
        }
        val score = distSq + yawDiff * 0.02 + (if (sameEndpoint) -576.0 else 0.0)
        return RailFollowContext(map, split, endpointIndex, score)
    }

    private fun betterFollowContext(current: RailFollowContext?, candidate: RailFollowContext?): RailFollowContext? {
        if (candidate == null) {
            return current
        }
        if (current == null || candidate.distanceSq < current.distanceSq) {
            return candidate
        }
        return current
    }

    private fun getRailMapsAt(pos: BlockPos): Array<RailMap?> {
        val blockEntity = level().getBlockEntity(pos)
        if (blockEntity is LargeRailCoreBlockEntity && blockEntity.isLoaded) {
            val core = blockEntity
            return (if (railLookupIncludeAllSegments) core.allRailMaps else core.activeRailMaps).map { it as RailMap? }.toTypedArray()
        }
        if (blockEntity is RailCollisionBlockEntity) {
            val corePos: BlockPos? = blockEntity.getCorePos()
            val core = if (corePos == null) null else level().getBlockEntity(corePos)
            if (core is LargeRailCoreBlockEntity && core.isLoaded) {
                return (if (railLookupIncludeAllSegments) core.allRailMaps else core.activeRailMaps).map { it as RailMap? }.toTypedArray()
            }
        }
        return arrayOfNulls<RailMap>(0)
    }


    fun coupleNearest() {
        if (level().isClientSide()) {
            return
        }
        var nearest: TrainEntity? = null
        var best = 9.0
        for (other in level().getEntitiesOfClass<TrainEntity>(
            TrainEntity::class.java,
            getBoundingBox().inflate(6.0)
        )) {
            if (other === this || !other!!.isAlive()) continue
            val d = other.position().distanceToSqr(this.position())
            if (d < best) {
                best = d
                nearest = other
            }
        }
        if (nearest != null) {
            coupleWith(nearest)
        }
    }

    fun coupleWith(other: TrainEntity?) {
        if (other == null || other === this || level().isClientSide()) {
            return
        }
        val tail = this.formationTail
        val otherHead = other.formationHead
        if (tail == null || otherHead == null || tail === otherHead) {
            return
        }
        if (otherHead.coupledLeaderUuid != null || tail.coupledFollowerUuid != null || otherHead.hasIndirectPassenger(
                tail
            ) || tail.isConnectedTo(otherHead)
        ) {
            return
        }
        tail.linkCouplingByPosition(otherHead)
        // stabilizeCoupledFormations handles: velocity zeroing, settings sync, position snapping,
        // formation rebuild, and immediate updateTrainMovement snap for all cars.
        stabilizeCoupledFormations(tail, otherHead)
        clearCouplingModeInvolving(this, other)
        tail.hurtMarked = true
        otherHead.hurtMarked = true
    }

    private fun linkCouplingByPosition(other: TrainEntity?) {
        if (other == null || other === this || isConnectedTo(other)) {
            return
        }
        val pair = findNearestCouplerPair(other)
        setCoupledFollowerUuid(other.getUUID())
        coupledFollowerThisSide = pair.thisSide
        coupledFollowerOtherSide = pair.otherSide
        other.setCoupledLeaderUuid(getUUID())
    }

    private fun findNearestCouplerPair(other: TrainEntity?): CouplerPair {
        var best = CouplerPair(-1, 1, Double.MAX_VALUE)
        if (other == null) {
            return best
        }
        for (thisSide in intArrayOf(1, -1)) {
            val thisPoint = getCouplerPoint(thisSide > 0)
            for (otherSide in intArrayOf(1, -1)) {
                val distance = thisPoint.distanceToSqr(other.getCouplerPoint(otherSide > 0))
                if (distance < best.distanceSqr) {
                    best = CouplerPair(thisSide, otherSide, distance)
                }
            }
        }
        return best
    }

    private fun getCouplerPoint(front: Boolean): Vec3 {
        val z = if (front) this.couplingHalfLength else -this.couplingHalfLength
        return localToWorld(Vec3(0.0, 0.0, z))
    }

    private fun getPreviousCouplerPoint(front: Boolean): Vec3 {
        val z = if (front) this.couplingHalfLength else -this.couplingHalfLength
        return localToWorldPrev(Vec3(0.0, 0.0, z))
    }

    private fun getNearestCouplerDistanceSqr(other: TrainEntity?): Double {
        return findNearestCouplerPair(other).distanceSqr
    }

    private fun canCompleteCouplingWith(other: TrainEntity?): Boolean {
        if (other == null || other === this || isConnectedTo(other)) {
            return false
        }
        for (thisSide in intArrayOf(-1, 1)) {
            for (otherSide in intArrayOf(-1, 1)) {
                if (getSweptCouplerDistanceSqr(other, thisSide, otherSide) <=
                    COUPLER_CONTACT_DISTANCE * COUPLER_CONTACT_DISTANCE
                ) return true
            }
        }
        return false
    }

    private fun enterCouplingMode(player: Player, bogieIndex: Int = -1) {
        if (level().isClientSide()) {
            return
        }
        val selectedBogieIndex = if (bogieIndex >= 0) {
            Mth.clamp(bogieIndex, 0, 1)
        } else {
            findNearestBogieIndex(player.position())
        }
        clearBogieActivation()
        setBogieActivated(selectedBogieIndex, true)
        val playerId = player.getUUID()
        val selection: CouplingSelection? = COUPLING_MODE.get(playerId)
        if (selection == null || selection.isComplete) {
            COUPLING_MODE.put(
                playerId,
                CouplingSelection(getUUID(), selectedBogieIndex, null, -1, level().getGameTime())
            )
            player.sendOverlayMessage(Component.literal("連結モード: もう片方の列車の台車を選択してください"))
            return
        }
        if (selection.first == getUUID()) {
            COUPLING_MODE[playerId] = CouplingSelection(
                getUUID(),
                selectedBogieIndex,
                null,
                -1,
                level().getGameTime()
            )
            player.sendOverlayMessage(Component.literal("連結モード: 端點已更新，請選擇另一輛列車"))
            return
        }
        COUPLING_MODE.put(
            playerId,
            CouplingSelection(
                selection.first,
                selection.firstBogieIndex,
                getUUID(),
                selectedBogieIndex,
                level().getGameTime()
            )
        )
        player.sendOverlayMessage(Component.literal("連結モード: 2両をゆっくり接触させてください"))
    }

    private fun findNearestBogieIndex(worldPosition: Vec3): Int {
        val bogies = getInteractionBogieCenters(getById(vehicleId))
        if (bogies.isEmpty()) {
            return 0
        }
        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        for (index in bogies.indices) {
            val distance = localToWorld(bogies[index]).distanceToSqr(worldPosition)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        return nearestIndex
    }

    private fun tryCompletePendingCoupling() {
        if (level() !is ServerLevel || COUPLING_MODE.isEmpty()) {
            return
        }
        val pending: MutableMap<UUID?, CouplingSelection?> = HashMap<UUID?, CouplingSelection?>(COUPLING_MODE)
        for (entry in pending.entries) {
            val selection = entry.value
            if (selection == null || !selection.isComplete) {
                continue
            }
            if (level().getGameTime() - selection.armedAt < 6L) {
                continue
            }
            val sourceRaw: Entity? = serverLevel.getEntity(selection.first!!)
            val targetRaw: Entity? = serverLevel.getEntity(selection.second!!)
            if (sourceRaw !is TrainEntity || !sourceRaw.isAlive()) {
                COUPLING_MODE.remove(entry.key)
                continue
            }
            if (targetRaw !is TrainEntity || !targetRaw.isAlive()) {
                COUPLING_MODE.remove(entry.key)
                continue
            }
            if (sourceRaw === targetRaw || sourceRaw.isConnectedTo(targetRaw)) {
                // 既に連結済み（別経路で接触連結された等）なら、残った連結モードを破棄する。
                COUPLING_MODE.remove(entry.key)
                continue
            }
            if (sourceRaw.canCompleteCouplingWith(targetRaw)) {
                val player: Player? = entry.key?.let { serverLevel.getPlayerByUUID(it) }
                if (sourceRaw.tryImmediateSelectedBogieCoupling(
                        player,
                        selection.first,
                        selection.firstBogieIndex,
                        targetRaw,
                        selection.secondBogieIndex
                    )
                ) {
                    COUPLING_MODE.remove(entry.key)
                    if (player != null) {
                        player.sendOverlayMessage(Component.literal("連結しました"))
                    }
                }
            }
        }
    }

    fun isConnectedTo(other: TrainEntity?): Boolean {
        if (other == null) {
            return false
        }
        return other.getUUID() == coupledFollowerUuid
                || other.getUUID() == coupledLeaderUuid
                || this.getUUID() == other.coupledFollowerUuid
                || this.getUUID() == other.coupledLeaderUuid
    }

    fun decouple() {
        if (level() is ServerLevel) {
            // Invalidate the shared Formation so all members rebuild independently on next tick
            if (formation != null) {
                formation!!.trainStream().forEach { t: TrainEntity? -> if (t != null) t.formation = null }
            }
            formation = null
            if (coupledFollowerUuid != null) {
                val followerRaw: Entity? = serverLevel.getEntity(coupledFollowerUuid!!)
                if (followerRaw is TrainEntity) {
                    followerRaw.setCoupledLeaderUuid(null)
                }
            }
            if (coupledLeaderUuid != null) {
                val leaderRaw: Entity? = serverLevel.getEntity(coupledLeaderUuid!!)
                if (leaderRaw is TrainEntity && this.getUUID() == leaderRaw.coupledFollowerUuid) {
                    leaderRaw.setCoupledFollowerUuid(null)
                    leaderRaw.coupledFollowerThisSide = -1
                    leaderRaw.coupledFollowerOtherSide = 1
                }
            }
        }
        setCoupledFollowerUuid(null)
        setCoupledLeaderUuid(null)
        coupledFollowerThisSide = -1
        coupledFollowerOtherSide = 1
    }

    val isConnected: Boolean
        get() = coupledFollowerUuid != null || coupledLeaderUuid != null

    val connectedTrain: TrainEntity?
        get() {
            if (level() !is ServerLevel) {
                return null
            }
            if (coupledFollowerUuid != null) {
                val entity: Entity? = serverLevel.getEntity(coupledFollowerUuid!!)
                if (entity is TrainEntity) return entity
            }
            if (coupledLeaderUuid != null) {
                val entity: Entity? = serverLevel.getEntity(coupledLeaderUuid!!)
                if (entity is TrainEntity) return entity
            }
            return null
        }

    private fun getAnchorForRenderedBogie(bogieIndex: Int): RailAnchor? {
        val def = getById(
            this.vehicleId
        )
        if (def == null || def.getBogies().isEmpty()) {
            return null
        }
        return getAnchorForBogieIndex(def, bogieIndex)
    }

    // ======== Coupling system (RTM-faithful port) ========
    private fun coupleFormationsRtMLike(
        sourceTrain: TrainEntity?,
        sourceSide: Int,
        targetTrain: TrainEntity?,
        targetSide: Int
    ): Boolean {
        if (sourceTrain == null || targetTrain == null || sourceTrain === targetTrain) return false
        if (level() !is ServerLevel) return false
        if (sourceTrain.isConnectedTo(targetTrain) || targetTrain.hasIndirectPassenger(sourceTrain)) return false
        val source = prepareCouplingEndpoint(sourceTrain, normalizeCouplerSide(sourceSide), true)
        val target = prepareCouplingEndpoint(targetTrain, normalizeCouplerSide(targetSide), false)
        if (source == null || target == null) return false
        if (source.train == null || target.train == null || source.train === target.train) return false
        if (source.train.coupledFollowerUuid != null || target.train.coupledLeaderUuid != null) return false
        if (!source.train.canCompleteCouplingWith(target.train, source.side, target.side)) return false
        source.train.linkCouplingBySelection(target.train, source.side, target.side)
        stabilizeCoupledFormations(source.train, target.train)
        return true
    }

    private fun linkCouplingBySelection(other: TrainEntity?, thisSide: Int, otherSide: Int) {
        if (other == null || other === this || isConnectedTo(other)) return
        setCoupledFollowerUuid(other.getUUID())
        coupledFollowerThisSide = thisSide
        coupledFollowerOtherSide = otherSide
        other.setCoupledLeaderUuid(getUUID())
    }

    private fun prepareCouplingEndpoint(
        selectedTrain: TrainEntity?,
        selectedSide: Int,
        sourceRole: Boolean
    ): CouplingEndpoint? {
        if (selectedTrain == null) return null
        val chain =
            selectedTrain.formationTrainsInOrder
        if (chain.isEmpty()) return null
        val head = chain.get(0)
        val tail = chain.get(chain.size - 1)
        if (chain.size == 1) return CouplingEndpoint(selectedTrain, selectedSide)
        val headSide = getExposedHeadSide(head)
        val tailSide = getExposedTailSide(tail)
        if (selectedTrain === head && headSide == selectedSide) return CouplingEndpoint(head, selectedSide)
        if (selectedTrain === tail && tailSide == selectedSide) return CouplingEndpoint(tail, selectedSide)
        return null
    }

    private fun stabilizeCoupledFormations(sourceTail: TrainEntity, targetHead: TrainEntity) {
        val formationHead = sourceTail.formationHead
        formationHead.forEachFormationTrain(Consumer { t: TrainEntity? ->
            t!!.speed = 0.0f
            t.notch = 0
            t.setDeltaMovement(Vec3.ZERO)
        })
        targetHead.forEachFormationTrain(Consumer { t: TrainEntity? ->
            t!!.speed = 0.0f
            t.notch = 0
            t.setDeltaMovement(Vec3.ZERO)
        })
        formationHead.setNotchForFormation(0)
        formationHead.setReverserForFormation(sourceTail.reverser)
        formationHead.setLightModeForFormation(sourceTail.lightMode)
        formationHead.setDestinationIndexForFormation(sourceTail.destinationIndex)
        val formationDriver =
            formationHead.formationDriver
        if (formationDriver != null) {
            formationHead.markDriverControl(formationDriver.controller)
            formationHead.ensureDriverReadyForFormation(formationDriver.controller)
        }
        // 本家RTM(Formation.connectTrain)準拠: 連結時に本体をテレポート・スナップさせない。
        // 連結はトレインが接触している時にしか成立しない(canCompleteCouplingWith)ので、
        // 既に隣接している。編成を結合してブレーキで止めるだけにし、位置は毎tickの追従で
        // 自然に整える。以前の placeCoupledFollower* による即時スナップ+updateTrainMovement は
        // 3両目連結時に中間車のレール状態が未確立のままフォールバック moveTo され、他車へ
        // テレポート・重なりする不具合の原因だった。
        sourceTail.stopFormationMotionForResync(8L)
        targetHead.stopFormationMotionForResync(8L)
        // Rebuild Formation for the newly combined UUID chain
        sourceTail.formationHead.rebuildFormationFromUuidChain()
        val newHead = sourceTail.formationHead
        if (newHead != null) {
            newHead.setNotchForFormation(0)
        }
        // 連結直後に「新しく連結した車両(targetHead)だけ」を連結位置へ静かに寄せる。
        // 接触連結なので元々隣接しており移動量は小さい(数ブロック以内)＝隙間が即座に詰まる。
        // 大ジャンプ(レール状態未確立で他車上へ飛ぶケース)は棄却して元位置を維持し、
        // 他車には一切触らないので重なり・TPは起きない。
        if (sourceTail.coupledFollowerUuid != null && targetHead != null) {
            val pX = targetHead.getX()
            val pY = targetHead.getY()
            val pZ = targetHead.getZ()
            val ok = sourceTail.placeCoupledFollowerOnRail(
                targetHead,
                sourceTail.coupledFollowerThisSide, sourceTail.coupledFollowerOtherSide
            )
            val jx = targetHead.getX() - pX
            val jz = targetHead.getZ() - pZ
            val maxJump = 6.0 // 接触連結は約3m以内なので、隙間詰めは小移動。これを超えたらテレポート扱い。
            if (!ok || jx * jx + jz * jz > maxJump * maxJump) {
                targetHead.setPos(pX, pY, pZ)
                targetHead.setRot(targetHead.getYRot(), targetHead.getXRot())
            } else {
                targetHead.settleCoupledRailPose()
            }
        }
    }

    private fun getCoupledFollowerDistanceErrorMeters(
        follower: TrainEntity?,
        thisSide: Int,
        followerSide: Int
    ): Double {
        if (follower == null) return Double.MAX_VALUE
        val expected = getDefaultDistanceToConnectedTrain(follower)
        val actual = sqrt(getCouplerDistanceSqr(follower, thisSide, followerSide))
        return abs(actual - expected)
    }

    private fun followCoupledTrainRtMLike(leader: TrainEntity?, follower: TrainEntity?): Boolean {
        if (leader == null || follower == null) return false
        val snapped =
            leader.placeCoupledFollowerOnRail(follower, leader.coupledFollowerThisSide, leader.coupledFollowerOtherSide)
                    || leader.trySoftPlaceCoupledFollowerOnRail(
                follower,
                leader.coupledFollowerThisSide,
                leader.coupledFollowerOtherSide,
                true
            )
        follower.speed = leader.speed
        follower.notch = leader.notch
        follower.reverser = leader.reverser
        if (snapped) {
            follower.centerGuidanceFallbackTicks = 0
            follower.railGuidanceFailureTicks = 0
            follower.travelStallTicks = 0
            follower.settleCoupledRailPose()
        } else {
            leader.placeCoupledFollowerFallback(
                follower,
                leader.coupledFollowerThisSide,
                leader.coupledFollowerOtherSide,
                leader.getDefaultDistanceToConnectedTrain(follower)
            )
            follower.centerGuidanceFallbackTicks = max(follower.centerGuidanceFallbackTicks, 16)
            follower.railGuidanceFailureTicks = 0
            follower.travelStallTicks = 0
            follower.clearRailGuidance()
            follower.setDeltaMovement(Vec3.ZERO)
            follower.settleCoupledRailPose()
        }
        return true
    }

    private fun trySoftPlaceCoupledFollowerOnRail(
        follower: TrainEntity?,
        thisSide: Int,
        followerSide: Int,
        curveSensitive: Boolean
    ): Boolean {
        if (follower == null) return false
        val prevRailMap = follower.activeRailMap
        val prevRailSplit = follower.activeRailSplit
        val prevRailIndex = follower.activeRailIndex
        val prevRailPosition = follower.activeRailPosition
        val prevRailDirection = follower.activeRailDirection
        val prevBodyDirection = follower.activeRailBodyDirection
        val prevFrontAnchor = follower.frontRailAnchor
        val prevRearAnchor = follower.rearRailAnchor
        val prevPos = follower.position()
        if (!placeCoupledFollowerOnRail(follower, thisSide, followerSide)) return false
        val maxJump = if (curveSensitive) max(1.4, abs(this.speed) * 0.42 + 0.8) else max(
            0.95, abs(
                this.speed
            ) * 0.34 + 0.5
        )
        val jumpSq = follower.position().distanceToSqr(prevPos)
        val maxDistanceError = if (curveSensitive) 5.2 else 3.75
        if (jumpSq <= maxJump * maxJump && isCoupledFollowerGeometryStable(
                follower,
                thisSide,
                followerSide,
                maxDistanceError
            )
        ) {
            return true
        }
        follower.restoreRailState(
            prevRailMap,
            prevRailSplit,
            prevRailIndex,
            prevRailPosition,
            prevRailDirection,
            prevBodyDirection,
            prevFrontAnchor,
            prevRearAnchor
        )
        if (isRailAnchorUsable(prevFrontAnchor) && isRailAnchorUsable(prevRearAnchor)) {
            follower.settleCoupledRailPose()
        } else {
            follower.setPos(prevPos.x, prevPos.y, prevPos.z)
            follower.setRot(follower.getYRot(), follower.getXRot())
        }
        follower.setDeltaMovement(Vec3.ZERO)
        return false
    }

    private fun isCoupledFollowerGeometryStable(
        follower: TrainEntity?,
        thisSide: Int,
        followerSide: Int,
        maxDistanceError: Double
    ): Boolean {
        if (follower == null) return false
        val leaderSide: Int = normalizeCouplerSide(thisSide)
        val otherSide: Int = normalizeCouplerSide(followerSide)
        val anchorGap = getCoupledAnchorGapMeters(leaderSide, follower, otherSide)
        val distance = sqrt(getCouplerDistanceSqr(follower, thisSide, followerSide))
        return distance <= anchorGap + maxDistanceError
    }

    private fun getCoupledAnchorGapMeters(thisSide: Int, follower: TrainEntity?, followerSide: Int): Double {
        if (follower == null) return max(4.0, this.configuredTrainDistance * 2.0)
        val dist = getDefaultDistanceToConnectedTrain(follower)
        val leaderOffset = abs(getRailOffsetForSide(thisSide))
        val followerOffset = abs(follower.getRailOffsetForSide(followerSide))
        return max(0.5, dist - leaderOffset - followerOffset)
    }

    private fun getRailOffsetForSide(side: Int): Double {
        val bogieZ = this.bogieRailOffsets
        return if (side > 0) bogieZ[1] else bogieZ[0]
    }

    private fun getCouplerDistanceSqr(other: TrainEntity?, thisSide: Int, otherSide: Int): Double {
        return if (other == null) Double.MAX_VALUE else
            getCouplerPoint(thisSide > 0).distanceToSqr(other.getCouplerPoint(otherSide > 0))
    }

    private fun forceCoupledFormationResync(follower: TrainEntity?, curveSensitive: Boolean): Boolean {
        if (follower == null) return false
        stopFormationMotionForResync(if (curveSensitive) 10L else 6L)
        follower.stopFormationMotionForResync(if (curveSensitive) 10L else 6L)
        val resynced =
            trackCoupledFollowerFromLeaderRtMLike(follower, coupledFollowerThisSide, coupledFollowerOtherSide)
                    || trySoftPlaceCoupledFollowerOnRail(
                follower,
                coupledFollowerThisSide,
                coupledFollowerOtherSide,
                true
            )
                    || placeCoupledFollowerOnRail(follower, coupledFollowerThisSide, coupledFollowerOtherSide)
        val head = this.formationHead
        head.settleConnectedFormationToRail()
        head.rememberConnectedFormationStableRailState()
        if (resynced) {
            follower.centerGuidanceFallbackTicks = 0
            follower.railGuidanceFailureTicks = 0
            follower.travelStallTicks = 0
            return true
        } else if (follower.restoreLastStableRailState()) {
            follower.settleCoupledRailPose()
            head.settleConnectedFormationToRail()
            head.rememberConnectedFormationStableRailState()
            follower.centerGuidanceFallbackTicks = max(follower.centerGuidanceFallbackTicks, 12)
            follower.railGuidanceFailureTicks = 0
            follower.travelStallTicks = 0
            return true
        } else {
            follower.centerGuidanceFallbackTicks = max(follower.centerGuidanceFallbackTicks, 8)
            return false
        }
    }

    private fun trackCoupledFollowerFromLeaderRtMLike(
        follower: TrainEntity?,
        thisSide: Int,
        followerSide: Int
    ): Boolean {
        if (follower == null) return false
        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)) return false
        val stationary: Boolean = areCoupledTrainsEffectivelyStationary(this, follower)
        val settleWindow = this.isWithinCouplingSettleWindow || follower.isWithinCouplingSettleWindow
        if (!stationary && !settleWindow && follower.isRailGuided) return false
        return placeCoupledFollowerOnRail(follower, thisSide, followerSide)
    }

    private fun stopFormationMotionForResync(settleTicks: Long) {
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train!!.setDeltaMovement(Vec3.ZERO)
            train.markCouplingSettleWindow(settleTicks)
            train.hurtMarked = true
            train.hurtMarked = true
        })
    }

    private fun applyImmediateContactBrake(holdTicks: Long) {
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            train!!.speed = 0.0f
            train.setDeltaMovement(Vec3.ZERO)
            train.markUncoupledContactStopWindow(holdTicks)
            train.hurtMarked = true
            train.hurtMarked = true
        })
    }

    fun tryCoupleFromBogieContact(thisBogieIndex: Int, otherTrain: TrainEntity?, otherBogieIndex: Int): Boolean {
        if (otherTrain == null || otherTrain === this || level().isClientSide()) return false
        val thisCouplingIndex = getPreferredCouplingBogieIndex(thisBogieIndex)
        val otherCouplingIndex = otherTrain.getPreferredCouplingBogieIndex(otherBogieIndex)
        if (!canResolveBogieContactWith(otherTrain, thisCouplingIndex, otherCouplingIndex)) return false
        // RTMスタイル: アクティベーション不要、接触したら自動連結
        val thisSide = getCouplerSideForBogieIndex(thisCouplingIndex)
        val otherSide = otherTrain.getCouplerSideForBogieIndex(otherCouplingIndex)
        if (isConnectionPresentOnSide(thisSide) || otherTrain.isConnectionPresentOnSide(otherSide)) return false
        // カーブなどで編成先頭車の前端に別の車両が誤接触するのを防ぐ。
        // 先頭車（後方に連結済み）の前端へのボギー接触連結は禁止し、プレイヤー操作連結のみ許可する。
        if (isHeadFrontSideOfMulticarFormation(thisSide) || otherTrain.isHeadFrontSideOfMulticarFormation(otherSide)) return false
        val coupled = coupleFormationsRtMLike(this, thisSide, otherTrain, otherSide)
                || otherTrain.coupleFormationsRtMLike(otherTrain, otherSide, this, thisSide)
        if (coupled) {
            clearCouplingModeInvolving(this, otherTrain)
            notifyCouplingChat(Component.literal("連結しました"), otherTrain, null)
        }
        return coupled
    }

    private fun isHeadFrontSideOfMulticarFormation(side: Int): Boolean {
        if (coupledFollowerUuid == null || coupledLeaderUuid != null) return false
        // この車両は多両編成の先頭車。sideが後方連結側でなければ「前端」と判定する。
        return normalizeCouplerSide(side) != normalizeCouplerSide(coupledFollowerThisSide)
    }

    fun handleBogieContactWithoutCoupling(thisBogieIndex: Int, otherTrain: TrainEntity?, otherBogieIndex: Int) {
        if (otherTrain == null || otherTrain === this || level().isClientSide()) return
        // 連結モード中(プレイヤーがこの2編成を選択して連結しようとしている)は、接触ブレーキを
        // 抑止して接近・連結させる。ブレーキすると速度0/1の振動で連結完了距離まで近づけず、
        // 3両目以降が連結できない不具合になっていた(tryCompletePendingCoupling が完了させる)。
        if (isCouplingModeActiveBetween(this, otherTrain)) {
            return
        }
        // 連結できない（既連結など）場合にめり込み防止のためブレーキ
        if (!this.isWithinUncoupledContactStopWindow && !otherTrain.isWithinUncoupledContactStopWindow) {
            applyImmediateContactBrake(12L)
            otherTrain.applyImmediateContactBrake(12L)
        }
    }

    fun canResolveBogieContactWith(otherTrain: TrainEntity?, thisBogieIndex: Int, otherBogieIndex: Int): Boolean {
        if (otherTrain == null || otherTrain === this) return false
        val thisAnchor = getAnchorForRenderedBogie(thisBogieIndex)
        val otherAnchor = otherTrain.getAnchorForRenderedBogie(otherBogieIndex)
        if (isRailAnchorUsable(thisAnchor) && otherTrain.isRailAnchorUsable(otherAnchor)) {
            val thisMap = thisAnchor!!.map
            val otherMap = otherAnchor!!.map
            if (thisMap === otherMap || sameRailShape(thisMap, otherMap) || railsShareEndpoint(thisMap, otherMap)) {
                return true
            }
        }
        val thisSide = getCouplerSideForBogieIndex(thisBogieIndex)
        val otherSide = otherTrain.getCouplerSideForBogieIndex(otherBogieIndex)
        val distance = getDefaultDistanceToConnectedTrain(otherTrain)
        val allowed = max(distance + 1.5, distance * 1.12)
        return getCouplerDistanceSqr(otherTrain, thisSide, otherSide) <= allowed * allowed
    }

    fun isCouplingApproachCloseEnough(other: TrainEntity?, thisBogieIndex: Int, otherBogieIndex: Int): Boolean {
        if (other == null || other === this) return false
        val thisCouplingIndex = getPreferredCouplingBogieIndex(thisBogieIndex)
        val otherCouplingIndex = other.getPreferredCouplingBogieIndex(otherBogieIndex)
        val thisSide = getCouplerSideForBogieIndex(thisCouplingIndex)
        val otherSide = other.getCouplerSideForBogieIndex(otherCouplingIndex)
        // 連結器端点間が3m以内のときのみ接近と判定（ブレーキ用途）
        return getCouplerDistanceSqr(other, thisSide, otherSide) <= 3.0 * 3.0
    }

    private fun scanNearbyCouplerContacts() {
        if (level() !is ServerLevel) {
            return
        }
        val thisHead = this.formationHead
        val searchBox = getBoundingBox().inflate(this.couplerContactScanRadius)
        for (other in serverLevel.getEntitiesOfClass<TrainEntity>(TrainEntity::class.java, searchBox)) {
            if (other === this || !other.isAlive() || other.isRemoved() || isConnectedTo(other)) {
                continue
            }
            val otherHead: TrainEntity? = other.formationHead
            if (otherHead == null || otherHead === thisHead || isConnectedTo(otherHead)) {
                continue
            }
            val request = findNearestExposedCouplerPair(otherHead)
            if (request == null || request.sourceTrain == null || request.targetTrain == null) {
                continue
            }
            if (request.sourceTrain.getSweptCouplerDistanceSqr(
                    request.targetTrain,
                    request.sourceSide,
                    request.targetSide
                ) > COUPLER_CONTACT_DISTANCE * COUPLER_CONTACT_DISTANCE
            ) {
                continue
            }
            if (!areCouplersFacing(request.sourceTrain, request.sourceSide, request.targetTrain, request.targetSide)
                || !areCouplerRailsCompatible(
                    request.sourceTrain,
                    request.sourceSide,
                    request.targetTrain,
                    request.targetSide
                )
            ) {
                continue
            }
            val coupled = coupleFormationsRtMLike(
                request.sourceTrain,
                request.sourceSide,
                request.targetTrain,
                request.targetSide
            )
                    || coupleFormationsRtMLike(
                request.targetTrain,
                request.targetSide,
                request.sourceTrain,
                request.sourceSide
            )
            if (coupled) {
                request.sourceTrain.clearBogieActivation()
                request.targetTrain.clearBogieActivation()
                clearCouplingModeInvolving(request.sourceTrain, request.targetTrain)
                notifyCouplingChat(Component.literal("連結しました"), request.targetTrain, null)
                return
            }
            if (!isCouplingModeActiveBetween(request.sourceTrain, request.targetTrain)) {
                request.sourceTrain.applyImmediateContactBrake(12L)
                request.targetTrain.applyImmediateContactBrake(12L)
            }
        }
    }

    private fun areCouplersFacing(
        source: TrainEntity?,
        sourceSide: Int,
        target: TrainEntity?,
        targetSide: Int
    ): Boolean {
        if (source == null || target == null) {
            return false
        }
        val sourceForward = source.localToWorld(Vec3(0.0, 0.0, 1.0))
            .subtract(source.localToWorld(Vec3.ZERO))
        val targetForward = target.localToWorld(Vec3(0.0, 0.0, 1.0))
            .subtract(target.localToWorld(Vec3.ZERO))
        if (sourceForward.lengthSqr() < 1.0E-6 || targetForward.lengthSqr() < 1.0E-6) {
            return false
        }
        val sourceOut = sourceForward.normalize().scale(normalizeCouplerSide(sourceSide).toDouble())
        val targetOut = targetForward.normalize().scale(normalizeCouplerSide(targetSide).toDouble())
        return sourceOut.dot(targetOut) <= -0.65
    }

    private val couplerContactScanRadius: Double
        get() {
            var radius = max(
                12.0,
                getDefaultDistanceToConnectedTrain(null) + COUPLER_CONTACT_SCAN_MARGIN
            )
            for (train in this.formationTrainsInOrder) {
                if (train != null) {
                    radius = max(
                        radius,
                        train.getDefaultDistanceToConnectedTrain(null) + COUPLER_CONTACT_SCAN_MARGIN
                    )
                }
            }
            return radius
        }

    private fun areCouplerRailsCompatible(
        source: TrainEntity?,
        sourceSide: Int,
        target: TrainEntity?,
        targetSide: Int
    ): Boolean {
        if (source == null || target == null) {
            return false
        }
        val sourceAnchor = source.getAnchorForRenderedBogie(source.getExtremeBogieIndexForCouplerSide(sourceSide))
        val targetAnchor = target.getAnchorForRenderedBogie(target.getExtremeBogieIndexForCouplerSide(targetSide))
        if (source.isRailAnchorUsable(sourceAnchor) && target.isRailAnchorUsable(targetAnchor)) {
            val sourceMap = sourceAnchor!!.map
            val targetMap = targetAnchor!!.map
            return sourceMap === targetMap || sameRailShape(sourceMap, targetMap) || railsShareEndpoint(
                sourceMap,
                targetMap
            )
        }
        return true
    }

    private fun getExtremeBogieIndexForCouplerSide(side: Int): Int {
        val def = getById(
            this.vehicleId
        )
        val extremes = getExtremeBogieIndices(def)
        return if (normalizeCouplerSide(side) > 0) extremes[1] else extremes[0]
    }

    private fun decoupleAtSide(side: Int) {
        val normalized: Int = normalizeCouplerSide(side)
        if (level() !is ServerLevel) return
        if (coupledFollowerUuid != null && normalizeCouplerSide(coupledFollowerThisSide) == normalized) {
            decouple()
        } else if (this.couplerSideConnectedToLeader == normalized && coupledLeaderUuid != null) {
            val leaderRaw: Entity? = serverLevel.getEntity(coupledLeaderUuid!!)
            if (leaderRaw is TrainEntity) {
                leaderRaw.decouple()
            }
        }
    }

    private fun isConnectionPresentOnSide(side: Int): Boolean {
        val normalized: Int = normalizeCouplerSide(side)
        if (coupledFollowerUuid != null && normalizeCouplerSide(coupledFollowerThisSide) == normalized) return true
        return this.couplerSideConnectedToLeader == normalized
    }

    private val couplerSideConnectedToLeader: Int
        get() {
            if (coupledLeaderUuid == null) return 0
            val leader = resolveCoupledTrain(coupledLeaderUuid)
            return if (leader != null && getUUID() == leader.coupledFollowerUuid)
                normalizeCouplerSide(leader.coupledFollowerOtherSide)
            else
                0
        }

    private fun getCouplerSideForBogieIndex(bogieIndex: Int): Int {
        val bogiePos = getBogieWorldPosition(bogieIndex)
        val forward = localToWorld(Vec3(0.0, 0.0, 1.0)).subtract(position())
        if (bogiePos != null && forward.lengthSqr() > 1e-6) {
            val relative = bogiePos.subtract(position())
            val projection = relative.dot(forward.normalize())
            if (abs(projection) > 0.1) return if (projection > 0.0) 1 else -1
        }
        val def = getById(
            this.vehicleId
        )
        if (def != null && bogieIndex >= 0 && bogieIndex < def.getBogies().size) {
            return if (def.getBogies().get(bogieIndex).position().z >= 0.0) 1 else -1
        }
        return if (resolveExtremeSideForBogieIndex(bogieIndex) == 1) 1 else -1
    }

    private fun getBogieHitbox(bogieIndex: Int): TrainBogieEntity? {
        return resolveBogieHitbox(Mth.clamp(bogieIndex, 0, 1))
    }

    fun setBogieActivated(bogieIndex: Int, activated: Boolean) {
        val bogie = getBogieHitbox(bogieIndex)
        if (bogie != null) bogie.setActivated(activated)
    }

    fun isBogieActivated(bogieIndex: Int): Boolean {
        val bogie = getBogieHitbox(bogieIndex)
        return bogie != null && bogie.isActivated()
    }

    private fun hasAnyBogieActivated(): Boolean {
        return isBogieActivated(0) || isBogieActivated(1)
    }

    private val activatedBogieIndex: Int
        get() {
            if (isBogieActivated(0)) return 0
            return if (isBogieActivated(1)) 1 else -1
        }

    private fun getPreferredCouplingBogieIndex(contactBogieIndex: Int): Int {
        val activated = this.activatedBogieIndex
        return if (activated >= 0) activated else Mth.clamp(contactBogieIndex, 0, 1)
    }

    private fun clearBogieActivation() {
        for (i in 0..1) setBogieActivated(i, false)
    }

    private fun getSelectedBogieWorldPosition(bogieIndex: Int): Vec3? {
        val bogie = resolveBogieHitbox(Mth.clamp(bogieIndex, 0, 1))
        return if (bogie != null) bogie.position() else getBogieWorldPosition(bogieIndex)
    }

    private fun getCouplerSideForSelectedBogieAgainst(bogieIndex: Int, other: TrainEntity?, otherBogieIndex: Int): Int {
        val selected = getSelectedBogieWorldPosition(bogieIndex)
        if (selected != null) {
            val frontDist = selected.distanceToSqr(getCouplerPoint(true))
            val rearDist = selected.distanceToSqr(getCouplerPoint(false))
            if (abs(frontDist - rearDist) > 0.01) return if (frontDist <= rearDist) 1 else -1
        }
        if (other != null) {
            val otherSelected = other.getSelectedBogieWorldPosition(otherBogieIndex)
            if (selected != null && otherSelected != null) {
                val frontDist = getCouplerPoint(true).distanceToSqr(otherSelected)
                val rearDist = getCouplerPoint(false).distanceToSqr(otherSelected)
                if (abs(frontDist - rearDist) > 0.01) return if (frontDist <= rearDist) 1 else -1
            }
        }
        return getCouplerSideForBogieIndex(bogieIndex)
    }

    private fun areSelectedBogiesTouching(other: TrainEntity?, thisBogieIndex: Int, otherBogieIndex: Int): Boolean {
        if (other == null) return false
        val thisBogie = resolveBogieHitbox(Mth.clamp(thisBogieIndex, 0, 1))
        val otherBogie = other.resolveBogieHitbox(Mth.clamp(otherBogieIndex, 0, 1))
        if (thisBogie != null && otherBogie != null) {
            if (thisBogie.getBoundingBox().inflate(0.45, 0.2, 0.45)
                    .intersects(otherBogie.getBoundingBox().inflate(0.45, 0.2, 0.45))
            ) return true
            if (thisBogie.position().distanceToSqr(otherBogie.position()) <= 6.25) return true
        }
        val thisSide = getCouplerSideForBogieIndex(thisBogieIndex)
        val otherSide = other.getCouplerSideForBogieIndex(otherBogieIndex)
        return getCouplerDistanceSqr(other, thisSide, otherSide) <= 6.25
    }

    private fun canCompleteCouplingWith(other: TrainEntity?, thisSide: Int, otherSide: Int): Boolean {
        if (other == null || other === this || isConnectedTo(other)) return false
        return getSweptCouplerDistanceSqr(other, thisSide, otherSide) <=
            COUPLER_CONTACT_DISTANCE * COUPLER_CONTACT_DISTANCE
    }

    private fun getSweptCouplerDistanceSqr(other: TrainEntity, thisSide: Int, otherSide: Int): Double {
        val previous = getPreviousCouplerPoint(thisSide > 0)
            .subtract(other.getPreviousCouplerPoint(otherSide > 0))
        val current = getCouplerPoint(thisSide > 0).subtract(other.getCouplerPoint(otherSide > 0))
        val movement = current.subtract(previous)
        val movementLengthSqr = movement.lengthSqr()
        if (movementLengthSqr <= 1.0E-8) {
            return current.lengthSqr()
        }
        val progress = Mth.clamp(-previous.dot(movement) / movementLengthSqr, 0.0, 1.0)
        return previous.add(movement.scale(progress)).lengthSqr()
    }

    private fun getExposedHeadSide(headTrain: TrainEntity?): Int {
        if (headTrain == null) return 0
        return if (headTrain.coupledFollowerUuid == null) 0 else -normalizeCouplerSide(headTrain.coupledFollowerThisSide)
    }

    private fun getExposedTailSide(tailTrain: TrainEntity?): Int {
        if (tailTrain == null) return 0
        val leaderSide = tailTrain.couplerSideConnectedToLeader
        return if (leaderSide == 0) 0 else -leaderSide
    }

    private fun findNearestExposedCouplerPair(other: TrainEntity?): CouplingRequest? {
        if (other == null) return null
        val first =
            this.exposedCouplerCandidates
        val second =
            other.exposedCouplerCandidates
        var best: CouplingRequest? = null
        for (a in first) {
            val aPoint = a.train!!.getCouplerPoint(a.side > 0)
            for (b in second) {
                val d = aPoint.distanceToSqr(b.train!!.getCouplerPoint(b.side > 0))
                if (best == null || d < best.distanceSqr) {
                    best = CouplingRequest(a.train, a.side, b.train, b.side, d)
                }
            }
        }
        return best
    }

    private val exposedCouplerCandidates: MutableList<CouplerCandidate>
        get() {
            val chain =
                this.formationTrainsInOrder
            if (chain.isEmpty()) return mutableListOf<CouplerCandidate>()
            if (chain.size == 1) {
                return mutableListOf(
                    CouplerCandidate(
                        chain.get(0),
                        -1
                    ), CouplerCandidate(chain.get(0), 1)
                )
            }
            val head = chain.get(0)
            val tail = chain.get(chain.size - 1)
            val result: MutableList<CouplerCandidate> =
                ArrayList<CouplerCandidate>(2)
            val headSide = getExposedHeadSide(head)
            val tailSide = getExposedTailSide(tail)
            if (headSide != 0) result.add(
                CouplerCandidate(
                    head,
                    headSide
                )
            )
            if (tailSide != 0) result.add(
                CouplerCandidate(
                    tail,
                    tailSide
                )
            )
            return result
        }

    private fun tryImmediateActivatedBogieCoupling(player: Player?, bogieIndex: Int): Boolean {
        if (level() == null || level().isClientSide()) return false
        val searchBox = getBoundingBox().inflate(max(6.0, getDefaultDistanceToConnectedTrain(null)))
        for (other in level().getEntitiesOfClass<TrainEntity>(TrainEntity::class.java, searchBox)) {
            if (other === this || !other!!.isAlive()) continue
            val otherBogieIndex = other.activatedBogieIndex
            if (otherBogieIndex < 0) continue
            val thisSide = getCouplerSideForSelectedBogieAgainst(bogieIndex, other, otherBogieIndex)
            val otherSide = other.getCouplerSideForSelectedBogieAgainst(otherBogieIndex, this, bogieIndex)
            val allowed = max(3.25, getDefaultDistanceToConnectedTrain(other) + 0.75)
            if (getCouplerDistanceSqr(other, thisSide, otherSide) > allowed * allowed) continue
            if (coupleFormationsRtMLike(this, thisSide, other, otherSide) || coupleFormationsRtMLike(
                    other,
                    otherSide,
                    this,
                    thisSide
                )
            ) {
                clearBogieActivation()
                other.clearBogieActivation()
                notifyCouplingChat(Component.literal("連結しました"), other, player)
                return true
            }
        }
        return false
    }

    private fun tryImmediateSelectedBogieCoupling(
        player: Player?,
        firstTrainUuid: UUID?,
        firstBogieIndex: Int,
        secondTrain: TrainEntity?,
        secondBogieIndex: Int
    ): Boolean {
        if (level() !is ServerLevel) return false
        if (firstTrainUuid == null || secondTrain == null) return false
        val sourceRaw: Entity? = serverLevel.getEntity(firstTrainUuid)
        if ((sourceRaw !is TrainEntity) || !sourceRaw.isAlive() || sourceRaw === secondTrain || sourceRaw.isConnectedTo(
                secondTrain
            )
        ) return false
        val sourceSide = sourceRaw.getCouplerSideForSelectedBogieAgainst(firstBogieIndex, secondTrain, secondBogieIndex)
        val targetSide = secondTrain.getCouplerSideForSelectedBogieAgainst(secondBogieIndex, sourceRaw, firstBogieIndex)
        if (!sourceRaw.coupleFormationsRtMLike(sourceRaw, sourceSide, secondTrain, targetSide)
            && !secondTrain.coupleFormationsRtMLike(secondTrain, targetSide, sourceRaw, sourceSide)
        ) return false
        sourceRaw.clearBogieActivation()
        secondTrain.clearBogieActivation()
        sourceRaw.notifyCouplingChat(Component.literal("連結しました"), secondTrain, player)
        return true
    }

    private fun notifyCouplingChat(message: Component?, otherTrain: TrainEntity?, directPlayer: Player?) {
        if (level().isClientSide() || message == null) return
        val delivered: MutableSet<UUID?> = HashSet<UUID?>()
        if (directPlayer is ServerPlayer) {
            directPlayer.sendSystemMessage(message)
            delivered.add(directPlayer.getUUID())
        }
        notifyCouplingChatToTrain(message, this, delivered)
        notifyCouplingChatToTrain(message, otherTrain, delivered)
    }

    private fun notifyCouplingChatToTrain(message: Component, train: TrainEntity?, delivered: MutableSet<UUID?>) {
        if (train == null) return
        for (passenger in train.getPassengers()) {
            if (passenger is ServerPlayer && delivered.add(passenger.getUUID())) {
                passenger.sendSystemMessage(message)
            }
        }
    }

    fun setNotchForFormation(notch: Int) {
        if (level().isClientSide()) {
            this.notch = notch
        } else {
            val clamped = Mth.clamp(notch, -this.maxBrakeNotch, this.maxPowerNotch)
            forEachFormationTrain(Consumer { train: TrainEntity? ->
                train!!.notch = clamped
            })
        }
    }

    val formationTrainsForDisplay: MutableList<TrainEntity?>
        // ======== End coupling system ========
        get() {
            val result: MutableList<TrainEntity?> =
                ArrayList<TrainEntity?>()
            var head: TrainEntity? = this
            var guard = 0
            while (guard++ < 16) {
                val leader =
                    resolveTrainByUuid(head!!.displayLeaderUuid)
                if (leader == null || leader === head) {
                    break
                }
                head = leader
            }

            var current = head
            guard = 0
            while (current != null && guard++ < 16) {
                result.add(current)
                val follower =
                    resolveTrainByUuid(current.displayFollowerUuid)
                if (follower == null || follower === current) {
                    break
                }
                current = follower
            }
            if (result.isEmpty()) {
                result.add(this)
            }
            return result
        }

    private val displayFollowerUuid: UUID?
        get() = if (coupledFollowerUuid != null) coupledFollowerUuid else parseUuid(
            entityData.get<String>(COUPLED_FOLLOWER)
        )

    private val displayLeaderUuid: UUID?
        get() = if (coupledLeaderUuid != null) coupledLeaderUuid else parseUuid(
            entityData.get<String>(COUPLED_LEADER)
        )

    private fun resolveTrainByUuid(uuid: UUID?): TrainEntity? {
        if (uuid == null) {
            return null
        }
        if (level() is ServerLevel) {
            val entity: Entity? = serverLevel.getEntity(uuid)
            return if (entity is TrainEntity) entity else null
        }
        for (train in level().getEntitiesOfClass<TrainEntity>(
            TrainEntity::class.java,
            getBoundingBox().inflate(256.0)
        )) {
            if (uuid == train!!.getUUID()) {
                return train
            }
        }
        return null
    }

    private fun getBogieLocalPosition(bogieIndex: Int, def: VehicleDefinition?): Vec3 {
        if (def != null && !def.getBogies().isEmpty()) {
            val clamped = Mth.clamp(bogieIndex, 0, def.getBogies().size - 1)
            return def.getBogies().get(clamped).position()
        }
        val bogieZ = this.bogieRailOffsets
        val clamped = Mth.clamp(bogieIndex, 0, 1)
        return Vec3(0.0, 0.0, bogieZ[clamped])
    }

    private fun getInteractionBogieCenters(def: VehicleDefinition?): MutableList<Vec3> {
        if (def != null && !def.getBogies().isEmpty()) {
            val centers: MutableList<Vec3> = ArrayList<Vec3>(def.getBogies().size)
            for (bogie in def.getBogies()) {
                centers.add(bogie.position())
            }
            return centers
        }
        val bogieZ = this.bogieRailOffsets
        val fallback: MutableList<Vec3> = ArrayList<Vec3>(2)
        fallback.add(Vec3(0.0, 0.0, bogieZ[0]))
        fallback.add(Vec3(0.0, 0.0, bogieZ[1]))
        return fallback
    }

    private val driverPassenger: Entity?
        get() {
            syncSeatAssignmentsFromEntityData()
            for (entry in seatAssignments.entries) {
                if (isDriverSeatIndex(entry.value!!)) {
                    val passenger = findAssignedPassenger(entry.key)
                    if (passenger != null) {
                        return passenger
                    }
                }
            }
            return null
        }

    private fun getCabDirectionSign(controller: Entity?): Float {
        return getCabDirectionSign(controller, this)
    }

    private fun getCabDirectionSign(controller: Entity?, cabTrain: TrainEntity?): Float {
        val reverser = this.reverser
        if (reverser == 0) {
            return 0.0f
        }
        val source = if (cabTrain == null) this else cabTrain
        return (source.getDriverCabDirection(controller) * reverser).toFloat()
    }

    private fun getDriverCabDirection(controller: Entity?): Int {
        val def = getById(
            this.vehicleId
        )
        if (controller != null && def != null) {
            val seatOffset = getAssignedSeatOffset(controller)
            if (abs(seatOffset.z) > 1.0E-4) {
                return if (seatOffset.z < 0.0) -1 else 1
            }
            val assignedSeat = getAssignedSeatIndex(controller)
            if (assignedSeat == resolveRearSeatIndex(def)) {
                return -1
            }
            if (assignedSeat == resolveFrontSeatIndex(def)) {
                return 1
            }
            val seats = getSelectableSeats(def)
            if (assignedSeat >= 0 && assignedSeat < seats.size) {
                return if (seats.get(assignedSeat).z < 0.0) -1 else 1
            }
        }
        return 1
    }

    fun isDriverSeatIndex(seatIndex: Int): Boolean {
        val def = getById(
            this.vehicleId
        )
        if (def == null || seatIndex < 0) {
            return false
        }
        if (def.isDriverSeatIndex(seatIndex)) {
            return true
        }
        return seatIndex == resolveFrontSeatIndex(def) || seatIndex == resolveRearSeatIndex(def)
    }

    fun isDriverPassenger(passenger: Entity?): Boolean {
        if (passenger == null) {
            return false
        }
        val assignedSeat = getAssignedSeatIndex(passenger)
        return isDriverSeatIndex(assignedSeat)
    }

    fun isLikelyDriverPassenger(passenger: Entity?): Boolean {
        if (passenger == null) {
            return false
        }
        val assignedSeat = getAssignedSeatIndex(passenger)
        if (isDriverSeatIndex(assignedSeat)) {
            return true
        }
        if (level().isClientSide()) {
            return isDriverSeatIndex(findNearestSeatIndex(passenger))
        }
        return false
    }

    fun markDriverControl(passenger: Entity?) {
        if (passenger == null) {
            return
        }
        this.activeDriverUuid = passenger.getUUID()
        this.activeDriverTicks = 40
        refreshFormationDirectionFromActiveDriver()
    }

    private fun refreshFormationDirectionFromActiveDriver() {
        val currentFormation = formation ?: return
        val entries = currentFormation.entries
        if (entries.size <= 1) {
            currentFormation.setDirection(0)
            return
        }
        for (index in entries.indices) {
            val train = entries[index]?.train ?: continue
            if (train.activeDriverUuid != null && train.activeDriverTicks > 0) {
                currentFormation.setDirection(if (index * 2 >= entries.lastIndex) 1 else 0)
                return
            }
        }
    }

    fun ensureDriverReady(passenger: Entity?) {
        if (passenger == null) {
            return
        }
        if (this.reverser != 0) {
            return
        }
        val def = getById(
            this.vehicleId
        )
        val seatIndex = getAssignedSeatIndex(passenger)
        if (seatIndex >= 0) {
            this.reverser = getDefaultReverserForSeat(def, seatIndex)
            return
        }
        this.reverser = 1
    }

    fun applyThrottle(throttle: Float) {
        val speed: Float = Mth.clamp(this.speed + throttle * ACCEL, -MAX_SPEED, MAX_SPEED)
        this.speed = speed
    }

    fun stepMascon(delta: Int) {
        if (delta > 0) {
            this.notch = min(this.maxPowerNotch, this.notch + delta)
        } else if (delta < 0) {
            this.notch = max(-this.maxBrakeNotch, this.notch + delta)
        }
    }

    fun forceDiscardTrain() {
        if (level().isClientSide()) {
            return
        }
        if (level() is ServerLevel) {
            val cleanupBox = getBoundingBox().inflate(8.0, 6.0, 8.0)
            for (train in serverLevel.getEntitiesOfClass<TrainEntity>(
                TrainEntity::class.java,
                cleanupBox,
                Predicate { other: TrainEntity? -> other != null && (other === this || other.getUUID() == this.getUUID()) })) {
                train.ejectPassengers()
                train.discardBogieHitboxes()
                train.discardSeatHitboxes()
                train.seatAssignments.clear()
                train.syncSeatAssignmentsToEntityData()
                train.speed = 0.0f
                train.notch = 0
                train.activeRailMap = null
                train.frontRailAnchor = null
                train.rearRailAnchor = null
                train.activeRailIndex = -1
                train.activeRailPosition = -1.0
                train.discard()
            }
            purgeDanglingTrainResidue(serverLevel, cleanupBox)
        }
        ejectPassengers()
        decouple()
        discardBogieHitboxes()
        discardSeatHitboxes()
        seatAssignments.clear()
        syncSeatAssignmentsToEntityData()
        this.speed = 0.0f
        this.notch = 0
        remove(RemovalReason.DISCARDED)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val attacker = source.getEntity()
        if (attacker is Player) {
            val hasCrowbar = attacker.getMainHandItem().`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                    || attacker.getOffhandItem().`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
            if (hasCrowbar) {
                forceDiscardTrain()
                return true
            }
        }
        return false
    }

    override fun getPassengerRidingPosition(passenger: Entity): Vec3 {
        val seat = getAssignedSeatOffset(passenger)
        return localToWorld(seat)
    }

    fun getSeatWorldPosition(seatIndex: Int): Vec3 {
        return localToWorld(getSeatOffset(seatIndex))
    }

    fun getSeatWorldYaw(seatIndex: Int): Float {
        val def = getById(
            this.vehicleId
        )
        if (isDriverSeatIndex(seatIndex)) {
            val cabDirection = getDriverCabDirectionBySeatIndex(seatIndex, def)
            return if (cabDirection >= 0) getYRot() else getYRot() + 180.0f
        }
        return getYRot()
    }

    override fun getVehicleAttachmentPoint(passenger: Entity): Vec3 {
        return localToWorld(getAssignedSeatOffset(passenger))
    }

    override fun canAddPassenger(passenger: Entity): Boolean {
        pruneSeatAssignments()
        val def = getById(
            this.vehicleId
        )
        val seatCount = max(1, getSeatCount(def))
        return seatAssignments.size < seatCount
    }

    // ---- 既存スクリプト向けAPI ----
    fun getTrainStateData(stateType: Int): Float {
        return getVehicleState(stateType)
    }

    fun setTrainStateData(stateType: Int, value: Float) {
        syncVehicleState(stateType, value)
    }

    fun getVehicleState(stateType: Int): Float {
        return when (stateType) {
            0 -> if (this.reverser >= 0) 0.0f else 1.0f
            1 -> this.notch.toFloat()
            2 -> this.railProgress
            3 -> 0.0f
            4 -> (if (this.isDoorRightOpen) 1.0f else 0.0f) + (if (this.isDoorLeftOpen) 2.0f else 0.0f)
            5 -> toLegacyLightMode(this.lightMode)
            6 -> if (this.isPantographUp) 1.0f else 0.0f
            // Legacy RTM reserves state 7 for the chunk-loader mode, not speed.
            7 -> 0.0f
            8 -> this.destinationIndex.toFloat()
            9 -> this.soundIndex.toFloat()
            10 -> 1.0f - this.reverser
            11 -> this.interiorLightMode
            12 -> this.brakeCylinderPressure
            13 -> this.brakePipePressure
            14 -> this.mainReservoirPressure
            15 -> this.legacyBrakeAirCount
            16 -> max(0, -this.notch).toFloat()
            else -> 0.0f
        }
    }

    private fun toLegacyLightMode(mode: Int): Float {
        return when (mode) {
            1 -> 1.0f
            2 -> 2.0f
            else -> 0.0f
        }
    }

    private val interiorLightMode: Float
        get() = if (this.isInteriorLightOn) 1.0f else 0.0f

    val trainDirection: Float
        get() = if (this.reverser >= 0) 0.0f else 1.0f

    val rotation: Float
        // 旧 RTM の Render スクリプトは entity.getRotation() で車体 yaw を取得する。
        get() = getYRot()

    val dir: Float
        get() = this.trainDirection

    val moveDir: Float
        get() = this.trainDirection

    fun getConnectedTrain(dir: Int): TrainEntity? {
        return if (dir == 0) this.coupledLeader else this.coupledFollower
    }

    fun getCouplerYaw(index: Int): Float {
        return 0.0f
    }

    val rollsignAnimation: Int
        get() = this.destinationIndex

    fun syncNotch(notch: Int) {
        this.notch = notch
    }

    fun syncVehicleState(stateType: Int, value: Float) {
        when (stateType) {
            0 -> this.reverser = if (value < 0.5f) 1 else -1
            1 -> this.notch = Math.round(value)
            2 -> this.railProgress = value
            4 -> {
                val door = Math.round(value)
                this.isDoorRightOpen = (door and 1) != 0
                this.isDoorLeftOpen = (door and 2) != 0
            }

            5 -> this.lightMode = Math.round(value)
            6 -> this.isPantographUp = value > 0.5f
            8 -> this.destinationIndex = max(0, Math.round(value))
            9 -> this.soundIndex = max(0, Math.round(value))
            11 -> this.isInteriorLightOn = value > 0.0f
            else -> {}
        }
    }

    fun getSeatRotation(): Float {
        return Mth.clamp(seatRotation / 45.0f, -1.0f, 1.0f)
    }

    fun getFormation(): FormationCompat {
        return FormationCompat(this)
    }

    fun func_145782_y(): Int {
        return getId()
    }

    fun func_70070_b(): Int {
        if (level() == null) {
            return 0
        }
        try {
            val bodyPos = BlockPos.containing(getX(), getY() + 1.5, getZ())
            val block = level().getBrightness(LightLayer.BLOCK, bodyPos)
            val sky = level().getBrightness(LightLayer.SKY, bodyPos)
            return (block shl 4) or (sky shl 20)
        } catch (ignored: Throwable) {
            return 0
        }
    }

    fun func_70070_b(ignored: Int): Int {
        return func_70070_b()
    }

    // ---- 追加互換API（E259系等で使用） ----
    fun func_184207_aI(): Entity? {
        val driver = this.driverPassenger
        if (driver != null) {
            return driver
        }
        return if (getPassengers().isEmpty()) null else getPassengers().get(0)
    }

    var signal: Int
        get() = entityData.get<Int>(SIGNAL)!!
        set(signal) {
            val current = this.signal
            if (signal > 0 && current != -1) {
                setSignal2(signal)
            }
        }

    fun setSignal2(signal: Int) {
        setLegacySignalState(signal)
    }

    val isControlCar: Boolean
        get() = true

    val modelSet: Any
        get() = ModelSetCompat(this.vehicleId)

    val resourceState: ResourceStateCompat
        get() = ResourceStateCompat(this)

    fun getBogie(index: Int): BogieCompat {
        return BogieCompat(this, scriptBogieIndexToDefinitionIndex(index))
    }

    /**
     * 台車モデルが .class(本家組込 ModelBogie 等、RTMU は標準台車へ差し替え)の車両か。
     * この場合、台車を BogieRenderer でレール追従描画し、車体モデル/スクリプト側の
     * 車体固定 bogie グループ描画は抑制する(でないとカーブで台車がレールからズレる)。
     */
    fun usesReplacementBogies(): Boolean {
        val def = getById(
            this.vehicleId
        )
        if (def == null) {
            return false
        }
        for (b in def.getBogies()) {
            val m = b.modelFile()
            if (m != null && m.lowercase().endsWith(".class")) {
                return true
            }
        }
        return false
    }

    fun scriptBogieIndexToDefinitionIndex(scriptIndex: Int): Int {
        val def = getById(
            this.vehicleId
        )
        if (def == null || def.getBogies().isEmpty()) {
            return Mth.clamp(scriptIndex, 0, 1)
        }
        val extremes = getExtremeBogieIndices(def)
        return if (scriptIndex == 0) extremes[1] else extremes[0]
    }

    class ResourceStateCompat(private val train: TrainEntity) {
        val dataMap: DataMapCompat
            /**
             * Returns a typed map view used by old render scripts.
             */
            get() = DataMapCompat(train)

        val resourceName: String
            /**
             * Returns the vehicle id for scripts that compare resource names.
             */
            get() = train.vehicleId!!

        val name: String
            // 旧 RTM スクリプトは getName() で列車名(カスタムネーム)を取得し、substring(0,4) 等で
            get() {
                val id = train.vehicleId
                return if (id == null) "" else id
            }

        val resourceSet: ModelSetCompat
            /**
             * Returns a config holder compatible with old render scripts.
             */
            get() = ModelSetCompat(train.vehicleId)

        fun addExclusionParts(vararg parts: Any?) {}
        fun removeExclusionParts(vararg parts: Any?) {}
    }

    class DataMapCompat(private val train: TrainEntity?) {
        private val values: MutableMap<String?, Any?> = HashMap<String?, Any?>()

        /**
         * Creates a script-visible data map for a train.
         */
        init {
            refresh()
        }

        /**
         * Returns a raw value by key.
         */
        fun get(key: String?): Any? {
            refresh()
            val value = values.get(key)
            if (value != null) {
                return value
            }
            return if (train == null) null else train.scriptData.get(key)
        }

        fun contains(key: String?): Boolean {
            refresh()
            return values.containsKey(key) || train != null && train.scriptData.containsKey(key)
        }

        /**
         * Returns an integer value by key.
         */
        fun getInt(key: String?): Int {
            val value = get(key)
            if (value is Number) {
                return value.toInt()
            }
            if (value is Boolean) {
                return if (value) 1 else 0
            }
            if (value is String) {
                try {
                    return value.toInt()
                } catch (ignored: NumberFormatException) {
                    try {
                        return Math.round(value.toDouble()).toInt()
                    } catch (ignoredAgain: NumberFormatException) {
                    }
                }
            }
            return 0
        }

        fun getHex(key: String?): Int {
            return getInt(key)
        }

        /**
         * Returns a boolean value by key.
         */
        fun getBoolean(key: String?): Boolean {
            val value = get(key)
            if (value is Boolean) {
                return value
            }
            if (value is Number) {
                return value.toInt() != 0
            }
            if (value is String) {
                return value.toBoolean() || "1" == value
            }
            return false
        }

        fun getString(key: String?): String? {
            val value = get(key)
            return if (value == null) "" else value.toString()
        }

        fun getDouble(key: String?): Double {
            val value = get(key)
            if (value is Number) {
                return value.toDouble()
            }
            if (value is Boolean) {
                return if (value) 1.0 else 0.0
            }
            if (value is String) {
                try {
                    return value.toDouble()
                } catch (ignored: NumberFormatException) {
                }
            }
            return 0.0
        }

        /**
         * Stores a boolean value for the current script frame.
         */
        fun setBoolean(key: String?, value: Boolean, syncType: Int) {
            values.put(key, value)
            if (train != null && key != null) {
                train.scriptData.put(key, value.toString())
                if (syncType != 0) train.scriptDataDirty = true
            }
        }

        fun setBoolean(key: String?, value: Any?, syncType: Int) {
            setBoolean(key, toBoolean(value), syncType)
        }

        fun setBoolean(key: Any?, value: Any?, syncType: Any?) {
            setBoolean(toKey(key), toBoolean(value), toSyncType(syncType))
        }

        /**
         * Stores an integer value for the current script frame.
         */
        fun setInt(key: String?, value: Int, syncType: Int) {
            values.put(key, value)
            if (train != null && key != null) {
                train.scriptData.put(key, value.toString())
                if (syncType != 0) train.scriptDataDirty = true
            }
        }

        fun setInt(key: String?, value: Any?, syncType: Int) {
            setInt(key, toInt(value), syncType)
        }

        fun setInt(key: Any?, value: Any?, syncType: Any?) {
            setInt(toKey(key), toInt(value), toSyncType(syncType))
        }

        fun setString(key: String?, value: String?, syncType: Int) {
            val safeValue = if (value == null) "" else value
            values.put(key, safeValue)
            if (train != null && key != null) {
                train.scriptData.put(key, safeValue)
                if (syncType != 0) train.scriptDataDirty = true
            }
        }

        fun setString(key: Any?, value: Any?, syncType: Any?) {
            setString(toKey(key), value?.toString(), toSyncType(syncType))
        }

        fun setDouble(key: String?, value: Double, syncType: Int) {
            values.put(key, value)
            if (train != null && key != null) {
                train.scriptData.put(key, value.toString())
                if (syncType != 0) train.scriptDataDirty = true
            }
        }

        fun setDouble(key: String?, value: Any?, syncType: Int) {
            setDouble(key, toDouble(value), syncType)
        }

        fun setDouble(key: Any?, value: Any?, syncType: Any?) {
            setDouble(toKey(key), toDouble(value), toSyncType(syncType))
        }

        private fun refresh() {
            if (train == null) {
                return
            }
            val notch = train.notch
            val powerNotch = max(0, notch)
            val brakeNotch = max(0, -notch)
            val speedKmh = abs(train.speed) * 72.0f
            val mainReservoirPressure = train.mainReservoirPressure
            val brakePipePressure = train.brakePipePressure
            val brakeCylinderPressure = train.brakeCylinderPressure
            values.put("headLight", if (train.isHeadlightOn) 1 else 0)
            values.put("door", if (train.isDoorOpen) 1 else 0)
            values.put("doorLeft", if (train.isDoorLeftOpen) 1 else 0)
            values.put("doorRight", if (train.isDoorRightOpen) 1 else 0)
            values.put("lightMode", train.lightMode)
            values.put("pantograph", if (train.isPantographUp) 1 else 0)
            values.put("destination", train.destinationIndex)
            values.put("rollsign", train.destinationIndex)
            values.put("rollsignId", train.destinationIndex)
            values.put("maku", train.destinationIndex)
            values.put("sound", train.soundIndex)
            values.put("reverse", if (train.reverser < 0) 1 else 0)
            values.put("reverser", train.reverser)
            values.put("notch", notch)
            values.put("mascon", powerNotch)
            values.put("power", powerNotch)
            values.put("brake", brakeNotch)
            values.put("brakeNotch", brakeNotch)
            values.put("brakeAirCount", train.legacyBrakeAirCount)
            values.put("speed", speedKmh)
            values.put("speedKmh", speedKmh)
            values.put("kmh", speedKmh)
            values.put("MR", mainReservoirPressure)
            values.put("mr", mainReservoirPressure)
            values.put("mainReservoir", mainReservoirPressure)
            values.put("BP", brakePipePressure)
            values.put("bp", brakePipePressure)
            values.put("brakePipe", brakePipePressure)
            values.put("BC", brakeCylinderPressure)
            values.put("bc", brakeCylinderPressure)
            values.put("brakeCylinder", brakeCylinderPressure)
            values.put("customButtons", train.customButtonBits)
            values.put("railProgress", train.railProgress)
            values.put("connected", if (train.isConnected) 1 else 0)
            values.put("carNumber", defaultCarNumber(train))
            values.put("prevFormationSize", defaultFormationSize(train))
            values.put("isFormationA", false)
            values.put("isFormationB", false)
            values.put("isFormationError", false)
            values.putIfAbsent("prevRollsignId", train.destinationIndex)
            for (i in 0..15) {
                val state = train.getCustomButtonValue(i)
                values.put("Button" + i, state)
                values.put("button" + i, state)
                values.put("CustomButton" + i, state)
                values.put("customButton" + i, state)
            }
            train.scriptData.forEach { (key: String, value: String) -> values.put(key, value) }
        }

        companion object {
            private fun toKey(value: Any?): String? {
                return value?.toString()
            }

            private fun toSyncType(value: Any?): Int {
                if (value is Boolean) return if (value) 1 else 0
                if (value is Number) return value.toInt()
                if (value is String) {
                    try {
                        return value.toInt()
                    } catch (ignored: NumberFormatException) {
                    }
                    return if (toBoolean(value)) 1 else 0
                }
                return 0
            }

            private fun toBoolean(value: Any?): Boolean {
                if (value is Boolean) return value
                if (value is Number) return value.toInt() != 0
                if (value is String) return value.toBoolean() || "1" == value
                return false
            }

            private fun toInt(value: Any?): Int {
                if (value is Number) return value.toInt()
                if (value is Boolean) return if (value) 1 else 0
                if (value is String) {
                    try {
                        return value.toInt()
                    } catch (ignored: NumberFormatException) {
                        try {
                            return Math.round(value.toDouble()).toInt()
                        } catch (ignoredAgain: NumberFormatException) {
                        }
                    }
                }
                return 0
            }

            private fun toDouble(value: Any?): Double {
                if (value is Number) return value.toDouble()
                if (value is Boolean) return if (value) 1.0 else 0.0
                if (value is String) {
                    try {
                        return value.toDouble()
                    } catch (ignored: NumberFormatException) {
                    }
                }
                return 0.0
            }

            private fun defaultCarNumber(train: TrainEntity?): Int {
                if (train == null) {
                    return 1
                }
                try {
                    val formationTrains =
                        train.formationTrainsForDisplay
                    val index = formationTrains.indexOf(train)
                    if (index >= 0) {
                        return index + 1
                    }
                } catch (ignored: RuntimeException) {
                }
                return 1
            }

            private fun defaultFormationSize(train: TrainEntity?): Int {
                if (train == null) {
                    return 1
                }
                try {
                    return max(1, train.formationTrainsForDisplay.size)
                } catch (ignored: RuntimeException) {
                    return 1
                }
            }
        }
    }

    @JvmRecord
    private data class CouplingSelection(
        val first: UUID?,
        val firstBogieIndex: Int,
        val second: UUID?,
        val secondBogieIndex: Int,
        val armedAt: Long
    ) {
        val isComplete: Boolean
            get() = first != null && second != null && firstBogieIndex >= 0 && secondBogieIndex >= 0
    }

    @JvmRecord
    private data class CouplerPair(val thisSide: Int, val otherSide: Int, val distanceSqr: Double)

    @JvmRecord
    private data class CouplerCandidate(val train: TrainEntity?, val side: Int)

    @JvmRecord
    private data class CouplingEndpoint(val train: TrainEntity?, val side: Int)

    @JvmRecord
    private data class CouplingLink(
        val leader: TrainEntity?,
        val follower: TrainEntity?,
        val leaderSide: Int,
        val followerSide: Int
    )

    @JvmRecord
    private data class CouplingRequest(
        val sourceTrain: TrainEntity?,
        val sourceSide: Int,
        val targetTrain: TrainEntity?,
        val targetSide: Int,
        val distanceSqr: Double
    )

    class FormationCompat
    /**
     * Creates a script-visible formation view.
     */(private val train: TrainEntity) {
        /**
         * Returns the number of cars visible to this script.
         */
        fun size(): Int {
            return scriptFormationSize()
        }

        /**
         * Returns a formation entry by index.
         */
        fun get(index: Int): FormationEntryCompat? {
            val trains =
                train.formationTrainsForDisplay
            if (index < 0 || index >= trains.size) {
                return null
            }
            val entryTrain = trains.get(index)
            var dir = 0
            if (train.formation != null) {
                val entry: FormationEntry? = train.formation!!.getEntry(entryTrain)
                if (entry != null) {
                    dir = entry.dir
                }
            }
            return FormationEntryCompat(index, entryTrain, dir)
        }

        /**
         * Returns the entry for a train.
         * ★引数は Object 受け: レガシー JS は entity(=LegacyScriptExecutor ラッパー)をそのまま渡すため。
         * TrainEntity 専用にすると Nashorn がラッパーを TrainEntity へキャストして ClassCastException
         * になる(isMiddleCar で発生していた)。中身は getTrain() で取り出すか、無ければ自分(train)。
         */
        fun getEntry(entity: Any?): FormationEntryCompat {
            var resolved = train
            if (entity is TrainEntity) {
                resolved = entity
            } else if (entity != null) {
                try {
                    val r = entity.javaClass.getMethod("getTrain").invoke(entity)
                    if (r is TrainEntity) {
                        resolved = r
                    }
                } catch (ignored: Exception) {
                }
            }
            // 号車位置(entryId)は「表示チェーン順(=先頭から数えた実際の連結位置)」を
            // 唯一のソースにする。size() も同じ表示チェーンから数えるため、
            // スクリプトの isMiddleCar (id==1||id==size の判定) が必ず整合する。
            // ※ 旧実装は size を表示チェーン・entryId を formation.entries[] と別ソースから
            //   取っており、編成方向によって不整合 → 連結器/幌が片側だけ消える原因だった。
            //   また 06/07 だけ中間車に偽装するハックもあったが、本家スクリプトの
            //   パーツ分けに任せる方針(ユーザー要望)で撤去した。
            val trains =
                train.formationTrainsForDisplay
            val index = trains.indexOf(resolved)
            var dir = 0
            if (train.formation != null) {
                val entry: FormationEntry? = train.formation!!.getEntry(resolved)
                if (entry != null) {
                    dir = entry.dir
                }
            }
            return FormationEntryCompat(if (index >= 0) index else 0, resolved, dir)
        }

        val notch: Int
            get() {
                val front = frontTrain()
                return if (front != null) front.notch else train.notch
            }

        val speed: Float
            get() {
                val front = frontTrain()
                return if (front != null) front.speed else train.speed
            }

        val direction: Byte
            get() = (if (train.formation != null) train.formation!!.getDirection() else 0).toByte()

        /**
         * Legacy RTM scripts call this through Java reflection, so keep an explicit JavaBean getter.
         */
        fun getControlCar(): TrainEntity? {
            return frontTrain()
        }

        fun getFrontTrain(): TrainEntity? {
            return frontTrain()
        }

        private fun frontTrain(): TrainEntity? {
            if (train.formation != null) {
                val front: FormationEntry? = train.formation!!.getFrontEntry()
                if (front != null && front.train != null) {
                    return front.train
                }
            }
            val trains =
                train.formationTrainsForDisplay
            return if (trains.isEmpty()) train else trains.get(0)
        }

        private fun scriptFormationSize(): Int {
            // 連結中の実両数(表示チェーン順)をそのまま返す。getEntry().entryId と同じソース。
            return train.formationTrainsForDisplay.size
        }

        /**
         * Placeholder for old packet refresh calls.
         */
        fun sendPacket() {
        }
    }

    class FormationEntryCompat
    /**
     * Creates a script-visible formation entry.
     */ @JvmOverloads constructor(val entryId: Int, val train: TrainEntity?, val dir: Int = 0)

    class BogieCompat @JvmOverloads constructor(private val train: TrainEntity? = null, private val index: Int = 0) {
        val field_70177_z: Float

        init {
            this.field_70177_z = if (train != null) train.getScriptBogieWorldYaw(index) else 0.0f
        }

        val rotation: Float
            get() {
                if (train == null) {
                    return 0.0f
                }
                return train.getScriptBogieWorldYaw(index)
            }

        val yaw: Float
            get() = this.rotation

        val pitch: Float
            get() = if (train != null) train.getXRot() else 0.0f
    }

    class ModelSetCompat(val textureName: String?) {
        private val definition: VehicleDefinition?

        init {
            this.definition = getById(textureName)
        }

        val config: ConfigCompat
            /**
             * Returns a minimal config object used by old render scripts.
             */
            get() = ConfigCompat(definition)
    }

    class ConfigCompat private constructor(name: String?, definition: VehicleDefinition?) {
        // Legacy cab scripts inspect the array length to know the brake notch count.
        val deccelerations: FloatArray = floatArrayOf(
            0.0f, 0.1f, 0.2f, 0.35f, 0.5f, 0.7f, 0.9f, 1.1f, 1.3f
        )

        // Legacy cab/monitor scripts read maxSpeed as an array of per-notch top speeds
        // (e.g. CustomMonitor_JRE1: config.maxSpeed[config.maxSpeed.length-1]*72). Must be
        // non-null with at least one element or scripts crash on undefined.length.
        // 値は blocks/tick 相当(末尾要素 1.1 ≈ 約79km/h)。実速度はエンジン側で決まるので表示用の上限。
        val maxSpeed: FloatArray = floatArrayOf(
            0.0f, 0.22f, 0.44f, 0.66f, 0.88f, 1.1f
        )
        val rollsignNames: Array<String?>?
        val customButtons: Array<String?>
        val customButtonNames: Array<String?>
        val customButtonOptions: Array<Array<String?>>

        // Legacy cab/server scripts read sound_Announcement as a 2D array [[name, soundPath], ...].
        val sound_Announcement: Array<Array<String?>?>
        val isSingleTrain: Boolean
        val trainName: String

        constructor() : this(null, null)

        constructor(name: String) : this(name, null)

        constructor(definition: VehicleDefinition?) : this(
            if (definition != null) definition.getId() else null,
            definition
        )

        init {
            this.trainName = if (name != null) name else ""
            if (definition != null && !definition.getRollsignNames().isEmpty()) {
                this.rollsignNames = definition.getRollsignNames().toTypedArray()
            } else {
                this.rollsignNames = DEFAULT_ROLLSIGN_NAMES
            }
            val buttonNames =
                if (definition != null) definition.getCustomButtonNames() else emptyList()
            this.customButtons = buttonNames.toTypedArray()
            this.customButtonNames = this.customButtons
            val buttonOptions =
                if (definition != null) definition.getCustomButtonOptions() else emptyList()
            this.customButtonOptions = buttonOptions.map { options ->
                options.map { it as String? }.toTypedArray()
            }.toTypedArray()
            this.isSingleTrain = definition != null && definition.isSingleTrain()
            val sounds: List<String> =
                if (definition != null) definition.getAnnouncementSounds() else emptyList()
            if (sounds.isEmpty()) {
                this.sound_Announcement = arrayOfNulls<Array<String?>>(0)
            } else {
                val arr = arrayOfNulls<Array<String?>>(sounds.size)
                for (i in sounds.indices) {
                    val s = sounds.get(i)
                    arr[i] = arrayOf<String?>(s, s)
                }
                this.sound_Announcement = arr
            }
        }

        companion object {
            private val DEFAULT_ROLLSIGN_NAMES = arrayOf<String?>(
                "None",
                "Out of service",
                "Test run",
                "Party",
                "Extra",
                "Local",
                "Rapid",
                "Express"
            )
        }
    }

    class WorldCompat
    /**
     * Creates a script-visible world view.
     */(private val train: TrainEntity?) {
        var field_72995_K: Boolean = false

        val isClientSide: Boolean
            /**
             * Returns whether the current level is client-side.
             */
            get() {
                field_72995_K = train != null && train.level().isClientSide()
                return field_72995_K
            }

        /**
         * RTM 1.7.10 互換: World.loadedEntityList (field_72996_f)。
         * スクリプトが `world.field_72996_f.size()` / `.get(i)` で走査する。
         * 毎呼び出し時に level の現在の全エンティティを ArrayList で返す。
         */
        fun field_72996_f(): MutableList<Entity?> {
            val out = ArrayList<Entity?>()
            if (train == null) return out
            val level = train.level()
            if (level is ServerLevel) {
                for (e in level.getAllEntities()) {
                    out.add(e)
                }
            } else if (level is ClientLevel) {
                for (e in level.entitiesForRendering()) {
                    out.add(e)
                }
            }
            return out
        }

        val field_72996_f: MutableList<Entity?>
            /** field 風アクセス対応のためのキャッシュ getter (JS の `world.field_72996_f` でも動くように)。  */
            get() = field_72996_f()
    }

    override fun hasIndirectPassenger(passenger: Entity): Boolean {
        if (super.hasIndirectPassenger(passenger)) {
            return true
        }
        if (passenger === this) {
            return true
        }
        return coupledFollowerUuid != null && passenger.getUUID() == coupledFollowerUuid
    }

    override fun onPassengerTurned(passenger: Entity) {
        super.onPassengerTurned(passenger)
        if (!hasPassenger(passenger)) {
            seatAssignments.remove(passenger.getUUID())
            syncSeatAssignmentsToEntityData()
        }
    }

    val passengersRidingOffset: Double
        get() = 0.0

    private fun getSeatOffset(index: Int): Vec3 {
        val def = getById(
            this.vehicleId
        )
        if (def == null) return Vec3(0.0, 1.2, 0.0)

        val seats = getSelectableSeats(def)
        if (!seats.isEmpty()) {
            if (index >= 0 && index < seats.size) {
                return seats.get(index)
            }
            return seats.get(0)
        }

        if (def.hasSeatOffset()) {
            return def.getSeatOffset()
        }

        return Vec3(0.0, 1.2, 0.0)
    }

    private fun getAssignedSeatOffset(passenger: Entity): Vec3 {
        val index = getAssignedSeatIndex(passenger)
        return getSeatOffset(index)
    }

    private fun getAssignedSeatIndex(passenger: Entity): Int {
        syncSeatAssignmentsFromEntityData()
        val id = passenger.getUUID()
        val def = getById(
            this.vehicleId
        )
        val seatCount = getSeatCount(def)
        val assignedIndex = seatAssignments.get(id)
        if (assignedIndex != null && assignedIndex >= 0 && assignedIndex < seatCount) {
            return assignedIndex
        }
        if (level().isClientSide()) {
            return findNearestSeatIndex(passenger)
        }
        return assignSeatIndex(passenger, findNearestSeatIndex(passenger))
    }

    private fun getSeatCount(def: VehicleDefinition?): Int {
        if (def == null) return 0
        var count = getSelectableSeats(def).size
        if (count == 0 && def.hasSeatOffset()) {
            count = 1
        }
        return count
    }

    private fun assignSeatIndex(passenger: Entity, desiredIndex: Int): Int {
        pruneSeatAssignments()
        val def = getById(
            this.vehicleId
        )
        if (def == null) return 0

        val seatCount = getSeatCount(def)
        if (seatCount <= 0) {
            seatAssignments.put(passenger.getUUID(), 0)
            syncSeatAssignmentsToEntityData()
            return 0
        }

        if (desiredIndex >= 0 && desiredIndex < seatCount && !isSeatTakenByOther(passenger, desiredIndex)) {
            seatAssignments.put(passenger.getUUID(), desiredIndex)
            syncSeatAssignmentsToEntityData()
            return desiredIndex
        }

        for (i in 0..<seatCount) {
            if (!isSeatTakenByOther(passenger, i)) {
                seatAssignments.put(passenger.getUUID(), i)
                syncSeatAssignmentsToEntityData()
                return i
            }
        }

        val fallback = max(0, min(desiredIndex, seatCount - 1))
        seatAssignments.put(passenger.getUUID(), fallback)
        syncSeatAssignmentsToEntityData()
        return fallback
    }

    fun hasAssignedSeat(passengerId: UUID?): Boolean {
        if (passengerId == null) {
            return false
        }
        syncSeatAssignmentsFromEntityData()
        return seatAssignments.containsKey(passengerId)
    }

    fun formationHasAssignedSeat(passengerId: UUID?): Boolean {
        if (passengerId == null) {
            return false
        }
        val found = booleanArrayOf(false)
        forEachFormationTrain(Consumer { train: TrainEntity? ->
            if (!found[0] && train!!.hasAssignedSeat(passengerId)) {
                found[0] = true
            }
        })
        return found[0]
    }

    fun findAssignedPassenger(passengerId: UUID?): Entity? {
        if (passengerId == null) {
            return null
        }
        for (passenger in getPassengers()) {
            if (passengerId == passenger.getUUID()) {
                return passenger
            }
        }
        if (level() is ServerLevel) {
            val player: Entity? = serverLevel.getEntity(passengerId)
            if (player != null && hasAssignedSeat(passengerId)) {
                return player
            }
        }
        return null
    }

    private val firstAssignedPassenger: Entity?
        get() {
            syncSeatAssignmentsFromEntityData()
            for (passengerId in seatAssignments.keys) {
                val passenger = findAssignedPassenger(passengerId)
                if (passenger != null) {
                    return passenger
                }
            }
            return null
        }

    fun clearSeatAssignment(passengerId: UUID?) {
        if (passengerId == null) {
            return
        }
        syncSeatAssignmentsFromEntityData()
        if (seatAssignments.remove(passengerId) != null) {
            syncSeatAssignmentsToEntityData()
        }
        if (passengerId == activeDriverUuid) {
            activeDriverUuid = null
            activeDriverTicks = 0
        }
    }

    private fun syncSeatAssignmentsToEntityData() {
        if (level().isClientSide()) {
            return
        }
        val data = StringBuilder()
        seatAssignments.forEach { (uuid: UUID?, seatIndex: Int?) ->
            if (!data.isEmpty()) {
                data.append(';')
            }
            data.append(uuid).append('=').append(seatIndex)
        }
        this.seatAssignmentsData = data.toString()
    }

    private fun syncSeatAssignmentsFromEntityData() {
        val data = this.seatAssignmentsData!!
        if (data == null || data.isBlank()) {
            return
        }
        val entries = data.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (entry in entries) {
            val sep = entry.indexOf('=')
            if (sep <= 0 || sep >= entry.length - 1) {
                continue
            }
            try {
                val uuid = UUID.fromString(entry.substring(0, sep))
                val seatIndex = entry.substring(sep + 1).toInt()
                seatAssignments.put(uuid, seatIndex)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun isSeatTakenByOther(passenger: Entity, seatIndex: Int): Boolean {
        pruneSeatAssignments()
        val passengerId = passenger.getUUID()
        for (entry in seatAssignments.entries) {
            if (entry.value == seatIndex && entry.key != passengerId) {
                return true
            }
        }
        return false
    }

    private fun pruneSeatAssignments() {
        if (seatAssignments.isEmpty()) {
            return
        }
        seatAssignments.keys.removeIf { uuid: UUID? -> findAssignedPassenger(uuid) == null }
    }

    private fun findNearestSeatIndex(passenger: Entity): Int {
        val def = getById(
            this.vehicleId
        )
        if (def == null) return -1

        val seats = getSelectableSeats(def)
        if (seats.isEmpty()) {
            return if (def.hasSeatOffset()) 0 else -1
        }

        // プレイヤーの位置から最も近い座席を返す
        val target = passenger.position()
        var bestIndex = -1
        var bestDistance = Double.MAX_VALUE
        for (i in seats.indices) {
            val seatPoint = localToWorld(seats.get(i))
            val distance = seatPoint.distanceToSqr(target)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun getSelectableSeatMarkers(def: VehicleDefinition?): List<VehicleDefinition.SeatMarker> {
        if (def == null) {
            return mutableListOf<VehicleDefinition.SeatMarker>()
        }
        return def.getRideableSeatMarkers()
    }

    private fun getSelectableSeats(def: VehicleDefinition?): List<Vec3> {
        if (def == null) {
            return mutableListOf<Vec3>()
        }
        return def.getRideableSeatPositions()
    }

    private fun resolveFrontSeatIndex(def: VehicleDefinition?): Int {
        val seatMarkers = getSelectableSeatMarkers(def)
        if (seatMarkers.isEmpty()) {
            return if (def != null && def.hasSeatOffset()) 0 else -1
        }

        val configured = if (def != null) def.getFrontDriverSeatIndex() else -1
        if (configured >= 0 && configured < seatMarkers.size) {
            return configured
        }

        val fallbackDriver = if (def != null) def.getDriverSeatIndex() else -1
        if (fallbackDriver >= 0 && fallbackDriver < seatMarkers.size) {
            return fallbackDriver
        }

        val driverSeat = findExtremeDriverSeatIndexByZ(seatMarkers, true)
        if (driverSeat >= 0) {
            return driverSeat
        }

        return findExtremeSeatIndexByZ(getSelectableSeats(def), true)
    }

    private fun resolveRearSeatIndex(def: VehicleDefinition?): Int {
        val seatMarkers = getSelectableSeatMarkers(def)
        if (seatMarkers.isEmpty()) {
            return if (def != null && def.hasSeatOffset()) 0 else -1
        }

        val configured = if (def != null) def.getRearDriverSeatIndex() else -1
        if (configured >= 0 && configured < seatMarkers.size) {
            return configured
        }

        val driverSeat = findExtremeDriverSeatIndexByZ(seatMarkers, false)
        if (driverSeat >= 0) {
            return driverSeat
        }

        return findExtremeSeatIndexByZ(getSelectableSeats(def), false)
    }

    private fun findExtremeSeatIndexByZ(seats: List<Vec3>?, front: Boolean): Int {
        if (seats == null || seats.isEmpty()) {
            return -1
        }

        var bestIndex = 0
        var bestZ = seats.get(0).z
        for (i in 1..<seats.size) {
            val z = seats.get(i).z
            if (if (front) z > bestZ else z < bestZ) {
                bestZ = z
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun findExtremeDriverSeatIndexByZ(seats: List<VehicleDefinition.SeatMarker>?, front: Boolean): Int {
        if (seats == null || seats.isEmpty()) {
            return -1
        }

        var bestIndex = -1
        var bestZ = 0.0
        for (i in seats.indices) {
            val seat = seats.get(i)
            if (!seat.driverCab()) {
                continue
            }
            val z = seat.position().z
            if (bestIndex < 0 || (if (front) z > bestZ else z < bestZ)) {
                bestZ = z
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun findSeatByClickPosition(player: Player, clickOffsetWorld: Vec3): Int {
        val def = getById(
            this.vehicleId
        )
        if (def == null) {
            return findNearestSeatIndex(player)
        }

        val byBogie = findSeatByClickedBogie(def, clickOffsetWorld)
        if (byBogie >= 0) {
            RealTrainModRenewed.LOGGER.debug(
                "Selected seat by bogie click: vehicle={}, seatIndex={}, clickOffset={}, player={}",
                this.vehicleId,
                byBogie,
                clickOffsetWorld,
                player.getName().getString()
            )
            return byBogie
        }

        val fallback = findNearestSeatToLocalClick(def, worldToLocal(position().add(clickOffsetWorld)))
        RealTrainModRenewed.LOGGER.debug(
            "Selected seat by nearest JSON seat: vehicle={}, seatIndex={}, clickOffset={}, player={}",
            this.vehicleId,
            fallback,
            clickOffsetWorld,
            player.getName().getString()
        )
        return fallback
    }

    private fun findNearestSeatToLocalClick(def: VehicleDefinition?, localClick: Vec3): Int {
        val seats = getSelectableSeats(def)
        if (seats.isEmpty()) {
            return if (def != null && def.hasSeatOffset()) 0 else -1
        }

        var bestIndex = 0
        var bestScore = Double.MAX_VALUE
        for (i in seats.indices) {
            val seat = seats.get(i)
            val dx = abs(seat.x - localClick.x)
            val dz = abs(seat.z - localClick.z)
            val sameSideBonus = if (abs(localClick.x) > 0.25 && sign(seat.x) == sign(localClick.x)) -0.25 else 0.0
            val score = dz * 3.0 + dx + sameSideBonus
            if (score < bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun findSeatByClickedBogie(def: VehicleDefinition, clickOffsetWorld: Vec3): Int {
        val bogies: List<VehicleDefinition.BogieDefinition> = def.getBogies()
        if (bogies.isEmpty()) {
            return -1
        }

        val clickedWorld = position().add(clickOffsetWorld)
        var nearest: VehicleDefinition.BogieDefinition? = null
        var nearestIndex = -1
        var bestDistance = Double.MAX_VALUE
        for (i in bogies.indices) {
            val bogie = bogies.get(i)
            val bogieWorld = localToWorld(bogie.position())
            val distance = bogieWorld.distanceToSqr(clickedWorld)
            if (distance < bestDistance) {
                bestDistance = distance
                nearest = bogie
                nearestIndex = i
            }
        }
        if (nearest == null || bestDistance > 4.0) {
            return -1
        }
        val yawRad = Math.toRadians(getYRot().toDouble())
        val forward = Vec3(-sin(yawRad), 0.0, cos(yawRad))
        val nearestOffset = localToWorld(nearest.position()).subtract(position())
        val frontBogie = nearestOffset.dot(forward) >= 0.0
        val seatIndex = if (frontBogie) resolveFrontSeatIndex(def) else resolveRearSeatIndex(def)
        RealTrainModRenewed.LOGGER.debug(
            "Clicked bogie resolved to seat: vehicle={}, bogieIndex={}, frontBogie={}, seatIndex={}, clickOffset={}, bestDistance={}",
            this.vehicleId,
            nearestIndex,
            frontBogie,
            seatIndex,
            clickOffsetWorld,
            bestDistance
        )
        return seatIndex
    }

    private fun resolveSeatIndexForBogieClick(def: VehicleDefinition?, bogieIndex: Int): Int {
        if (def == null || bogieIndex < 0 || bogieIndex >= def.getBogies().size) {
            return -1
        }
        val extremes = getExtremeBogieIndices(def)
        val frontBogie = bogieIndex == extremes[1]
        val seatIndex = if (frontBogie) resolveFrontSeatIndex(def) else resolveRearSeatIndex(def)
        return if (isDriverSeatIndex(seatIndex)) seatIndex else -1
    }

    fun interactWithBogie(
        player: Player?,
        bogieIndex: Int,
        hand: InteractionHand?,
        holdingCrowbar: Boolean
    ): InteractionResult? {
        if (player == null) {
            return InteractionResult.PASS
        }
        if (holdingCrowbar) {
            if (!level().isClientSide()) {
                enterCouplingMode(player, bogieIndex)
            }
            return if (level().isClientSide()) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS
        }
        if (player.getVehicle() != null) {
            return InteractionResult.PASS
        }
        val def = getById(
            this.vehicleId
        )
        if (def == null) {
            return InteractionResult.PASS
        }
        val clampedBogie = Mth.clamp(bogieIndex, 0, max(0, getInteractionBogieCenters(def).size - 1))
        val clickOffsetWorld = localToWorld(getBogieLocalPosition(clampedBogie, def)).subtract(position())
        var seatIndex = resolveSeatIndexForBogieClick(def, clampedBogie)
        if (seatIndex < 0) {
            seatIndex = findNearestSeatToLocalClick(def, worldToLocal(position().add(clickOffsetWorld)))
        }
        return tryRideWithSeat(player, seatIndex)
    }

    fun rideSeat(player: Player, seatIndex: Int): InteractionResult {
        return tryRideWithSeat(player, seatIndex)
    }

    private fun tryRideWithSeat(player: Player, seatIndex: Int): InteractionResult {
        if (seatIndex < 0) {
            RealTrainModRenewed.LOGGER.debug(
                "Ride denied: invalid seat index {} for vehicle {}", seatIndex,
                this.vehicleId
            )
            return InteractionResult.PASS
        }
        if (player.getVehicle() != null) {
            return InteractionResult.PASS
        }

        val seatEntity = getOrCreateSeatHitbox(seatIndex)
        if (seatEntity == null) {
            RealTrainModRenewed.LOGGER.warn(
                "Ride denied: seat entity missing for vehicle {} seat {}",
                this.vehicleId, seatIndex
            )
            return InteractionResult.PASS
        }
        assignSeatIndex(player, seatIndex)
        val def = getById(
            this.vehicleId
        )
        if (isDriverSeatIndex(seatIndex)) {
            this.reverser = getDefaultReverserForSeat(def, seatIndex)
        }
        RealTrainModRenewed.LOGGER.info(
            "Ride request: vehicle={}, player={}, seatIndex={}, isDriverSeat={}, clickPassengers={}",
            this.vehicleId,
            player.getName().getString(),
            seatIndex,
            isDriverSeatIndex(seatIndex),
            seatAssignments.size
        )
        RealTrainModRenewed.LOGGER.debug(
            "Try mount: player='{}' vehicle='{}' seat={} passengers={}/{} canAddPassenger={}",
            player.getName().getString(),
            this.vehicleId,
            seatIndex,
            seatAssignments.size,
            max(1, getSeatCount(getById(this.vehicleId))),
            this.canAddPassenger(player)
        )
        if (player.startRiding(this, true, false)) {
            RealTrainModRenewed.LOGGER.info(
                "Player '{}' mounted vehicle '{}' at seat {}",
                player.getName().getString(),
                this.vehicleId,
                seatIndex
            )
            return InteractionResult.SUCCESS
        }

        RealTrainModRenewed.LOGGER.warn(
            "Player '{}' failed to mount vehicle '{}' at seat {}",
            player.getName().getString(),
            this.vehicleId,
            seatIndex
        )
        seatAssignments.remove(player.getUUID())
        return InteractionResult.PASS
    }

    private fun getDefaultReverserForSeat(def: VehicleDefinition?, seatIndex: Int): Int {
        return getDriverCabDirectionBySeatIndex(seatIndex, def)
    }

    private fun getDriverCabDirectionBySeatIndex(seatIndex: Int, def: VehicleDefinition?): Int {
        if (seatIndex < 0 || def == null) {
            return 1
        }
        if (seatIndex == resolveRearSeatIndex(def)) {
            return -1
        }
        if (seatIndex == resolveFrontSeatIndex(def)) {
            return 1
        }
        val seats = getSelectableSeats(def)
        if (seatIndex >= 0 && seatIndex < seats.size) {
            return if (seats.get(seatIndex).z < 0.0) -1 else 1
        }
        return 1
    }

    fun interactAt(player: Player, vec: Vec3?, hand: InteractionHand?): InteractionResult {
        if (isHoldingTrainPlacementItem(player)) {
            return InteractionResult.PASS
        }
        val holdingCrowbar = player.getMainHandItem().`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                || player.getOffhandItem().`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
        if (holdingCrowbar) {
            if (!level().isClientSide()) {
                enterCouplingMode(player)
            }
            return InteractionResult.CONSUME
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS
        }
        if (player.getVehicle() != null) {
            return InteractionResult.PASS
        }
        val clickOffsetWorld = if (vec != null) vec else player.position().subtract(position())
        val seatIndex = findSeatByClickPosition(player, clickOffsetWorld)
        return tryRideWithSeat(player, seatIndex)
    }

    override fun getDismountLocationForPassenger(passenger: LivingEntity): Vec3 {
        val seat = if (passenger != null) getAssignedSeatOffset(passenger) else Vec3.ZERO
        val preferredSide = if (seat.x >= 0.0) 1.0 else -1.0
        val localCandidates = listOf(
            Vec3(preferredSide * 3.2, 0.0, seat.z),
            Vec3(preferredSide * 4.0, 0.0, seat.z),
            Vec3(-preferredSide * 3.2, 0.0, seat.z),
            Vec3(preferredSide * 3.0, 0.0, seat.z + 2.0),
            Vec3(preferredSide * 3.0, 0.0, seat.z - 2.0),
            Vec3(-preferredSide * 3.0, 0.0, seat.z + 2.0),
            Vec3(-preferredSide * 3.0, 0.0, seat.z - 2.0)
        )
        val yOffsets = doubleArrayOf(0.5, 1.0, 0.0, 1.5)
        for (local in localCandidates) {
            val base = localToWorld(local)
            for (yOffset in yOffsets) {
                val candidate = Vec3(base.x, base.y + yOffset, base.z)
                if (isSafeDismountLocation(passenger, candidate)) {
                    return candidate
                }
            }
        }
        val fallback = localToWorld(Vec3(preferredSide * 3.6, 0.0, seat.z))
        return Vec3(fallback.x, fallback.y + 0.5, fallback.z)
    }

    fun forceDismountPassenger(player: Player?) {
        if (player == null) {
            return
        }
        val dismountPos = getDismountLocationForPassenger(player)
        player.stopRiding()
        clearSeatAssignment(player.getUUID())
        player.teleportTo(dismountPos.x, dismountPos.y, dismountPos.z)
        player.fallDistance = 0.0
    }

    private fun isSafeDismountLocation(passenger: LivingEntity?, pos: Vec3): Boolean {
        if (passenger == null) {
            return false
        }
        val box = passenger.getDimensions(passenger.getPose()).makeBoundingBox(pos)
        if (!level().getWorldBorder().isWithinBounds(BlockPos.containing(pos))) {
            return false
        }
        if (!level().noCollision(passenger, box)) {
            return false
        }
        val feetPos = BlockPos.containing(pos.x, pos.y - 0.1, pos.z)
        val belowPos = feetPos.below()
        return !level().getBlockState(belowPos).isAir() || !level().getFluidState(feetPos).isEmpty()
    }

    private fun localToWorld(local: Vec3): Vec3 {
        var def = getById(
            this.vehicleId
        )
        if (def == null) {
            def = getSelected()
        }

        val offset = if (def != null) def.getModelOffset() else Vec3.ZERO
        val scale = if (def != null) def.getModelScale() else 1.0f
        // モデルのZ+を前方としてYRotで回転させる。
        val yawRad = Math.toRadians(-this.getYRot().toDouble())

        val localX = local.x
        val localY = local.y
        val localZ = local.z
        // Z+を前方として回転
        val rotatedX = cos(yawRad) * localX - sin(yawRad) * localZ
        val rotatedZ = sin(yawRad) * localX + cos(yawRad) * localZ
        val offsetX = cos(yawRad) * offset.x - sin(yawRad) * offset.z
        val offsetZ = sin(yawRad) * offset.x + cos(yawRad) * offset.z

        return Vec3(
            this.getX() + offsetX + rotatedX * scale,
            this.getY() + offset.y + localY * scale,
            this.getZ() + offsetZ + rotatedZ * scale
        )
    }

    private fun worldToLocal(world: Vec3): Vec3 {
        var def = getById(
            this.vehicleId
        )
        if (def == null) {
            def = getSelected()
        }

        val offset = if (def != null) def.getModelOffset() else Vec3.ZERO
        val scale = if (def != null) def.getModelScale() else 1.0f
        val yawRad = Math.toRadians(-this.getYRot().toDouble())
        val offsetX = cos(yawRad) * offset.x - sin(yawRad) * offset.z
        val offsetZ = sin(yawRad) * offset.x + cos(yawRad) * offset.z
        val dx = world.x - this.getX() - offsetX
        val dy = world.y - this.getY() - offset.y
        val dz = world.z - this.getZ() - offsetZ

        val localX = cos(yawRad) * dx + sin(yawRad) * dz
        val localZ = -sin(yawRad) * dx + cos(yawRad) * dz
        return Vec3(localX / scale, dy / scale, localZ / scale)
    }

    /**
     * 台車描画用の world→body-local 変換。台車は TrainEntityRenderer の本体ポーズ
     * (yaw → pitch → bank → modelOffset → scale)の中で translate されるため、その全段を厳密に
     * 逆変換しないと、カーブのバンク(カント)や坂のpitchで台車がレールからズレて描画される。
     * (従来の worldToLocalForRender は yaw しか逆回転しておらずバンク分ズレていた。)
     */
    private fun worldToBogieLocalForRender(world: Vec3, partialTicks: Float): Vec3 {
        var def = getById(
            this.vehicleId
        )
        if (def == null) {
            def = getSelected()
        }
        val offset = if (def != null) def.getModelOffset() else Vec3.ZERO
        val scale = if (def != null) def.getModelScale() else 1.0f
        val dxo = this.getX() - this.xo
        val dyo = this.getY() - this.yo
        val dzo = this.getZ() - this.zo
        val staleOldPos = (dxo * dxo + dyo * dyo + dzo * dzo) > 64.0
        val renderX = if (staleOldPos) this.getX() else Mth.lerp(partialTicks.toDouble(), this.xo, this.getX())
        val renderY = if (staleOldPos) this.getY() else Mth.lerp(partialTicks.toDouble(), this.yo, this.getY())
        val renderZ = if (staleOldPos) this.getZ() else Mth.lerp(partialTicks.toDouble(), this.zo, this.getZ())
        // TrainEntityRenderer と同じ式で yaw / pitch / bank を求める。
        val renderYaw = if (staleOldPos) getYRot() else Mth.rotLerp(partialTicks, this.yRotO, getYRot())
        val renderPitch =
            Mth.clamp(if (staleOldPos) getXRot() else Mth.lerp(partialTicks, this.xRotO, getXRot()), -45.0f, 45.0f)
        val yawDelta = Mth.wrapDegrees(getYRot() - this.yRotO)
        val horizSpeed = getDeltaMovement().horizontalDistance().toFloat()
        val bankAngle = Mth.clamp(-yawDelta * horizSpeed * 5.0f, -10.0f, 10.0f)
        // レンダラの回転(YP yaw → XP -pitch → ZP bank)を組み、その逆(共役)で world ベクトルを戻す。
        val q = Quaternionf()
            .rotateY(Math.toRadians(renderYaw.toDouble()).toFloat())
            .rotateX(Math.toRadians(-renderPitch.toDouble()).toFloat())
            .rotateZ(Math.toRadians(bankAngle.toDouble()).toFloat())
        val d = Vector3f(
            (world.x - renderX).toFloat(), (world.y - renderY).toFloat(), (world.z - renderZ).toFloat()
        )
        q.conjugate().transform(d)
        // modelOffset は回転後の本体フレームで translate されるので、回転を戻した後に引く。
        d.sub(offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat())
        return Vec3((d.x / scale).toDouble(), (d.y / scale).toDouble(), (d.z / scale).toDouble())
    }

    private fun worldToLocalForRender(world: Vec3, renderYaw: Float, partialTicks: Float): Vec3 {
        var def = getById(
            this.vehicleId
        )
        if (def == null) {
            def = getSelected()
        }

        val offset = if (def != null) def.getModelOffset() else Vec3.ZERO
        val scale = if (def != null) def.getModelScale() else 1.0f
        val dxo = this.getX() - this.xo
        val dyo = this.getY() - this.yo
        val dzo = this.getZ() - this.zo
        val staleOldPos = (dxo * dxo + dyo * dyo + dzo * dzo) > 64.0

        val renderX = if (staleOldPos) this.getX() else Mth.lerp(partialTicks.toDouble(), this.xo, this.getX())
        val renderY = if (staleOldPos) this.getY() else Mth.lerp(partialTicks.toDouble(), this.yo, this.getY())
        val renderZ = if (staleOldPos) this.getZ() else Mth.lerp(partialTicks.toDouble(), this.zo, this.getZ())
        val effectiveYaw = if (staleOldPos) this.getYRot() else renderYaw
        val yawRad = Math.toRadians(-effectiveYaw.toDouble())
        val offsetX = cos(yawRad) * offset.x - sin(yawRad) * offset.z
        val offsetZ = sin(yawRad) * offset.x + cos(yawRad) * offset.z
        val dx = world.x - renderX - offsetX
        val dy = world.y - renderY - offset.y
        val dz = world.z - renderZ - offsetZ

        val localX = cos(yawRad) * dx + sin(yawRad) * dz
        val localZ = -sin(yawRad) * dx + cos(yawRad) * dz
        return Vec3(localX / scale, dy / scale, localZ / scale)
    }

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        return interactAt(player, location, hand)
    }

    fun interact(player: Player, hand: InteractionHand?): InteractionResult {
        if (isHoldingTrainPlacementItem(player)) {
            return InteractionResult.PASS
        }
        val holdingCrowbar = player.getMainHandItem().`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                || player.getOffhandItem().`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
        if (holdingCrowbar) {
            if (!level().isClientSide()) {
                enterCouplingMode(player)
            }
            return InteractionResult.CONSUME
        }
        if (player.isSecondaryUseActive()) return InteractionResult.PASS
        if (player.getVehicle() != null) {
            return InteractionResult.PASS
        }
        return tryRideWithSeat(player, findNearestSeatIndex(player))
    }

    private fun ensureBogieHitboxes() {
        if (level().isClientSide()) {
            return
        }
        val def = getById(
            this.vehicleId
        )
        val bogies = getInteractionBogieCenters(def)
        val count = bogies.size
        for (bogieIndex in 0..<count) {
            val bogieWorld = getBogieEntityWorldPosition(bogieIndex)
            var bogieEntity = resolveBogieHitbox(bogieIndex)
            if (bogieEntity == null) {
                bogieEntity =
                    RealTrainModRenewedEntities.TRAIN_BOGIE.get().create(level(), EntitySpawnReason.SPAWN_ITEM_USE)
                if (bogieEntity == null) {
                    continue
                }
                bogieEntity.attachToTrain(this, bogieIndex)
                bogieEntity.setPos(bogieWorld.x, bogieWorld.y, bogieWorld.z)
                bogieEntity.setYRot(getYRot())
                level().addFreshEntity(bogieEntity)
                bogieHitboxUuids.put(bogieIndex, bogieEntity.getUUID())
            } else {
                bogieEntity.attachToTrain(this, bogieIndex)
                bogieEntity.setPos(bogieWorld.x, bogieWorld.y, bogieWorld.z)
                bogieEntity.setYRot(getYRot())
            }
            bogieEntity.setOldPosAndRot()
        }
        val staleIndices: MutableList<Int?> = ArrayList<Int?>()
        for (bogieIndex in bogieHitboxUuids.keys) {
            if (bogieIndex >= count) {
                staleIndices.add(bogieIndex)
            }
        }
        for (bogieIndex in staleIndices) {
            val stale = resolveBogieHitbox(bogieIndex!!)
            if (stale != null) {
                stale.discard()
            }
            bogieHitboxUuids.remove(bogieIndex)
        }
    }

    private fun ensureSeatHitboxes() {
        if (level().isClientSide()) {
            return
        }
        val def = getById(
            this.vehicleId
        )
        val seatCount = getSeatCount(def)
        for (seatIndex in 0..<seatCount) {
            getOrCreateSeatHitbox(seatIndex)
        }
        seatHitboxUuids.entries.removeIf { entry: MutableMap.MutableEntry<Int?, UUID> ->
            if (entry.key!! < seatCount) {
                return@removeIf false
            }
            val stale = resolveSeatHitbox(entry.key!!)
            if (stale != null) {
                stale.discard()
            }
            true
        }
    }

    private fun getOrCreateSeatHitbox(seatIndex: Int): TrainSeatEntity? {
        if (level() == null || level().isClientSide() || seatIndex < 0) {
            return null
        }
        var seatEntity = resolveSeatHitbox(seatIndex)
        if (seatEntity != null) {
            seatEntity.attachToTrain(this, seatIndex)
            val seatWorld = getSeatWorldPosition(seatIndex)
            seatEntity.setPos(seatWorld.x, seatWorld.y, seatWorld.z)
            seatEntity.setYRot(getSeatWorldYaw(seatIndex))
            return seatEntity
        }
        seatEntity = RealTrainModRenewedEntities.TRAIN_SEAT.get().create(level(), EntitySpawnReason.SPAWN_ITEM_USE)
        if (seatEntity == null) {
            return null
        }
        seatEntity.attachToTrain(this, seatIndex)
        val seatWorld = getSeatWorldPosition(seatIndex)
        seatEntity.setPos(seatWorld.x, seatWorld.y, seatWorld.z)
        seatEntity.setYRot(getSeatWorldYaw(seatIndex))
        level().addFreshEntity(seatEntity)
        seatHitboxUuids.put(seatIndex, seatEntity.getUUID())
        return seatEntity
    }

    private fun resolveSeatHitbox(seatIndex: Int): TrainSeatEntity? {
        if (level() == null || level().isClientSide()) {
            return null
        }
        val uuid = seatHitboxUuids.get(seatIndex)
        if (uuid == null) {
            return null
        }
        if (level() is ServerLevel) {
            val entity: Entity? = serverLevel.getEntity(uuid)
            if (entity is TrainSeatEntity && entity.isAlive()) {
                return entity
            }
        }
        return null
    }

    private fun resolveBogieHitbox(bogieIndex: Int): TrainBogieEntity? {
        if (level() == null || level().isClientSide()) {
            return null
        }
        val uuid = bogieHitboxUuids.get(bogieIndex)
        if (uuid == null) {
            return null
        }
        if (level() is ServerLevel) {
            val entity: Entity? = serverLevel.getEntity(uuid)
            if (entity is TrainBogieEntity && entity.isAlive()) {
                return entity
            }
        }
        return null
    }

    private fun discardBogieHitboxes() {
        for (bogieIndex in bogieHitboxUuids.keys.toList()) {
            val bogie = resolveBogieHitbox(bogieIndex)
            if (bogie != null) {
                bogie.discard()
            }
            bogieHitboxUuids.remove(bogieIndex)
        }
        if (level() is ServerLevel) {
            val searchBox = getBoundingBox().inflate(64.0)
            for (bogie in serverLevel.getEntitiesOfClass<TrainBogieEntity>(TrainBogieEntity::class.java, searchBox)) {
                if (bogie.belongsToTrain(this.getId()) || bogie.getTrain() == null) {
                    bogie.discard()
                }
            }
        }
    }

    private fun discardSeatHitboxes() {
        if (level() is ServerLevel) {
            for (uuid in seatHitboxUuids.values) {
                val entity: Entity? = serverLevel.getEntity(uuid)
                if (entity is TrainSeatEntity) {
                    entity.ejectPassengers()
                    entity.discard()
                }
            }
            val searchBox = getBoundingBox().inflate(64.0)
            for (seat in serverLevel.getEntitiesOfClass<TrainSeatEntity>(TrainSeatEntity::class.java, searchBox)) {
                if (seat.belongsToTrain(this.getId()) || seat.getTrain() == null) {
                    seat.ejectPassengers()
                    seat.discard()
                }
            }
        }
        seatHitboxUuids.clear()
    }

    @JvmRecord
    private data class BogieViewHit(val train: TrainEntity?, val bogieIndex: Int, val localHit: Vec3?)

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide()) {
            ejectPassengers()
            discardBogieHitboxes()
            discardSeatHitboxes()
            seatAssignments.clear()
            syncSeatAssignmentsToEntityData()
            // Invalidate Formation so remaining cars rebuild without this train on next tick
            if (formation != null) {
                formation!!.trainStream().forEach { t: TrainEntity? -> if (t != null && t !== this) t.formation = null }
                formation = null
            }
        }
        super.remove(reason)
    }

    override fun readAdditionalSaveData(tag: ValueInput) {
        this.vehicleId = tag.getStringOr("VehicleId", "")
        this.speed = tag.getFloatOr("Speed", 0.0f)
        if (tag.getString("TrainDistance").isPresent() || tag.getFloatOr(
                "TrainDistance",
                Float.NaN
            ) == tag.getFloatOr("TrainDistance", 0.0f)
        ) {
            this.trainDistance = tag.getFloatOr("TrainDistance", this.trainDistance)
            refreshDimensions()
        }
        if (tag.getInt("Notch").isPresent()) {
            this.notch = tag.getIntOr("Notch", 0)
        }
        if (tag.getInt("LightMode").isPresent()) this.lightMode = tag.getIntOr("LightMode", 0)
        else if (tag.getString("HeadlightOn").isPresent() || tag.getBooleanOr(
                "HeadlightOn",
                false
            )
        ) this.isHeadlightOn =
            tag.getBooleanOr("HeadlightOn", false)
        if (tag.getString("InteriorLightOn").isPresent() || tag.getBooleanOr(
                "InteriorLightOn",
                false
            )
        ) this.isInteriorLightOn =
            tag.getBooleanOr("InteriorLightOn", false)
        if (tag.getString("DoorOpen").isPresent() || tag.getBooleanOr("DoorOpen", false)) this.isDoorOpen =
            tag.getBooleanOr("DoorOpen", false)
        if (tag.getString("DoorLeftOpen").isPresent() || tag.getBooleanOr("DoorLeftOpen", false)) this.isDoorLeftOpen =
            tag.getBooleanOr("DoorLeftOpen", false)
        if (tag.getString("DoorRightOpen").isPresent() || tag.getBooleanOr(
                "DoorRightOpen",
                false
            )
        ) this.isDoorRightOpen =
            tag.getBooleanOr("DoorRightOpen", false)
        if (tag.getString("PantographUp").isPresent() || tag.getBooleanOr("PantographUp", false)) this.isPantographUp =
            tag.getBooleanOr("PantographUp", false)
        if (tag.getInt("Reverser").isPresent()) {
            this.reverser = tag.getIntOr("Reverser", 1)
        } else if (tag.getString("Reverse").isPresent() || tag.getBooleanOr("Reverse", false)) {
            this.isReverse = tag.getBooleanOr("Reverse", false)
        }
        if (tag.getInt("DestinationIndex").isPresent()) this.destinationIndex = tag.getIntOr("DestinationIndex", 0)
        if (tag.getInt("SoundIndex").isPresent()) this.soundIndex = tag.getIntOr("SoundIndex", 0)
        if (!java.lang.Float.isNaN(tag.getFloatOr("BodyRoll", Float.NaN))) this.bodyRoll =
            tag.getFloatOr("BodyRoll", 0.0f)
        this.mainReservoirPressure = tag.getFloatOr(
            "MainReservoirPressure",
            MAIN_RESERVOIR_NORMAL
        )
        this.brakePipePressure = tag.getFloatOr(
            "BrakePipePressure",
            BRAKE_PIPE_NORMAL
        )
        this.brakeCylinderPressure = tag.getFloatOr("BrakeCylinderPressure", 0.0f)
        if (tag.getInt("CustomButtonBits").isPresent()) this.customButtonBits = tag.getIntOr("CustomButtonBits", 0)
        if (!java.lang.Float.isNaN(tag.getFloatOr("RailProgress", Float.NaN))) this.railProgress =
            tag.getFloatOr("RailProgress", 0.0f)
        if (tag.getInt("Signal").isPresent()) setLegacySignalState(tag.getIntOr("Signal", 0))
        tag.getString("CoupledFollower").ifPresent(Consumer { value: String? ->
            try {
                setCoupledFollowerUuid(UUID.fromString(value))
            } catch (ignored: Exception) {
                setCoupledFollowerUuid(null)
            }
        })
        tag.getString("CoupledLeader").ifPresent(Consumer { value: String? ->
            try {
                setCoupledLeaderUuid(UUID.fromString(value))
            } catch (ignored: Exception) {
                setCoupledLeaderUuid(null)
            }
        })
        coupledFollowerThisSide = tag.getInt("CoupledFollowerThisSide")
            .map<Int>(Function { side: Int -> Companion.normalizeCouplerSide(side) }).orElse(-1)
        coupledFollowerOtherSide = tag.getInt("CoupledFollowerOtherSide")
            .map<Int>(Function { side: Int -> Companion.normalizeCouplerSide(side) }).orElse(1)

        seatAssignments.clear()
        scriptData.clear()
        tag.read<CompoundTag>("SeatAssignments", CompoundTag.CODEC).ifPresent(Consumer { assignments: CompoundTag ->
            val def = getById(
                this.vehicleId
            )
            val seatCount = getSeatCount(def)
            for (key in assignments!!.keySet()) {
                try {
                    val uuid = UUID.fromString(key)
                    val seatIndex = assignments.getIntOr(key, 0)
                    if (seatCount <= 0) {
                        seatAssignments.put(uuid, 0)
                    } else if (seatIndex < 0) {
                        seatAssignments.put(uuid, 0)
                    } else if (seatIndex >= seatCount) {
                        seatAssignments.put(uuid, seatCount - 1)
                    } else {
                        seatAssignments.put(uuid, seatIndex)
                    }
                } catch (e: IllegalArgumentException) {
                    // ignore malformed UUIDs
                }
            }
        })
        tag.read<CompoundTag>("ScriptData", CompoundTag.CODEC).ifPresent(Consumer { scriptDataTag: CompoundTag ->
            for (key in scriptDataTag!!.keySet()) {
                scriptData.put(key, scriptDataTag.getStringOr(key, ""))
            }
        })
    }

    override fun addAdditionalSaveData(tag: ValueOutput) {
        tag.putString("VehicleId", this.vehicleId!!)
        tag.putFloat("Speed", this.speed)
        tag.putFloat("TrainDistance", this.trainDistance)
        tag.putInt("Notch", this.notch)
        tag.putBoolean("HeadlightOn", this.isHeadlightOn)
        tag.putBoolean("DoorOpen", this.isDoorOpen)
        tag.putBoolean("DoorLeftOpen", this.isDoorLeftOpen)
        tag.putBoolean("DoorRightOpen", this.isDoorRightOpen)
        tag.putInt("LightMode", this.lightMode)
        tag.putBoolean("InteriorLightOn", this.isInteriorLightOn)
        tag.putBoolean("PantographUp", this.isPantographUp)
        tag.putBoolean("Reverse", this.isReverse)
        tag.putInt("Reverser", this.reverser)
        tag.putInt("DestinationIndex", this.destinationIndex)
        tag.putInt("SoundIndex", this.soundIndex)
        tag.putFloat("BodyRoll", this.bodyRoll)
        tag.putFloat("MainReservoirPressure", this.mainReservoirPressure)
        tag.putFloat("BrakePipePressure", this.brakePipePressure)
        tag.putFloat("BrakeCylinderPressure", this.brakeCylinderPressure)
        tag.putInt("CustomButtonBits", this.customButtonBits)
        tag.putFloat("RailProgress", this.railProgress)
        tag.putInt("Signal", this.signal)
        if (coupledFollowerUuid != null) {
            tag.putString("CoupledFollower", coupledFollowerUuid.toString())
            tag.putInt("CoupledFollowerThisSide", coupledFollowerThisSide)
            tag.putInt("CoupledFollowerOtherSide", coupledFollowerOtherSide)
        }
        if (coupledLeaderUuid != null) {
            tag.putString("CoupledLeader", coupledLeaderUuid.toString())
        }

        if (!seatAssignments.isEmpty()) {
            val assignments = CompoundTag()
            seatAssignments.forEach { (uuid: UUID?, seatIndex: Int?) ->
                assignments.putInt(
                    uuid.toString(),
                    seatIndex!!
                )
            }
            tag.store<CompoundTag>("SeatAssignments", CompoundTag.CODEC, assignments)
        }
        if (!scriptData.isEmpty()) {
            val scriptDataTag = CompoundTag()
            scriptData.forEach { (name: String, value: String) -> scriptDataTag.putString(name, value) }
            tag.store<CompoundTag>("ScriptData", CompoundTag.CODEC, scriptDataTag)
        }
    }

    override fun positionRider(passenger: Entity, moveFunction: MoveFunction) {
        if (!this.hasPassenger(passenger)) {
            return
        }

        val seatPos = getPassengerRidingPosition(passenger)
        moveFunction.accept(passenger, seatPos.x, seatPos.y, seatPos.z)
        // 視点固定しすぎない（左右確認できるようにする）
        if (passenger is LivingEntity) {
            passenger.setYBodyRot(this.getYRot())
        }
    }

    val isRailGuided: Boolean
        // ---- Missing methods restored ----
        get() = activeRailMap != null && activeRailSplit > 0 && activeRailPosition >= 0.0

    fun clearRailGuidance() {
        activeRailMap = null
        activeRailSplit = 0
        activeRailPosition = -1.0
        frontRailAnchor = null
        rearRailAnchor = null
    }

    private var couplingSettleWindowEnd = Long.MIN_VALUE
    private var uncoupledContactStopWindowEnd = Long.MIN_VALUE

    val isWithinCouplingSettleWindow: Boolean
        get() = level() != null && level().getGameTime() <= couplingSettleWindowEnd

    fun markCouplingSettleWindow(durationTicks: Long) {
        if (level() != null) couplingSettleWindowEnd = level().getGameTime() + durationTicks
    }

    val isWithinUncoupledContactStopWindow: Boolean
        get() = level() != null && level().getGameTime() <= uncoupledContactStopWindowEnd

    fun markUncoupledContactStopWindow(durationTicks: Long) {
        if (level() != null) uncoupledContactStopWindowEnd = level().getGameTime() + durationTicks
    }

    fun settleCoupledRailPose() {
        if (this.isRailGuided && isRailAnchorUsable(frontRailAnchor) && isRailAnchorUsable(rearRailAnchor)) {
            val front = sampleBogieRail(frontRailAnchor!!.map, frontRailAnchor!!.split, frontRailAnchor!!.index)
            val rear = sampleBogieRail(rearRailAnchor!!.map, rearRailAnchor!!.split, rearRailAnchor!!.index)
            applyPoseFromBogieSamples(front, rear, getYRot(), getXRot(), false)
        }
    }

    fun settleConnectedFormationToRail() {
        forEachFormationTrain(Consumer { t: TrainEntity? -> if (t!!.isRailGuided) t.settleCoupledRailPose() })
    }

    private var stableRailMap: RailMap? = null
    private var stableRailSplit = 0
    private var stableRailPosition = 0.0
    private var stableRailBodyDirection = 0
    private var stableFrontAnchor: RailAnchor? = null
    private var stableRearAnchor: RailAnchor? = null

    init {
        this.setNoGravity(true)
        // noCulling は false。描画カリングは TrainEntityRenderer.shouldRender が車両長ベースの
        // 広い AABB でフラスタム判定するので、画面外の車両は描画(JS実行含む)がスキップされる。
        // true にするとバニラ経路で常時描画扱いになりうるため false にしておく。
    }

    fun rememberConnectedFormationStableRailState() {
        forEachFormationTrain(Consumer { t: TrainEntity? ->
            if (t!!.isRailGuided) {
                t.stableRailMap = t.activeRailMap
                t.stableRailSplit = t.activeRailSplit
                t.stableRailPosition = t.activeRailPosition
                t.stableRailBodyDirection = t.activeRailBodyDirection
                t.stableFrontAnchor = t.frontRailAnchor
                t.stableRearAnchor = t.rearRailAnchor
            }
        })
    }

    fun restoreLastStableRailState(): Boolean {
        if (stableRailMap != null && stableRailSplit > 0) {
            activeRailMap = stableRailMap
            activeRailSplit = stableRailSplit
            activeRailPosition = stableRailPosition
            activeRailBodyDirection = stableRailBodyDirection
            frontRailAnchor = stableFrontAnchor
            rearRailAnchor = stableRearAnchor
            return true
        }
        return false
    }

    fun restoreStableRailStateForFormation() {
        forEachFormationTrain(Consumer { obj: TrainEntity? -> obj!!.restoreLastStableRailState() })
    }

    private fun restoreRailState(
        map: RailMap?,
        split: Int,
        index: Int,
        position: Double,
        dir: Int,
        bodyDir: Int,
        front: RailAnchor?,
        rear: RailAnchor?
    ) {
        activeRailMap = map
        activeRailSplit = split
        activeRailIndex = index
        activeRailPosition = position
        activeRailDirection = dir
        activeRailBodyDirection = bodyDir
        frontRailAnchor = front
        rearRailAnchor = rear
    }

    private fun railsShareEndpoint(a: RailMap?, b: RailMap?): Boolean {
        if (a == null || b == null) return false
        return sameRailEndpoint(a.startRP, b.startRP)
                || sameRailEndpoint(a.startRP, b.endRP)
                || sameRailEndpoint(a.endRP, b.startRP)
                || sameRailEndpoint(a.endRP, b.endRP)
    }

    fun resolveCoupledTrain(uuid: UUID?): TrainEntity? {
        return resolveTrainByUuid(uuid)
    }

    val coupledLeader: TrainEntity?
        get() = resolveTrainByUuid(this.displayLeaderUuid)

    val coupledFollower: TrainEntity?
        get() = resolveTrainByUuid(this.displayFollowerUuid)

    private val formationTrainsInOrder: MutableList<TrainEntity?>
        get() {
            val result: MutableList<TrainEntity?> =
                ArrayList<TrainEntity?>()
            var head: TrainEntity? = this
            var guard = 0
            while (head!!.coupledLeaderUuid != null && guard++ < 16) {
                val leader =
                    head.resolveCoupledTrain(head.coupledLeaderUuid)
                if (leader == null) {
                    head.setCoupledLeaderUuid(null)
                    break
                }
                head = leader
            }
            var cur = head
            guard = 0
            while (cur != null && guard++ < 16) {
                result.add(cur)
                if (cur.coupledFollowerUuid == null) break
                val follower =
                    cur.resolveCoupledTrain(cur.coupledFollowerUuid)
                if (follower == null) {
                    cur.setCoupledFollowerUuid(null)
                    break
                }
                cur = follower
            }
            return result
        }

    private val configuredTrainDistance: Double
        get() = this.trainHalfLength * 2.0

    fun getDefaultDistanceToConnectedTrain(other: TrainEntity?): Double {
        return getCoupledGap(this, if (other == null) this else other)
    }

    private fun ensureDriverReadyForFormation(driver: Entity?) {
        // stub — driver seat handling not needed for rail movement
    }

    fun setReverserForFormation(value: Int) {
        forEachFormationTrain(Consumer { t: TrainEntity? ->
            t!!.reverser = value
        })
    }

    // ---- Formation field setters/methods ----
    fun setFormation(f: Formation?) {
        this.formation = f
    }

    /**
     * Builds (or rebuilds) this train's Formation from the UUID-linked chain.
     * Called lazily on first tick for head/solo cars, and after coupling/decoupling.
     */
    private fun rebuildFormationFromUuidChain() {
        if (level().isClientSide()) return
        val chain =
            this.formationTrainsInOrder
        if (chain.isEmpty()) {
            getInstance().createNewFormation(this)
            return
        }
        if (chain.size == 1) {
            if (formation == null) {
                getInstance().createNewFormation(this)
            }
            return
        }
        val fid = getInstance().getNewId()
        val f =
            Formation(fid, chain.size)
        var entryDirection = 0
        for (i in chain.indices) {
            val t: TrainEntity = chain.get(i)!!
            var leaderSide = -1
            var followerSide = 1
            if (i > 0) {
                val prev: TrainEntity = chain.get(i - 1)!!
                leaderSide = normalizeCouplerSide(prev.coupledFollowerThisSide)
                followerSide = normalizeCouplerSide(prev.coupledFollowerOtherSide)
                if (leaderSide == followerSide) {
                    entryDirection = 1 - entryDirection
                }
            }
            f.entries[i] = FormationEntry(t, i, entryDirection, leaderSide, followerSide)
            t.formation = f
        }
    }

    private fun refreshInteractionHitboxes(force: Boolean = false) {
        val movingEnoughToMissInteractionHitboxes = abs(speed) > 0.02f
        if (!force && !movingEnoughToMissInteractionHitboxes && interactionHitboxRefreshCooldown-- > 0) {
            return
        }
        ensureBogieHitboxes()
        ensureSeatHitboxes()
        interactionHitboxRefreshCooldown = if (movingEnoughToMissInteractionHitboxes || force) 0 else 20
    }

    fun moveAsFormationFollower(leader: TrainEntity?, leaderSide: Int, followerSide: Int, speed: Float) {
        if (leader == null || level().isClientSide()) return
        // テレポート・ガード: フォロワーがレール追従に失敗した時のフォールバック moveTo は、
        // 中間車のレール状態が未確立だと遠方(他車の上)へ飛ぶことがある。連結中の正当な
        // 微調整を超える大ジャンプは棄却し、本体を元位置に留める(本家は台車を保持して飛ばさない)。
        val preX = getX()
        val preY = getY()
        val preZ = getZ()
        val placed = leader.placeCoupledFollowerOnRail(this, leaderSide, followerSide)
        if (!placed) {
            val gap: Double = getCoupledGap(leader, this)
            leader.placeCoupledFollowerFallback(this, leaderSide, followerSide, gap)
            val jumpX = getX() - preX
            val jumpZ = getZ() - preZ
            val maxJump = max(8.0, leader.getDefaultDistanceToConnectedTrain(this) + 2.0)
            if (jumpX * jumpX + jumpZ * jumpZ > maxJump * maxJump) {
                // ありえない大移動 → テレポート扱いで棄却し元の位置を維持。
                setPos(preX, preY, preZ)
                setRot(getYRot(), getXRot())
            }
            clearRailGuidance()
        } else {
            centerGuidanceFallbackTicks = 0
            railGuidanceFailureTicks = 0
            travelStallTicks = 0
            settleCoupledRailPose()
        }
        this.speed = speed
        this.notch = leader.notch
        this.reverser = leader.reverser
        setDeltaMovement(getX() - preX, getY() - preY, getZ() - preZ)
        this.hurtMarked = true
        this.hurtMarked = true
        refreshInteractionHitboxes(force = true)
    }

    // ---- Custom button multi-value storage ----
    fun getCustomButtonValue(index: Int): Int {
        if (index < 0 || index >= 16) return 0
        if (customButtonValues == null || index >= customButtonValues!!.size) {
            return if (isCustomButtonOn(index)) 1 else 0
        }
        return customButtonValues!![index]
    }

    fun setCustomButtonValue(index: Int, value: Int) {
        if (index < 0 || index >= 16) return
        if (customButtonValues == null) customButtonValues = IntArray(16)
        customButtonValues!![index] = value
        setCustomButton(index, value != 0)
        scriptData.put("Button" + index, value.toString())
        scriptData.put("button" + index, value.toString())
        scriptData.put("CustomButton" + index, value.toString())
        scriptData.put("customButton" + index, value.toString())
        scriptDataDirty = true
    }

    // ---- Previous-frame bogie world position (for bogie entity xo/yo/zo sync) ----
    fun getBogieEntityWorldPositionPrev(bogieIndex: Int): Vec3 {
        val def = getById(
            this.vehicleId
        )
        val local = getBogieLocalPosition(bogieIndex, def)
        return localToWorldPrev(local)
    }

    private fun localToWorldPrev(local: Vec3): Vec3 {
        var def = getById(
            this.vehicleId
        )
        if (def == null) def = getSelected()
        val offset = if (def != null) def.getModelOffset() else Vec3.ZERO
        val scale = if (def != null) def.getModelScale().toDouble() else 1.0
        val yawRad = Math.toRadians(-this.yRotO.toDouble())
        val rotatedX = cos(yawRad) * local.x - sin(yawRad) * local.z
        val rotatedZ = sin(yawRad) * local.x + cos(yawRad) * local.z
        val offsetX = cos(yawRad) * offset.x - sin(yawRad) * offset.z
        val offsetZ = sin(yawRad) * offset.x + cos(yawRad) * offset.z
        return Vec3(
            this.xo + offsetX + rotatedX * scale,
            this.yo + offset.y + local.y * scale,
            this.zo + offsetZ + rotatedZ * scale
        )
    }

    companion object {
        private val VEHICLE_ID =
            SynchedEntityData.defineId<String>(TrainEntity::class.java, EntityDataSerializers.STRING)
        private val SPEED = SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val TRAIN_DISTANCE =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val NOTCH = SynchedEntityData.defineId<Int>(TrainEntity::class.java, EntityDataSerializers.INT)
        private val HEADLIGHT_ON =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val DOOR_OPEN =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val DOOR_LEFT_OPEN =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val DOOR_RIGHT_OPEN =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val LIGHT_MODE = SynchedEntityData.defineId<Int>(TrainEntity::class.java, EntityDataSerializers.INT)
        private val PANTOGRAPH_UP =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val REVERSE =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val REVERSER = SynchedEntityData.defineId<Int>(TrainEntity::class.java, EntityDataSerializers.INT)
        private val DESTINATION_INDEX =
            SynchedEntityData.defineId<Int>(TrainEntity::class.java, EntityDataSerializers.INT)
        private val SOUND_INDEX = SynchedEntityData.defineId<Int>(TrainEntity::class.java, EntityDataSerializers.INT)
        private val BODY_ROLL = SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val MAIN_RESERVOIR_PRESSURE =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val BRAKE_PIPE_PRESSURE =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val BRAKE_CYLINDER_PRESSURE =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val CUSTOM_BUTTON_BITS =
            SynchedEntityData.defineId<Int>(TrainEntity::class.java, EntityDataSerializers.INT)
        private val RAIL_PROGRESS =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val SIGNAL = SynchedEntityData.defineId<Int>(TrainEntity::class.java, EntityDataSerializers.INT)
        private val SEAT_ASSIGNMENTS =
            SynchedEntityData.defineId<String>(TrainEntity::class.java, EntityDataSerializers.STRING)
        private val COUPLED_FOLLOWER =
            SynchedEntityData.defineId<String>(TrainEntity::class.java, EntityDataSerializers.STRING)
        private val COUPLED_LEADER =
            SynchedEntityData.defineId<String>(TrainEntity::class.java, EntityDataSerializers.STRING)
        private val INTERIOR_LIGHT_ON =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)

        // 前後(端)台車のワールド位置を「エンティティ位置からのオフセット」でサーバー→クライアント同期する。
        // クライアントは movement(travelAlongRail)を走らせずレールマップも持たないため、これが無いと
        // 台車を剛体(弦上)で描画してカーブでレールからズレる。サーバーの実台車位置を同期して正確に描く。
        private val FRONT_BOGIE_DX =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val FRONT_BOGIE_DY =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val FRONT_BOGIE_DZ =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val REAR_BOGIE_DX =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val REAR_BOGIE_DY =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val REAR_BOGIE_DZ =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)

        /** 端台車のワールド位置同期が有効か(サーバーがレール上に乗っている間のみ true)。  */
        private val BOGIE_SYNC_VALID =
            SynchedEntityData.defineId<Boolean>(TrainEntity::class.java, EntityDataSerializers.BOOLEAN)

        // 端台車のレール接線ヨー(度)もサーバーから同期する。クライアントで毎フレーム探索計算すると
        // 別レールを拾う/180°反転で「一瞬明日の方向」になるため、サーバーのアンカー接線を正とする(RTM同様)。
        private val FRONT_BOGIE_YAW =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)
        private val REAR_BOGIE_YAW =
            SynchedEntityData.defineId<Float>(TrainEntity::class.java, EntityDataSerializers.FLOAT)

        private const val ACCEL = 0.012f
        private const val BRAKE = 0.013f // 減速度
        private const val MAX_SPEED = 2.0f

        // 慣性の法則: 力行/制動を解いたあとも勢いが長く続くよう、転がり抵抗と
        // 空気抵抗を実車に近い小さい値に抑える。FRICTION は等比減衰、DRAG_* は
        // notch<=0 の時にだけ働く線形抵抗。
        private const val FRICTION = 0.9997f
        private const val DRAG_BASE = 0.00012f
        private const val DRAG_SPEED_FACTOR = 0.00018f
        private const val MAIN_RESERVOIR_NORMAL = 780.0f
        private const val BRAKE_PIPE_NORMAL = 490.0f
        private const val BRAKE_PIPE_SERVICE_DROP = 150.0f
        private const val BRAKE_CYLINDER_SERVICE_MAX = 420.0f
        private const val BRAKE_CYLINDER_EMERGENCY_MAX = 480.0f
        private const val BRAKE_APPLY_RATE = 13.0f
        private const val BRAKE_RELEASE_RATE = 8.0f
        private const val BRAKE_PIPE_RATE = 9.0f
        private const val MAIN_RESERVOIR_RATE = 2.5f
        private const val LEGACY_MAX_AIR_COUNT = 2880
        private const val LEGACY_MIN_AIR_COUNT = 2480

        // 本家RTM(EnumNotch)準拠: 力行 P1-P5(5段)、ブレーキ B1-B7 + 非常EB(-8) = 8段。
        private const val MAX_POWER_NOTCH = 5
        private const val MAX_BRAKE_NOTCH = 8

        // 本家EnumNotch: 力行ノッチごとの最高速 (P_n = 0.36*n)。ブレーキ各段の減速度。
        private const val RTM_POWER_SPEED_PER_NOTCH = 0.36f
        private const val MOVEMENT_SMOOTHING = 0.22f
        private const val RAIL_SEARCH_RADIUS = 12.0
        private const val BOGIE_CLICK_RADIUS_SQ = 1.96
        private const val BOGIE_INTERACT_HALF_WIDTH = 0.72
        private const val BOGIE_INTERACT_HALF_HEIGHT = 1.1
        private const val BOGIE_INTERACT_HALF_LENGTH = 1.18
        private const val RTM_VEHICLE_Y_OFFSET = 1.1875

        /** BogieRenderer 描画台車(MQO台車モデル車両)の高さ微調整。ごく薄いZファイト/埋まりだけを避ける。  */
        private const val BOGIE_RENDER_LIFT = 0.02
        private const val DEFAULT_HALF_WIDTH = 1.35
        private const val DEFAULT_HALF_HEIGHT = 2.2
        private const val TRAIN_BODY_MARGIN = 1.2
        private const val COUPLED_CLEARANCE = -0.06
        private const val COUPLER_CONTACT_DISTANCE = 0.35
        private const val COUPLER_CONTACT_SCAN_MARGIN = 6.0
        private const val BOGIE_SPAN_TOLERANCE = 1.75
        private const val RAIL_CONNECTION_MAX_DISTANCE_SQ = 0.25
        private const val RAIL_CONNECTION_MAX_YAW_DIFF = 20.0f

        // 1tickの本体中心移動の許容上限(=移動距離 + この余裕)。これを超えるジャンプは
        // 逆向き継ぎ目等でのレール選択不安定によるワープとみなし棄却する。MAX_SPEED(2.0)に
        // カーブ補間の揺れ余裕を足した値。通常走行・カーブでは到達しない。
        private const val RAIL_TELEPORT_TOLERANCE = 3.0

        // RailMap.getRailHeight() はすでにワールド軌面Yを返す。車体中心だけ RTM 本家と同じ
        // 1.1875 を足し、sample 側では高さを二重に加算しない。
        private const val TRAIN_BODY_HEIGHT_OFFSET = RTM_VEHICLE_Y_OFFSET
        const val BOGIE_VISUAL_LIFT: Double = 0.39
        private const val BODY_HITBOX_SIZE = 0.1f
        private const val BOGIE_SPLITS_PER_METER = 48

        private val COUPLING_MODE: MutableMap<UUID?, CouplingSelection?> = HashMap<UUID?, CouplingSelection?>()
        @JvmStatic
        fun create(
            level: Level,
            vehicleId: String?,
            x: Double,
            y: Double,
            z: Double,
            yRot: Float,
            trainDistance: Float
        ): TrainEntity? {
            val e = RealTrainModRenewedEntities.TRAIN.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE)
            if (e == null) return null
            e.vehicleId = vehicleId
            e.trainDistance = trainDistance
            e.lightMode = 0
            // スポーン位置を RTM の車体中心基準に合わせる。通常は直後に initializeOnRail で
            // 台車基準から再配置されるが、プレビュー等でも同じ高さを使う。
            e.setPos(x, y + TRAIN_BODY_HEIGHT_OFFSET, z)
            e.setRot(yRot, 0.0f)
            // 前tick位置(xo/yo/zo)を現在位置に揃える。これをしないとスポーン直後の
            // 描画補間が原点(0,0,0)から行われ、台車のワールド位置計算が大きくズレる
            // (=「列車を出した瞬間だけ台車がズレる/動かすと直る」の原因)。
            e.setOldPosAndRot()
            e.refreshDimensions()

            // スクリプトはMqoModelLoaderでロードされるため、ここではロードしない
            return e
        }

        private fun parseUuid(value: String?): UUID? {
            if (value == null || value.isBlank()) {
                return null
            }
            try {
                return UUID.fromString(value)
            } catch (ignored: IllegalArgumentException) {
                return null
            }
        }

        private fun getCoupledGap(front: TrainEntity?, rear: TrainEntity?): Double {
            val frontHalf = if (front == null) 4.5 else front.couplingHalfLength
            val rearHalf = if (rear == null) frontHalf else rear.couplingHalfLength
            // 車体端どうし + 余裕。固定の最小値(旧:4.0)で底上げしないので短い車両は詰まる。
            return max(2.0, frontHalf + rearHalf + COUPLED_CLEARANCE)
        }

        private fun normalizeCouplerSide(side: Int): Int {
            return if (side >= 0) 1 else -1
        }

        /** [RTM-DBG] レールマップの始点/終点ブロック座標を文字列化(分岐遷移の診断用)。  */
        private fun railEndpoints(m: RailMap): String {
            try {
                val s = m.startRP
                val e = m.endRP
                return "[" + s.blockX + "," + s.blockY + "," + s.blockZ + "->" + e.blockX + "," + e.blockY + "," + e.blockZ + "]"
            } catch (t: Throwable) {
                return "[?]"
            }
        }

        private fun shouldCenterAsymmetricBogieAnchors(def: VehicleDefinition?): Boolean {
            if (def == null) {
                return false
            }
            val id = if (def.getId() == null) "" else def.getId().lowercase()
            val model = if (def.getModelFile() == null) "" else def.getModelFile().lowercase()
            var classBogie = false
            for (bogie in def.getBogies()) {
                val bogieModel = if (bogie.modelFile() == null) "" else bogie.modelFile().lowercase()
                if (bogieModel.endsWith(".class")) {
                    classBogie = true
                    break
                }
            }
            return classBogie && (id.contains("tkmtp") || id.contains("c56") || model.contains("tkmtp") || model.contains(
                "c56"
            ))
        }

        /** 本家RTM EntityBogie.fixBogieYaw 準拠: yaw2 を reference と同じ向き(90°以内)に整える。  */
        private fun fixBogieYaw(reference: Float, yaw2: Float): Float {
            val diff = abs(Mth.wrapDegrees(reference - yaw2))
            return Mth.wrapDegrees(if (diff > 90.0f) yaw2 + 180.0f else yaw2)
        }

        private fun areCoupledTrainsEffectivelyStationary(first: TrainEntity?, second: TrainEntity?): Boolean {
            if (first == null || second == null) return false
            return abs(first.speed) < 0.01f && abs(second.speed) < 0.01f && first.getDeltaMovement()
                .horizontalDistanceSqr() < 1e-4 && second.getDeltaMovement().horizontalDistanceSqr() < 1e-4
        }

        /** 2編成間で連結モード(プレイヤー選択)がアクティブか。接触ブレーキ抑止の判定に使う。  */
        private fun isCouplingModeActiveBetween(a: TrainEntity?, b: TrainEntity?): Boolean {
            if (COUPLING_MODE.isEmpty() || a == null || b == null) {
                return false
            }
            val aSet: MutableSet<UUID?> = HashSet<UUID?>()
            a.forEachFormationTrain(Consumer { t: TrainEntity? -> if (t != null) aSet.add(t.getUUID()) })
            val bSet: MutableSet<UUID?> = HashSet<UUID?>()
            b.forEachFormationTrain(Consumer { t: TrainEntity? -> if (t != null) bSet.add(t.getUUID()) })
            for (s in COUPLING_MODE.values) {
                if (s == null) {
                    continue
                }
                val firstInA = s.first != null && aSet.contains(s.first)
                val firstInB = s.first != null && bSet.contains(s.first)
                val secondInA = s.second != null && aSet.contains(s.second)
                val secondInB = s.second != null && bSet.contains(s.second)
                if ((firstInA && secondInB) || (firstInB && secondInA)) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun tryEnterCouplingModeFromPlayerView(player: ServerPlayer?): Boolean {
            if (player == null || player.getVehicle() != null || player.isSecondaryUseActive()) {
                return false
            }
            val hit: BogieViewHit? = findBogieHitFromPlayerView(player)
            if (hit == null || hit.train == null) {
                return false
            }
            hit.train.enterCouplingMode(player, hit.bogieIndex)
            return true
        }

        @JvmStatic
        fun tryRideFromPlayerView(player: ServerPlayer?): Boolean {
            if (player == null || player.getVehicle() != null || player.isSecondaryUseActive()) {
                return false
            }
            val hit: BogieViewHit? = findBogieHitFromPlayerView(player)
            if (hit == null || hit.train == null) {
                return false
            }
            val def = getById(
                hit.train.vehicleId
            )
            if (def == null) {
                return false
            }
            var seatIndex = hit.train.resolveSeatIndexForBogieClick(def, hit.bogieIndex)
            if (seatIndex < 0) {
                seatIndex = hit.train.findNearestSeatToLocalClick(def, hit.localHit!!)
            }
            return hit.train.tryRideWithSeat(player, seatIndex).consumesAction()
        }

        private fun findBogieHitFromPlayerView(player: ServerPlayer): BogieViewHit? {
            val eye = player.getEyePosition()
            val look = player.getLookAngle()
            val reach = 12.0
            val searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(8.0)
            var bestTrain: TrainEntity? = null
            var bestLocalHit: Vec3? = null
            var bestBogieIndex = -1
            var bestT = Double.MAX_VALUE

            for (train in player.level().getEntitiesOfClass<TrainEntity>(TrainEntity::class.java, searchBox)) {
                if (!train!!.isAlive()) {
                    continue
                }
                val def = getById(
                    train.vehicleId
                )
                val localStart = train.worldToLocal(eye)
                val localEnd = train.worldToLocal(eye.add(look.scale(reach)))
                val bogies = train.getInteractionBogieCenters(def)
                for (bogieIndex in bogies.indices) {
                    val bogie = bogies.get(bogieIndex)
                    val t: Double? = intersectSegmentAabb(
                        localStart,
                        localEnd,
                        bogie.x - BOGIE_INTERACT_HALF_WIDTH,
                        bogie.y - 0.6,
                        bogie.z - BOGIE_INTERACT_HALF_LENGTH,
                        bogie.x + BOGIE_INTERACT_HALF_WIDTH,
                        bogie.y + BOGIE_INTERACT_HALF_HEIGHT,
                        bogie.z + BOGIE_INTERACT_HALF_LENGTH
                    )
                    if (t != null && t < bestT) {
                        bestT = t
                        bestTrain = train
                        bestLocalHit = localStart.add(localEnd.subtract(localStart).scale(t))
                        bestBogieIndex = bogieIndex
                    }
                }
            }

            if (bestTrain == null || bestLocalHit == null) {
                return null
            }
            return BogieViewHit(bestTrain, bestBogieIndex, bestLocalHit)
        }

        private fun intersectSegmentAabb(
            start: Vec3,
            end: Vec3,
            minX: Double,
            minY: Double,
            minZ: Double,
            maxX: Double,
            maxY: Double,
            maxZ: Double
        ): Double? {
            var tMin = 0.0
            var tMax = 1.0
            val s = doubleArrayOf(start.x, start.y, start.z)
            val d = doubleArrayOf(end.x - start.x, end.y - start.y, end.z - start.z)
            val min = doubleArrayOf(minX, minY, minZ)
            val max = doubleArrayOf(maxX, maxY, maxZ)
            for (i in 0..2) {
                if (abs(d[i]) < 1.0E-8) {
                    if (s[i] < min[i] || s[i] > max[i]) {
                        return null
                    }
                    continue
                }
                val inv = 1.0 / d[i]
                var t1 = (min[i] - s[i]) * inv
                var t2 = (max[i] - s[i]) * inv
                if (t1 > t2) {
                    val tmp = t1
                    t1 = t2
                    t2 = tmp
                }
                tMin = max(tMin, t1)
                tMax = min(tMax, t2)
                if (tMin > tMax) {
                    return null
                }
            }
            return tMin
        }

        private fun getMaxPowerNotch(def: VehicleDefinition?): Int {
            if (def != null && !def.getNotchMaxSpeeds().isEmpty()) {
                return Mth.clamp(def.getNotchMaxSpeeds().size, 1, 12)
            }
            return MAX_POWER_NOTCH
        }

        @JvmStatic
        fun purgeDanglingTrainResidue(serverLevel: ServerLevel?, bounds: AABB?) {
            if (serverLevel == null || bounds == null) {
                return
            }
            for (bogie in serverLevel.getEntitiesOfClass<TrainBogieEntity>(
                TrainBogieEntity::class.java,
                bounds.inflate(2.0)
            )) {
                val train = bogie!!.getTrain()
                if (train == null || !train.isAlive() || train.isRemoved()) {
                    bogie.discard()
                }
            }
            for (seat in serverLevel.getEntitiesOfClass<TrainSeatEntity>(
                TrainSeatEntity::class.java,
                bounds.inflate(2.0)
            )) {
                val train = seat!!.getTrain()
                if (train == null || !train.isAlive() || train.isRemoved()) {
                    seat.ejectPassengers()
                    seat.discard()
                }
            }
        }

        @JvmStatic
        fun clearCouplingModes() {
            COUPLING_MODE.clear()
        }

        /**
         * 連結が成立したら、その2両（および各編成の全車両）を選択中の連結モードを解除する。
         * プレイヤー操作・接触連結など、どの経路で連結しても確実にモードが消えるようにする。
         */
        private fun clearCouplingModeInvolving(a: TrainEntity?, b: TrainEntity?) {
            if (COUPLING_MODE.isEmpty()) {
                return
            }
            val involved: MutableSet<UUID?> = HashSet<UUID?>()
            for (root in arrayOf<TrainEntity?>(a, b)) {
                if (root == null) {
                    continue
                }
                root.forEachFormationTrain(Consumer { t: TrainEntity? ->
                    if (t != null) {
                        involved.add(t.getUUID())
                    }
                })
                involved.add(root.getUUID())
            }
            COUPLING_MODE.entries.removeIf { e: MutableMap.MutableEntry<UUID?, CouplingSelection?>? ->
                val s = e!!.value
                s != null && (involved.contains(s.first) || involved.contains(s.second))
            }
        }

        private fun isHoldingTrainPlacementItem(player: Player?): Boolean {
            return player != null && (player.getMainHandItem().`is`(RealTrainModRenewedItems.TRAIN_ITEM.get())
                    || player.getOffhandItem().`is`(RealTrainModRenewedItems.TRAIN_ITEM.get())
                    || player.getMainHandItem().`is`(RealTrainModRenewedItems.TRAIN_VEHICLE_ITEM.get())
                    || player.getOffhandItem().`is`(RealTrainModRenewedItems.TRAIN_VEHICLE_ITEM.get())
                    )
        }

        private fun approachZero(value: Float, step: Float): Float {
            if (value > 0.0f) {
                return max(0.0f, value - step)
            }
            if (value < 0.0f) {
                return min(0.0f, value + step)
            }
            return 0.0f
        }

        private fun approach(value: Float, target: Float, step: Float): Float {
            if (value < target) {
                return min(target, value + step)
            }
            if (value > target) {
                return max(target, value - step)
            }
            return value
        }
    }
}


