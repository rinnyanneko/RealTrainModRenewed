package cc.mirukuneko.realtrainmodrenewed.entity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.network.CarScriptDataPayload
import cc.mirukuneko.realtrainmodrenewed.network.CarScriptDataSyncPayload
import cc.mirukuneko.realtrainmodrenewed.script.TrainScriptSystem
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.item.CrowbarItem
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import cc.mirukuneko.realtrainmodrenewed.util.RealTrainModRenewedConstants.SECONDS_IN_TICK
import cc.mirukuneko.realtrainmodrenewed.util.RealTrainModRenewedConstants.TICK_PER_SECOND
import cc.mirukuneko.realtrainmodrenewed.util.UnitConverter
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import javax.script.ScriptEngine
import kotlin.math.abs

class CarEntity(type: EntityType<out CarEntity>, level: Level) : Entity(type, level) {
    companion object {
        private val DATA_VEHICLE_ID: EntityDataAccessor<String> = SynchedEntityData.defineId(CarEntity::class.java, EntityDataSerializers.STRING)
        val WHEEL_X_COORD: Float = UnitConverter.cm2m(72.47766876220703f)
        private val RIDING_CAPACITY = 5
        val WHEEL_F_COORD: Float = UnitConverter.cm2m(158.62274169921875f)
        val WHEEL_R_COORD: Float = UnitConverter.cm2m(-164.98480224609375f)
        val WHEEL_Y_COORD: Float = UnitConverter.cm2m(37.28034973144531f)
        val WHEEL_RADIUS: Float = WHEEL_Y_COORD
        private val WHEELBASE: Float = WHEEL_F_COORD - WHEEL_R_COORD
        private val ACCELERATION: Float = UnitConverter.mpss2bpts(4.15f)
        private val DECELERATION: Float = ACCELERATION * 1.2f
        private val SLOWDOWN_DECELERATION: Float = 0.001f
        private val MAX_SPEED: Float = UnitConverter.kph2bpt(120.0f)
        private val SPEED_STOP_THRESHOLD: Float = 0.01f
        const val STEERING_RATIO: Float = 1 / 12.0f
        private const val STEERING_WHEEL_ANGULAR_VELOCITY_MANIPULATED: Float = 10.0f
        private const val STEERING_WHEEL_SELF_CENTERING_PARAMETER: Float = 2.0f
        private const val STEERING_WHEEL_MAX_ANGLE: Float = 630.0f
        private const val ACCELERATOR_STROKE_CHANGE_RATE: Float = 1.0f / TICK_PER_SECOND / 3.0f
        private const val BRAKE_STROKE_CHANGE_RATE: Float = 1.0f / TICK_PER_SECOND
    }

    // RTM compat fields
    @JvmField var field_70177_z: Float = 0f
    @JvmField var field_70125_A: Float = 0f
    @JvmField var field_70173_aa: Int = 0
    @JvmField val field_70170_p: CarWorldCompat = CarWorldCompat(this)
    @JvmField var field_70159_w: Double = 0.0
    @JvmField var field_70181_x: Double = 0.0
    @JvmField var field_70179_y: Double = 0.0

    var currentSteeringWheelAngle: Float = 0f
    var prevSteeringWheelAngle: Float = 0f
    var wheelRotation: Float = 0f
    var prevWheelRotation: Float = 0f
    var speed: Float = 0f

    private var serverScriptEngine: ScriptEngine? = null
    private var attemptedServerScriptLoad: Boolean = false
    private val scriptData: MutableMap<String, String> = HashMap()
    private var scriptDataDirty: Boolean = false
    private var acceleratorStroke: Float = 0f
    private var brakeStroke: Float = 0f
    private var isReversing: Boolean = false
    private var isBraking: Boolean = false
    private var prevWs: Float = 0f
    private var isReversalLocked: Boolean = false
    private var deltaYaw: Float = 0f

    var vehicleId: String
        get() = entityData.get(DATA_VEHICLE_ID)
        set(value) { entityData.set(DATA_VEHICLE_ID, value) }
    fun setVehicleId(id: String?) { vehicleId = id ?: "" }

    fun getScriptDataValue(key: String): String = scriptData[key] ?: ""
    fun setScriptDataValue(key: String?, value: String?) {
        if (key.isNullOrBlank()) return
        val v = value ?: ""
        val prev = scriptData.put(key, v)
        if (v != prev) scriptDataDirty = true
    }
    fun applyScriptDataSync(data: Map<String, String>?) { if (data != null) scriptData.putAll(data) }
    fun scriptDataMap(): Map<String, String> = scriptData

    // Legacy compat
    class CarWorldCompat(private val car: CarEntity) {
        @JvmField var field_72995_K: Boolean = false
        fun isClientSide(): Boolean { field_72995_K = car.level().isClientSide; return field_72995_K }
        fun getLevel(): Level = car.level()
        fun func_175625_s(pos: net.minecraft.core.BlockPos) = car.level().getBlockEntity(pos)
        fun func_175625_s(x: Double, y: Double, z: Double) = car.level().getBlockEntity(net.minecraft.core.BlockPos(x.toInt(), y.toInt(), z.toInt()))
        fun func_73045_a(id: Any?): Entity? {
            if (id == null) return null
            return try { car.level().getEntity((id as? Number)?.toInt() ?: id.toString().toInt()) } catch (_: Throwable) { null }
        }
        fun func_180495_p(pos: net.minecraft.core.BlockPos) = car.level().getBlockState(pos)
    }

    class ResourceStateCompat(car: CarEntity) { val dataMap: DataMapCompat = DataMapCompat(car) }
    class DataMapCompat(private val car: CarEntity) {
        fun getString(key: String): String = car.getScriptDataValue(key)
        fun getBoolean(key: String): Boolean = getString(key).let { it.equals("true", ignoreCase = true) || it == "1" }
        fun getInt(key: String): Int = try { getString(key).toInt() } catch (_: Exception) { 0 }
        fun getDouble(key: String): Double = try { getString(key).toDouble() } catch (_: Exception) { 0.0 }
        fun setString(key: String, value: String?, syncType: Int) = apply(key, value ?: "", syncType)
        fun setBoolean(key: String, value: Boolean, syncType: Int) = apply(key, value.toString(), syncType)
        fun setInt(key: String, value: Int, syncType: Int) = apply(key, value.toString(), syncType)
        fun setDouble(key: String, value: Double, syncType: Int) = apply(key, value.toString(), syncType)
        private fun apply(key: String, value: String, syncType: Int) {
            car.setScriptDataValue(key, value)
            if (syncType != 0 && car.level().isClientSide) {
                try { net.minecraft.client.Minecraft.getInstance().connection?.send(CarScriptDataPayload(car.id, key, value)) } catch (_: Throwable) { }
            }
        }
    }

    // RTM 1.12.2 MCP compat methods
    fun func_184188_bt(): List<Entity> = passengers
    fun func_184187_bx(): Entity? = vehicle
    fun func_184210_p() { stopRiding() }
    fun func_145782_y(): Int = id
    fun func_70106_y() { discard() }
    fun func_70107_b(x: Double, y: Double, z: Double) { setPos(x, y, z) }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) { builder.define(DATA_VEHICLE_ID, "") }
    override fun readAdditionalSaveData(tag: ValueInput) {
        setVehicleId(tag.getStringOr("VehicleId", "")); scriptData.clear()
        tag.read("ScriptData", CompoundTag.CODEC).ifPresent { sd -> for (key in sd.keySet()) scriptData[key] = sd.getStringOr(key, "") }
    }
    override fun addAdditionalSaveData(tag: ValueOutput) {
        tag.putString("VehicleId", vehicleId)
        if (scriptData.isNotEmpty()) { val sd = CompoundTag(); scriptData.forEach { (k, v) -> sd.putString(k, v) }; tag.store("ScriptData", CompoundTag.CODEC, sd) }
    }

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        if (canAddPassenger(player)) { player.startRiding(this); return InteractionResult.SUCCESS }
        return InteractionResult.PASS
    }
    override fun canAddPassenger(passenger: Entity): Boolean = passengers.size < RIDING_CAPACITY
    override fun getControllingPassenger(): LivingEntity? = passengers.firstOrNull() as? LivingEntity
    override fun getPassengerAttachmentPoint(passenger: Entity, dimensions: EntityDimensions, partialTick: Float): Vec3 {
        val idx = passengers.indexOf(passenger)
        val base = when (idx) {
            0 -> Vec3(-0.42, dimensions.height() * 0.2, 0.1)
            1 -> Vec3(0.42, dimensions.height() * 0.2, 0.1)
            2 -> Vec3(0.42, dimensions.height() * 0.2, -1.0)
            3 -> Vec3(-0.42, dimensions.height() * 0.2, -1.0)
            4 -> Vec3(0.0, dimensions.height() * 0.2, -1.0)
            else -> Vec3(0.0, dimensions.height() * 0.9, 0.0)
        }
        return base.yRot(Math.toRadians(-yRot.toDouble()).toFloat())
    }
    override fun positionRider(passenger: Entity, callback: MoveFunction) {
        super.positionRider(passenger, callback)
        if (passenger is Player) passenger.yRot += deltaYaw
    }
    override fun canCollideWith(entity: Entity): Boolean = true
    override fun isPushable(): Boolean = false
    override fun isPickable(): Boolean = true

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val player = source.entity as? Player ?: return false
        val held = player.mainHandItem
        if (!held.isEmpty && held.item !is CrowbarItem) return false
        if (passengers.isNotEmpty()) ejectPassengers()
        spawnAtLocation(level, ItemStack(RealTrainModRenewedItems.CAR_ITEM.get()))
        discard(); return true
    }

    override fun getAddEntityPacket(entity: ServerEntity): Packet<ClientGamePacketListener> = ClientboundAddEntityPacket(this, entity)

    private fun ensureServerScriptLoaded() {
        if (attemptedServerScriptLoad) return
        val id = vehicleId; if (id.isBlank()) { attemptedServerScriptLoad = true; return }
        val def = VehicleRegistry.getById(id)
        if (def == null || !def.hasServerScript()) { attemptedServerScriptLoad = true; return }
        attemptedServerScriptLoad = true
        try { serverScriptEngine = MqoModelLoader.loadServerScriptForVehicle(def) } catch (t: Throwable) { RealTrainModRenewed.LOGGER.warn("Failed to load server script for {}: {}", id, t.toString()) }
    }

    override fun tick() {
        super.tick()
        field_70177_z = yRot; field_70125_A = xRot; field_70173_aa = tickCount; field_70170_p.isClientSide()

        if (!level().isClientSide) {
            ensureServerScriptLoaded()
            if (serverScriptEngine != null) {
                TrainScriptSystem.invokeServerScriptOnUpdate(serverScriptEngine!!, this)
                yRot = field_70177_z; xRot = field_70125_A; yRotO = field_70177_z; xRotO = field_70125_A
            }
            if (scriptDataDirty && scriptData.isNotEmpty()) {
                scriptDataDirty = false
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, CarScriptDataSyncPayload(id, HashMap(scriptData)))
            }
        }

        if (level().isClientSide) {
            val hostId = getScriptDataValue("hostPlayerEntityId")
            if (hostId.isNotEmpty()) {
                try {
                    val host = level().getEntity(hostId.toInt())
                    if (host != null) {
                        setPos(host.x, host.y + 2.0, host.z); xOld = host.xOld; yOld = host.yOld + 2.0; zOld = host.zOld
                        xo = host.xo; yo = host.yo + 2.0; zo = host.zo; yRot = 0f; xRot = 0f; yRotO = 0f; xRotO = 0f
                    }
                } catch (_: Exception) { }
            }
        }

        prevSteeringWheelAngle = currentSteeringWheelAngle
        val driver = controllingPassenger
        if (driver is Player) handlePlayerInput(driver)
        else { updatePedals(); updateSteeringAngle() }
        updateSpeed(); applyMovement()
        if (level().isClientSide) updateWheelRotationInClient()
        move(MoverType.SELF, deltaMovement)
    }

    private fun handlePlayerInput(player: Player) {
        val wS = player.zza; val aD = player.xxa
        if (wS > 0f) {
            val justStartedW = prevWs <= 0f
            if (!isReversing) { brakeStroke = 0f; isBraking = false; acceleratorStroke = (acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE).coerceIn(0f, 1f) }
            else {
                acceleratorStroke = 0f
                if (isStopping() && justStartedW && !isReversalLocked) { isReversing = false; brakeStroke = 0f; acceleratorStroke = (acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE).coerceIn(0f, 1f) }
                else { if (speed >= -SPEED_STOP_THRESHOLD) isReversalLocked = true; brakeStroke = (brakeStroke + BRAKE_STROKE_CHANGE_RATE).coerceIn(0f, 1f) }
            }
        } else if (wS < 0f) {
            val justStartedS = prevWs >= 0f
            if (isReversing) { brakeStroke = 0f; isBraking = false; acceleratorStroke = (acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE).coerceIn(0f, 1f) }
            else {
                acceleratorStroke = 0f
                if (isStopping() && justStartedS && !isReversalLocked) { isReversing = true; brakeStroke = 0f; acceleratorStroke = (acceleratorStroke + ACCELERATOR_STROKE_CHANGE_RATE).coerceIn(0f, 1f) }
                else { if (speed <= SPEED_STOP_THRESHOLD) isReversalLocked = true; brakeStroke = (brakeStroke + BRAKE_STROKE_CHANGE_RATE).coerceIn(0f, 1f) }
            }
        } else { isReversalLocked = false; updatePedals() }
        prevWs = wS
        if (aD != 0f) currentSteeringWheelAngle = (currentSteeringWheelAngle + if (aD > 0f) 1f else -1f * -STEERING_WHEEL_ANGULAR_VELOCITY_MANIPULATED).coerceIn(-STEERING_WHEEL_MAX_ANGLE, STEERING_WHEEL_MAX_ANGLE)
        else updateSteeringAngle()
    }

    private fun updatePedals() { acceleratorStroke = (acceleratorStroke - ACCELERATOR_STROKE_CHANGE_RATE).coerceIn(0f, 1f); brakeStroke = (brakeStroke - BRAKE_STROKE_CHANGE_RATE).coerceIn(0f, 1f) }
    private fun updateSteeringAngle() { currentSteeringWheelAngle *= 1 - STEERING_WHEEL_SELF_CENTERING_PARAMETER * speed * SECONDS_IN_TICK }
    private fun isStopping(): Boolean = abs(speed) < SPEED_STOP_THRESHOLD

    private fun updateSpeed() {
        var ns = speed
        if (acceleratorStroke > 0f) ns += (if (isReversing) -1 else 1) * acceleratorStroke * ACCELERATION
        else if (brakeStroke > 0f) ns += (if (isReversing) 1 else -1) * brakeStroke * DECELERATION
        else ns *= 1 - SLOWDOWN_DECELERATION
        if (abs(ns) < SPEED_STOP_THRESHOLD) ns = 0f
        speed = ns.coerceIn(-MAX_SPEED, MAX_SPEED)
    }

    private fun applyMovement() {
        val yawRad = Math.toRadians(yRot.toDouble())
        deltaMovement = Vec3(-Math.sin(yawRad) * speed, deltaMovement.y, Math.cos(yawRad) * speed)
        val steerAngle = currentSteeringWheelAngle * STEERING_RATIO
        if (abs(speed) > 0.001f) deltaYaw = steerAngle * if (speed > 0f) 1f else if (speed < 0f) -1f else 0f * SECONDS_IN_TICK
        else { deltaYaw *= 0.9f; if (abs(deltaYaw) < 0.01f) deltaYaw = 0f }
        yRot += deltaYaw
    }

    private fun updateWheelRotationInClient() { prevWheelRotation = wheelRotation; wheelRotation += speed * SECONDS_IN_TICK / WHEEL_RADIUS }
}
