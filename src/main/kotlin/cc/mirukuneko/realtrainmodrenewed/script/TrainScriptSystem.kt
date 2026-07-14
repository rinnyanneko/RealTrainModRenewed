// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.script

import cc.mirukuneko.realtrainmodrenewed.BundledPackStore
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.client.ScriptClientCompat
import cc.mirukuneko.realtrainmodrenewed.client.ScriptKeyboardCompat
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader
import cc.mirukuneko.realtrainmodrenewed.client.model.MqoModelLoader.MqoModel
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.model.MQOModel
import cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder
import cc.mirukuneko.realtrainmodrenewed.util.PackZipReader
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.loading.FMLPaths
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.joml.Matrix3f
import org.joml.Matrix4f
import java.io.IOException
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.IntPredicate
import java.util.function.Supplier
import java.util.function.ToDoubleFunction
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import javax.script.*
import kotlin.concurrent.Volatile
import kotlin.math.*

class TrainScriptSystem private constructor() {
    private var engine: ScriptEngine? = null
    private val entityContexts: MutableMap<UUID?, EntityScriptContext> = HashMap<UUID?, EntityScriptContext>()

    class ScriptCoreCompat {
        /**
         * Returns the version string exposed to old train scripts.
         */
        /**
         * Returns the version string exposed to old train scripts.
         */
        @Suppress("unused")
        val version: String = SCRIPT_CORE_VERSION
    }

    class ScriptUtilCompat {
        fun doScript(script: String?): ScriptEngine? {
            return doScriptCompat(script)
        }

        fun doScriptFunction(scriptEngine: Any?, functionName: String?, args: Any?): Any? {
            return doScriptFunctionCompat(scriptEngine, functionName, args)
        }

        fun doScriptIgnoreError(scriptEngine: Any?, functionName: String?, args: Any?): Any? {
            return doScriptIgnoreErrorCompat(scriptEngine, functionName, args)
        }

        fun getScriptField(scriptEngine: Any?, fieldName: String?): Any? {
            return getScriptFieldCompat(scriptEngine, fieldName)
        }
    }

    fun initialize() {
        RealTrainModRenewed.LOGGER.info("Initializing legacy Script System...")
        try {
            var manager = ScriptEngineManager(Thread.currentThread().getContextClassLoader())
            engine = getAvailableScriptEngine(manager)
            if (engine == null) {
                RealTrainModRenewed.LOGGER.info("Retrying script engine discovery with TrainScriptSystem class loader.")
                manager = ScriptEngineManager(TrainScriptSystem::class.java.getClassLoader())
                engine = getAvailableScriptEngine(manager)
            }
            if (engine == null) {
                RealTrainModRenewed.LOGGER.warn("JavaScript engine not available. Java 21 requires an external JS engine dependency such as Graal.js.")
            } else {
                RealTrainModRenewed.LOGGER.info("JavaScript engine initialized successfully.")
            }
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.error("Failed to initialize JavaScript engine: {}", e.message, e)
        }
    }

    fun setScriptEngine(engine: ScriptEngine?) {
        this.engine = engine
    }

    fun executeTrainScript(train: TrainEntity, script: String?) {
        if (engine == null || script == null || script.isEmpty()) {
            return
        }

        val context = getOrCreateContext(train)
        setupScriptContext(context, train, null)

        try {
            val bindings = engine!!.createBindings()
            bindings.putAll(context.variables)
            engine!!.eval(script, bindings)
        } catch (e: ScriptException) {
            RealTrainModRenewed.LOGGER.error("Script execution error for vehicle '{}'", train.vehicleId, e)
        }
    }

    fun executeBlockScript(
        level: ServerLevel,
        pos: BlockPos,
        script: String?,
        powered: Boolean,
        train: TrainEntity?
    ): Boolean {
        if (engine == null || script == null || script.isBlank()) {
            return false
        }
        try {
            val bindings = engine!!.createBindings()
            bindings.put("level", level)
            bindings.put("world", level)
            bindings.put("pos", pos.immutable())
            bindings.put("x", pos.getX())
            bindings.put("y", pos.getY())
            bindings.put("z", pos.getZ())
            bindings.put("powered", powered)
            bindings.put("redstone", level.getBestNeighborSignal(pos))
            bindings.put("train", train)
            bindings.put("currentTrain", train)
            bindings.put("logger", RealTrainModRenewed.LOGGER)
            engine!!.eval(script, bindings)
            return true
        } catch (e: ScriptException) {
            RealTrainModRenewed.LOGGER.error("Script block execution error at {}", pos, e)
            return false
        }
    }

    fun executeEventScript(entity: Entity, eventType: String?, vararg parameters: Any?) {
        val context = getOrCreateContext(entity)
        context.variables.put("eventType", eventType)
        context.variables.put("eventParams", parameters)

        // Event scripts would be loaded from model definition
        // For now, this is a placeholder for future implementation
    }

    private fun getOrCreateContext(entity: Entity): EntityScriptContext {
        return entityContexts.computeIfAbsent(entity.getUUID()) { k: UUID? -> EntityScriptContext() }
    }

    private fun setupScriptContext(context: EntityScriptContext, train: TrainEntity, player: Player?) {
        context.variables.put("currentTrain", train)
        context.variables.put("train", train)
        context.variables.put("player", player)
        context.variables.put("currentPlayer", player)
        context.variables.put("world", train.level())
        context.variables.put("level", train.level())
        context.variables.put("x", train.getX())
        context.variables.put("y", train.getY())
        context.variables.put("z", train.getZ())
        context.variables.put("yaw", train.getYRot())
        context.variables.put("pitch", train.getXRot())
        context.variables.put("trainDistance", train.trainDistance)
        context.variables.put("vehicleId", train.vehicleId)
    }

    fun removeContext(entity: Entity) {
        entityContexts.remove(entity.getUUID())
    }

    private class EntityScriptContext {
        val variables: MutableMap<String?, Any?> = HashMap<String?, Any?>()
    }

    class BogieCompat internal constructor(var field_70177_z: Float, var field_70125_A: Float)

    class LegacyScriptExecutor(val vehicle: TrainEntity?) {
        // ---- Tick count helpers ----
        @JvmField
        val count: Long
        val time: Long

        // RTM 1.12.2 obfuscated field names used by legacy render scripts
        @JvmField
        val field_70177_z: Float // yRot
        @JvmField
        val field_70125_A: Float // xRot
        @JvmField
        val field_70173_aa: Int // tickCount
        @JvmField
        val field_70153_n: Entity? // riding/driver compat

        // Door animation state (accessed as properties by legacy scripts)
        @JvmField
        val doorMoveL: Float
        @JvmField
        val doorMoveR: Float
        @JvmField
        val seatRotation: Float
        @JvmField
        val pantograph_F: Float
        @JvmField
        val pantograph_B: Float

        // Brake pressure fields used by sd8200-style scripts for gauge animation
        @JvmField
        val brakeCount: Float
        @JvmField
        val brakeAirCount: Float
        @JvmField
        val mainReservoirPressure: Float
        @JvmField
        val brakePipePressure: Float
        @JvmField
        val brakeCylinderPressure: Float
        @JvmField
        val destination: Int
        @JvmField
        val rollsign: Int

        // RTM 1.7/1.12.2 legacy coordinate fields
        @JvmField
        val xCoord: Double
        @JvmField
        val yCoord: Double
        @JvmField
        val zCoord: Double

        // Wheel rotation in degrees (accessed as entity.wheelRotationR in scripts)
        @JvmField
        val wheelRotationR: Float

        // RTM 1.7.10 obfuscated world field (entity.field_70170_p)。lib_FormationFix 等が
        // entity.field_70170_p.field_72995_K(world.isRemote) を参照するため公開する。
        @JvmField
        val field_70170_p: TrainEntity.WorldCompat

        fun once(): Boolean {
            return this.time <= 0L
        }

        fun every(interval: Long): Boolean {
            return interval > 0L && Math.floorMod(this.time, interval) == 0L
        }

        fun between(start: Long, endExclusive: Long): Boolean {
            return this.time >= start && (endExclusive < 0L || this.time < endExclusive)
        }

        fun times(maxCount: Long): Boolean {
            return maxCount < 0L || this.time < maxCount
        }

        fun getCount(): Long {
            return count
        }

        fun getTick(): Long {
            return count
        }

        fun suggestState(value: Any?): Any? {
            return value
        }

        fun suggestState(value: Any?, fallback: Any?): Any? {
            return if (value == null) fallback else value
        }

        /**
         * Legacy EntityTrainBase#getSpeed() value in blocks per tick.
         * RTM render scripts commonly multiply this by 72 to obtain km/h.
         */
        val speed: Float
            get() = if (this.vehicle == null) 0.0f else vehicle.speed

        val maxSpeed: Float
            get() {
                if (this.vehicle == null) {
                    return 0.0f
                }
                val definition =
                    VehicleRegistry.getById(vehicle.vehicleId)
                if (definition == null || definition.getNotchMaxSpeeds().isEmpty()) {
                    return 0.0f
                }
                var max = 0.0f
                for (speed in definition.getNotchMaxSpeeds()) {
                    if (speed != null && java.lang.Float.isFinite(speed)) {
                        max = max(max, speed)
                    }
                }
                return max
            }

        fun getEntity(): TrainEntity? {
            return vehicle
        }

        fun getTrain(): TrainEntity? {
            return vehicle
        }

        fun isControlCar(): Boolean {
            return this.vehicle != null && vehicle.isControlCar
        }

        val notch: Int
            get() = if (this.vehicle == null) 0 else vehicle.notch

        val reverser: Int
            get() = if (this.vehicle == null) 0 else vehicle.reverser

        var sound: Int
            get() = if (this.vehicle == null) 0 else vehicle.soundIndex
            // ---- RTM setTrainStateData (write from script) ----
            set(index) {
                if (this.vehicle != null) vehicle.soundIndex = index
            }

        fun getRollsign(): Int {
            return if (this.vehicle == null) 0 else vehicle.destinationIndex
        }

        fun getDestination(): Int {
            return this.destinationIndex
        }

        fun getMainReservoirPressure(): Float {
            return if (this.vehicle == null) 0.0f else vehicle.mainReservoirPressure
        }

        fun getMRPressure(): Float {
            return getMainReservoirPressure()
        }

        fun getBrakePipePressure(): Float {
            return if (this.vehicle == null) 0.0f else vehicle.brakePipePressure
        }

        fun getBPPressure(): Float {
            return getBrakePipePressure()
        }

        fun getBrakeCylinderPressure(): Float {
            return if (this.vehicle == null) 0.0f else vehicle.brakeCylinderPressure
        }

        fun getBCPressure(): Float {
            return getBrakeCylinderPressure()
        }

        fun getBrakeAirCount(): Float {
            return if (this.vehicle == null) 0.0f else vehicle.legacyBrakeAirCount
        }

        fun getBrakeCount(): Float {
            return if (this.vehicle == null) 0.0f else max(0, -vehicle.notch).toFloat()
        }

        fun inTunnel(): Boolean {
            val train = this.vehicle ?: return false
            val sample = BlockPos.containing(train.x, train.y + 2.0, train.z)
            return !train.level().canSeeSky(sample)
        }

        val isComplessorActive: Boolean
            get() {
                if (this.vehicle == null) {
                    return false
                }
                val speedKmh = this.speedKmh
                if (speedKmh > 2.0f || vehicle.notch > 0) {
                    return false
                }
                val cycle = Math.floorMod(vehicle.tickCount, 240)
                return cycle < 55
            }

        fun complessorCount(): Int {
            if (!this.isComplessorActive) {
                return 0
            }
            return Math.floorMod(vehicle!!.tickCount, 240)
        }

        val isCompressorActive: Boolean
            get() = this.isComplessorActive

        fun compressorCount(): Int {
            return complessorCount()
        }

        fun playSound(namespace: String?, soundName: String?, volume: Double, pitch: Double) {
            invokeLegacySoundManager("play", namespace, soundName, volume.toFloat(), pitch.toFloat(), true)
        }

        fun playSound(namespace: String?, soundName: String?, volume: Any?, pitch: Any?) {
            playSound(namespace, soundName, toSoundDouble(volume, 1.0), toSoundDouble(pitch, 1.0), true)
        }

        fun playSound(
            namespace: String?,
            soundName: String?,
            volume: Double = 1.0,
            pitch: Double = 1.0,
            looping: Boolean = true
        ) {
            invokeLegacySoundManager("play", namespace, soundName, volume.toFloat(), pitch.toFloat(), looping)
        }

        fun playSound(namespace: String?, soundName: String?, volume: Any?, pitch: Any?, looping: Any?) {
            playSound(
                namespace,
                soundName,
                toSoundDouble(volume, 1.0),
                toSoundDouble(pitch, 1.0),
                toSoundBoolean(looping, true)
            )
        }

        fun playSoundAtRange(
            namespace: String?,
            soundName: String?,
            volume: Double = 1.0,
            pitch: Double = 1.0,
            soundRange: Double = 16.0,
            looping: Boolean = true
        ) {
            invokeLegacySoundManagerWithRange(
                namespace,
                soundName,
                volume.toFloat(),
                pitch.toFloat(),
                soundRange.toFloat(),
                looping
            )
        }

        fun playSoundAtRange(
            namespace: String?,
            soundName: String?,
            volume: Any?,
            pitch: Any?,
            soundRange: Any?,
            looping: Any?
        ) {
            playSoundAtRange(
                namespace,
                soundName,
                toSoundDouble(volume, 1.0),
                toSoundDouble(pitch, 1.0),
                toSoundDouble(soundRange, 16.0),
                toSoundBoolean(looping, true)
            )
        }

        fun stopSound(namespace: String?, soundName: String?) {
            invokeLegacySoundManager("stop", namespace, soundName, 0.0f, 0.0f, false)
        }

        private fun invokeLegacySoundManager(
            method: String?,
            namespace: String?,
            soundName: String?,
            volume: Float,
            pitch: Float,
            looping: Boolean
        ) {
            if (this.vehicle == null || !vehicle.level().isClientSide()) {
                return
            }
            try {
                val managerClass =
                    Class.forName("cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager")
                if ("play" == method) {
                    managerClass.getMethod(
                        "play",
                        TrainEntity::class.java,
                        String::class.java,
                        String::class.java,
                        Float::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    )
                        .invoke(null, this.vehicle, namespace, soundName, volume, pitch, looping)
                } else {
                    managerClass.getMethod("stop", TrainEntity::class.java, String::class.java, String::class.java)
                        .invoke(null, this.vehicle, namespace, soundName)
                }
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.debug("Legacy sound bridge failed for {}:{}", namespace, soundName, e)
            }
        }

        private fun invokeLegacySoundManagerWithRange(
            namespace: String?,
            soundName: String?,
            volume: Float,
            pitch: Float,
            soundRange: Float,
            looping: Boolean
        ) {
            if (this.vehicle == null || !vehicle.level().isClientSide()) {
                return
            }
            try {
                val managerClass =
                    Class.forName("cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager")
                managerClass.getMethod(
                    "playWithRange",
                    TrainEntity::class.java,
                    String::class.java,
                    String::class.java,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                ).invoke(null, this.vehicle, namespace, soundName, volume, pitch, soundRange, looping)
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.debug("Legacy ranged sound bridge failed for {}:{}", namespace, soundName, e)
            }
        }

        // ---- TrainStateData (RTM 2.x API) ----
        fun getTrainStateData(stateType: Int): Float {
            return if (this.vehicle == null) 0.0f else vehicle.getTrainStateData(stateType)
        }

        fun getVehicleState(stateType: Int): Float {
            return getTrainStateData(stateType)
        }

        fun setTrainStateData(stateType: Int, value: Float) {
            if (this.vehicle == null) return
            vehicle.syncVehicleState(stateType, value)
        }

        fun getData(key: Any?): Any {
            if (this.vehicle == null || key == null) {
                return 0
            }
            val value = vehicle.getScriptDataValue(key.toString())
            if (value == null || value.isBlank()) {
                return 0
            }
            try {
                if (value.indexOf('.') >= 0) {
                    return value.toDouble()
                }
                return value.toInt()
            } catch (ignored: NumberFormatException) {
                return value
            }
        }

        fun getData(key: Long): Any {
            return getData(key.toString())
        }

        fun getData(key: Int): Any {
            return getData(key.toString())
        }

        fun setData(key: Any?, value: Any?) {
            if (this.vehicle == null || key == null) {
                return
            }
            vehicle.setScriptDataValue(key.toString(), if (value == null) "" else value.toString())
        }

        fun setData(key: Long, value: Any?) {
            setData(key.toString(), value)
        }

        fun setData(key: Int, value: Any?) {
            setData(key.toString(), value)
        }

        // ---- RTM compat fields / methods ----
        fun getSeatRotation(): Float {
            return if (this.vehicle == null) 0.0f else vehicle.getSeatRotation()
        }

        val formation: Any?
            get() = if (this.vehicle == null) null else vehicle.getFormation()

        fun func_145782_y(): Int {
            return if (this.vehicle == null) 0 else vehicle.getId()
        }

        fun func_70070_b(): Int {
            return if (this.vehicle == null) 0x00F000F0 else vehicle.func_70070_b()
        }

        fun func_70070_b(ignored: Int): Int {
            return func_70070_b()
        }

        val doorState: Int
            // ---- Door state (RTM-compatible) ----
            get() {
                if (this.vehicle == null) return 0
                return (if (vehicle.isDoorRightOpen) 1 else 0) or (if (vehicle.isDoorLeftOpen) 2 else 0)
            }

        fun getDoorState(side: Int): Int {
            if (this.vehicle == null) return 0
            return if (side == 0) (if (vehicle.isDoorRightOpen) 1 else 0) else (if (vehicle.isDoorLeftOpen) 1 else 0)
        }

        val isDoorOpen: Boolean
            get() = this.vehicle != null && (vehicle.isDoorRightOpen || vehicle.isDoorLeftOpen)

        val isDoorRightOpen: Boolean
            get() = this.vehicle != null && vehicle.isDoorRightOpen

        val isDoorLeftOpen: Boolean
            get() = this.vehicle != null && vehicle.isDoorLeftOpen

        // ---- Custom buttons (RTM-compatible) ----
        fun getCustomButton(index: Int): Boolean {
            return this.vehicle != null && vehicle.isCustomButtonOn(index)
        }

        fun getCustomButtonValue(index: Int): Int {
            return if (this.vehicle == null) 0 else vehicle.getCustomButtonValue(index)
        }

        fun setCustomButton(index: Int, on: Boolean) {
            if (this.vehicle != null) vehicle.setCustomButton(index, on)
        }

        fun setCustomButton(index: Int, value: Int) {
            if (this.vehicle != null) vehicle.setCustomButton(index, value != 0)
        }

        fun toggleCustomButton(index: Int) {
            if (this.vehicle != null) vehicle.toggleCustomButton(index)
        }

        val isInsideTrain: Boolean
            // ---- Passengers / interior ----
            get() = this.vehicle != null && !vehicle.getPassengers().isEmpty()

        fun hasPassenger(): Boolean {
            return this.isInsideTrain
        }

        val passengerCount: Int
            get() = if (this.vehicle == null) 0 else vehicle.getPassengers().size

        fun func_184207_aI(): Entity? {
            return resolvePrimaryPassenger(
                this.vehicle
            )
        }

        fun func_184188_bt(): MutableList<Entity?> {
            return if (this.vehicle == null) mutableListOf<Entity?>() else ArrayList<Entity?>(vehicle.getPassengers())
        }

        fun func_184187_bx(): Entity? {
            return func_184207_aI()
        }

        val trainLength: Double
            // ---- Train dimensions ----
            get() = if (this.vehicle == null) 0.0 else vehicle.trainDistance.toDouble()

        val trainDistance: Double
            get() = this.trainLength

        // ---- Bogie compat (RTM 1.12.2 entity.getBogie(n)) ----
        // Returns a BogieCompat with field_70177_z/field_70125_A so legacy scripts can read bogie yaw/pitch.
        fun getBogie(index: Int): BogieCompat? {
            if (this.vehicle == null) return null
            val mapped = vehicle.scriptBogieIndexToDefinitionIndex(index)
            val yaw = vehicle.getBogieWorldYaw(mapped)
            val pitch = vehicle.getBogiePitch(mapped)
            return BogieCompat(yaw, pitch)
        }

        val frontTrain: TrainEntity?
            // ---- Connected trains (formation) ----
            get() = if (this.vehicle == null) null else vehicle.coupledLeader

        val rearTrain: TrainEntity?
            get() = if (this.vehicle == null) null else vehicle.coupledFollower

        fun hasFrontTrain(): Boolean {
            return this.frontTrain != null
        }

        fun hasRearTrain(): Boolean {
            return this.rearTrain != null
        }

        var isPantographUp: Boolean
            // ---- Pantograph ----
            get() = this.vehicle != null && vehicle.isPantographUp
            set(up) {
                if (this.vehicle != null) vehicle.isPantographUp = up
            }

        var lightMode: Int
            // ---- Light mode ----
            get() = if (this.vehicle == null) 0 else vehicle.lightMode
            set(mode) {
                if (this.vehicle != null) vehicle.lightMode = mode
            }

        var destinationIndex: Int
            // ---- Destination (rollsign) ----
            get() = if (this.vehicle == null) 0 else vehicle.destinationIndex
            set(index) {
                if (this.vehicle != null) vehicle.destinationIndex = index
            }

        val rawSpeed: Float
            // ---- Speed (raw m/tick) ----
            get() = if (this.vehicle == null) 0.0f else vehicle.speed

        val world: Any?
            // ---- World access ----
            get() = if (this.vehicle == null) null else vehicle.level()

        val x: Double
            get() = if (this.vehicle == null) 0.0 else vehicle.getX()
        val y: Double
            get() = if (this.vehicle == null) 0.0 else vehicle.getY()
        val z: Double
            get() = if (this.vehicle == null) 0.0 else vehicle.getZ()
        val yaw: Float
            get() = if (this.vehicle == null) 0f else vehicle.getYRot()
        val pitch: Float
            get() = if (this.vehicle == null) 0f else vehicle.getXRot()
        val rotation: Float
            // 旧 RTM Render スクリプトは entity.getRotation() で yaw を取得する。
            get() = this.yaw

        val speedKmh: Float
            // ---- Speed (km/h, RTM compat alias) ----
            get() = if (this.vehicle == null) 0.0f else abs(vehicle.speed) * 72.0f

        val formationPassengerCount: Int
            // ---- Formation passenger count ----
            get() {
                if (this.vehicle == null) return 0
                var total = 0
                for (t in vehicle!!.formationTrainsForDisplay) {
                    t ?: continue
                    total += t.getPassengers().size
                    // also count passengers on seat entities attached to this train
                    for (e in t.level().getEntitiesOfClass<TrainSeatEntity>(
                        TrainSeatEntity::class.java,
                        t.getBoundingBox().inflate(20.0)
                    )) {
                        if (e is TrainSeatEntity
                            && e.getTrain() === t && !e.getPassengers().isEmpty()
                        ) {
                            total += e.getPassengers().size
                        }
                    }
                }
                return total
            }

        val headingAngle: Float
            // ---- Heading / direction ----
            get() = if (this.vehicle == null) 0.0f else vehicle.getYRot()

        fun setRollsign(index: Int) {
                if (this.vehicle != null) vehicle.destinationIndex = index
        }

        // ---- hasIndirectPassenger guard (passenger count > 0) ----
        fun hasDriver(): Boolean {
            if (this.vehicle == null) return false
            for (e in vehicle.getPassengers()) {
                if (e is Player) return true
            }
            return false
        }

        val resourceState: TrainEntity.ResourceStateCompat?
            // ---- Resource state (RTM 1.12.2 entity.resourceState.getDataMap()) ----
            get() = if (this.vehicle == null) null else vehicle.resourceState

        val trainDirection: Float
            // ---- Formation / coupler (RTM 1.12.2 entity.trainDirection etc.) ----
            get() = if (this.vehicle == null) 0.0f else vehicle.trainDirection

        fun getConnectedTrain(dir: Int): TrainEntity? {
            return if (this.vehicle == null) null else vehicle.getConnectedTrain(dir)
        }

        fun getCouplerYaw(index: Int): Float {
            return if (this.vehicle == null) 0.0f else vehicle.getCouplerYaw(index)
        }

        val rollsignAnimation: Int
            get() = if (this.vehicle == null) 0 else vehicle.rollsignAnimation

        // ---- Notch sync (called from cab controller scripts) ----
        fun syncNotch(notch: Int) {
            if (this.vehicle != null) vehicle.syncNotch(notch)
        }

        val dir: Int
            get() {
                // For train entity: direction as 0/1. Use heading bucket for 4-way.
                if (this.vehicle == null) return 0
                val yaw = ((vehicle.getYRot() % 360.0f) + 360.0f) % 360.0f
                return Math.round(yaw / 90.0f) % 4
            }

        val moveDir: Float
            get() = if (this.vehicle == null) 0.0f else sign(vehicle.speed)

        val accelerationForward: Float
            get() = 0.0f
        val accelerationStrafe: Float
            get() = 0.0f

        val isOnGround: Boolean
            // ---- Ground check ----
            get() = this.vehicle != null && vehicle.onGround()

        // ---- 1.7.10 / 1.12.2 obfuscated Minecraft entity method stubs ----
        // These are called from RTM render/tick scripts that were written against old MC versions.
        // func_70089_S() = isEntityAlive (1.7.10/1.12.2)
        fun func_70089_S(): Boolean {
            return this.vehicle != null && !vehicle.isRemoved()
        }

        // func_70027_ad() = isDead / isRemoved (1.7.10/1.12.2)
        fun func_70027_ad(): Boolean {
            return this.vehicle == null || vehicle.isRemoved()
        }

        // func_70075_an() = isInWater (1.7.10)
        fun func_70075_an(): Boolean {
            return this.vehicle != null && vehicle.isInWater()
        }

        // func_70093_af() = isSneaking (1.7.10/1.12.2)
        fun func_70093_af(): Boolean {
            return false
        }

        // func_70661_as() = isSprinting (1.7.10/1.12.2)
        fun func_70661_as(): Boolean {
            return false
        }

        // func_70617_f_() = isInWater (1.12.2 variant)
        fun func_70617_f_(): Boolean {
            return func_70075_an()
        }

        // func_70086_ai() = isWet (1.7.10)
        fun func_70086_ai(): Boolean {
            return this.vehicle != null && vehicle.isInWaterOrRain()
        }

        // func_70023_ah() = isBurning (1.7.10)
        fun func_70023_ah(): Boolean {
            return this.vehicle != null && vehicle.isOnFire()
        }

        // func_70040_Z() = isSneaking variant (1.7.10)
        fun func_70040_Z(): Boolean {
            return false
        }

        // func_70003_b(int, String) = canUseCommand / hasPermissionLevel (1.12.2)
        fun func_70003_b(level: Int, command: String?): Boolean {
            return false
        }

        // func_110140_aT() = getAttributeMap (1.12.2) — return stub
        fun func_110140_aT(): Any? {
            return null
        }

        // func_70012_b(double,double,double,float,float) = setPositionAndRotation (1.7.10/1.12.2)
        fun func_70012_b(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
            // no-op in render context
        }

        // func_70107_b(double,double,double) = setPosition (1.7.10)
        fun func_70107_b(x: Double, y: Double, z: Double) {
            // no-op in render context
        }

        // func_70030_z() = preparePlayerToSpawn / onUpdate stub (1.7.10)
        fun func_70030_z() {}

        // func_70021_al() = getPassengerList (1.7.10) — returns empty
        fun func_70021_al(): Array<Any?> {
            return arrayOfNulls<Any>(0)
        }

        // func_70678_g(float) = getEyeHeight variant (1.7.10)
        fun func_70678_g(partialTick: Float): Float {
            return 1.5f
        }

        // func_70047_e() = getEyeHeight (1.7.10/1.12.2)
        fun func_70047_e(): Float {
            return 1.5f
        }

        // func_70057_ab() = getAir (1.7.10 variant)
        fun func_70057_ab(): Int {
            return 300
        }

        // func_70020_e() = setAir (1.7.10)
        fun func_70020_e(air: Int) {}

        // Field equivalents often read as properties in JS
        // field_70128_N = isDead (1.7.10)
        val field_70128_N: Boolean = false

        init {
            this.field_70170_p = if (vehicle != null)
                vehicle.field_70170_p
            else
                TrainEntity.WorldCompat(null)
            this.time = if (vehicle == null) 0L else max(0L, vehicle.tickCount.toLong())
            this.count = this.time
            this.field_70177_z = if (vehicle == null) 0.0f else vehicle.getYRot()
            this.field_70125_A = if (vehicle == null) 0.0f else vehicle.getXRot()
            this.field_70173_aa = if (vehicle == null) 0 else vehicle.tickCount
            this.field_70153_n = resolvePrimaryPassenger(
                vehicle
            )
            this.doorMoveL = if (vehicle == null) 0.0f else vehicle.doorMoveL
            this.doorMoveR = if (vehicle == null) 0.0f else vehicle.doorMoveR
            this.seatRotation = resolveLegacySeatRotation(
                vehicle
            )
            this.pantograph_F = if (vehicle == null) 0.0f else vehicle.pantograph_F
            this.pantograph_B = if (vehicle == null) 0.0f else vehicle.pantograph_B
            // brakeCount: 0-8 equivalent brake notch position for gauge display
            this.brakeCount = if (vehicle == null) 0.0f else max(0, -vehicle.notch).toFloat()
            this.brakeAirCount = if (vehicle == null) 0.0f else vehicle.legacyBrakeAirCount
            this.mainReservoirPressure = if (vehicle == null) 0.0f else vehicle.mainReservoirPressure
            this.brakePipePressure = if (vehicle == null) 0.0f else vehicle.brakePipePressure
            this.brakeCylinderPressure = if (vehicle == null) 0.0f else vehicle.brakeCylinderPressure
            this.destination = if (vehicle == null) 0 else vehicle.destinationIndex
            this.rollsign = this.destination
            this.xCoord = if (vehicle == null) 0.0 else vehicle.getX()
            this.yCoord = if (vehicle == null) 0.0 else vehicle.getY()
            this.zCoord = if (vehicle == null) 0.0 else vehicle.getZ()
            // 走行距離ベースの累積回転角(TrainEntity.tick で毎tick加算)。
            // 旧 tickCount×速度 は速度変化で巨大ジャンプ→空転の原因だったので使わない。
            this.wheelRotationR = if (vehicle == null) 0.0f else vehicle.wheelRotationDegrees
        }

        // field_70179_y = motionX (1.7.10)
        fun field_70179_y(): Double {
            return if (this.vehicle == null) 0.0 else vehicle.getDeltaMovement().x
        }

        // field_70181_x = motionY (1.7.10)
        fun field_70181_x(): Double {
            return if (this.vehicle == null) 0.0 else vehicle.getDeltaMovement().y
        }

        // field_70178_ae = motionZ (1.7.10)
        fun field_70178_ae(): Double {
            return if (this.vehicle == null) 0.0 else vehicle.getDeltaMovement().z
        }

        val barrelYaw: Float
            // func_145782_y() is already implemented (getId / entityId)
            get() = 0.0f
        val barrelPitch: Float
            get() = 0.0f
        val recoil: Float
            get() = 0.0f

        val randomScale: Float
            // ---- Installed-object random scale (RenderPalm etc.) ----
            get() = 1.0f

        companion object {
            private fun resolveLegacySeatRotation(train: TrainEntity?): Float {
                if (train == null) {
                    return 0.0f
                }
                if (train.lightMode > 0) {
                    return if (train.reverser < 0) -45.0f else 45.0f
                }
                return Mth.clamp(train.seatRotation, -45.0f, 45.0f)
            }

            private fun toSoundDouble(value: Any?, fallback: Double): Double {
                if (value is Number) {
                    val result = value.toDouble()
                    return if (java.lang.Double.isFinite(result)) result else fallback
                }
                if (value is Boolean) {
                    return if (value) 1.0 else 0.0
                }
                if (value != null) {
                    val text = value.toString()
                    if (!text.isBlank() && !"undefined".equals(text, ignoreCase = true) && !"null".equals(
                            text,
                            ignoreCase = true
                        )
                    ) {
                        try {
                            val result = text.toDouble()
                            return if (java.lang.Double.isFinite(result)) result else fallback
                        } catch (ignored: NumberFormatException) {
                        }
                    }
                }
                return fallback
            }

            private fun toSoundBoolean(value: Any?, fallback: Boolean): Boolean {
                if (value is Boolean) {
                    return value
                }
                if (value is Number) {
                    return value.toDouble() != 0.0
                }
                if (value != null) {
                    val text = value.toString().trim { it <= ' ' }
                    if (!text.isEmpty() && !"undefined".equals(text, ignoreCase = true) && !"null".equals(
                            text,
                            ignoreCase = true
                        )
                    ) {
                        return text.toBoolean()
                    }
                }
                return fallback
            }

            private fun resolvePrimaryPassenger(train: TrainEntity?): Entity? {
                if (train == null) {
                    return null
                }
                return if (train.getPassengers().isEmpty()) null else train.getPassengers().get(0)
            }
        }
    }

    class LegacySoundBridge(private val executor: LegacyScriptExecutor?) {
        fun getSpeed(): Float {
            return if (executor?.vehicle == null) 0.0f else abs(executor.vehicle.speed) * 72.0f
        }

        fun getRawSpeed(): Float {
            return if (executor?.vehicle == null) 0.0f else executor.vehicle.speed
        }

        fun getMaxSpeed(): Float {
            return abs(executor?.maxSpeed ?: 0.0f) * 72.0f
        }

        fun getNotch(): Int {
            return executor?.notch ?: 0
        }

        fun getReverser(): Int {
            return executor?.reverser ?: 0
        }

        fun getTrain(): TrainEntity? {
            return executor?.vehicle
        }

        fun getEntity(): TrainEntity? {
            return executor?.vehicle
        }

        fun inTunnel(): Boolean {
            return executor?.inTunnel() ?: false
        }

        fun isComplessorActive(): Boolean {
            return executor?.isComplessorActive ?: false
        }

        fun isCompressorActive(): Boolean {
            return executor?.isCompressorActive ?: false
        }

        fun complessorCount(): Int {
            return executor?.complessorCount() ?: 0
        }

        fun compressorCount(): Int {
            return executor?.compressorCount() ?: 0
        }

        fun playSound(namespace: String?, soundName: String?, volume: Double, pitch: Double) {
            if (executor != null) {
                STATES.put(soundKey(namespace, soundName), SoundState(volume, pitch, null))
                executor.playSound(namespace, soundName, volume, pitch)
            }
        }

        fun playSound(namespace: String?, soundName: String?, volume: Any?, pitch: Any?, looping: Any?) {
            if (executor != null) {
                val resolvedVolume = toSoundDouble(volume, 1.0)
                val resolvedPitch = toSoundDouble(pitch, 1.0)
                STATES[soundKey(namespace, soundName)] = SoundState(resolvedVolume, resolvedPitch, null)
                executor.playSound(namespace, soundName, resolvedVolume, resolvedPitch, toSoundBoolean(looping, true))
            }
        }

        fun playSoundAtRange(
            namespace: String?,
            soundName: String?,
            volume: Double,
            pitch: Double,
            soundRange: Double
        ) {
            if (executor != null) {
                STATES.put(soundKey(namespace, soundName), SoundState(volume, pitch, soundRange))
                executor.playSoundAtRange(namespace, soundName, volume, pitch, soundRange)
            }
        }

        fun stopSound(namespace: String?, soundName: String?) {
            if (executor != null) {
                STATES.remove(soundKey(namespace, soundName))
                executor.stopSound(namespace, soundName)
            }
        }

        fun setSoundVolume(namespace: String?, soundName: String?, volume: Double) {
            if (executor != null) {
                val state: SoundState =
                    STATES.computeIfAbsent(soundKey(namespace, soundName)) {
                        ignored: String? -> SoundState(1.0, 1.0, null)
                    }
                state.volume = volume
                playState(namespace, soundName, state)
            }
        }

        fun setSoundPitch(namespace: String?, soundName: String?, pitch: Double) {
            if (executor != null) {
                val state: SoundState =
                    STATES.computeIfAbsent(soundKey(namespace, soundName)) {
                        ignored: String? -> SoundState(1.0, 1.0, null)
                    }
                state.pitch = pitch
                playState(namespace, soundName, state)
            }
        }

        fun setSoundRange(namespace: String?, soundName: String?, soundRange: Double) {
            if (executor != null) {
                val state: SoundState =
                    STATES.computeIfAbsent(soundKey(namespace, soundName)) {
                        ignored: String? -> SoundState(1.0, 1.0, null)
                    }
                state.soundRange = if (soundRange.isFinite() && soundRange > 0.0) soundRange else null
                playState(namespace, soundName, state)
            }
        }

        private fun toSoundDouble(value: Any?, fallback: Double): Double = when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() } ?: fallback
            else -> value?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fallback
        }

        private fun toSoundBoolean(value: Any?, fallback: Boolean): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            null -> fallback
            else -> value.toString().toBooleanStrictOrNull() ?: fallback
        }

        private fun playState(namespace: String?, soundName: String?, state: SoundState) {
            val soundRange = state.soundRange
            if (soundRange != null) {
                executor!!.playSoundAtRange(namespace, soundName, state.volume, state.pitch, soundRange)
            } else {
                executor!!.playSound(namespace, soundName, state.volume, state.pitch)
            }
        }

        private fun soundKey(namespace: String?, soundName: String?): String {
            val trainKey: Any = if (executor!!.vehicle == null) "none" else executor.vehicle.getUUID()
            return trainKey.toString() + "|" + namespace.toString() + ":" + soundName.toString()
        }

        private class SoundState(var volume: Double, var pitch: Double, var soundRange: Double?)
        companion object {
            private val STATES: MutableMap<String?, SoundState> = ConcurrentHashMap<String?, SoundState>()
        }
    }

    class ScriptModelRenderer(private val model: Any?, defaultModelName: String?) {
        private val mqoModel: MqoModel?
        private val defaultModelName: String
        private var poseStack: PoseStack? = null
        private var buffer: MultiBufferSource? = null
        private var packedLight = 0
        private var basePackedLight = 0
        private var lightmapMaxForced = false
        private var overlay = 0
        var currentPass: Int = 0
            private set
        var currentEntity: Any? = null
            private set
        var boundTexture: Identifier? = null
            private set
        private var colorRed = 1.0f
        private var colorGreen = 1.0f
        private var colorBlue = 1.0f
        private var colorAlpha = 1.0f
        private var uvWindowActive = false
        private var uvU0 = 0f
        private var uvV0 = 0f
        private var uvU1 = 1.0f
        private var uvV1 = 1.0f
        private var uvOffsetActive = false
        private var uvOffsetU = 0f
        private var uvOffsetV = 0f
        var matrixDepth: Int = 0
            private set
        private var invalidMatrixDepth = -1
        private var scriptLocalOrigin = Vec3.ZERO
        private val scriptLocalStack = ArrayDeque<Vec3?>()
        var renderPartsCalls: Int = 0
            private set
        var renderedBatchCount: Int = 0
            private set
        private val scriptedOpaqueGroups: MutableSet<String?> = java.util.LinkedHashSet<String?>()
        private val scriptedTranslucentGroups: MutableSet<String?> = java.util.LinkedHashSet<String?>()

        // emissive pass (pass>=2) の発光描画はライトON状態のときに行う別系統。
        // translucent pass (pass=1) の半透明描画とは独立に 1 フレーム 1 回制限する。
        private val scriptedEmissiveGroups: MutableSet<String?> = java.util.LinkedHashSet<String?>()

        // 通常 translucent (AlphaBlend) を「最初に描画したパス」だけに限定するための記録。
        // -1 = まだ未描画。 これにより mat1 等の半透明面が複数 pass で重ね描きされて
        // z-fighting する (外装チラつき) のを防ぎつつ、 同一 pass 内の複数 renderParts
        // (椅子を複数脚など) は全て描画できる。
        private var firstTranslucentPass = -1

        // Groups registered via registerParts() during init() — script "owns" these, baked render skips them
        private val scriptRegisteredGroups: MutableSet<String> = java.util.LinkedHashSet<String>()
        private val scriptData: MutableMap<Long?, Any?> = HashMap<Long?, Any?>()
        private var cachedExecutorTrain: TrainEntity? = null
        private var cachedExecutor: LegacyScriptExecutor? = null
        private var replayCacheDisabledForFrame = false
        private var replayCacheAllowed = true
        private val tessellatorVertices: MutableList<TessVertex> = ArrayList<TessVertex>()
        private var tessColorRed = 1.0f
        private var tessColorGreen = 1.0f
        private var tessColorBlue = 1.0f
        private var tessColorAlpha = 1.0f
        private var tessNormalX = 0.0f
        private var tessNormalY = 1.0f
        private var tessNormalZ = 0.0f
        private var tessNormalSet = false

        private class TessVertex(val x: Float, val y: Float, val z: Float, val u: Float, val v: Float)

        // ==== 半透明遅延描画 (Deferred Translucent) ====
        // スクリプトは body_o(窓含む)→body_i(椅子) の順に「不透明→半透明」を即描画するため、
        // 窓(半透明)が椅子(不透明)より先に描かれ、窓越しに内装が見えない等の順序問題が出る。
        // deferTranslucent=true の間は半透明描画を即時せずキューに溜め、スクリプト全体(全 parts)が
        // 終わってから flushDeferredTranslucent() で一括描画する。これで「全不透明 → 全半透明」の
        // 正しい順序になり、窓越しに内装が見え、ライト等の半透明も最後に正しく重なる。
        private var deferTranslucent = false
        private val deferredTranslucents: MutableList<DeferredTranslucent> = ArrayList<DeferredTranslucent>()

        private class DeferredTranslucent(
            val groups: MutableSet<String?>?, val pass: Int, val packedLight: Int, val overlay: Int,
            val r: Float, val g: Float, val b: Float, val a: Float,
            val boundTexture: Identifier?,
            pose: Matrix4f?, normal: Matrix3f?,
            uvWindowActive: Boolean, uvU0: Float, uvV0: Float, uvU1: Float, uvV1: Float
        ) {
            val pose: Matrix4f?
            val normal: Matrix3f?

            // UV ウィンドウ状態(方向幕など CustomAnimator が setUvWindow したまま renderParts する場合に保存)
            val uvWindowActive: Boolean
            val uvU0: Float
            val uvV0: Float
            val uvU1: Float
            val uvV1: Float

            init {
                this.pose = pose
                this.normal = normal
                this.uvWindowActive = uvWindowActive
                this.uvU0 = uvU0
                this.uvV0 = uvV0
                this.uvU1 = uvU1
                this.uvV1 = uvV1
            }
        }

        fun setDeferTranslucent(v: Boolean) {
            this.deferTranslucent = v
            if (!v) this.deferredTranslucents.clear()
        }

        /** 溜めた半透明描画を「全不透明の後」に一括描画する。  */
        fun flushDeferredTranslucent(poseStack: PoseStack, buffer: MultiBufferSource) {
            if (deferredTranslucents.isEmpty() || mqoModel == null) return
            val prevDefer = this.deferTranslucent
            this.deferTranslucent = false // flush 中は即描画
            // 現在のコンテキストを退避
            val sPass = this.currentPass
            val sLight = this.packedLight
            val sOverlay = this.overlay
            val sr = this.colorRed
            val sg = this.colorGreen
            val sb = this.colorBlue
            val sa = this.colorAlpha
            val sTex = this.boundTexture
            val sUvActive = this.uvWindowActive
            val sUu0 = this.uvU0
            val sUv0 = this.uvV0
            val sUu1 = this.uvU1
            val sUv1 = this.uvV1
            try {
                for (d in deferredTranslucents) {
                    this.currentPass = d.pass
                    this.packedLight = d.packedLight
                    this.overlay = d.overlay
                    this.colorRed = d.r
                    this.colorGreen = d.g
                    this.colorBlue = d.b
                    this.colorAlpha = d.a
                    this.boundTexture = d.boundTexture
                    // UV ウィンドウを復元(方向幕など setUvWindow した状態でキューに入ったエントリのため)
                    this.uvWindowActive = d.uvWindowActive
                    this.uvU0 = d.uvU0
                    this.uvV0 = d.uvV0
                    this.uvU1 = d.uvU1
                    this.uvV1 = d.uvV1
                    poseStack.pushPose()
                    try {
                        poseStack.last().pose().set(d.pose)
                        poseStack.last().normal().set(d.normal)
                        mqoModel.renderNamedGroups(poseStack, buffer, d.packedLight, d.overlay, true, d.groups, this)
                    } finally {
                        poseStack.popPose()
                    }
                }
            } finally {
                // コンテキスト復元
                this.currentPass = sPass
                this.packedLight = sLight
                this.overlay = sOverlay
                this.colorRed = sr
                this.colorGreen = sg
                this.colorBlue = sb
                this.colorAlpha = sa
                this.boundTexture = sTex
                this.uvWindowActive = sUvActive
                this.uvU0 = sUu0
                this.uvV0 = sUv0
                this.uvU1 = sUu1
                this.uvV1 = sUv1
                this.deferTranslucent = prevDefer
                deferredTranslucents.clear()
            }
        }

        /** 半透明描画を即時 or 遅延キューへ。戻り値 true=遅延した(即描画しない)。  */
        private fun enqueueOrDrawTranslucent(
            poseStack: PoseStack, buffer: MultiBufferSource,
            pass: Int, groups: MutableSet<String?>
        ): Boolean {
            if (!deferTranslucent) return false
            val pose: Matrix4f = Matrix4f(poseStack.last().pose())
            val normal: Matrix3f = Matrix3f(poseStack.last().normal())
            deferredTranslucents.add(
                DeferredTranslucent(
                    java.util.LinkedHashSet<String?>(groups), pass, this.packedLight, this.overlay,
                    this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha,
                    this.boundTexture, pose, normal,
                    this.uvWindowActive, this.uvU0, this.uvV0, this.uvU1, this.uvV1
                )
            )
            return true
        }

        class OpList {
            var kinds: IntArray = IntArray(32)
            var floats: FloatArray = FloatArray(32 * 5)
            var strings: Array<String?> = arrayOfNulls<String>(32)
            var chars: CharArray = CharArray(32)
            var size: Int = 0
            fun add(kind: Int, f0: Float, f1: Float, f2: Float, f3: Float, f4: Float, s: String?, c: Char) {
                if (size == kinds.size) {
                    val n = size * 2
                    kinds = kinds.copyOf(n)
                    floats = floats.copyOf(n * 5)
                    strings = strings.copyOf<String?>(n)
                    chars = chars.copyOf(n)
                }
                kinds[size] = kind
                val b = size * 5
                floats[b] = f0
                floats[b + 1] = f1
                floats[b + 2] = f2
                floats[b + 3] = f3
                floats[b + 4] = f4
                strings[size] = s
                chars[size] = c
                size++
            }

            fun clear() {
                size = 0
            }
        }

        private data class ReplayKey(
            val entityUuid: UUID,
            val tickCount: Int,
            val pass: Int,
            val doorL: Int,
            val doorR: Int,
            val lightMode: Int,
            val notch: Int,
            val reverser: Int,
            val destination: Int,
            val interiorLight: Boolean,
            val pantographFront: Int,
            val pantographBack: Int,
            val soundIndex: Int,
            val customButtonBits: Int,
            val customButtonValues: List<Int>,
        )

        private val replayCache: MutableMap<ReplayKey, OpList?> =
            object : LinkedHashMap<ReplayKey, OpList?>(64, 0.75f, true) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<ReplayKey, OpList?>?): Boolean {
                return size > REPLAY_CACHE_MAX
            }
        }
        private var currentRecording: OpList? = null // null = recording off
        var isReplaying: Boolean = false
            private set
        private var currentSignature: ReplayKey? = null

        private fun recordOp(kind: Int, f0: Float, f1: Float, f2: Float, f3: Float, f4: Float, s: String?, c: Char) {
            if (currentRecording != null) {
                currentRecording!!.add(kind, f0, f1, f2, f3, f4, s, c)
            }
        }

        /**
         * pass + entity 状態を long に圧縮。同じ entity の同一 game tick 内だけ
         * 録画された Op 列を再生する。tick を跨いだら必ず JS を再実行するため、
         * LCD・圧力計・点滅など tick 駆動の表示や script data 更新は停止しない。
         */
        private fun computeReplaySignature(pass: Int, entity: Any?): ReplayKey? {
            if (!replayCacheAllowed) return null
            if (entity !is TrainEntity) return null
            if (abs(entity.speed) > 0.001f) {
                return null
            }
            val doorL = Math.round(entity.doorMoveL * 32.0f)
            val doorR = Math.round(entity.doorMoveR * 32.0f)
            val lightMode = entity.lightMode
            val notch = entity.notch
            val rev = entity.reverser
            val dest = entity.destinationIndex
            val pantographFront = Math.round(entity.pantograph_F)
            val pantographBack = Math.round(entity.pantograph_B)
            var customButtonBits = 0
            for (buttonIndex in 0..30) {
                if (entity.isCustomButtonOn(buttonIndex)) {
                    customButtonBits = customButtonBits or (1 shl buttonIndex)
                }
            }
            return ReplayKey(
                entity.uuid,
                entity.tickCount,
                pass,
                doorL,
                doorR,
                lightMode,
                notch,
                rev,
                dest,
                entity.isInteriorLightOn,
                pantographFront,
                pantographBack,
                entity.soundIndex,
                customButtonBits,
                List(31) { entity.getCustomButtonValue(it) },
            )
        }

        fun configureReplaySafety(scriptSource: String?) {
            replayCacheAllowed = scriptSource.isNullOrBlank() ||
                !FRAME_SENSITIVE_SCRIPT_PATTERN.matcher(scriptSource).find()
        }

        fun tryReplayCachedScript(pass: Int, entity: Any?): Boolean {
            val signature = computeReplaySignature(pass, entity)
            if (signature == null) return false
            val list = replayCache.get(signature)
            if (list == null) return false
            this.isReplaying = true
            try {
                executeOpList(list)
            } finally {
                this.isReplaying = false
            }
            return true
        }

        fun beginRecording(pass: Int, entity: Any?) {
            val signature = computeReplaySignature(pass, entity)
            replayCacheDisabledForFrame = false
            if (signature == null) {
                currentRecording = null
                currentSignature = null
                return
            }
            currentSignature = signature
            var existing = replayCache.get(signature)
            if (existing == null) {
                existing = OpList()
            } else {
                existing.clear()
            }
            currentRecording = existing
        }

        fun endRecording(keep: Boolean) {
            if (currentRecording != null && keep && currentSignature != null && !replayCacheDisabledForFrame) {
                replayCache[currentSignature!!] = currentRecording
            }
            currentRecording = null
            currentSignature = null
            replayCacheDisabledForFrame = false
        }

        fun disableReplayCacheForFrame() {
            replayCacheDisabledForFrame = true
            currentRecording = null
        }

        private fun executeOpList(list: OpList) {
            for (i in 0..<list.size) {
                val k = list.kinds[i]
                val b = i * 5
                val f0 = list.floats[b]
                val f1 = list.floats[b + 1]
                val f2 = list.floats[b + 2]
                val f3 = list.floats[b + 3]
                val f4 = list.floats[b + 4]
                val s = list.strings[i]
                when (k) {
                    OP_TRANSLATE -> translate(f0, f1, f2)
                    OP_ROTATE_AXIS -> rotate(
                        f0.toDouble(),
                        list.chars[i].toString(),
                        f1.toDouble(),
                        f2.toDouble(),
                        f3.toDouble()
                    )

                    OP_ROTATE_FREE -> rotate(f0, f1, f2, f3)
                    OP_PUSH -> pushMatrix()
                    OP_POP -> popMatrix()
                    OP_SCALE -> scale(f0, f1, f2)
                    OP_RENDER_PARTS -> renderParts(s)
                    OP_SET_COLOR -> setColor(f0.toDouble(), f1.toDouble(), f2.toDouble(), f3.toDouble())
                    OP_RESET_COLOR -> resetColor()
                    OP_BIND_TEX -> bindScriptTextureFromRecord(s, Math.round(f0))
                    OP_CLEAR_TEX -> clearScriptTexture()
                    OP_SET_UV_WINDOW -> setUvWindow(f0.toDouble(), f1.toDouble(), f2.toDouble(), f3.toDouble())
                    OP_CLEAR_UV_WINDOW -> clearUvWindow()
                    OP_SET_LIGHTMAP_MAX -> setLightmapMaxBrightness()
                    OP_SET_BRIGHTNESS -> setBrightness(Math.round(f0))
                    OP_ENABLE_LIGHTING -> enableLighting()
                    OP_RENDER_TEXTURE_WINDOW -> renderTextureWindowFromRecord(s, Math.round(f0), f1, f2, f3, f4)
                    else -> {}
                }
            }
        }

        private fun bindScriptTextureFromRecord(record: String?, frameIndex: Int) {
            if (record == null || record.isBlank()) {
                clearScriptTexture()
                return
            }
            val parts: Array<String?> = record.split("\\n".toRegex(), limit = 2).toTypedArray()
            if (parts.size < 2 || parts[1]!!.isBlank()) {
                clearScriptTexture()
                return
            }
            bindScriptTexture(parts[0], parts[1], frameIndex)
        }

        // renderParts() の入力文字列 → 解析済み結果のキャッシュ。
        // SL の動軸スクリプトのように毎フレーム同じ groupsStr で renderParts を多数回
        // 呼び出すケースで、extractGroupNames/strip/filter/normalize/hasGroupNamed の
        // 重複処理を完全に省く。Parts.groupsStr は JS 側で 1 回しか生成されないため
        // 同一 String インスタンスのまま渡され、HashMap ヒット率はほぼ 100%。
        private val renderPartsParseCache: MutableMap<String?, ParsedGroupSet?> = HashMap<String?, ParsedGroupSet?>()

        // emissive pass で、lightMode ごとの presentGroupNames をキャッシュ。
        // pass 2 の renderParts ごとに 3 コレクション確保していたのを排除する。
        // キー: ParsedGroupSet インスタンスの identity × lightMode int → Set<String>
        private val emissiveLightModeKeys = IdentityHashMap<ParsedGroupSet?, IntArray?>()
        private val emissivePresentCache = IdentityHashMap<ParsedGroupSet?, Array<MutableSet<String?>?>?>()

        private class ParsedGroupSet(// shadow/guide 除外後の元名前列
            val filteredGroupNames: MutableList<String?>, // 正規化(lowercase trim) 後
            val normalizedNames: MutableSet<String?>, // モデルに実在する正規化名
            val presentGroupNames: MutableSet<String?>,
            val legacyDisplaySelection: Boolean, // filteredGroupNames に emissive が 1 つでもあるか
            val hasAnyEmissiveGroup: Boolean
        ) {
            val empty: Boolean

            init {
                this.empty = filteredGroupNames.isEmpty()
            }
        }

        val renderer: ScriptModelRenderer = this

        // RTM NGTRenderer/GLHelper aliases — scripts import these as classes then call directly
        val NGTRenderer: ScriptModelRenderer = this

        // NPC biped animation angles (set by setRotationAngles, read by rotateAndRender)
        var headAngleX: Float = 0f
        var headAngleY: Float = 0f
        var headAngleZ: Float = 0f
        var bodyAngleX: Float = 0f
        var bodyAngleY: Float = 0f
        var bodyAngleZ: Float = 0f
        var rightArmAngleX: Float = 0f
        var rightArmAngleY: Float = 0f
        var rightArmAngleZ: Float = 0f
        var leftArmAngleX: Float = 0f
        var leftArmAngleY: Float = 0f
        var leftArmAngleZ: Float = 0f
        var rightLegAngleX: Float = 0f
        var rightLegAngleY: Float = 0f
        var rightLegAngleZ: Float = 0f
        var leftLegAngleX: Float = 0f
        var leftLegAngleY: Float = 0f
        var leftLegAngleZ: Float = 0f

        fun getModel(): Any? {
            if (mqoModel != null) {
                return mqoModel.scriptModel
            }
            return model
        }

        val modelObject: Any?
            /**
             * Returns the model object expected by legacy render scripts.
             */
            get() = getModel()

        val modelSet: Any?
            /**
             * Returns the model object as a legacy model-set placeholder.
             */
            get() = getModel()

        fun registerParts(parts: Any?): Any {
            val names: MutableList<String?>? = extractGroupNames(parts)
            val usable: MutableList<String?> = ArrayList<String?>()
            var rejected = 0
            if (names != null) {
                for (name in names) {
                    val normalized: String = normalizeLegacyGroupName(name)
                    if (!normalized.isEmpty() && mqoModel != null && mqoModel.hasGroupNamed(normalized)) {
                        scriptRegisteredGroups.add(normalized)
                        usable.add(name)
                    } else {
                        rejected++
                    }
                }
            }
            // 初回調査用。通常プレイでは script 初期化ログだけでもかなり多くなるため debug に留める。
            if (scriptRegisteredGroups.size < 200) {
                RealTrainModRenewed.LOGGER.debug(
                    "[registerParts] partsType={} extracted={} usable={} rejected={} totalRegistered={}",
                    if (parts == null) "null" else parts.javaClass.getSimpleName(),
                    if (names == null) 0 else names.size,
                    usable.size, rejected, scriptRegisteredGroups.size
                )
            }
            // RTM 原作の Parts は .render(renderer) で対応グループを描画する。
            // ここで実用版 ScriptParts を返し、bogieF.render(renderer) 等が機能するようにする。
            return ScriptParts(this, usable)
        }

        /** スクリプトが `bogieF.render(renderer)` を呼ぶと現在の poseStack で描画する。  */
        class ScriptParts(private val renderer: ScriptModelRenderer?, groupNames: MutableList<String?>?) {
            val groupNames: MutableList<String?>

            init {
                this.groupNames = if (groupNames == null) mutableListOf<String?>() else ArrayList<String?>(groupNames)
            }

            fun getObjects(model: Any?): MutableList<Any?> {
                return if (renderer == null) mutableListOf<Any?>() else renderer.getScriptModelObjects(
                    java.lang.String.join(
                        ",",
                        groupNames
                    )
                )
            }

            val objects: MutableList<Any?>
                get() = getObjects(null)

            /** RTM 原作互換: 与えた renderer の現在の poseStack でグループを描画する。  */
            fun render(rendererArg: Any?) {
                val target = if (rendererArg is ScriptModelRenderer) rendererArg else renderer
                if (target != null) {
                    target.renderRegisteredGroups(groupNames)
                }
            }

            /** 引数なし変種 (一部 RTM スクリプトで使われる)  */
            fun render() {
                if (renderer != null) {
                    renderer.renderRegisteredGroups(groupNames)
                }
            }
        }

        fun renderRegisteredGroups(rawNames: MutableList<String?>?) {
            val stack = poseStack
            val targetBuffer = buffer
            if (rawNames == null || rawNames.isEmpty() || mqoModel == null || stack == null || targetBuffer == null ||
                isMatrixInvalid()
            ) {
                return
            }
            renderPartsCalls++
            var normalized: MutableSet<String?> = java.util.LinkedHashSet<String?>()
            for (n in rawNames) {
                val x: String = normalizeLegacyGroupName(n)
                if (x.isEmpty()) continue
                // pack 由来の擬似シャドウ (黒い平板を地面に貼るタイプ) は無効化する。
                // entity renderer の shadowRadius は 0 にしてあるが、こちらはモデル内の
                // group として描かれるためここでフィルタする。
                // shadow 完全一致のみ。 shadowXX など別の group まで巻き込まないようにする。
                if (x.equals("shadow", ignoreCase = true)) continue
                if (currentPass >= 2 && isLightOffGroup(x)) continue
                if (shouldSuppressOerMseScriptHoodGroup(x)) continue
                normalized.add(x)
            }
            if (normalized.isEmpty()) return
            if (currentPass >= 2) {
                normalized = filterLegacyScriptEmissiveGroups(normalized)
                if (normalized.isEmpty()) return
            }
            // 角度バリアント (body-30 / body-90 / body-180 / bogie1-90 等) のフィルタ。
            // RTM の連結曲げ用に用意された「曲げ角ごとの代替メッシュ」で、本家は曲げ角に応じ
            // 1 つだけ描画する。移植版は同じ Parts に登録された全部を重ねて描くため、
            // 直線状態でも曲げボディが翼のように外へはみ出す (ポリゴン重なり)。
            // 0°(サフィックス無し)の本体が同じ描画呼び出しに含まれる時だけ曲げ変種を除外し、
            // 直線/単行の見た目を本家に合わせる。
            if (!shouldKeepNumberedVariantGroups(normalized)) {
                normalized = filterAngleVariantGroups(normalized)
            }
            if (normalized.isEmpty()) return
            try {
                val renderPackedLight = effectivePackedLightForScriptParts(normalized)
                // RTM 本家の vehicle script は pass0=不透明(alpha==255)、pass1=半透明(alpha<255)。
                // ここで pass1 でも不透明側を描いてしまうと、KQ のような AlphaBlend 車両で
                // 窓/ガラス用グループの不透明マスクが床下に黒板のように残る。
                if (currentPass != 1) {
                    mqoModel.renderNamedGroups(stack, targetBuffer, renderPackedLight, overlay, false, normalized, this)
                    scriptedOpaqueGroups.addAll(normalized)
                }

                if (currentPass >= 2) {
                    // emissive pass: renderSelectedBatches が「emissiveTexture を持たない batch」を
                    // スキップするので、 ここでは発光マテリアル (Light) のみ描画される (前照灯/室内灯)。
                    // 半透明発光も「全不透明の後」に描くため遅延キューへ。
                    if (!enqueueOrDrawTranslucent(stack, targetBuffer, currentPass, normalized)) {
                        mqoModel.renderNamedGroups(
                            stack,
                            targetBuffer,
                            renderPackedLight,
                            overlay,
                            true,
                            normalized,
                            this
                        )
                    }
                    scriptedTranslucentGroups.addAll(normalized)
                } else {
                    // 通常 translucent (AlphaBlend = 車体/窓/内装) は本家RTMと同様に pass1 でだけ描く。
                    // pass0 では opaqueTexture 側だけ、pass1 では windowTexture 側だけ出す。
                    if (currentPass == 1) {
                        if (!enqueueOrDrawTranslucent(stack, targetBuffer, currentPass, normalized)) {
                            mqoModel.renderNamedGroups(
                                stack,
                                targetBuffer,
                                renderPackedLight,
                                overlay,
                                true,
                                normalized,
                                this
                            )
                        }
                        scriptedTranslucentGroups.addAll(normalized)
                    }
                }
                // replay キャッシュに記録する。記録しないと2フレーム目以降は JS を skip した結果
                // この描画呼び出しも再現されず、scriptedOpaqueGroups が空のままになり
                // baked filter が動かず全 body 変形が重なって描画される。
                if (!this.isReplaying) {
                    val joined = java.lang.String.join(",", normalized)
                    recordOp(OP_RENDER_PARTS, 0f, 0f, 0f, 0f, 0f, joined, ' ')
                }
            } catch (ignored: Throwable) {
                // 個別グループの描画失敗で他の処理を巻き込まない
            }
        }

        /**
         * 連結曲げ用の角度/ミラー変種を除外する。
         * RTM は同一 Parts に「素の名前(0°直線)」と曲げ角・ミラーの代替メッシュをまとめて登録し、
         * 曲げ角に応じ 1 つだけ描く (例: C57 body = "body","body(mx)","body-30",...,"body-180(mx)" /
         * bogie2 = "bogie2-60","bogie2-90" など素の名前が無いケースもある)。
         * 移植版には曲げ処理が無いため全部描くと翼状に重なる。
         * 
         * ★重要: RTM の "(mx)" は「鏡像コピー」。直線(0°)の車体は body と body(mx) の
         * 左右 2 枚で 1 つになる。曲げ変種は body-30 / body-90(mx) のように**角度数字付き**だけ。
         * → 角度数字付き(-NN, NN≥THRESHOLD)だけを「曲げ変種」として落とす。角度の無い (mx) は
         * 直線の鏡像半身なので残す (これを落とすと車体が半分になり断片化する)。
         * 
         * 方針:
         * - 非曲げ変種(素 / 鏡像 (mx) / セクション -1〜-9) は保持。
         * - 曲げ変種(角度-数字≥THRESHOLD)は一切描かない (素の有無に関わらず)。
         * D51 の body-1/2/3 はセクション扱いで全保持。
         */
        private fun filterAngleVariantGroups(names: MutableSet<String?>): MutableSet<String?> {
            // 曲げ変種(角度数字付き)は一切描かない。直線の単行列車に曲げメッシュは不要で、
            // 原点姿勢で描くと翼状/斜めに飛び出す (C57 の body-30..180, bogie2-60 等)。
            // 素 / 鏡像 (mx) / セクション(-1〜-9) のみ残す。素が無い部品 (例: 面を持たない
            // placeholder bogie2 しか 0° が無い後台車) は 0° では非表示 = RTM 互換。
            val out: MutableSet<String?> = java.util.LinkedHashSet<String?>(names.size)
            for (n in names) {
                if (!Companion.isBendVariant(n!!)) {
                    out.add(n)
                }
            }
            return out
        }

        private fun shouldKeepNumberedVariantGroups(names: MutableSet<String?>?): Boolean {
            val entity = currentEntity
            if (entity !is TrainEntity) {
                return false
            }
            val id: String? = entity.vehicleId
            if (id == null) {
                return false
            }
            val lowerId = id.lowercase()
            // TTP/TkmTP は body-35/body-90/common-35/bogie-35 等を曲線用の捨てパーツではなく
            // 実モデルの分割部品として使う車両が多い。ここで落とすと車体の大半が消えて
            // スクリプト部品だけが残るため、TTP 全体では番号付きグループをそのまま描く。
            return lowerId.startsWith("ttp_")
        }

        fun hasAlphaPassContent(): Boolean {
            if (mqoModel == null) {
                return true
            }
            if (!scriptRegisteredGroups.isEmpty()) {
                return true
            }
            return mqoModel.hasTranslucentBatches()
                    || mqoModel.hasGroupNamed("alpha")
                    || mqoModel.hasGroupNamed("doorFL1")
                    || mqoModel.hasGroupNamed("doorFR1")
                    || mqoModel.hasGroupNamed("doorBL1")
                    || mqoModel.hasGroupNamed("doorBR1")
        }

        fun hasEmissivePassContent(): Boolean {
            if (mqoModel == null) {
                return true
            }
            if (!scriptRegisteredGroups.isEmpty()) {
                return true
            }
            return mqoModel.hasGroupNamed("light")
                    || mqoModel.hasGroupNamed("lightF")
                    || mqoModel.hasGroupNamed("lightB")
                    || mqoModel.hasGroupNamed("ExLightF")
                    || mqoModel.hasGroupNamed("ExLightB")
                    || mqoModel.hasGroupNamed("ElightF")
                    || mqoModel.hasGroupNamed("ElightB")
                    || mqoModel.hasGroupNamed("dest")
                    || mqoModel.hasGroupNamed("type")
                    || mqoModel.hasLegacyLightTextures()
        }

        val config: TrainEntity.ConfigCompat
            get() {
                val entity = currentEntity
                if (entity is TrainEntity) {
                    return entity.resourceState.resourceSet.config
                }
                return TrainEntity.ConfigCompat(defaultModelName)
            }

        val resourceName: String?
            get() {
                val entity = currentEntity
                if (entity is TrainEntity) {
                    return entity.vehicleId
                }
                return if (defaultModelName.isBlank()) "train" else defaultModelName
            }

        val modelName: String?
            get() {
                val entity = currentEntity
                if (entity is TrainEntity) {
                    return entity.vehicleId
                }
                if (entity is InstalledObjectBlockEntity) {
                    return entity.modelName
                }
                return defaultModelName
            }

        fun setRenderContext(
            poseStack: PoseStack?,
            buffer: MultiBufferSource?,
            packedLight: Int,
            overlay: Int,
            pass: Int,
            entity: Any?
        ) {
            restoreMatrixDepth(0)
            this.poseStack = poseStack
            this.buffer = buffer
            // pass 2+ is the legacy emissive pass, but do not make the whole script pass
            // fullbright. Some train scripts (Spacia etc.) call render_parts() again in pass
            // 2, and a pass-wide fullbright makes exterior body meshes flash/glow in daylight.
            // Only explicit GLHelper.setLightmapMaxBrightness() or filtered emissive batches
            // should become fullbright.
            this.packedLight = packedLight
            this.basePackedLight = packedLight
            this.lightmapMaxForced = false
            this.overlay = overlay
            this.currentPass = pass
            this.currentEntity = entity
            this.boundTexture = null
            this.tessellatorFallbackTexture = null
            resetColor()
            clearUvWindow()
            clearUvOffset()
            this.matrixDepth = 0
            this.invalidMatrixDepth = -1
            this.scriptLocalOrigin = Vec3.ZERO
            this.scriptLocalStack.clear()
        }

        fun clearRenderContext() {
            restoreMatrixDepth(0)
            this.poseStack = null
            this.buffer = null
            this.currentEntity = null
            this.boundTexture = null
            this.tessellatorFallbackTexture = null
            this.currentPass = 0
            resetColor()
            clearUvWindow()
            clearUvOffset()
            this.matrixDepth = 0
            this.invalidMatrixDepth = -1
            this.scriptLocalOrigin = Vec3.ZERO
            this.scriptLocalStack.clear()
        }

        fun scriptEntityFor(entity: Any?): Any? {
            if (entity is TrainEntity) {
                // Legacy scripts read many values as plain fields (count, doorMoveL,
                // field_70173_aa, seatRotation, etc.). A cached wrapper freezes those
                // fields and stops tick-driven displays like E259 rollsign/LCD.
                cachedExecutorTrain = entity
                cachedExecutor = LegacyScriptExecutor(entity)
                return cachedExecutor
            }
            return entity
        }

        fun setColor(red: Double, green: Double, blue: Double, alpha: Double) {
            this.colorRed = Mth.clamp(red.toFloat(), 0.0f, 1.0f)
            this.colorGreen = Mth.clamp(green.toFloat(), 0.0f, 1.0f)
            this.colorBlue = Mth.clamp(blue.toFloat(), 0.0f, 1.0f)
            this.colorAlpha = Mth.clamp(alpha.toFloat(), 0.0f, 1.0f)
            recordOp(OP_SET_COLOR, red.toFloat(), green.toFloat(), blue.toFloat(), alpha.toFloat(), 0f, null, ' ')
        }

        fun resetColor() {
            this.colorRed = 1.0f
            this.colorGreen = 1.0f
            this.colorBlue = 1.0f
            this.colorAlpha = 1.0f
            recordOp(OP_RESET_COLOR, 0f, 0f, 0f, 0f, 0f, null, ' ')
        }

        val colorRed255: Int
            get() = Mth.clamp(Math.round(colorRed * 255.0f), 0, 255)

        val colorGreen255: Int
            get() = Mth.clamp(Math.round(colorGreen * 255.0f), 0, 255)

        val colorBlue255: Int
            get() = Mth.clamp(Math.round(colorBlue * 255.0f), 0, 255)

        fun applyAlpha255(alpha: Int): Int {
            return Mth.clamp(Math.round(Mth.clamp(alpha, 0, 255) * colorAlpha), 0, 255)
        }

        fun resetRenderStatistics() {
            this.renderPartsCalls = 0
            this.renderedBatchCount = 0
            this.scriptedOpaqueGroups.clear()
            this.scriptedTranslucentGroups.clear()
            this.scriptedEmissiveGroups.clear()
            this.firstTranslucentPass = -1
        }

        fun onBatchRendered() {
            renderedBatchCount++
        }

        var currentMatId: Int = 0
        var currentBatchTexture: Identifier? = null
        private var tessellatorFallbackTexture: Identifier? = null

        init {
            this.mqoModel = if (model is MqoModel) model else null
            this.defaultModelName = if (defaultModelName == null) "" else defaultModelName
        }

        fun clearScriptTexture() {
            boundTexture = null
            recordOp(OP_CLEAR_TEX, 0f, 0f, 0f, 0f, 0f, null, ' ')
        }

        fun setUvWindow(u0: Double, v0: Double, u1: Double, v1: Double) {
            disableReplayCacheForFrame()
            uvWindowActive = true
            uvU0 = u0.toFloat()
            uvV0 = v0.toFloat()
            uvU1 = u1.toFloat()
            uvV1 = v1.toFloat()
            recordOp(OP_SET_UV_WINDOW, uvU0, uvV0, uvU1, uvV1, 0f, null, ' ')
        }

        fun clearUvWindow() {
            uvWindowActive = false
            uvU0 = 0.0f
            uvV0 = 0.0f
            uvU1 = 1.0f
            uvV1 = 1.0f
            recordOp(OP_CLEAR_UV_WINDOW, 0f, 0f, 0f, 0f, 0f, null, ' ')
        }

        /**
         * UV ウィンドウまたは UV オフセットがアクティブかどうか。
         * GPU VBO キャッシュは静的 UV を前提とするため、これらが有効なフレームでは
         * CPU フォールバック経路を使う。
         */
        fun hasUvWindow(): Boolean {
            return uvWindowActive || uvOffsetActive
        }

        fun setUvOffset(u: Double, v: Double) {
            disableReplayCacheForFrame()
            uvOffsetActive = true
            uvOffsetU = u.toFloat()
            uvOffsetV = v.toFloat()
        }

        fun clearUvOffset() {
            uvOffsetActive = false
            uvOffsetU = 0.0f
            uvOffsetV = 0.0f
        }

        fun mapU(u: Float): Float {
            val result = if (uvOffsetActive) u + uvOffsetU else u
            return if (uvWindowActive) uvU0 + (uvU1 - uvU0) * result else result
        }

        fun mapV(v: Float): Float {
            val result = if (uvOffsetActive) v + uvOffsetV else v
            return if (uvWindowActive) uvV0 + (uvV1 - uvV0) * result else result
        }

        fun mapU(u: Float, sourceMin: Float, sourceMax: Float): Float {
            val result = if (uvOffsetActive) u + uvOffsetU else u
            if (!uvWindowActive) {
                return result
            }
            val width = sourceMax - sourceMin
            val normalized = if (abs(width) < 1.0E-6f) 0.5f else (result - sourceMin) / width
            return uvU0 + (uvU1 - uvU0) * normalized
        }

        fun mapV(v: Float, sourceMin: Float, sourceMax: Float): Float {
            val result = if (uvOffsetActive) v + uvOffsetV else v
            if (!uvWindowActive) {
                return result
            }
            val height = sourceMax - sourceMin
            val normalized = if (abs(height) < 1.0E-6f) 0.5f else (result - sourceMin) / height
            return uvV0 + (uvV1 - uvV0) * normalized
        }

        fun bindScriptTexture(domain: String?, path: String?, frameIndex: Int) {
            val safeDomain = if (domain == null || domain.isBlank()) "minecraft" else domain
            boundTexture = MqoModelLoader.getScriptTexture(safeDomain, path, frameIndex)
            if (path != null && !path.isBlank()) {
                recordOp(OP_BIND_TEX, frameIndex.toFloat(), 0f, 0f, 0f, 0f, safeDomain + "\n" + path, ' ')
            }
        }

        fun getScriptModelObjects(groupsCsv: String?): MutableList<Any?> {
            if (mqoModel == null || groupsCsv == null || groupsCsv.isBlank()) {
                return mutableListOf<Any?>()
            }
            val groups: MutableSet<String?> = expandSerializedGroupNames(groupsCsv).stream()
                .map<String?> { groupName: String? -> normalizeLegacyGroupName(groupName) }
                .filter { name: String? -> !name!!.isEmpty() }
                .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
            if (groups.isEmpty()) {
                return mutableListOf<Any?>()
            }
            val out: MutableList<Any?> = ArrayList<Any?>()
            for (q in mqoModel.getGroupQuadCorners(groups)) {
                if (q == null || q.size < 12) {
                    continue
                }
                out.add(q)
            }
            return out
        }

        fun markScriptManagedParts(parts: Any?) {
            if (parts == null) {
                return
            }
            val groupNames: MutableList<String?> = extractGroupNames(parts)
            for (name in groupNames) {
                val normalized: String = normalizeLegacyGroupName(name)
                if (!normalized.isEmpty() && mqoModel != null && mqoModel.hasGroupNamed(normalized)) {
                    scriptRegisteredGroups.add(normalized)
                }
            }
        }

        fun getScriptQuadVertexLists(parts: Any?): MutableList<MutableList<MutableList<Double?>?>?> {
            val out: MutableList<MutableList<MutableList<Double?>?>?> = ArrayList<MutableList<MutableList<Double?>?>?>()
            if (mqoModel == null || parts == null) return out
            val groupNames: MutableList<String?> = extractGroupNames(parts)
            if (groupNames.isEmpty()) return out
            val groups: MutableSet<String?> = java.util.LinkedHashSet<String?>()
            for (name in groupNames) {
                val normalized: String = normalizeLegacyGroupName(name)
                if (!normalized.isEmpty()) {
                    groups.add(normalized)
                }
            }
            if (groups.isEmpty()) return out
            for (q in mqoModel.getGroupQuadCorners(groups)) {
                var q: FloatArray? = q
                if (q == null || q.size < 12) continue
                q = sortQuadCornersForLegacyOverlay(q)
                val face: MutableList<MutableList<Double?>?> = ArrayList<MutableList<Double?>?>(4)
                for (i in 0..3) {
                    val vertex: MutableList<Double?> = ArrayList<Double?>(3)
                    vertex.add(q[i * 3].toDouble())
                    vertex.add(q[i * 3 + 1].toDouble())
                    vertex.add(q[i * 3 + 2].toDouble())
                    face.add(vertex)
                }
                out.add(face)
            }
            return out
        }

        fun tessellatorStart() {
            disableReplayCacheForFrame()
            tessellatorVertices.clear()
            tessColorRed = colorRed
            tessColorGreen = colorGreen
            tessColorBlue = colorBlue
            tessColorAlpha = colorAlpha
            tessNormalX = 0.0f
            tessNormalY = 1.0f
            tessNormalZ = 0.0f
            tessNormalSet = false
        }

        fun tessellatorAddVertex(x: Double, y: Double, z: Double) {
            tessellatorAddVertexWithUV(x, y, z, 0.0, 0.0)
        }

        fun tessellatorAddVertexWithUV(x: Double, y: Double, z: Double, u: Double, v: Double) {
            tessellatorVertices.add(
                TessVertex(
                    x.toFloat(),
                    y.toFloat(),
                    z.toFloat(),
                    mapU(u.toFloat(), 0.0f, 1.0f),
                    mapV(v.toFloat(), 0.0f, 1.0f)
                )
            )
        }

        fun tessellatorSetColor(r: Double, g: Double, b: Double, a: Double) {
            tessColorRed = Mth.clamp(r.toFloat(), 0.0f, 1.0f)
            tessColorGreen = Mth.clamp(g.toFloat(), 0.0f, 1.0f)
            tessColorBlue = Mth.clamp(b.toFloat(), 0.0f, 1.0f)
            tessColorAlpha = Mth.clamp(a.toFloat(), 0.0f, 1.0f)
        }

        fun tessellatorSetNormal(x: Double, y: Double, z: Double) {
            tessNormalX = x.toFloat()
            tessNormalY = y.toFloat()
            tessNormalZ = z.toFloat()
            tessNormalSet = true
        }

        fun tessellatorDraw() {
            if (tessellatorVertices.isEmpty() || poseStack == null || buffer == null) {
                tessellatorVertices.clear()
                return
            }
            onBatchRendered()
            var texture = boundTexture
            if (texture == null) {
                texture = tessellatorFallbackTexture
            }
            if (texture == null) {
                texture = currentBatchTexture
            }
            if (texture == null) {
                texture = MqoModelLoader.getScriptTexture("minecraft", "textures/block/white_wool.png", 0)
            }
            val vc =
                buffer!!.getBuffer(RenderTypes.entityTranslucent(texture!!))
            val mat: Matrix4f = poseStack!!.last().pose()
            val r = Math.round(Mth.clamp(tessColorRed, 0.0f, 1.0f) * 255.0f)
            val g = Math.round(Mth.clamp(tessColorGreen, 0.0f, 1.0f) * 255.0f)
            val b = Math.round(Mth.clamp(tessColorBlue, 0.0f, 1.0f) * 255.0f)
            val a = Math.round(Mth.clamp(tessColorAlpha, 0.0f, 1.0f) * 255.0f)
            val overlayBias = 0.0035f
            var i = 0
            while (i + 3 < tessellatorVertices.size) {
                val v0 = tessellatorVertices.get(i)
                val v1 = tessellatorVertices.get(i + 1)
                val v3 = tessellatorVertices.get(i + 3)
                var nx = tessNormalX
                var ny = tessNormalY
                var nz = tessNormalZ
                if (!tessNormalSet) {
                    val e1x = v1.x - v0.x
                    val e1y = v1.y - v0.y
                    val e1z = v1.z - v0.z
                    val e2x = v3.x - v0.x
                    val e2y = v3.y - v0.y
                    val e2z = v3.z - v0.z
                    nx = e1y * e2z - e1z * e2y
                    ny = e1z * e2x - e1x * e2z
                    nz = e1x * e2y - e1y * e2x
                    val nl = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
                    if (nl > 1.0E-6f) {
                        nx /= nl
                        ny /= nl
                        nz /= nl
                    } else {
                        nx = 0.0f
                        ny = 1.0f
                        nz = 0.0f
                    }
                }
                for (c in 0..3) {
                    val vtx = tessellatorVertices.get(i + c)
                    vc.addVertex(
                        mat,
                        vtx.x + nx * overlayBias,
                        vtx.y + ny * overlayBias,
                        vtx.z + nz * overlayBias
                    )
                        .setColor(r, g, b, a)
                        .setUv(vtx.u, vtx.v)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(if (currentPass >= 2 || isLegacyVehicleLightRequested()) 0x00F000F0 else packedLight)
                        .setNormal(nx, ny, nz)
                }
                i += 4
            }
            tessellatorVertices.clear()
        }

        /**
         * 指定グループ(LCD面)の各クワッド面の上に、gif/画像テクスチャをフルUV(0-1)で貼り付けて描画する。
         * E259 等の CustomMonitor_LCD は別オーバーレイモデルを NGTLib 直接GLで重ねるが、その API は
         * 1.21 に無いため、ここでは LCD 面の実頂点に直接 gif クワッドを描く(発光・面法線方向に微小オフセット)。
         * @param groupsCsv 対象グループ名(カンマ/スペース区切り, 例 "lcd1")
         */
        fun renderGifOnGroup(groupsCsv: String?, domain: String?, path: String?, frameIndex: Int) {
            if (mqoModel == null || poseStack == null || buffer == null || groupsCsv == null) return
            val groups: MutableSet<String?> = HashSet<String?>()
            for (g in groupsCsv.split("[ ,]+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                if (!g.isBlank()) groups.add(g.trim { it <= ' ' })
            }
            renderTextureWindowOnNormalizedGroupsWithRecord(
                groups, domain, path, frameIndex,
                0.0f, 0.0f, 1.0f, 1.0f
            )
        }

        fun renderGifOnParts(parts: Any?, domain: String?, path: String?, frameIndex: Int) {
            if (parts == null) return
            val groupNames: MutableList<String?> = extractGroupNames(parts)
            if (groupNames.isEmpty()) return
            val groups: MutableSet<String?> = java.util.LinkedHashSet<String?>()
            for (name in groupNames) {
                val normalized: String = normalizeLegacyGroupName(name)
                if (!normalized.isEmpty()) {
                    groups.add(normalized)
                }
            }
            renderTextureWindowOnNormalizedGroupsWithRecord(
                groups, domain, path, frameIndex,
                0.0f, 0.0f, 1.0f, 1.0f
            )
        }

        fun renderTextureWindowOnParts(
            parts: Any?, domain: String?, path: String?, frameIndex: Int,
            u0: Double, v0: Double, u1: Double, v1: Double
        ) {
            if (parts == null || path == null || path.isBlank()) return
            val groupNames: MutableList<String?> = extractGroupNames(parts)
            if (groupNames.isEmpty()) return
            val groups: MutableSet<String?> = java.util.LinkedHashSet<String?>()
            for (name in groupNames) {
                val normalized: String = normalizeLegacyGroupName(name)
                if (!normalized.isEmpty()) {
                    groups.add(normalized)
                }
            }
            if (groups.isEmpty()) return
            renderTextureWindowOnNormalizedGroupsWithRecord(
                groups, domain, path, frameIndex,
                u0.toFloat(), v0.toFloat(), u1.toFloat(), v1.toFloat()
            )
        }

        private fun renderTextureWindowOnNormalizedGroupsWithRecord(
            groups: MutableSet<String?>?, domain: String?, path: String?,
            frameIndex: Int, u0: Float, v0: Float, u1: Float, v1: Float
        ) {
            if (groups == null || groups.isEmpty() || path == null || path.isBlank()) return
            disableReplayCacheForFrame()
            if (!this.isReplaying) {
                val record = (java.lang.String.join(",", groups) + "\n"
                        + (if (domain == null || domain.isBlank()) "minecraft" else domain) + "\n"
                        + path)
                recordOp(OP_RENDER_TEXTURE_WINDOW, frameIndex.toFloat(), u0, v0, u1, v1, record, ' ')
            }
            renderTextureWindowOnNormalizedGroups(groups, domain, path, frameIndex, u0, v0, u1, v1)
        }

        private fun renderTextureWindowFromRecord(
            record: String?, frameIndex: Int,
            u0: Float, v0: Float, u1: Float, v1: Float
        ) {
            if (record == null || record.isBlank()) return
            val parts: Array<String?> = record.split("\\n".toRegex(), limit = 3).toTypedArray()
            if (parts.size < 3) return
            val groups: MutableSet<String?> = java.util.LinkedHashSet<String?>()
            for (group in parts[0]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                if (!group.isBlank()) {
                    groups.add(group.trim { it <= ' ' })
                }
            }
            renderTextureWindowOnNormalizedGroups(groups, parts[1], parts[2], frameIndex, u0, v0, u1, v1)
        }

        private fun renderTextureWindowOnNormalizedGroups(
            groups: MutableSet<String?>?, domain: String?, path: String?,
            frameIndex: Int, u0: Float, v0: Float, u1: Float, v1: Float
        ) {
            renderTexturedQuadsOnNormalizedGroups(groups, domain, path, frameIndex, u0, v0, u1, v1)
        }

        private fun renderGifOnNormalizedGroups(
            groups: MutableSet<String?>?,
            domain: String?,
            path: String?,
            frameIndex: Int
        ) {
            renderTexturedQuadsOnNormalizedGroups(groups, domain, path, frameIndex, 0.0f, 0.0f, 1.0f, 1.0f)
        }

        private fun renderTexturedQuadsOnNormalizedGroups(
            groups: MutableSet<String?>?, domain: String?, path: String?,
            frameIndex: Int, u0: Float, v0: Float, u1: Float, v1: Float
        ) {
            val stack = poseStack
            val targetBuffer = buffer
            if (mqoModel == null || stack == null || targetBuffer == null || groups == null || groups.isEmpty() ||
                isMatrixInvalid()
            ) return
            val quads: MutableList<FloatArray> = mqoModel.getGroupQuadCorners(groups).filterNotNull().toMutableList()
            if (quads.isEmpty()) return
            onBatchRendered()
            val tex: Identifier =
                MqoModelLoader.getScriptTexture(
                    if (domain == null || domain.isBlank()) "minecraft" else domain,
                    path,
                    frameIndex
                ) ?: return
            val vc =
                targetBuffer.getBuffer(RenderTypes.entityTranslucent(tex))
            val mat: Matrix4f = stack.last().pose()
            val light = packedLight
            for (q in quads) {
                var q = q
                q = orientOverlayQuad(q)!!
                // 面法線(隅0→1, 0→3 の外積)方向へ微小オフセットして z-fight 回避
                val e1x = q[3] - q[0]
                val e1y = q[4] - q[1]
                val e1z = q[5] - q[2]
                val e2x = q[9] - q[0]
                val e2y = q[10] - q[1]
                val e2z = q[11] - q[2]
                var nx = e1y * e2z - e1z * e2y
                var ny = e1z * e2x - e1x * e2z
                var nz = e1x * e2y - e1y * e2x
                val nl = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
                if (nl > 1.0E-6f) {
                    nx /= nl
                    ny /= nl
                    nz /= nl
                } else {
                    nz = 0f
                    ny = nz
                    nx = ny
                }
                val off = 0.003f
                val uv = arrayOf<FloatArray?>(
                    floatArrayOf(u0, v0),
                    floatArrayOf(u0, v1),
                    floatArrayOf(u1, v1),
                    floatArrayOf(u1, v0)
                )
                for (c in 0..3) {
                    val vx = q[c * 3] + nx * off
                    val vy = q[c * 3 + 1] + ny * off
                    val vz = q[c * 3 + 2] + nz * off
                    vc.addVertex(mat, vx, vy, vz)
                        .setColor(255, 255, 255, 255)
                        .setUv(uv[c]!![0], uv[c]!![1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(nx, ny, nz)
                }
            }
        }

        fun bindAnimatedScriptTexture(domain: String?, path: String?, tick: Double, fps: Double) {
            disableReplayCacheForFrame()
            boundTexture = MqoModelLoader.getScriptTextureByTick(domain, path, tick, fps)
        }

        fun getScriptTextureFrameCount(domain: String?, path: String?): Int {
            return MqoModelLoader.getScriptTextureData(domain, path).frames.size
        }

        fun getScriptTextureWidth(domain: String?, path: String?): Int {
            return MqoModelLoader.getScriptTextureData(domain, path).width
        }

        fun getScriptTextureHeight(domain: String?, path: String?): Int {
            return MqoModelLoader.getScriptTextureData(domain, path).height
        }

        fun bindTexture(texture: Any?) {
            if (texture == null) {
                clearScriptTexture()
                return
            }
            try {
                val domain: String? = readTextureComponent(texture, "func_110624_b", "namespace", "domain")
                val path: String? = readTextureComponent(texture, "func_110623_a", "path", "resourcePath")
                if (path == null || path.isBlank()) {
                    clearScriptTexture()
                    return
                }
                bindScriptTexture(if (domain == null || domain.isBlank()) "minecraft" else domain, path, 0)
            } catch (e: Exception) {
                clearScriptTexture()
            }
        }

        /**
         * Disables lighting for old model scripts.
         */
        fun disableLighting() {
            // Modern rendering keeps lighting in the packed light value.
        }

        /**
         * Enables lighting for old model scripts.
         */
        fun enableLighting() {
            this.packedLight = basePackedLight
            this.lightmapMaxForced = false
            recordOp(OP_ENABLE_LIGHTING, 0f, 0f, 0f, 0f, 0f, null, ' ')
        }

        /**
         * Applies a packed light value requested by old model scripts.
         */
        fun setBrightness(value: Any?) {
            if (value is Number) {
                this.packedLight = value.toInt()
                this.lightmapMaxForced = false
                recordOp(OP_SET_BRIGHTNESS, this.packedLight.toFloat(), 0f, 0f, 0f, 0f, null, ' ')
            } else {
                this.packedLight = basePackedLight
                this.lightmapMaxForced = false
                recordOp(OP_SET_BRIGHTNESS, this.packedLight.toFloat(), 0f, 0f, 0f, 0f, null, ' ')
            }
        }

        /**
         * Forces full brightness for emissive script parts.
         */
        fun setLightmapMaxBrightness() {
            this.packedLight = 0x00F000F0
            this.lightmapMaxForced = true
            recordOp(OP_SET_LIGHTMAP_MAX, 0f, 0f, 0f, 0f, 0f, null, ' ')
        }

        fun renderParts(groups: Any?) {
            val stack = poseStack
            val targetBuffer = buffer
            if (mqoModel == null || stack == null || targetBuffer == null || isMatrixInvalid()) {
                return
            }
            renderPartsCalls++
            val baseDepth = matrixDepth
            val savedPackedLight = packedLight
            val savedLightmapMaxForced = lightmapMaxForced
            try {
                // 文字列入力時はキャッシュ参照。SL の動軸スクリプト等で
                // 毎フレーム同じ groupsStr が来るため、解析処理を 1 回に削減。
                var groupNames: MutableList<String?>
                var normalizedNames: MutableSet<String?>
                var presentGroupNames: MutableSet<String?>
                val legacyDisplaySelection: Boolean
                if (groups is String) {
                    val parseCacheKey = currentPass.toString() + "\u0000" + groups
                    var cached = renderPartsParseCache.get(parseCacheKey)
                    if (cached == null) {
                        var raw: MutableList<String?> = expandSerializedGroupNames(groups)
                        raw = stripLegacyPlaceholderGroups(raw)
                        val filtered: MutableList<String?> = ArrayList<String?>(raw.size)
                        for (g in raw) {
                            val n: String = normalizeLegacyGroupName(g)
                            if (n == "shadow" || n.startsWith("shadow_") || n.endsWith("_shadow")) continue
                            if (n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari")) continue
                            if (currentPass >= 2 && isLightOffGroup(n)) continue
                            if (shouldSuppressOerMseScriptHoodGroup(n)) continue
                            filtered.add(g)
                        }
                        val legacy = isLegacyDisplaySelection(filtered)
                        val norm: MutableSet<String?> = java.util.LinkedHashSet<String?>(filtered.size)
                        for (g in filtered) {
                            val n: String = normalizeLegacyGroupName(g)
                            if (!n.isEmpty()) norm.add(n)
                        }
                        val present: MutableSet<String?> = java.util.LinkedHashSet<String?>(norm.size)
                        for (n in norm) {
                            if (mqoModel.hasGroupNamed(n)) present.add(n)
                        }
                        var hasEmissive = false
                        for (g in filtered) {
                            if (isEmissiveGroup(g)) {
                                hasEmissive = true
                                break
                            }
                        }
                        cached = ParsedGroupSet(filtered, norm, present, legacy, hasEmissive)
                        renderPartsParseCache.put(parseCacheKey, cached)
                    }
                    if (cached.empty) {
                        return
                    }
                    // pass 2 に録画する OP_RENDER_PARTS は emissive グループを持つものだけ。
                    // これにより pass 2 の op-list が SL の ~5 ops に圧縮され replay が超高速化。
                    if (currentRecording != null) {
                        recordOp(OP_RENDER_PARTS, 0f, 0f, 0f, 0f, 0f, groups, ' ')
                    }
                    groupNames = cached.filteredGroupNames
                    legacyDisplaySelection = cached.legacyDisplaySelection
                    if (legacyDisplaySelection && currentPass != 2) {
                        return
                    }
                    if (currentEntity is TrainEntity) {
                        if (currentPass >= 2) {
                            normalizedNames = cached.normalizedNames
                            presentGroupNames = cached.presentGroupNames
                        } else {
                            normalizedNames = cached.normalizedNames
                            presentGroupNames = cached.presentGroupNames
                        }
                    } else {
                        normalizedNames = cached.normalizedNames
                        presentGroupNames = cached.presentGroupNames
                    }
                } else {
                    // Collection/配列入力はキャッシュ非対応
                    groupNames = extractGroupNames(groups)
                    groupNames = stripLegacyPlaceholderGroups(groupNames)
                    groupNames = groupNames.stream()
                        .filter { g: String? ->
                            val n: String = normalizeLegacyGroupName(g)
                            if (n == "shadow" || n.startsWith("shadow_") || n.endsWith("_shadow")) return@filter false
                            if (n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari")) return@filter false
                            if (currentPass >= 2 && isLightOffGroup(n)) return@filter false
                            if (shouldSuppressOerMseScriptHoodGroup(n)) return@filter false
                            true
                        }
                        .collect(Collectors.toList())
                    legacyDisplaySelection = isLegacyDisplaySelection(groupNames)
                    if (legacyDisplaySelection && currentPass != 2) {
                        return
                    }
                    if (currentEntity is TrainEntity) {
                        if (currentPass >= 2) {
                            groupNames = groupNames.stream().collect(Collectors.toList())
                        }
                        if (groupNames.isEmpty()) {
                            return
                        }
                    }
                    normalizedNames = groupNames.stream()
                        .map<String?> { groupName: String? -> normalizeLegacyGroupName(groupName) }
                        .filter { name: String? -> !name!!.isEmpty() }
                        .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
                    presentGroupNames = normalizedNames.stream()
                        .filter(mqoModel::hasGroupNamed)
                        .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
                }
                if (currentPass >= 2) {
                    normalizedNames = filterLegacyScriptEmissiveGroups(normalizedNames)
                    presentGroupNames = normalizedNames.stream()
                        .filter(mqoModel::hasGroupNamed)
                        .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
                    if (normalizedNames.isEmpty() || presentGroupNames.isEmpty()) {
                        return
                    }
                }
                if (currentPass == 1) {
                    scriptedTranslucentGroups.addAll(presentGroupNames)
                } else if (currentPass < 2) {
                    scriptedOpaqueGroups.addAll(presentGroupNames)
                }
                val renderPackedLight = effectivePackedLightForScriptParts(presentGroupNames)
                currentMatId = 0
                if (legacyDisplaySelection) {
                    val boundRollsign = bindLegacyRollsignTextureIfPresent(groupNames)
                    try {
                        mqoModel.renderNamedGroups(
                            stack,
                            targetBuffer,
                            renderPackedLight,
                            overlay,
                            false,
                            normalizedNames,
                            this
                        )
                    } finally {
                        if (boundRollsign) {
                            clearUvWindow()
                            clearScriptTexture()
                        }
                    }
                } else {
                    if (currentPass >= 2) {
                        // Emissive pass: RTM light groups (lightF, lightB etc.) may be either
                        // opaque or translucent batches. Render both so nothing is skipped.
                        mqoModel.renderNamedGroups(
                            stack,
                            targetBuffer,
                            renderPackedLight,
                            overlay,
                            false,
                            normalizedNames,
                            this
                        )
                        mqoModel.renderNamedGroups(
                            stack,
                            targetBuffer,
                            renderPackedLight,
                            overlay,
                            true,
                            normalizedNames,
                            this
                        )
                    } else {
                        if (currentPass <= 0) {
                            mqoModel.renderNamedGroups(
                                stack,
                                targetBuffer,
                                renderPackedLight,
                                overlay,
                                false,
                                normalizedNames,
                                this
                            )
                        } else {
                            mqoModel.renderNamedGroups(
                                stack,
                                targetBuffer,
                                renderPackedLight,
                                overlay,
                                true,
                                normalizedNames,
                                this
                            )
                        }
                    }
                }
            } finally {
                packedLight = savedPackedLight
                lightmapMaxForced = savedLightmapMaxForced
                // このrenderParts呼び出し内で増えた分だけ戻す
                while (matrixDepth > baseDepth) {
                    poseStack!!.popPose()
                    matrixDepth--
                }
            }
        }

        private fun effectivePackedLightForScriptParts(presentGroupNames: MutableSet<String?>?): Int {
            val entity = currentEntity
            if (entity !is TrainEntity) {
                return packedLight
            }
            if (lightmapMaxForced) {
                return packedLight
            }
            if (presentGroupNames != null && presentGroupNames.isNotEmpty() && isLegacyVehicleLightRequested() &&
                presentGroupNames.all { isExteriorTrainLightGroup(it?.lowercase()) }
            ) {
                return 0x00F000F0
            }
            if (packedLight == basePackedLight) {
                return packedLight
            }
            if (presentGroupNames == null || presentGroupNames.isEmpty()) {
                return basePackedLight
            }
            for (name in presentGroupNames) {
                val lower = if (name == null) "" else name.lowercase()
                if (!isInteriorEmissionGroup(lower) && !isLegacyDisplayGroup(lower)) {
                    return basePackedLight
                }
            }
            if (!entity.isInteriorLightOn) {
                for (name in presentGroupNames) {
                    val lower = if (name == null) "" else name.lowercase()
                    if (isInteriorEmissionGroup(lower)) {
                        return basePackedLight
                    }
                }
            }
            return packedLight
        }

        private fun isLegacyVehicleLightRequested(): Boolean {
            val train = resolveCurrentTrainEntity() ?: return false
            return train.lightMode > 0 || train.isPantographUp || train.getCustomButtonValue(2) > 0
        }

        private fun shouldSuppressOerMseScriptHoodGroup(lowerGroupName: String?): Boolean {
            return false
        }

        private fun resolveCurrentTrainEntity(): TrainEntity? {
            val entity = currentEntity
            if (entity is TrainEntity) {
                return entity
            }
            if (entity is LegacyScriptExecutor) {
                return entity.vehicle
            }
            if (entity != null) {
                try {
                    val train = entity.javaClass.getMethod("getTrain").invoke(entity)
                    if (train is TrainEntity) {
                        return train
                    }
                } catch (ignored: Exception) {
                }
            }
            return null
        }

        private fun isFormationMiddle(train: TrainEntity?): Boolean {
            if (train == null) {
                return false
            }
            try {
                val trains = train.formationTrainsForDisplay
                val index = trains.indexOf(train)
                return trains.size > 1 && index > 0 && index < trains.size - 1
            } catch (ignored: Exception) {
                return false
            }
        }

        private fun stripLegacyPlaceholderGroups(groupNames: MutableList<String?>?): MutableList<String?> {
            if (groupNames == null || groupNames.isEmpty() || mqoModel == null) {
                return groupNames!!
            }
            val hasIndexedDest: Boolean = mqoModel.hasGroupNamed("dest0")
            val hasIndexedType: Boolean = mqoModel.hasGroupNamed("type0")
            val hasAnimatedDoors = mqoModel.hasGroupNamed("doorFL")
                    || mqoModel.hasGroupNamed("doorFR")
                    || mqoModel.hasGroupNamed("doorBL")
                    || mqoModel.hasGroupNamed("doorBR")
            val hasCabLeverStates = mqoModel.hasGroupNamed("L_F")
                    || mqoModel.hasGroupNamed("L_M")
                    || mqoModel.hasGroupNamed("L_B")
            return groupNames.stream()
                .filter { group: String? ->
                    if (group == null) {
                        return@filter false
                    }
                    val trimmed = group.trim { it <= ' ' }
                    val lower = trimmed.lowercase()
                    if (hasIndexedDest && lower == "dest") {
                        return@filter false
                    }
                    if (hasIndexedType && lower == "type") {
                        return@filter false
                    }
                    if (hasCabLeverStates && lower == "lever") {
                        return@filter false
                    }
                    if (hasAnimatedDoors && lower == "door") {
                        return@filter false
                    }
                    true
                }
                .collect(Collectors.toList())
        }

        private fun isLegacyDisplaySelection(groupNames: MutableList<String?>?): Boolean {
            if (groupNames == null || groupNames.isEmpty()) {
                return false
            }
            for (groupName in groupNames) {
                if (!isLegacyDisplayGroup(groupName)) {
                    return false
                }
            }
            return true
        }

        private fun isLegacyDisplayGroup(groupName: String?): Boolean {
            if (groupName == null) {
                return false
            }
            val lower = groupName.trim { it <= ' ' }.lowercase()
            return lower == "dest"
                    || lower == "type"
                    || lower.startsWith("dest") && lower.length > 4 && lower.substring(4).chars()
                .allMatch(IntPredicate { codePoint: Int -> Character.isDigit(codePoint) }) || lower.startsWith("type") && lower.length > 4 && lower.substring(
                4
            ).chars().allMatch(
                IntPredicate { codePoint: Int -> Character.isDigit(codePoint) })
        }

        private fun shouldRenderLightGroup(train: TrainEntity?, groupName: String?): Boolean {
            if (train == null || groupName == null) {
                return true
            }
            val lower = groupName.lowercase()
            if (lower == "lightf" || lower == "lightb") {
                // 旧RTM系 script は lightF/lightB の選択自体を JS 側で行うことがあるので、
                // ここで消してしまわず script の描画意図を優先する。
                return true
            }
            val head = lower.contains("hlight") || lower.contains("headlight") || lower.contains("head_light")
                    || lower == "lightf" || lower == "light_f" || lower == "frontlight" || lower == "front_light"
            val tail = lower.contains("tlight") || lower.contains("taillight") || lower.contains("tail_light")
                    || lower == "lightb" || lower == "light_b" || lower == "rearlight" || lower == "rear_light"
            val auxiliary = lower.contains("elight")
            if (!head && !tail && !auxiliary) {
                return true
            }
            val mode = train.lightMode
            if (mode <= 0) {
                return false
            }
            if (head) {
                return mode == 1 || mode == 2 || mode == 3
            }
            if (tail) {
                return mode >= 2
            }
            return true
        }

        private fun shouldRenderTrainInterior(train: TrainEntity): Boolean {
            try {
                val mc = Minecraft.getInstance()
                val cameraEntity = mc.gameRenderer.getMainCamera().entity()
                return (mc.player != null && mc.player!!.getVehicle() === train)
                        || (cameraEntity != null && cameraEntity.distanceToSqr(train) < 8.0 * 8.0)
            } catch (ignored: Throwable) {
                return true
            }
        }

        private fun shouldRenderInteriorGroup(groupName: String?, renderInterior: Boolean): Boolean {
            return true
        }

        private fun filterLegacyScriptEmissiveGroups(groupNames: MutableSet<String?>?): MutableSet<String?> {
            if (groupNames == null || groupNames.isEmpty()) {
                return groupNames!!
            }
            val entity = currentEntity
            val train: TrainEntity? = if (entity is TrainEntity) entity else null
            val interiorOn = train != null && train.isInteriorLightOn
            return groupNames.stream()
                .filter { name: String? -> shouldRenderLegacyScriptEmissiveGroup(train, name, interiorOn) }
                .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))
        }

        private fun shouldRenderLegacyScriptEmissiveGroup(
            train: TrainEntity?,
            groupName: String?,
            interiorOn: Boolean
        ): Boolean {
            if (groupName == null || groupName.isBlank()) {
                return false
            }
            val lower = groupName.lowercase()
            if (train != null) {
                // For train vehicles, legacy pass > 1 must not turn exterior body/light
                // meshes into daylight/fullbright flashes. Interior light is the only
                // train-wide emissive surface here; headlights and destination signs are
                // rendered normally in pass 0/1 and should not brighten the shell.
                if (interiorOn && isInteriorEmissionGroup(lower) && !isExteriorTrainLightGroup(lower)) {
                    return true
                }
                return shouldRenderLightGroup(train, lower) && !isLightOffGroup(lower)
            }
            if (isLegacyDisplayGroup(lower)) {
                return true
            }
            if (isInteriorEmissionGroup(lower)) {
                return interiorOn
            }
            if (lower.matches("lamp_\\d+".toRegex())) {
                return false
            }
            if (lower.contains("doorlamp")) {
                return false
            }
            if (lower.contains("light") || lower.contains("lamp") || lower.contains("marker")) {
                return shouldRenderLightGroup(train, lower) && !isLightOffGroup(lower)
            }
            return false
        }

        private fun isInteriorEmissionGroup(lowerGroupName: String?): Boolean {
            if (lowerGroupName == null) {
                return false
            }
            if (isExteriorTrainLightGroup(lowerGroupName)) {
                return false
            }
            return lowerGroupName.contains("interior")
                    || lowerGroupName.contains("roomlight")
                    || lowerGroupName.contains("room_light")
                    || lowerGroupName.contains("cabinlight")
                    || lowerGroupName.contains("cabin_light")
                    || lowerGroupName.contains("_ceil")
                    || lowerGroupName.contains("led_box")
                    || lowerGroupName.contains("led")
                    || lowerGroupName == "i_body"
                    || lowerGroupName == "inner"
        }

        private fun isExteriorTrainLightGroup(lowerGroupName: String?): Boolean {
            if (lowerGroupName == null) {
                return false
            }
            return lowerGroupName.contains("hlight")
                    || lowerGroupName.contains("headlight")
                    || lowerGroupName.contains("head_light")
                    || lowerGroupName.contains("tlight")
                    || lowerGroupName.contains("taillight")
                    || lowerGroupName.contains("tail_light")
                    || lowerGroupName == "lightf"
                    || lowerGroupName == "light_f"
                    || lowerGroupName == "lightb"
                    || lowerGroupName == "light_b"
                    || lowerGroupName == "frontlight"
                    || lowerGroupName == "front_light"
                    || lowerGroupName == "rearlight"
                    || lowerGroupName == "rear_light"
        }

        private fun bindLegacyRollsignTextureIfPresent(groupNames: MutableList<String?>?): Boolean {
            val entity = currentEntity
            if (entity !is TrainEntity) {
                return false
            }
            val definition = VehicleRegistry.getById(entity.vehicleId)
            if (definition == null) {
                return false
            }
            val rollsignTexture = definition.getRollsignTexture()
            if (rollsignTexture == null || rollsignTexture.isBlank()) {
                return false
            }
            val count = max(1, if (definition.getRollsignNames().isEmpty()) 1 else definition.getRollsignNames().size)
            val index = Math.floorMod(entity.destinationIndex, count)
            val v0 = index / count.toFloat()
            val v1 = (index + 1.0f) / count.toFloat()
            bindScriptTexture("minecraft", rollsignTexture, 0)
            setUvWindow(0.0, v0.toDouble(), 1.0, v1.toDouble())
            return true
        }

        private fun isEmissiveGroup(groupName: String?): Boolean {
            if (groupName == null) {
                return false
            }
            val lower = groupName.lowercase()
            return lower.contains("light")
                    || lower.contains("lamp")
                    || lower.contains("marker")
                    || lower.contains("led")
                    || lower.contains("幕")
                    || lower.contains("roll")
                    || lower == "dest"
                    || lower == "type"
                    || DEST_N_PATTERN.matcher(lower).matches()
                    || TYPE_N_PATTERN.matcher(lower).matches()
                    || lower.contains("destination")
        }

        fun renderPart(group: String?) {
            renderParts(group)
        }

        fun render(groups: Any?) {
            renderParts(groups)
        }

        fun hasScriptRenderedGroups(): Boolean {
            // RTM scripts own every group registered via registerParts().
            // Even when a script intentionally skips a registered group this frame,
            // baked rendering must not draw it back in (MSE hood/end parts, car-type variants).
            return !scriptRegisteredGroups.isEmpty() || !scriptedOpaqueGroups.isEmpty() || !scriptedTranslucentGroups.isEmpty()
        }

        /**
         * Called when the script is permanently disabled (render() threw an exception).
         * Clears group ownership so baked render can render all groups normally instead
         * of skipping everything that was registered via registerParts() during init.
         */
        fun clearScriptRegisteredGroups() {
            scriptRegisteredGroups.clear()
            scriptedOpaqueGroups.clear()
            scriptedTranslucentGroups.clear()
            scriptedEmissiveGroups.clear()
        }

        fun shouldRenderBakedGroup(groupName: String?, translucent: Boolean): Boolean {
            val normalized: String = normalizeLegacyGroupName(groupName)
            if (normalized.isEmpty()) {
                return true
            }
            // 擬似シャドウ group (完全一致のみ) は常に skip (ユーザー要望で車両の影は無効化)。
            if (normalized.equals("shadow", ignoreCase = true)) {
                return false
            }
            if (shouldSuppressOerMseScriptHoodGroup(normalized)) {
                return false
            }
            // 角度曲げ変種 (body-30 / body-180(mx) / bogie1-90 / bogie2-60 等、角度数字付き) は
            // RTM の連結曲げ用代替メッシュ。移植版には曲げ処理が無く、原点姿勢で描くと翼状/斜めに
            // 散乱する。直線の単行列車では一切描かない (台車ルールより前に判定。bogie2-60 もここで除外)。
            // 角度の無い (mx) 鏡像は直線の半身なので対象外。D51 の body-1/2/3 はセクション扱い。
            if (isBendVariant(normalized)) {
                return false
            }
            // 台車・車輪グループは常にベイクド描画する。スクリプトの render_bogie() が
            // サイレント失敗・座標バグ・getWheelRotationR 未実装等で見えなくなる事例が
            // 多発するため、スクリプト描画と併せて baked からも必ず描く。
            // (二重描画になるが、車輪が消えるよりは重なって見える方を優先 — ユーザー要望)
            val isBogieLike: Boolean = isBogieLikeGroup(normalized)
            if (isBogieLike) {
                return !scriptedOpaqueGroups.contains(normalized)
                        && !scriptedTranslucentGroups.contains(normalized)
            }
            // Groups registered by the script in init() are fully managed by it —
            // skip them in baked render even if they weren't rendered this frame
            // (e.g. body02 when CarType="01": script skips it, baked must too).
            if (scriptRegisteredGroups.contains(normalized)) {
                return translucent && !scriptedTranslucentGroups.contains(normalized)
            }
            // RTM script は on/off、号車別、表示種別の片方だけを render() で選ぶ。
            // 直接 registerParts されていない兄弟 group を baked が描くと、
            // MSE の幌、スペーシアの急行灯、床下板や表示器の別状態が復活する。
            // そのため script が同じ「切替ファミリー」を 1 つでも登録している場合、
            // 未登録の兄弟も script 管理として baked から落とす。
            if (scriptRegisteredAnyInSelectorFamily(normalized)) {
                return false
            }
            // "type" セレクタ変種 (type_1 / type_180 / type0 等) は RTM が車種設定で 1 つだけ
            // 表示する front-end タイプ。同じ MQO に複数同梱され、スクリプトは使う 1 つだけ登録する。
            // スクリプトが type* を登録済みなのに、この未登録 type* を baked が原点描画すると
            // 空中に破片として出る (C57_001 の type_180 = ユーザー報告の散乱パネル)。
            // → type* を 1 つでも登録していれば、未登録の type* は描かない。
            if (normalized.startsWith("type") && scriptRegisteredAnyWithPrefix("type")) {
                return false
            }
            return !scriptedOpaqueGroups.contains(normalized)
                    && !scriptedTranslucentGroups.contains(normalized)
        }

        /** scriptRegisteredGroups に指定 prefix で始まるグループが 1 つでもあるか。  */
        private fun scriptRegisteredAnyWithPrefix(prefix: String): Boolean {
            for (g in scriptRegisteredGroups) {
                if (g.startsWith(prefix)) {
                    return true
                }
            }
            return false
        }

        private fun currentTrainHasSeparateBogieModel(): Boolean {
            val entity = currentEntity
            if (entity !is TrainEntity) {
                return false
            }
            val def = VehicleRegistry.getById(entity.vehicleId)
            return def != null && def.getBogies().stream()
                .anyMatch { b: VehicleDefinition.BogieDefinition? ->
                    b!!.modelFile() != null && !b.modelFile().isBlank()
                }
        }

        private fun scriptRegisteredAnyInSelectorFamily(normalizedGroupName: String?): Boolean {
            if (scriptRegisteredGroups.isEmpty()) {
                return false
            }
            val family: String? = selectorFamilyKey(normalizedGroupName)
            if (family == null || family.length < 3) {
                return false
            }
            for (registered in scriptRegisteredGroups) {
                val registeredFamily: String? = selectorFamilyKey(registered)
                if (family == registeredFamily) {
                    return true
                }
            }
            return false
        }

        fun restoreMatrixDepth(targetDepth: Int) {
            if (poseStack == null) {
                matrixDepth = max(0, targetDepth)
                return
            }
            val safeTarget = max(0, targetDepth)
            while (matrixDepth > safeTarget) {
                poseStack!!.popPose()
                matrixDepth--
            }
        }

        /**
         * Returns wheel rotation angle in degrees for legacy scripts.
         * Called as renderer.getWheelRotationR(entity) in RTM 1.12.2 scripts.
         */
        fun getWheelRotationR(entity: Any?): Float {
            if (entity is TrainEntity) {
                // 走行距離ベースの累積回転角(TrainEntity.tick で毎tick加算)を使う。
                // 旧実装(tickCount × 現在速度)は速度変化のたびに巨大な回転ジャンプを起こし
                // 「速度10でも空転しまくる」原因だった。
                return entity.wheelRotationDegrees
            }
            if (entity is LegacyScriptExecutor && entity.vehicle != null) {
                return getWheelRotationR(entity.vehicle)
            }
            return 0.0f
        }

        fun getTick(entity: Any?): Int {
            try {
                if (entity is LegacyScriptExecutor) {
                    return entity.time.toInt()
                }
                if (entity is Entity) {
                    val field = Entity::class.java.getDeclaredField("tickCount")
                    field.setAccessible(true)
                    return field.getInt(entity)
                }
                if (entity is BlockEntity) {
                    return if (entity.getLevel() == null) 0 else entity.getLevel()!!.getGameTime().toInt()
                }
            } catch (ignored: Exception) {
            }
            return 0
        }

        val systemTime: Long
            get() = System.currentTimeMillis() / 1000L

        /**
         * Returns legacy renderer scratch data addressed by an integer key.
         */
        fun getData(key: Long): Any? {
            return scriptData.getOrDefault(key, 0)
        }

        /**
         * Stores legacy renderer scratch data addressed by an integer key.
         */
        fun setData(key: Long, value: Any?) {
            scriptData.put(key, if (value == null) 0 else value)
        }

        fun getMCTime(entity: Any?): Int {
            return (getWorldDayTime(entity) % 24000L).toInt()
        }

        val mCTime: Int
            get() = getMCTime(currentEntity)

        fun getMCHour(entity: Any?): Int {
            val time = getMCTime(entity)
            return (time / 1000 + 6) % 24
        }

        val mCHour: Int
            /**
             * Returns the current world hour for old scripts that omit the entity argument.
             */
            get() = getMCHour(currentEntity)

        fun getMCMinute(entity: Any?): Int {
            val time = getMCTime(entity)
            return ((time % 1000) * 0.06f).toInt()
        }

        val mCMinute: Int
            /**
             * Returns the current world minute for old scripts that omit the entity argument.
             */
            get() = getMCMinute(currentEntity)

        fun getMovingCount(entity: Any?): Float {
            if (entity is InstalledObjectBlockEntity) {
                return entity.barMoveCount / 90.0f
            }
            return 0.0f
        }

        fun getLightState(entity: Any?): Int {
            if (entity is InstalledObjectBlockEntity) {
                // 本家 MachinePartsRenderer.getLightState 準拠:
                //   照明(LIGHT) → レッドストーン電力で 1(点灯) / -1(消灯)。
                //     ※ TileEntityLight.isGettingPower と同じ。スクリプトは pass==2 で
                //       state==1 のとき発光(_lightN)テクスチャを描く。
                //   踏切(CROSSING) → 点滅カウンタ(0/1)、信号(SIGNAL) → 現示状態。
                //     (どちらも getLightCount() が従来通り返す。)
                if (entity.category
                    == InstalledObjectCategory.LIGHT
                ) {
                    return if (entity.isPowered) 1 else -1
                }
                return entity.getLightCount()
            }
            return -1
        }

        private fun getWorldDayTime(entity: Any?): Long {
            try {
                if (entity is Entity) {
                    return if (entity.level() == null) 0 else entity.level().getLevelData().getGameTime()
                }
                if (entity is BlockEntity) {
                    return if (entity.getLevel() == null) 0 else entity.getLevel()!!.getLevelData().getGameTime()
                }
                val mc = Minecraft.getInstance()
                if (mc.level != null) {
                    return mc.level!!.getLevelData().getGameTime()
                }
            } catch (ignored: Exception) {
            }
            return 0
        }

        fun sigmoid(x: Double): Float {
            if (x <= 0.0) {
                return 0.0f
            }
            if (x >= 1.0) {
                return 1.0f
            }
            val centered = (x - 0.5) * 5.0
            val curved = centered / sqrt(1.0 + centered * centered)
            return ((curved + 1.0) * 0.5).toFloat()
        }

        val isRenderingTrain: Boolean
            get() = currentEntity is TrainEntity

        val isRenderingInteriorLitTrain: Boolean
            get() {
                val entity = currentEntity
                return entity is TrainEntity && entity.isInteriorLightOn
            }

        private fun extractTrain(entity: Any?): TrainEntity? {
            if (entity is TrainEntity) return entity
            if (entity is LegacyScriptExecutor) return entity.vehicle
            val current = currentEntity
            return if (current is TrainEntity) current else null
        }

        fun getDoorMovementL(entity: Any?): Float {
            val t = extractTrain(entity)
            return if (t == null) 0.0f else min(1.0f, t.doorMoveL / 60.0f)
        }

        fun getDoorMovementR(entity: Any?): Float {
            val t = extractTrain(entity)
            return if (t == null) 0.0f else min(1.0f, t.doorMoveR / 60.0f)
        }

        val doorMovementL: Float
            get() = getDoorMovementL(currentEntity)
        val doorMovementR: Float
            get() = getDoorMovementR(currentEntity)

        fun getPantographMovementBack(entity: Any?): Float {
            val t = extractTrain(entity)
            return if (t == null) 1.0f else t.pantograph_B / 40.0f
        }

        fun getPantographMovementFront(entity: Any?): Float {
            val t = extractTrain(entity)
            return if (t == null) 1.0f else t.pantograph_F / 40.0f
        }

        val pantographMovementBack: Float
            get() = getPantographMovementBack(currentEntity)
        val pantographMovementFront: Float
            get() = getPantographMovementFront(currentEntity)

        fun pushMatrix() {
            if (poseStack != null) {
                poseStack!!.pushPose()
                matrixDepth++
            }
            scriptLocalStack.push(scriptLocalOrigin)
            recordOp(OP_PUSH, 0f, 0f, 0f, 0f, 0f, null, ' ')
        }

        fun popMatrix() {
            if (poseStack != null && matrixDepth > 0) {
                poseStack!!.popPose()
                matrixDepth--
            }
            if (invalidMatrixDepth > matrixDepth) {
                invalidMatrixDepth = -1
            }
            scriptLocalOrigin =
                (if (scriptLocalStack.isEmpty()) net.minecraft.world.phys.Vec3.ZERO else scriptLocalStack.pop())!!
            recordOp(OP_POP, 0f, 0f, 0f, 0f, 0f, null, ' ')
        }

        fun setLegacyMaterialContext(materialId: Int, texture: Identifier?) {
            currentMatId = materialId
            currentBatchTexture = texture
            tessellatorFallbackTexture = texture
        }

        fun translate(x: Float, y: Float, z: Float) {
            // NaN/Infinite ガード: スクリプトが undefined を渡すと NaN になり poseStack 全体が破壊される。
            if (!java.lang.Float.isFinite(x) || !java.lang.Float.isFinite(y) || !java.lang.Float.isFinite(z)) {
                markMatrixInvalid()
                return
            }
            val applied = adjustLegacyScriptBogieTranslate(x, y, z)
            if (poseStack != null) {
                poseStack!!.translate(applied.x, applied.y, applied.z)
            }
            scriptLocalOrigin = scriptLocalOrigin.add(applied)
            recordOp(OP_TRANSLATE, x, y, z, 0f, 0f, null, ' ')
        }

        fun translate(x: Double, y: Double, z: Double) {
            if (!java.lang.Double.isFinite(x) || !java.lang.Double.isFinite(y) || !java.lang.Double.isFinite(z)) {
                markMatrixInvalid()
                return
            }
            translate(x.toFloat(), y.toFloat(), z.toFloat())
        }

        fun translate(x: Any?, y: Any?, z: Any?) {
            val tx = toScriptDouble(x, Double.NaN)
            val ty = toScriptDouble(y, Double.NaN)
            val tz = toScriptDouble(z, Double.NaN)
            if (!java.lang.Double.isFinite(tx) || !java.lang.Double.isFinite(ty) || !java.lang.Double.isFinite(tz)) {
                markMatrixInvalid()
                return
            }
            translate(tx, ty, tz)
        }

        private fun adjustLegacyScriptBogieTranslate(x: Float, y: Float, z: Float): Vec3 {
            val requested = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
            val entity = currentEntity
            if (entity !is TrainEntity) {
                return requested
            }
            val def = VehicleRegistry.getById(entity.vehicleId)
            if (def == null || def.getBogies().isEmpty()) {
                return requested
            }

            val targetLocal = scriptLocalOrigin.add(requested)
            var bestIndex = -1
            var bestDistance = Double.POSITIVE_INFINITY
            for (i in def.getBogies().indices) {
                val bogiePos = def.getBogies().get(i).position()
                val dx = bogiePos.x - targetLocal.x
                val dy = bogiePos.y - targetLocal.y
                val dz = bogiePos.z - targetLocal.z
                val distance = dx * dx + dy * dy + dz * dz
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            if (bestIndex < 0 || bestDistance > 0.35 * 0.35) {
                return requested
            }

            val bogie = def.getBogies().get(bestIndex)
            val corrected: Vec3 = entity.getBogieRenderOffset(bestIndex, bogie, entity.getYRot(), 1.0f)
            return corrected.subtract(scriptLocalOrigin)
        }

        fun rotate(angle: Float, x: Float, y: Float, z: Float) {
            // NaN/Infinite ガード
            if (!java.lang.Float.isFinite(angle) || !java.lang.Float.isFinite(x) || !java.lang.Float.isFinite(y) || !java.lang.Float.isFinite(
                    z
                )
            ) {
                markMatrixInvalid()
                return
            }
            recordOp(OP_ROTATE_FREE, angle, x, y, z, 0f, null, ' ')
            if (poseStack == null) {
                return
            }
            // 角度 0 はノーオペ - SL の rod 計算が 0 度になるフレームが多い
            if (angle == 0.0f) return
            if (x == 1.0f && y == 0.0f && z == 0.0f) {
                poseStack!!.mulPose(Axis.XP.rotationDegrees(angle))
            } else if (x == 0.0f && y == 1.0f && z == 0.0f) {
                poseStack!!.mulPose(Axis.YP.rotationDegrees(angle))
            } else if (x == 0.0f && y == 0.0f && z == 1.0f) {
                poseStack!!.mulPose(Axis.ZP.rotationDegrees(angle))
            }
            // 任意軸は未サポート (RTM スクリプトでは使われない)
        }

        fun rotate(angle: Double, x: Double, y: Double, z: Double) {
            if (!java.lang.Double.isFinite(angle) || !java.lang.Double.isFinite(x) || !java.lang.Double.isFinite(y) || !java.lang.Double.isFinite(
                    z
                )
            ) {
                markMatrixInvalid()
                return
            }
            rotate(angle.toFloat(), x.toFloat(), y.toFloat(), z.toFloat())
        }

        fun rotate(angle: Any?, x: Any?, y: Any?, z: Any?) {
            val a = toScriptDouble(angle, Double.NaN)
            val rx = toScriptDouble(x, Double.NaN)
            val ry = toScriptDouble(y, Double.NaN)
            val rz = toScriptDouble(z, Double.NaN)
            if (!java.lang.Double.isFinite(a) || !java.lang.Double.isFinite(rx) || !java.lang.Double.isFinite(ry) ||
                !java.lang.Double.isFinite(rz)
            ) {
                markMatrixInvalid()
                return
            }
            rotate(a, rx, ry, rz)
        }

        fun rotate(angle: Double, axis: String?, originX: Double, originY: Double, originZ: Double) {
            if (poseStack == null || axis == null || axis.isBlank()) {
                return
            }
            // NaN/Infinite ガード
            if (!java.lang.Double.isFinite(angle) || !java.lang.Double.isFinite(originX) || !java.lang.Double.isFinite(
                    originY
                ) || !java.lang.Double.isFinite(originZ)
            ) {
                markMatrixInvalid()
                return
            }

            val a = angle.toFloat()
            val x = originX.toFloat()
            val y = originY.toFloat()
            val z = originZ.toFloat()

            // 内部で translate + rotate(float, ...) + translate を呼ぶ。それぞれ各メソッドが
            // recordOp するため、ここでは追加 record しない (二重録画回避)。
            translate(x, y, z)
            when (axis.trim { it <= ' ' }.uppercase(Locale.getDefault())) {
                "X" -> rotate(a, 1.0f, 0.0f, 0.0f)
                "Y" -> rotate(a, 0.0f, 1.0f, 0.0f)
                "Z" -> rotate(a, 0.0f, 0.0f, 1.0f)
                else -> {
                    RealTrainModRenewed.LOGGER.warn("Unsupported rotate axis in script: {}", axis)
                }
            }
            translate(-x, -y, -z)
        }

        fun rotate(angle: Any?, axis: Any?, originX: Any?, originY: Any?, originZ: Any?) {
            val a = toScriptDouble(angle, Double.NaN)
            val x = toScriptDouble(originX, Double.NaN)
            val y = toScriptDouble(originY, Double.NaN)
            val z = toScriptDouble(originZ, Double.NaN)
            val axisText = axis?.toString()
            if (!java.lang.Double.isFinite(a) || !java.lang.Double.isFinite(x) || !java.lang.Double.isFinite(y) ||
                !java.lang.Double.isFinite(z) || axisText == null || axisText.isBlank() ||
                "undefined".equals(axisText, ignoreCase = true) || "null".equals(axisText, ignoreCase = true)
            ) {
                markMatrixInvalid()
                return
            }
            rotate(
                a,
                axisText,
                x,
                y,
                z
            )
        }

        fun scale(x: Float, y: Float, z: Float) {
            // NaN/Infinite/Zero ガード: スケール 0 や NaN は matrix を壊す
            if (!java.lang.Float.isFinite(x) || !java.lang.Float.isFinite(y) || !java.lang.Float.isFinite(z) ||
                x == 0.0f || y == 0.0f || z == 0.0f
            ) {
                markMatrixInvalid()
                return
            }
            if (poseStack != null) {
                poseStack!!.scale(x, y, z)
            }
            recordOp(OP_SCALE, x, y, z, 0f, 0f, null, ' ')
        }

        fun scale(x: Double, y: Double, z: Double) {
            if (!java.lang.Double.isFinite(x) || !java.lang.Double.isFinite(y) || !java.lang.Double.isFinite(z)) {
                markMatrixInvalid()
                return
            }
            scale(x.toFloat(), y.toFloat(), z.toFloat())
        }

        fun scale(x: Any?, y: Any?, z: Any?) {
            val sx = toScriptDouble(x, Double.NaN)
            val sy = toScriptDouble(y, Double.NaN)
            val sz = toScriptDouble(z, Double.NaN)
            if (!java.lang.Double.isFinite(sx) || !java.lang.Double.isFinite(sy) || !java.lang.Double.isFinite(sz)) {
                markMatrixInvalid()
                return
            }
            scale(sx, sy, sz)
        }

        private fun markMatrixInvalid() {
            if (matrixDepth > 0 && invalidMatrixDepth < 0) {
                invalidMatrixDepth = matrixDepth
            }
        }

        private fun isMatrixInvalid(): Boolean {
            return invalidMatrixDepth >= 0 && matrixDepth >= invalidMatrixDepth
        }

        // ---- NPC biped animation ----
        fun setRotationAngles(entity: Any?, partialTick: Any?) {
            val pt = if (partialTick is Number) partialTick.toFloat() else 0.0f
            var speed = 0.0f
            var onGround = true
            if (entity is LegacyScriptExecutor) {
                speed = abs(entity.speed)
                onGround = entity.isOnGround
            } else if (entity is TrainEntity) {
                speed = abs(entity.speed)
            }
            val swingProgress = min(1.0f, speed * 10.0f)
            val swing = Mth.cos((swingProgress * Math.PI.toFloat() * 0.6662f).toDouble()) * 2.0f
            headAngleX = 0.0f
            headAngleY = 0.0f
            headAngleZ = 0.0f
            bodyAngleX = 0.0f
            bodyAngleY = 0.0f
            bodyAngleZ = 0.0f
            rightArmAngleX = swing * 0.5f
            rightArmAngleY = 0.0f
            rightArmAngleZ = 0.0f
            leftArmAngleX = -swing * 0.5f
            leftArmAngleY = 0.0f
            leftArmAngleZ = 0.0f
            rightLegAngleX = -swing * 0.5f
            rightLegAngleY = 0.0f
            rightLegAngleZ = 0.0f
            leftLegAngleX = swing * 0.5f
            leftLegAngleY = 0.0f
            leftLegAngleZ = 0.0f
        }

        fun rotateAndRender(
            parts: Any?, pivotX: Double, pivotY: Double, pivotZ: Double,
            angleX: Double, angleY: Double, angleZ: Double
        ) {
            if (poseStack == null) return
            pushMatrix()
            translate(pivotX.toFloat(), pivotY.toFloat(), pivotZ.toFloat())
            if (angleZ != 0.0) poseStack!!.mulPose(Axis.ZP.rotationDegrees(angleZ.toFloat()))
            if (angleY != 0.0) poseStack!!.mulPose(Axis.YP.rotationDegrees(angleY.toFloat()))
            if (angleX != 0.0) poseStack!!.mulPose(Axis.XP.rotationDegrees(angleX.toFloat()))
            translate(-pivotX.toFloat(), -pivotY.toFloat(), -pivotZ.toFloat())
            renderParts(parts)
            popMatrix()
        }

        // ---- Entity position/orientation getters (renderer.getX(entity) etc.) ----
        fun getX(entity: Any?): Double {
            if (entity is Entity) return entity.getX()
            if (entity is BlockEntity) return entity.getBlockPos().getX().toDouble()
            if (entity is LegacyScriptExecutor) return entity.x
            return 0.0
        }

        fun getY(entity: Any?): Double {
            if (entity is Entity) return entity.getY()
            if (entity is BlockEntity) return entity.getBlockPos().getY().toDouble()
            if (entity is LegacyScriptExecutor) return entity.y
            return 0.0
        }

        fun getZ(entity: Any?): Double {
            if (entity is Entity) return entity.getZ()
            if (entity is BlockEntity) return entity.getBlockPos().getZ().toDouble()
            if (entity is LegacyScriptExecutor) return entity.z
            return 0.0
        }

        fun getYaw(entity: Any?): Float {
            if (entity is Entity) return entity.getYRot()
            if (entity is LegacyScriptExecutor) return entity.yaw
            return 0.0f
        }

        fun getPitch(entity: Any?): Float {
            if (entity is Entity) return entity.getXRot()
            if (entity is LegacyScriptExecutor) return entity.pitch
            return 0.0f
        }

        fun getWorld(entity: Any?): Any? {
            if (entity is Entity) return entity.level()
            if (entity is BlockEntity) return entity.getLevel()
            if (entity is LegacyScriptExecutor) return entity.world
            return null
        }

        // ---- Block/entity state queries ----
        fun isPowered(entity: Any?): Boolean {
            if (entity is InstalledObjectBlockEntity) return entity.isPowered
            if (entity is BlockEntity) {
                val lvl = entity.getLevel()
                if (lvl != null) return lvl.hasNeighborSignal(entity.getBlockPos())
            }
            return false
        }

        fun isOpaqueCube(entity: Any?): Boolean {
            return false
        }

        fun isRidden(entity: Any?): Boolean {
            if (entity is Entity) return !entity.getPassengers().isEmpty()
            return false
        }

        fun getLodState(entity: Any?): Int {
            return 0
        }

        fun getMetadata(entity: Any?): Int {
            return 0
        }

        fun isSwitchRail(entity: Any?): Boolean {
            if (entity is BlockEntity) {
                val lvl = entity.getLevel()
                if (lvl != null) {
                    val state = lvl.getBlockState(entity.getBlockPos())
                    val blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                    return blockId.contains("switch") || blockId.contains("point")
                }
            }
            return false
        }

        // ---- Rail/wire rendering helpers (stubs for scripts that call these) ----
        fun renderStaticParts(entity: Any?, posX: Double, posY: Double, posZ: Double) {
            // renderParts with null renders all groups — serves as "render static (non-moving) parts"
            if (mqoModel != null && poseStack != null && buffer != null) {
                renderParts(if (scriptRegisteredGroups.isEmpty()) "base" else scriptRegisteredGroups.iterator().next())
            }
        }

        fun renderLightEffect(
            entity: Any?, x: Double, y: Double, z: Double,
            sizeX: Double, sizeY: Double, sizeZ: Double,
            normal: Any?, color: Int, alpha: Float
        ) {
        }

        fun renderLightEffect(
            entity: Any?, x: Double, y: Double, z: Double,
            sizeX: Double, sizeZ: Double, normal: Any?, color: Int, alpha: Float
        ) {
        }

        fun renderLightEffect(vararg args: Any?) {}

        fun renderRailMapStatic(vararg args: Any?) {}

        fun renderWireDeflection(vararg args: Any?) {}

        // ---- Brightness / lighting helpers ----
        fun getBrightness(world: Any?, x: Double, y: Double, z: Double): Float {
            return 1.0f
        }

        fun getBrightness(world: Any?, x: Int, y: Int, z: Int): Float {
            return 1.0f
        }

        // ---- Light position and surface normal helpers (stubs for catenary scripts) ----
        fun getLightPos(
            entity: Any?, offX: Double, offY: Double, offZ: Double,
            offYaw: Double, yaw: Double
        ): FloatArray {
            val ex = getX(entity) + offX
            val ey = getY(entity) + offY
            val ez = getZ(entity) + offZ
            return floatArrayOf(ex.toFloat(), ey.toFloat(), ez.toFloat())
        }

        fun getNormal(
            entity: Any?, nx: Double, ny: Double, nz: Double,
            pitch: Double, yaw: Double
        ): FloatArray {
            return floatArrayOf(nx.toFloat(), ny.toFloat(), nz.toFloat())
        }

        // ---- Rotation by Axis enum value ----
        fun getRotation(entity: Any?, axisId: Int): Float {
            if (entity is InstalledObjectBlockEntity) {
                when (axisId) {
                    3, 4 -> {
                        return entity.yaw
                    }

                    else -> {
                        return 0.0f
                    }
                }
            }
            if (entity is LegacyScriptExecutor) {
                when (axisId) {
                    3, 4 -> {
                        return entity.yaw
                    }

                    1, 2 -> {
                        return entity.pitch
                    }

                    else -> {
                        return 0.0f
                    }
                }
            }
            return 0.0f
        }

        // ---- Inventory helpers (stubs) ----
        fun getInventoryItem(entity: Any?, slot: Int): Any? {
            return null
        }

        fun getStackSize(stack: Any?): Int {
            return if (stack == null) 0 else 1
        }

        fun renderItem(entity: Any?, item: Any?) {}

        val playerYaw: Float
            // ---- Player camera / view ----
            get() {
                try {
                    val mc = Minecraft.getInstance()
                    if (mc.player != null) return mc.player!!.getYRot()
                } catch (ignored: Exception) {
                }
                return 0.0f
            }

        val systemTimeMillis: Long
            // ---- System clock helpers ----
            get() = System.currentTimeMillis()

        val systemHour: Int
            get() = LocalTime.now().getHour()

        val systemMinute: Int
            get() = LocalTime.now().getMinute()

        val systemSecond: Int
            get() = LocalTime.now().getSecond()

        val systemMillisecond: Int
            get() = (System.currentTimeMillis() % 1000L).toInt()

        // ---- Color as int (returned by renderer.getColor(entity)) ----
        fun getColor(entity: Any?): Int {
            return 0xFFFFFF
        }

        companion object {
            // ==== Script Replay Cache (停車中など状態が変わらないフレームで JS engine を完全に bypass) ====
            // 1 度 render() を実行したときに renderer に対して行われた呼び出し列を記録し、
            // 同じ entity 状態のフレームでは記録された Op 列を直接再生する (JS engine 起動なし)。
            // GraalJS の関数実行 + JS↔Java 橋渡しコスト (1フレーム ~10-20ms) が消える。
            const val OP_TRANSLATE: Int = 1
            const val OP_ROTATE_AXIS: Int = 2 // rotate(angle, axis, x, y, z)
            const val OP_ROTATE_FREE: Int = 3 // rotate(angle, x, y, z)
            const val OP_PUSH: Int = 4
            const val OP_POP: Int = 5
            const val OP_SCALE: Int = 6
            const val OP_RENDER_PARTS: Int = 7
            const val OP_SET_COLOR: Int = 8
            const val OP_RESET_COLOR: Int = 9
            const val OP_BIND_TEX: Int = 10
            const val OP_CLEAR_TEX: Int = 11
            const val OP_SET_UV_WINDOW: Int = 12
            const val OP_CLEAR_UV_WINDOW: Int = 13
            const val OP_SET_LIGHTMAP_MAX: Int = 14
            const val OP_BIND_LEGACY_ROLLSIGN: Int = 15
            const val OP_RENDER_TEXTURE_WINDOW: Int = 16
            const val OP_ENABLE_LIGHTING: Int = 17
            const val OP_SET_BRIGHTNESS: Int = 18

            // (passKey, stateHash) → 録画。LinkedHashMap で access order LRU 制限 (メモリ上限)。
            private const val REPLAY_CACHE_MAX = 256

            private val FRAME_SENSITIVE_SCRIPT_PATTERN: Pattern = Pattern.compile(
                "partial\\s*tick|systemTime|systemHour|systemMinute|systemSecond|systemMillisecond|" +
                    "currentTimeMillis|nanoTime|new\\s+Date|Date\\s*\\(|camera|viewerPos|renderViewEntity",
                Pattern.CASE_INSENSITIVE,
            )

            // isEmissiveGroup のコンパイル済み regex (毎回コンパイルするコストを排除)
            private val DEST_N_PATTERN: Pattern = Pattern.compile("dest\\d+")
            private val TYPE_N_PATTERN: Pattern = Pattern.compile("type\\d+")

            private fun toScriptDouble(value: Any?, fallback: Double): Double {
                if (value is Number) {
                    val result = value.toDouble()
                    return if (java.lang.Double.isFinite(result)) result else fallback
                }
                if (value is Boolean) {
                    return if (value) 1.0 else 0.0
                }
                if (value != null) {
                    val text = value.toString().trim { it <= ' ' }
                    if (text.isNotEmpty() && !"undefined".equals(text, ignoreCase = true) &&
                        !"null".equals(text, ignoreCase = true)
                    ) {
                        try {
                            val result = text.toDouble()
                            return if (java.lang.Double.isFinite(result)) result else fallback
                        } catch (ignored: NumberFormatException) {
                        }
                    }
                }
                return fallback
            }

            /** 指定グループ名のリストを現在の poseStack で描画する。  */
            /** 台車・車輪(走り装置)グループ名か。.class台車車両でスクリプト描画を抑制する判定用。  */
            private fun isBogieGroupName(x: String?): Boolean {
                if (x == null) return false
                return x.contains("bogie") || x.contains("truck") || x.contains("wheel")
                        || x.contains("daisya") || x.contains("sharin") || x.contains("台車") || x.contains("車輪")
            }

            // 角度サフィックスのしきい値。これ以上の「-数字」は連結曲げ用の角度バリアント(度数)、
            // これ未満 (1〜9) は車体のセクション分割 (D51 の body-1/2/3 等) とみなす。
            private const val ANGLE_SUFFIX_THRESHOLD = 10

            private fun stripMirrorSuffix(n: String): String {
                // RTM の鏡像/向き変種サフィックスは多様: (mx)/(my)/(mz) 単軸, (mxz)/(mxy)/(mxyz) 複数軸,
                // (r) 反転, さらにそれらが積み重なる場合もある(例 "body-35(mxz)", "body-100(r)")。
                // 以前は (mx) や単軸しか剥がせず、(mxz)/(r) 付きの曲げ変種が角度判定をすり抜けて
                // 原点姿勢で描画され、パーツの飛び散り/車番の重複を起こしていた。
                // 末尾の括弧が「鏡像/向きトークン(m,x,y,z,r のみ)」なら、すべて(連続も)剥がす。
                var s = n
                while (s.length >= 3 && s.endsWith(")")) {
                    val open = s.lastIndexOf('(')
                    if (open <= 0 || open >= s.length - 1) break
                    val inside = s.substring(open + 1, s.length - 1)
                    if (inside.isEmpty()) break
                    var mirrorToken = true
                    for (i in 0..<inside.length) {
                        val c = inside.get(i)
                        if (c != 'm' && c != 'x' && c != 'y' && c != 'z' && c != 'r') {
                            mirrorToken = false
                            break
                        }
                    }
                    if (!mirrorToken) break
                    s = s.substring(0, open)
                }
                return s
            }

            /** 末尾「-数字」の数値を返す ((mx) は先に剥がす)。無ければ -1。  */
            private fun angleSuffixValue(n: String): Int {
                val s: String = stripMirrorSuffix(n)
                val dash = s.lastIndexOf('-')
                if (dash <= 0 || dash == s.length - 1) return -1
                for (i in dash + 1..<s.length) {
                    if (!Character.isDigit(s.get(i))) return -1
                }
                try {
                    return s.substring(dash + 1).toInt()
                } catch (e: NumberFormatException) {
                    return -1
                }
            }

            /** 角度サフィックス(-NN, NN≥THRESHOLD)を持つ → 連結曲げ用変種。角度の無い (mx) 鏡像は対象外。  */
            private fun isBendVariant(n: String): Boolean {
                return angleSuffixValue(n) >= ANGLE_SUFFIX_THRESHOLD
            }

            private fun sortQuadCornersForLegacyOverlay(q: FloatArray): FloatArray {
                var cx = 0.0f
                var cy = 0.0f
                var cz = 0.0f
                for (i in 0..3) {
                    cx += q[i * 3]
                    cy += q[i * 3 + 1]
                    cz += q[i * 3 + 2]
                }
                cx *= 0.25f
                cy *= 0.25f
                cz *= 0.25f

                val e1x = q[3] - q[0]
                val e1y = q[4] - q[1]
                val e1z = q[5] - q[2]
                val e2x = q[9] - q[0]
                val e2y = q[10] - q[1]
                val e2z = q[11] - q[2]
                var nx = e1y * e2z - e1z * e2y
                var ny = e1z * e2x - e1x * e2z
                var nz = e1x * e2y - e1y * e2x
                val nl = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
                if (nl < 1.0E-6f) {
                    return q
                }
                nx /= nl
                ny /= nl
                nz /= nl

                var upRefX = 0.0f
                var upRefY = 1.0f
                var upRefZ = 0.0f
                if (abs(ny) > 0.98f) {
                    upRefX = 0.0f
                    upRefY = 0.0f
                    upRefZ = 1.0f
                }
                var rx = upRefY * nz - upRefZ * ny
                var ry = upRefZ * nx - upRefX * nz
                var rz = upRefX * ny - upRefY * nx
                val rl = sqrt((rx * rx + ry * ry + rz * rz).toDouble()).toFloat()
                if (rl < 1.0E-6f) {
                    return q
                }
                rx /= rl
                ry /= rl
                rz /= rl
                val ux = ny * rz - nz * ry
                val uy = nz * rx - nx * rz
                val uz = nx * ry - ny * rx

                val vertices: MutableList<FloatArray?> = ArrayList<FloatArray?>(4)
                for (i in 0..3) {
                    val vx = q[i * 3]
                    val vy = q[i * 3 + 1]
                    val vz = q[i * 3 + 2]
                    val dx = vx - cx
                    val dy = vy - cy
                    val dz = vz - cz
                    val localX = dx * rx + dy * ry + dz * rz
                    val localY = dx * ux + dy * uy + dz * uz
                    vertices.add(floatArrayOf(vx, vy, vz, localX, localY))
                }
                vertices.sortWith(Comparator { a: FloatArray?, b: FloatArray? ->
                    val byY = java.lang.Float.compare(b!![4], a!![4])
                    if (byY != 0) byY else java.lang.Float.compare(a[3], b[3])
                })
                val top: MutableList<FloatArray?> = ArrayList<FloatArray?>(vertices.subList(0, 2))
                val bottom: MutableList<FloatArray?> = ArrayList<FloatArray?>(vertices.subList(2, 4))
                top.sortWith(Comparator.comparingDouble<FloatArray?>(ToDoubleFunction { v: FloatArray? -> v!![3].toDouble() }))
                bottom.sortWith(Comparator.comparingDouble<FloatArray?>(ToDoubleFunction { v: FloatArray? -> v!![3].toDouble() }))

                val out = FloatArray(12)
                val ordered = arrayOf<FloatArray?>(top.get(0), bottom.get(0), bottom.get(1), top.get(1))
                for (i in 0..3) {
                    out[i * 3] = ordered[i]!![0]
                    out[i * 3 + 1] = ordered[i]!![1]
                    out[i * 3 + 2] = ordered[i]!![2]
                }
                return out
            }

            private fun orientOverlayQuad(q: FloatArray?): FloatArray? {
                if (q == null || q.size < 12) {
                    return q
                }
                val e1x = q[3] - q[0]
                val e1y = q[4] - q[1]
                val e1z = q[5] - q[2]
                val e2x = q[9] - q[0]
                val e2y = q[10] - q[1]
                val e2z = q[11] - q[2]
                var nx = e1y * e2z - e1z * e2y
                var ny = e1z * e2x - e1x * e2z
                var nz = e1x * e2y - e1y * e2x
                val nl = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
                if (nl > 1.0E-6f) {
                    nx /= nl
                    ny /= nl
                    nz /= nl
                } else {
                    nx = 0f
                    ny = 1f
                    nz = 0f
                }

                var upx = 0.0f
                var upy = 1.0f
                var upz = 0.0f
                if (abs(ny) > 0.65f) {
                    upx = 0.0f
                    upy = 0.0f
                    upz = -1.0f
                }
                var dot = upx * nx + upy * ny + upz * nz
                upx -= nx * dot
                upy -= ny * dot
                upz -= nz * dot
                var ul = sqrt((upx * upx + upy * upy + upz * upz).toDouble()).toFloat()
                if (ul <= 1.0E-6f) {
                    upx = 0.0f
                    upy = 0.0f
                    upz = -1.0f
                    dot = upx * nx + upy * ny + upz * nz
                    upx -= nx * dot
                    upy -= ny * dot
                    upz -= nz * dot
                    ul = sqrt((upx * upx + upy * upy + upz * upz).toDouble()).toFloat()
                }
                if (ul > 1.0E-6f) {
                    upx /= ul
                    upy /= ul
                    upz /= ul
                }
                var rx = upy * nz - upz * ny
                var ry = upz * nx - upx * nz
                var rz = upx * ny - upy * nx
                val rl = sqrt((rx * rx + ry * ry + rz * rz).toDouble()).toFloat()
                if (rl > 1.0E-6f) {
                    rx /= rl
                    ry /= rl
                    rz /= rl
                }

                var cx = 0f
                var cy = 0f
                var cz = 0f
                for (i in 0..3) {
                    cx += q[i * 3]
                    cy += q[i * 3 + 1]
                    cz += q[i * 3 + 2]
                }
                cx *= 0.25f
                cy *= 0.25f
                cz *= 0.25f

                var tl = -1
                var bl = -1
                var br = -1
                var tr = -1
                var bestTl = -Float.MAX_VALUE
                var bestBl = -Float.MAX_VALUE
                var bestBr = -Float.MAX_VALUE
                var bestTr = -Float.MAX_VALUE
                for (i in 0..3) {
                    val dx = q[i * 3] - cx
                    val dy = q[i * 3 + 1] - cy
                    val dz = q[i * 3 + 2] - cz
                    val su = dx * rx + dy * ry + dz * rz
                    val sv = dx * upx + dy * upy + dz * upz
                    val sTl = -su + sv
                    val sBl = -su - sv
                    val sBr = su - sv
                    val sTr = su + sv
                    if (sTl > bestTl) {
                        bestTl = sTl
                        tl = i
                    }
                    if (sBl > bestBl) {
                        bestBl = sBl
                        bl = i
                    }
                    if (sBr > bestBr) {
                        bestBr = sBr
                        br = i
                    }
                    if (sTr > bestTr) {
                        bestTr = sTr
                        tr = i
                    }
                }
                if (tl < 0 || bl < 0 || br < 0 || tr < 0) {
                    return q
                }
                val out = FloatArray(12)
                val order = intArrayOf(tl, bl, br, tr)
                for (i in 0..3) {
                    val src = order[i] * 3
                    out[i * 3] = q[src]
                    out[i * 3 + 1] = q[src + 1]
                    out[i * 3 + 2] = q[src + 2]
                }
                return out
            }

            private fun readTextureComponent(texture: Any, methodName: String, vararg fieldNames: String): String? {
                try {
                    val hasMember = texture.javaClass.getMethod("hasMember", String::class.java)
                    val getMember = texture.javaClass.getMethod("getMember", String::class.java)
                    if (java.lang.Boolean.TRUE == hasMember.invoke(texture, methodName)) {
                        val fn = getMember.invoke(texture, methodName)
                        if (fn != null) {
                            try {
                                val value = fn.javaClass.getMethod("execute", Array<Any>::class.java)
                                    .invoke(fn, arrayOfNulls<Any>(0) as Any)
                                if (value != null) return value.toString()
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                    for (fieldName in fieldNames) {
                        if (java.lang.Boolean.TRUE == hasMember.invoke(texture, fieldName)) {
                            val value = getMember.invoke(texture, fieldName)
                            if (value != null) {
                                return value.toString()
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
                try {
                    val value = texture.javaClass.getMethod(methodName).invoke(texture)
                    if (value != null) {
                        return value.toString()
                    }
                } catch (ignored: Exception) {
                }
                for (fieldName in fieldNames) {
                    try {
                        val value = texture.javaClass.getField(fieldName).get(texture)
                        if (value != null) {
                            return value.toString()
                        }
                    } catch (ignored: Exception) {
                    }
                }
                return null
            }

            private fun normalizeLegacyGroupName(groupName: String?): String {
                return if (groupName == null) "" else groupName.trim { it <= ' ' }.lowercase()
            }

            private fun isLightOffGroup(lowerGroupName: String?): Boolean {
                if (lowerGroupName == null || lowerGroupName.isBlank()) {
                    return false
                }
                val lightLike = lowerGroupName.contains("light") || lowerGroupName.contains("lamp")
                if (!lightLike) {
                    return false
                }
                return lowerGroupName.endsWith("_off")
                        || lowerGroupName.endsWith("-off")
                        || lowerGroupName.endsWith("off")
                        || lowerGroupName.contains("_off_")
                        || lowerGroupName.contains("-off-")
            }

            private fun isBogieLikeGroup(normalized: String?): Boolean {
                if (normalized == null || normalized.isBlank()) {
                    return false
                }
                return normalized.contains("bogie")
                        || normalized.contains("wheel")
                        || normalized.contains("truck")
                        || normalized.contains("daisya")
                        || normalized.contains("daisha")
                        || normalized.contains("sharin")
                        || normalized.startsWith("台車")
            }

            private fun selectorFamilyKey(groupName: String?): String? {
                if (groupName == null || groupName.isBlank()) {
                    return null
                }
                val lower = groupName.trim { it <= ' ' }.lowercase()

                // Pack-specific but stable RTM selector families.
                if (lower.startsWith("cp6_hood")) return "cp6_hood"
                if (lower.startsWith("cp7_hood")) return "cp7_hood"
                if (lower.startsWith("ex16_light_f_")) return "ex16_light_f"
                if (lower.startsWith("ex16_light_r_")) return "ex16_light_r"
                if (lower.startsWith("doorlamp_")) return stripStateSuffix(lower)
                if (lower.startsWith("mark_number")) return "mark_number"
                if (lower.startsWith("mark_old")) return "mark_old"
                if (lower.startsWith("mark_new")) return "mark_new"
                if (lower.startsWith("under_panel")) return "under_panel"
                if (lower.matches("under\\d+.*".toRegex())) return "under"
                if (lower.matches("notch\\d+.*".toRegex())) return "notch"
                if (lower.matches("brake\\d+.*".toRegex())) return "brake"
                if (lower.matches("lv[_-]?[fnb]".toRegex())) return "lv"

                // Generic state meshes used by many RTM scripts.
                if (lower.contains("light") || lower.contains("lamp") || lower.contains("marker")) {
                    var stripped: String = stripStateSuffix(lower)
                    stripped = stripped.replace("[_-]?\\d+$".toRegex(), "")
                    return if (stripped.length >= 3) stripped else null
                }
                return null
            }

            private fun stripStateSuffix(lowerGroupName: String): String {
                val stripped = lowerGroupName
                    .replace("(?i)([_-]?(on|off))\\d*$".toRegex(), "")
                    .replace("(?i)([_-]?(on|off))([_-].*)$".toRegex(), "$2")
                    .replace("[_-]+$".toRegex(), "")
                return if (stripped.isEmpty()) lowerGroupName else stripped
            }

            private fun extractGroupNames(groups: Any?): MutableList<String?> {
                if (groups == null) {
                    return mutableListOf<String?>()
                }
                if (groups is String) {
                    return expandSerializedGroupNames(groups)
                }
                if (groups is MutableCollection<*>) {
                    return groups.stream()
                        .flatMap<String?> { value: Any? -> expandSerializedGroupNames(value.toString()).stream() }
                        .collect(Collectors.toList())
                }
                if (groups.javaClass.isArray()) {
                    val arr = groups as Array<Any?>
                    return Arrays.stream<Any?>(arr)
                        .flatMap<String?> { value: Any? -> expandSerializedGroupNames(value.toString()).stream() }
                        .collect(Collectors.toList())
                }
                if (groups is MutableMap<*, *>) {
                    val lengthValue = groups.get("length")
                    if (lengthValue is Number) {
                        val length = lengthValue.toInt()
                        val result: MutableList<String?> = ArrayList<String?>()
                        for (i in 0..<length) {
                            val value = groups.get(i.toString())
                            if (value != null) {
                                result.addAll(expandSerializedGroupNames(value.toString()))
                            }
                        }
                        return result
                    }
                    return groups.values.stream()
                        .flatMap<String?> { value: Any? -> expandSerializedGroupNames(value.toString()).stream() }
                        .collect(Collectors.toList())
                }
                val scriptObjectGroups: MutableList<String?> = extractScriptObjectGroupNames(groups)
                if (!scriptObjectGroups.isEmpty()) {
                    return scriptObjectGroups
                }
                return expandSerializedGroupNames(groups.toString())
            }

            private fun expandSerializedGroupNames(raw: String?): MutableList<String?> {
                if (raw == null) {
                    return mutableListOf<String?>()
                }
                val trimmed = raw.trim { it <= ' ' }
                if (trimmed.isEmpty()) {
                    return mutableListOf<String?>()
                }
                if (!trimmed.contains(",") && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                    return mutableListOf(trimmed)
                }
                var normalized = trimmed
                if (normalized.startsWith("[") && normalized.endsWith("]")) {
                    normalized = normalized.substring(1, normalized.length - 1)
                }
                val result: MutableList<String?> = ArrayList<String?>()
                for (token in normalized.split("\\s*,\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    var candidate = token.trim { it <= ' ' }
                    if ((candidate.startsWith("\"") && candidate.endsWith("\""))
                        || (candidate.startsWith("'") && candidate.endsWith("'"))
                    ) {
                        candidate = candidate.substring(1, candidate.length - 1).trim { it <= ' ' }
                    }
                    if (!candidate.isEmpty()) {
                        result.add(candidate)
                    }
                }
                return if (result.isEmpty()) mutableListOf(trimmed) else result
            }

            private fun extractScriptObjectGroupNames(groups: Any): MutableList<String?> {
                try {
                    val type: Class<*> = groups.javaClass
                    // First: try to read groupsStr from a Parts JS object.
                    // Parts() stores a pre-joined comma string as this.groupsStr so Java can read it
                    // reliably via getMember() even when the object is opaque to normal reflection.
                    try {
                        val hasMember = type.getMethod("hasMember", String::class.java)
                        val getMember = type.getMethod("getMember", String::class.java)
                        if (java.lang.Boolean.TRUE == hasMember.invoke(groups, "groupsStr")) {
                            val strVal = getMember.invoke(groups, "groupsStr")
                            if (strVal is String && !strVal.isBlank()) {
                                val result: MutableList<String?> = expandSerializedGroupNames(strVal)
                                if (!result.isEmpty()) {
                                    return result
                                }
                            }
                        }
                    } catch (ignored: NoSuchMethodException) {
                    } catch (ignored: Exception) {
                    }

                    var isArrayMethod: Method? = null
                    try {
                        isArrayMethod = type.getMethod("isArray")
                    } catch (ignored: NoSuchMethodException) {
                    }
                    if (isArrayMethod != null && java.lang.Boolean.TRUE == isArrayMethod.invoke(groups)) {
                        val length: Int? = getScriptObjectLength(groups)
                        if (length != null) {
                            return getScriptObjectElements(groups, length)
                        }
                    }

                    val length: Int? = getScriptObjectLength(groups)
                    if (length != null) {
                        return getScriptObjectElements(groups, length)
                    }
                } catch (ignored: Exception) {
                }
                return mutableListOf<String?>()
            }

            private fun getScriptObjectLength(groups: Any): Int? {
                try {
                    val type: Class<*> = groups.javaClass
                    val hasMember = type.getMethod("hasMember", String::class.java)
                    val getMember = type.getMethod("getMember", String::class.java)
                    if (java.lang.Boolean.TRUE == hasMember.invoke(groups, "length")) {
                        val lengthValue = getMember.invoke(groups, "length")
                        if (lengthValue is Number) {
                            return lengthValue.toInt()
                        }
                    }
                } catch (ignored: NoSuchMethodException) {
                } catch (ignored: Exception) {
                }

                try {
                    val type: Class<*> = groups.javaClass
                    val get = type.getMethod("get", Any::class.java)
                    val lengthMethod = type.getMethod("length")
                    val lengthValue = lengthMethod.invoke(groups)
                    if (lengthValue is Number) {
                        return lengthValue.toInt()
                    }
                } catch (ignored: NoSuchMethodException) {
                } catch (ignored: Exception) {
                }

                return null
            }

            private fun getScriptObjectElements(groups: Any, length: Int): MutableList<String?> {
                val result: MutableList<String?> = ArrayList<String?>()
                try {
                    val type: Class<*> = groups.javaClass
                    val getMember = type.getMethod("getMember", String::class.java)
                    for (i in 0..<length) {
                        val value = getMember.invoke(groups, i.toString())
                        if (value != null) {
                            result.add(value.toString())
                        }
                    }
                    return result
                } catch (e: NoSuchMethodException) {
                    try {
                        val type: Class<*> = groups.javaClass
                        val get = type.getMethod("get", Any::class.java)
                        var i = 0
                        while (i < length) {
                            val value = get.invoke(groups, i)
                            if (value != null) {
                                result.add(value.toString())
                            }
                            i++
                        }
                        return result
                    } catch (ignored: Exception) {
                    }
                } catch (ignored: Exception) {
                }
                return mutableListOf<String?>()
            }
        }
    }

    /**
     * GL11 即時モード関数の最小シム。RTM 原作のレンダラスクリプトが直叩きする
     * glPushMatrix/glPopMatrix/glTranslated/glRotated/glScalef 等を ScriptModelRenderer の
     * poseStack 操作にブリッジする。
     */
    class GL11Compat(private val renderer: ScriptModelRenderer?) {
        // GL11 行列操作を ScriptModelRenderer 経由で PoseStack に橋渡しする。
        // ScriptModelRenderer 側で NaN/Infinite ガードと matrixDepth 管理を行うため、
        // ここでは委譲するだけ。これで RTM スクリプトの GL11.glPushMatrix → glTranslated →
        // glRotated → model.renderPart("doorL") → glPopMatrix パターンが機能する。
        fun glPushMatrix() {
            if (renderer != null) renderer.pushMatrix()
        }

        fun glPopMatrix() {
            if (renderer != null) renderer.popMatrix()
        }

        fun glTranslatef(x: Float, y: Float, z: Float) {
            if (renderer != null) renderer.translate(x, y, z)
        }

        fun glTranslated(x: Double, y: Double, z: Double) {
            if (renderer != null) renderer.translate(x.toFloat(), y.toFloat(), z.toFloat())
        }

        fun glRotatef(angle: Float, x: Float, y: Float, z: Float) {
            if (renderer != null) renderer.rotate(angle, x, y, z)
        }

        fun glRotated(angle: Double, x: Double, y: Double, z: Double) {
            if (renderer != null) renderer.rotate(angle.toFloat(), x.toFloat(), y.toFloat(), z.toFloat())
        }

        fun glScalef(sx: Float, sy: Float, sz: Float) {
            if (renderer != null) renderer.scale(sx, sy, sz)
        }

        fun glScaled(sx: Double, sy: Double, sz: Double) {
            if (renderer != null) renderer.scale(sx.toFloat(), sy.toFloat(), sz.toFloat())
        }

        // 以下は no-op (色/ブレンドは ScriptModelRenderer 側の高レベル API で扱う)
        fun glColor3f(r: Float, g: Float, b: Float) {}
        fun glColor4f(r: Float, g: Float, b: Float, a: Float) {}
        fun glEnable(cap: Int) {}
        fun glDisable(cap: Int) {}
        fun glBlendFunc(sfactor: Int, dfactor: Int) {}
        fun glDepthMask(flag: Boolean) {}
        fun glLineWidth(width: Float) {}
        fun glNormal3f(x: Float, y: Float, z: Float) {}
        fun glBegin(mode: Int) {}
        fun glEnd() {}
        fun glVertex3f(x: Float, y: Float, z: Float) {}
        fun glVertex3d(x: Double, y: Double, z: Double) {}
        fun glTexCoord2f(u: Float, v: Float) {}
        fun glTexCoord2d(u: Double, v: Double) {}

        companion object {
            // よく参照される定数
            const val GL_BLEND: Int = 0x0BE2
            const val GL_TEXTURE_2D: Int = 0x0DE1
            const val GL_LIGHTING: Int = 0x0B50
            const val GL_DEPTH_TEST: Int = 0x0B71
            const val GL_CULL_FACE: Int = 0x0B44
            const val GL_ALPHA_TEST: Int = 0x0BC0
            const val GL_SRC_ALPHA: Int = 0x0302
            const val GL_ONE_MINUS_SRC_ALPHA: Int = 0x0303
            const val GL_ONE: Int = 1
            const val GL_ZERO: Int = 0
            const val GL_TRIANGLES: Int = 0x0004
            const val GL_QUADS: Int = 0x0007
        }
    }

    /**
     * スクリプトで `new Parts("bogieF", "wheelF1", ...)` と呼ばれる用のビルダー。
     * 引数の文字列群を保持し、`ScriptModelRenderer.registerParts(parts)` に渡された時点で
     * グループ名を抽出する。
     */
    class PartsBuilder {
        val groupNames: MutableList<String?> = ArrayList<String?>()

        constructor(arg0: Any?) {
            addArg(arg0)
        }

        constructor(arg0: Any?, arg1: Any?) {
            addArg(arg0)
            addArg(arg1)
        }

        constructor(arg0: Any?, arg1: Any?, arg2: Any?) {
            addArg(arg0)
            addArg(arg1)
            addArg(arg2)
        }

        constructor(arg0: Any?, arg1: Any?, arg2: Any?, arg3: Any?) {
            addArg(arg0)
            addArg(arg1)
            addArg(arg2)
            addArg(arg3)
        }

        constructor(arg0: Any?, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?) {
            addArg(arg0)
            addArg(arg1)
            addArg(arg2)
            addArg(arg3)
            addArg(arg4)
        }

        constructor(arg0: Any?, arg1: Any?, arg2: Any?, arg3: Any?, arg4: Any?, arg5: Any?) {
            addArg(arg0)
            addArg(arg1)
            addArg(arg2)
            addArg(arg3)
            addArg(arg4)
            addArg(arg5)
        }

        constructor(args: Array<Any?>?) {
            if (args != null) for (a in args) addArg(a)
        }

        private fun addArg(a: Any?) {
            if (a == null) return
            val s = a.toString()
            if (!s.isBlank()) groupNames.add(s)
        }

        val groupsStr: String
            get() = java.lang.String.join(",", groupNames)
    }

    /**
     * RTM 原作の jp.ngt.ngtlib.io.NGTText スタブ。
     * Nashorn のオーバーロード解決は不安定で、複数の readText() オーバーロードがあると
     * "is not a function" エラーになることがある。各メソッドを 1 個の定義に統一する。
     * readText は本来テキストファイル内容を List<String> で返す。
     * 追加パック側には eval(NGTText.readText(...)) のように文字列化を期待する
     * スクリプトもあるため、List として扱えつつ toString() は元テキストを返す。
    </String> */
    class NGTTextCompat @JvmOverloads constructor(private val scriptEngine: ScriptEngine? = null) {
        fun readText(resource: Any?): MutableList<String?> {
            val content = readTextContent(resource)
            if (content.isEmpty()) {
                return ScriptTextLines.Companion.empty()
            }
            return ScriptTextLines.Companion.from(content)
        }

        fun readTextLines(resource: Any?): Array<String?>? {
            val lines = readText(resource)
            return lines.toTypedArray()
        }

        fun writeText(a: Any?) {}
        fun loadText(a: Any?): String {
            return ""
        }

        fun createText(a: Any?): String {
            return ""
        }

        fun getText(a: Any?): String {
            return ""
        }

        fun getFormattedText(a: Any?): String {
            return ""
        }

        fun getString(a: Any?): String {
            return ""
        }

        fun appendSibling(a: Any?) {}
        fun appendText(a: Any?) {}
        fun applyTextStyles(a: Any?) {}

        private class ScriptTextLines(private val content: String?) : ArrayList<String?>() {
            override fun toString(): String {
                return content!!
            }

            companion object {
                fun empty(): ScriptTextLines {
                    return ScriptTextLines("")
                }

                fun from(content: String): ScriptTextLines {
                    val lines = ScriptTextLines(content)
                    Collections.addAll<String>(lines, *content.split("\\R".toRegex()).toTypedArray())
                    return lines
                }
            }
        }

        private fun readTextContent(resource: Any?): String {
            val requested: String = resourcePath(resource)
            if (requested.isBlank()) {
                return ""
            }
            val normalized: String = normalizeResourcePath(requested)
            if (normalized.isBlank() || normalized.contains("..")) {
                return ""
            }
            try {
                val fromScript = readRelativeToCurrentScript(normalized)
                if (!fromScript.isEmpty()) {
                    return fromScript
                }
                for (candidate in listScriptPackCandidates()) {
                    val cacheKey: String = normalizeResourcePath(candidate.toString()) + "|" + normalized
                    val cached: String? = TEXT_CACHE.get(cacheKey)
                    if (cached != null) {
                        return cached
                    }
                    val text: String = if (Files.isDirectory(candidate))
                        readFromDirectory(candidate, normalized)
                    else
                        readFromArchive(candidate, normalized)
                    if (!text.isEmpty()) {
                        TEXT_CACHE.put(cacheKey, text)
                        return text
                    }
                }
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.debug("NGTText.readText failed for {}", normalized, e)
            }
            return ""
        }

        @Throws(IOException::class)
        private fun readRelativeToCurrentScript(requested: String): String {
            if (scriptEngine == null) {
                return ""
            }
            val rawScriptPath = scriptEngine.get(SCRIPT_PATH_KEY)
            if (rawScriptPath !is String || rawScriptPath.isBlank()) {
                return ""
            }
            val cacheKey = "script:" + normalizeResourcePath(rawScriptPath) + "|" + requested
            val cached: String? = TEXT_CACHE.get(cacheKey)
            if (cached != null) {
                return cached
            }
            try {
                val current = Path.of(rawScriptPath)
                val parent = current.getParent()
                if (parent != null) {
                    val relative = parent.resolve(requested).normalize()
                    if (Files.isRegularFile(relative)) {
                        val text = PackTextDecoder.readText(relative)
                        TEXT_CACHE.put(cacheKey, text)
                        return text
                    }
                }
            } catch (ignored: Exception) {
                // Most pack scripts use virtual ZIP paths here; fall back to pack search.
            }
            return ""
        }

        companion object {
            private val TEXT_CACHE: MutableMap<String?, String?> = ConcurrentHashMap<String?, String?>()

            @Throws(IOException::class)
            private fun readFromDirectory(root: Path, requested: String): String {
                val direct = root.resolve(requested).normalize()
                if (Files.isRegularFile(direct)) {
                    return PackTextDecoder.readText(direct)
                }
                val assetsMinecraft = root.resolve("assets").resolve("minecraft").resolve(requested).normalize()
                if (Files.isRegularFile(assetsMinecraft)) {
                    return PackTextDecoder.readText(assetsMinecraft)
                }
                val leaf = requested.substring(requested.lastIndexOf('/') + 1)
                Files.walk(root).use { stream ->
                    val found = stream
                        .filter { path: Path? -> Files.isRegularFile(path) }
                        .filter { path: Path? -> path!!.getFileName().toString().equals(leaf, ignoreCase = true) }
                        .filter { path: Path? ->
                            normalizeResourcePath(root.relativize(path).toString()).endsWith(requested)
                                    || scoreLooseResourceMatch(root.relativize(path).toString(), requested) > 0
                        }
                        .findFirst()
                        .orElse(null)
                    return if (found == null) "" else PackTextDecoder.readText(found)
                }
            }

            @Throws(IOException::class)
            private fun readFromArchive(archive: Path, requested: String): String {
                PackZipReader.openZipFile(archive).use { zipFile ->
                    val entry: ZipEntry? = zipFile.stream()
                        .filter { e: ZipEntry? -> !e!!.isDirectory() }
                        .filter { e: ZipEntry? ->
                            val name: String = normalizeResourcePath(e!!.getName())
                            name == requested
                                    || name.endsWith("/" + requested)
                                    || name.endsWith("/assets/minecraft/" + requested)
                                    || scoreLooseResourceMatch(name, requested) > 0
                        }
                        .findFirst()
                        .orElse(null)
                    if (entry == null) {
                        return ""
                    }
                    zipFile.getInputStream(entry).use { input ->
                        return PackTextDecoder.readText(input)
                    }
                }
            }

            private fun scoreLooseResourceMatch(path: String?, requested: String): Int {
                val normalizedPath: String = normalizeResourcePath(path)
                val requestedLeaf = requested.substring(requested.lastIndexOf('/') + 1)
                if (!normalizedPath.endsWith("/" + requestedLeaf) && normalizedPath != requestedLeaf) {
                    return -1
                }
                var score = 1
                if (normalizedPath.contains("/scripts/") && requested.contains("scripts/")) score += 4
                if (normalizedPath.contains("/script/") && requested.contains("script/")) score += 3
                if (normalizedPath.endsWith(requested)) score += 8
                return score
            }

            private fun resourcePath(resource: Any?): String {
                if (resource == null) {
                    return ""
                }
                if (resource is CharSequence) {
                    return resource.toString()
                }
                var path: String = invokeString(resource, "func_110623_a")
                if (path.isBlank()) path = invokeString(resource, "getPath")
                if (path.isBlank()) path = fieldString(resource, "path")
                if (path.isBlank()) path = fieldString(resource, "resourcePath")
                if (!path.isBlank()) {
                    return path
                }
                return resource.toString()
            }

            private fun invokeString(target: Any, method: String): String {
                try {
                    val value = target.javaClass.getMethod(method).invoke(target)
                    return if (value == null) "" else value.toString()
                } catch (ignored: Exception) {
                    return ""
                }
            }

            private fun fieldString(target: Any, field: String): String {
                try {
                    val f = target.javaClass.getField(field)
                    val value = f.get(target)
                    return if (value == null) "" else value.toString()
                } catch (ignored: Exception) {
                    return ""
                }
            }

            private fun normalizeResourcePath(raw: String?): String {
                if (raw == null) {
                    return ""
                }
                var normalized = raw.replace('\\', '/').trim { it <= ' ' }
                val colon = normalized.indexOf(':')
                if (colon >= 0) {
                    normalized = normalized.substring(colon + 1)
                }
                normalized = normalized.replaceFirst("^/+".toRegex(), "")
                if (normalized.startsWith("assets/minecraft/")) {
                    normalized = normalized.substring("assets/minecraft/".length)
                }
                return normalized
            }

            private fun listScriptPackCandidates(): MutableList<Path> {
                val seen = java.util.LinkedHashSet<Path?>()
                val result = ArrayList<Path>()
                val gameDir: Path = FMLPaths.GAMEDIR.get()
                addDirectoryChildren(gameDir, seen, result)
                addArchiveChildren(gameDir, seen, result)
                for (dir in arrayOf<String>("mods", "content", "vehicle_packs")) {
                    addDirectoryChildren(gameDir.resolve(dir), seen, result)
                    addArchiveChildren(gameDir.resolve(dir), seen, result)
                }
                val config = gameDir.resolve("config")
                addDirectoryChildren(config.resolve("realtrainmodrenewed"), seen, result)
                addArchiveChildren(config.resolve("realtrainmodrenewed"), seen, result)
                addDirectoryChildren(config.resolve("realtrainmodunofficial"), seen, result)
                addArchiveChildren(config.resolve("realtrainmodunofficial"), seen, result)
                for (category in arrayOf<String>("vehicle", "rail", "installed_object", "official")) {
                    for (path in BundledPackStore.listBundledPacks(category)) {
                        if (seen.add(path)) {
                            result.add(path)
                        }
                    }
                }
                return result
            }

            private fun addDirectoryChildren(dir: Path?, seen: MutableSet<Path?>, result: MutableList<Path>) {
                if (dir == null || !Files.isDirectory(dir)) {
                    return
                }
                try {
                    Files.list(dir).use { stream ->
                        stream.filter { path: Path? -> Files.isDirectory(path) }.forEach { path: Path? ->
                            if (seen.add(path)) {
                                result.add(path!!)
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }

            private fun addArchiveChildren(dir: Path?, seen: MutableSet<Path?>, result: MutableList<Path>) {
                if (dir == null || !Files.isDirectory(dir)) {
                    return
                }
                try {
                    Files.list(dir).use { stream ->
                        stream.filter { path: Path? -> Files.isRegularFile(path) }
                            .filter { path: Path? ->
                                val name = path!!.getFileName().toString().lowercase()
                                name.endsWith(".zip") || name.endsWith(".jar")
                            }
                            .forEach { path: Path? ->
                                if (seen.add(path)) {
                                    result.add(path!!)
                                }
                            }
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /** RTM 原作の jp.ngt.ngtlib.io.NGTLog スタブ。debug/info/warn/error を Java の logger に橋渡し。  */
    class NGTLogCompat {
        fun debug(vararg args: Any?) { /* silent */
        }

        fun info(vararg args: Any?) {
            if (args != null && args.size > 0) {
                RealTrainModRenewed.LOGGER.info("[NGTLog] {}", args[0])
            }
        }

        fun warn(vararg args: Any?) {
            if (args != null && args.size > 0) {
                RealTrainModRenewed.LOGGER.warn("[NGTLog] {}", args[0])
            }
        }

        fun error(vararg args: Any?) {
            if (args != null && args.size > 0) {
                RealTrainModRenewed.LOGGER.error("[NGTLog] {}", args[0])
            }
        }
    }

    /** RTM 原作の jp.ngt.ngtlib.util.NGTUtil スタブ。  */
    class NGTUtilCompat {
        val currentTime: Long
            get() = System.currentTimeMillis()
        val uniqueId: Long
            get() = System.nanoTime()
        val isClient: Boolean
            get() = true
        val currentWorld: Any?
            get() = null
        val currentPlayer: Any?
            get() {
                try {
                    val mc = Minecraft.getInstance()
                    return if (mc == null) null else mc.player
                } catch (t: Throwable) {
                    return null
                }
            }
        val mCVersion: String
            get() = "1.21.1"

        fun isLanguage(code: String?): Boolean {
            try {
                return code != null && code.equals(
                    Minecraft.getInstance().getLanguageManager().getSelected(),
                    ignoreCase = true
                )
            } catch (t: Throwable) {
                return false
            }
        }
    }

    /** RTM 原作の jp.ngt.ngtlib.math.NGTMath スタブ。  */
    class NGTMathCompat {
        fun toRadians(deg: Double): Double {
            return Math.toRadians(deg)
        }

        fun toDegrees(rad: Double): Double {
            return Math.toDegrees(rad)
        }

        fun sin(a: Float): Float {
            return kotlin.math.sin(a.toDouble()).toFloat()
        }

        fun cos(a: Float): Float {
            return kotlin.math.cos(a.toDouble()).toFloat()
        }

        fun tan(a: Float): Float {
            return kotlin.math.tan(a.toDouble()).toFloat()
        }

        fun atan2(y: Float, x: Float): Float {
            return kotlin.math.atan2(y.toDouble(), x.toDouble()).toFloat()
        }

        // RTM 原作 NGTMath は static getSin/getCos/getAtan2 (引数 double) を持つ。
        // user script が importPackage(Packages.jp.ngt.ngtlib.math) で NGTMath を
        // Java 参照に上書きするケースに備えてここに用意する (Render_c12.js 等)。
        fun getSin(rad: Double): Double {
            return kotlin.math.sin(rad)
        }

        fun getCos(rad: Double): Double {
            return kotlin.math.cos(rad)
        }

        fun getTan(rad: Double): Double {
            return kotlin.math.tan(rad)
        }

        fun getAtan2(y: Double, x: Double): Double {
            return kotlin.math.atan2(y, x)
        }

        fun getSqrt(x: Double): Double {
            return kotlin.math.sqrt(x)
        }

        fun sqrt(x: Float): Float {
            return kotlin.math.sqrt(x.toDouble()).toFloat()
        }

        fun floor(x: Float): Float {
            return kotlin.math.floor(x.toDouble()).toFloat()
        }

        fun ceil(x: Float): Float {
            return kotlin.math.ceil(x.toDouble()).toFloat()
        }

        fun clamp(v: Float, min: Float, max: Float): Float {
            return max(min, min(max, v))
        }

        fun clampD(v: Double, min: Double, max: Double): Double {
            return max(min, min(max, v))
        }

        fun normalizeAngle(a: Float): Float {
            var a = a
            while (a >= 180.0f) a -= 360.0f
            while (a < -180.0f) a += 360.0f
            return a
        }
    }

    companion object {
        @JvmField
        val INSTANCE: TrainScriptSystem = TrainScriptSystem()

        @JvmStatic
        fun getInstance(): TrainScriptSystem = INSTANCE

        private val PREFERRED_ECMA_VERSIONS = arrayOf<String?>("2024", "2023", "2022")
        private const val SCRIPT_PATH_KEY = "__ptScriptPath"

        /**
         * ユーザースクリプトの先頭に prepend する JS。
         * Nashorn の Java overloaded method 解決が不安定で `NGTText.readText is not a function`
         * になる事例が頻発するため、純 JS で NGTText 等をオブジェクトとして定義する。
         * readText は List<String> を期待されるので空 ArrayList を返す（no-op として）。
         * importPackage も併せて no-op に上書きしておく（ユーザースクリプトが書き換える前に確立）。
        </String> */
        private val LEGACY_API_PREPEND = "importPackage = function(p) {};\n" +
                "importClass = function(c) {};\n" +  // RTM 1.7.10 互換: スクリプトで instanceof チェックされる旧クラス名を、1.21 NeoForge の対応クラスに alias する。
                "try { EntityPlayer = Java.type('net.minecraft.world.entity.player.Player'); } catch(e) { EntityPlayer = function() {}; }\n" +
                "try { Entity = Java.type('net.minecraft.world.entity.Entity'); } catch(e) {}\n" +
                "try { EntityLivingBase = Java.type('net.minecraft.world.entity.LivingEntity'); } catch(e) { EntityLivingBase = function() {}; }\n" +
                "try { World = Java.type('net.minecraft.world.level.Level'); } catch(e) { World = function() {}; }\n" +
                "try { ItemStack = Java.type('net.minecraft.world.item.ItemStack'); } catch(e) { ItemStack = function() {}; }\n" +
                "try { Block = Java.type('net.minecraft.world.level.block.Block'); } catch(e) { Block = function() {}; }\n" +
                "try { Item = Java.type('net.minecraft.world.item.Item'); } catch(e) { Item = function() {}; }\n" +  // RTM 系の旧クラス名は no-op コンストラクタにする (instanceof は常に false になる)
                "if (typeof EntityVehiclePart === 'undefined') EntityVehiclePart = function() {};\n" +
                "if (typeof EntityTrainBase === 'undefined') EntityTrainBase = function() {};\n" +
                "if (typeof EntityBogie === 'undefined') EntityBogie = function() {};\n" +  // Packages.jp.ngt.ngtlib.math.Vec3 のシム実装。
                // 原作の Vec3 は sub/add/rotateAroundY/getX/getY/getZ/length 等のメソッドを持つ。
                // Packages プロキシ経由だと no-op になるので、JS で独自実装する。
                "(function() {\n" +
                "  if (typeof Packages === 'undefined') return;\n" +
                "  if (!Packages.jp) Packages.jp = {};\n" +
                "  if (!Packages.jp.ngt) Packages.jp.ngt = {};\n" +
                "  if (!Packages.jp.ngt.ngtlib) Packages.jp.ngt.ngtlib = {};\n" +
                "  if (!Packages.jp.ngt.ngtlib.math) Packages.jp.ngt.ngtlib.math = {};\n" +
                "  var Vec3Impl = function(x, y, z) {\n" +
                "    this.x = +x || 0; this.y = +y || 0; this.z = +z || 0;\n" +
                "  };\n" +
                "  Vec3Impl.prototype.getX = function() { return this.x; };\n" +
                "  Vec3Impl.prototype.getY = function() { return this.y; };\n" +
                "  Vec3Impl.prototype.getZ = function() { return this.z; };\n" +
                "  Vec3Impl.prototype.add = function(o) { return new Vec3Impl(this.x + o.x, this.y + o.y, this.z + o.z); };\n" +
                "  Vec3Impl.prototype.sub = function(o) { return new Vec3Impl(this.x - o.x, this.y - o.y, this.z - o.z); };\n" +
                "  Vec3Impl.prototype.subtract = function(o) { return this.sub(o); };\n" +
                "  Vec3Impl.prototype.scale = function(s) { return new Vec3Impl(this.x * s, this.y * s, this.z * s); };\n" +
                "  Vec3Impl.prototype.multiply = function(s) { return this.scale(s); };\n" +
                "  Vec3Impl.prototype.dot = function(o) { return this.x*o.x + this.y*o.y + this.z*o.z; };\n" +
                "  Vec3Impl.prototype.length = function() { return Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z); };\n" +
                "  Vec3Impl.prototype.lengthSquared = function() { return this.x*this.x + this.y*this.y + this.z*this.z; };\n" +
                "  Vec3Impl.prototype.normalize = function() { var l = this.length(); return l > 0 ? new Vec3Impl(this.x/l, this.y/l, this.z/l) : new Vec3Impl(0,0,0); };\n" +
                "  Vec3Impl.prototype.distanceTo = function(o) { return this.sub(o).length(); };\n" +  // NGT Vec3.getYaw/getPitch 互換(SRB の help 表示やマーカー方向計算で使用)。
                "  Vec3Impl.prototype.getYaw = function() { return Math.atan2(this.x, this.z) * 180 / Math.PI; };\n" +
                "  Vec3Impl.prototype.getPitch = function() { return Math.atan2(this.y, Math.sqrt(this.x*this.x + this.z*this.z)) * 180 / Math.PI; };\n" +
                "  Vec3Impl.prototype.rotateAroundX = function(deg) {\n" +
                "    var r = deg * Math.PI / 180; var c = Math.cos(r), s = Math.sin(r);\n" +
                "    return new Vec3Impl(this.x, c*this.y - s*this.z, s*this.y + c*this.z);\n" +
                "  };\n" +
                "  Vec3Impl.prototype.rotateAroundY = function(deg) {\n" +
                "    var r = deg * Math.PI / 180; var c = Math.cos(r), s = Math.sin(r);\n" +
                "    return new Vec3Impl(c*this.x + s*this.z, this.y, -s*this.x + c*this.z);\n" +
                "  };\n" +
                "  Vec3Impl.prototype.rotateAroundZ = function(deg) {\n" +
                "    var r = deg * Math.PI / 180; var c = Math.cos(r), s = Math.sin(r);\n" +
                "    return new Vec3Impl(c*this.x - s*this.y, s*this.x + c*this.y, this.z);\n" +
                "  };\n" +  // Packages 経由の代入は JavaPackage プロキシだと throw する可能性があるので try で隔離。
                "  try { Packages.jp.ngt.ngtlib.math.Vec3 = Vec3Impl; } catch (e) {}\n" +  // 無条件で Vec3 グローバルを上書きする。
                "  try { Vec3 = Vec3Impl; } catch (e) {}\n" +
                "  try { this.Vec3 = Vec3Impl; } catch (e) {}\n" +
                "  try { (function() { Vec3 = Vec3Impl; }).call(this); } catch (e) {}\n" +  // this = global object in non-strict mode
                "})();\n" +  // 念のため IIFE 外でも Vec3 をグローバルに割り当てる(IIFE 内の代入が global に届かない実装に備える)。
                "var __Vec3Impl = (function(){\n" +
                "  var V = function(x, y, z) { this.x = +x || 0; this.y = +y || 0; this.z = +z || 0; };\n" +
                "  V.prototype.getX = function() { return this.x; };\n" +
                "  V.prototype.getY = function() { return this.y; };\n" +
                "  V.prototype.getZ = function() { return this.z; };\n" +
                "  V.prototype.add = function(o) { return new V(this.x+o.x, this.y+o.y, this.z+o.z); };\n" +
                "  V.prototype.sub = function(o) { return new V(this.x-o.x, this.y-o.y, this.z-o.z); };\n" +
                "  V.prototype.subtract = function(o) { return this.sub(o); };\n" +
                "  V.prototype.scale = function(s) { return new V(this.x*s, this.y*s, this.z*s); };\n" +
                "  V.prototype.multiply = function(s) { return this.scale(s); };\n" +  // NGT Vec3.multi(d): スカラー倍。Vec3 を渡された場合は成分積。
                "  V.prototype.multi = function(s) { if (s && typeof s === 'object') return new V(this.x*(s.x||0), this.y*(s.y||0), this.z*(s.z||0)); return new V(this.x*s, this.y*s, this.z*s); };\n" +
                "  V.prototype.copy = function() { return new V(this.x, this.y, this.z); };\n" +
                "  V.prototype.dot = function(o) { return this.x*o.x + this.y*o.y + this.z*o.z; };\n" +
                "  V.prototype.length = function() { return Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z); };\n" +
                "  V.prototype.lengthSquared = function() { return this.x*this.x + this.y*this.y + this.z*this.z; };\n" +
                "  V.prototype.normalize = function() { var l = this.length(); return l > 0 ? new V(this.x/l, this.y/l, this.z/l) : new V(0,0,0); };\n" +
                "  V.prototype.distanceTo = function(o) { return this.sub(o).length(); };\n" +
                "  V.prototype.getYaw = function() { return Math.atan2(this.x, this.z) * 180 / Math.PI; };\n" +
                "  V.prototype.getPitch = function() { return Math.atan2(this.y, Math.sqrt(this.x*this.x + this.z*this.z)) * 180 / Math.PI; };\n" +
                "  V.prototype.rotateAroundX = function(deg) { var r = deg*Math.PI/180, c = Math.cos(r), s = Math.sin(r); return new V(this.x, c*this.y - s*this.z, s*this.y + c*this.z); };\n" +
                "  V.prototype.rotateAroundY = function(deg) { var r = deg*Math.PI/180, c = Math.cos(r), s = Math.sin(r); return new V(c*this.x + s*this.z, this.y, -s*this.x + c*this.z); };\n" +
                "  V.prototype.rotateAroundZ = function(deg) { var r = deg*Math.PI/180, c = Math.cos(r), s = Math.sin(r); return new V(c*this.x - s*this.y, s*this.x + c*this.y, this.z); };\n" +
                "  return V;\n" +
                "})();\n" +
                "var Vec3 = __Vec3Impl;\n" +
                "NGTText = (typeof __RTMU_NGTText__ !== 'undefined' && __RTMU_NGTText__) ? __RTMU_NGTText__ : {\n" +  // 空 ArrayList を返すと sound_includeSoundLib の eval が no-op になり、
                // onUpdate が再定義されないまま onUpdate(su) を再帰呼出して StackOverflow する。
                // dummy の onUpdate/onUpdate2 定義を1要素入れて、eval で no-op 化させる。
                "  readText: function(r) { var l = new java.util.ArrayList(); l.add('function onUpdate(su) {} function onUpdate2(su) {} function tick(e) {} function update(e,pt) {}'); return l; },\n" +
                "  readTextLines: function(r) { return []; },\n" +
                "  writeText: function() {},\n" +
                "  loadText: function() { return ''; },\n" +
                "  createText: function() { return ''; },\n" +
                "  getText: function() { return ''; },\n" +
                "  getFormattedText: function() { return ''; },\n" +
                "  getString: function() { return ''; },\n" +
                "  appendSibling: function() {},\n" +
                "  appendText: function() {},\n" +
                "  applyTextStyles: function() {}\n" +
                "};\n" +
                "NGTLog = { debug: function() {}, info: function() {}, warn: function() {}, error: function() {}, sendChatMessage: function(player, msg){ try{ if(typeof __SRB__!=='undefined'&&__SRB__) __SRB__.chat(player, ''+msg); }catch(e){} }, sendChatMessageToAll: function(msg){ try{ if(typeof __SRB__!=='undefined'&&__SRB__) __SRB__.chat((typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__)?__RTMU_MC__.getPlayer():null, ''+msg); }catch(e){} } };\n" +
                "NGTUtil = { getCurrentTime: function() { return java.lang.System.currentTimeMillis(); }, getUniqueId: function() { return java.lang.System.nanoTime(); }, isClient: function() { return true; }, getCurrentWorld: function() { return null; }, getCurrentPlayer: function() { return null; }, getMCVersion: function() { return '1.21.1'; }, isLanguage: function() { return false; } };\n" +
                "NGTMath = { toRadians: function(d) { return d * Math.PI / 180.0; }, toDegrees: function(r) { return r * 180.0 / Math.PI; }, sin: Math.sin, cos: Math.cos, tan: Math.tan, atan2: Math.atan2, sqrt: Math.sqrt, floor: Math.floor, ceil: Math.ceil, clamp: function(v,a,b) { return Math.max(a, Math.min(b, v)); }, normalizeAngle: function(a) { while(a>=180)a-=360; while(a<-180)a+=360; return a; } };\n" +  // ModelPackManager: スクリプトの sound lib include で頻出するので空 stub
                "if (typeof ModelPackManager === 'undefined') ModelPackManager = { INSTANCE: { getResource: function() { return null; }, getModel: function() { return null; } } };\n" +  // RTM 拡張パックで使われる TrainControllerManager (ATO/ATC など) のスタブ。
                // 実装は無いので getController は空オブジェクトを返し、null チェックが効くようにする。
                "var __ptCtl = function() { return { isActive: false, stopDistance: 0, targetSpeed: 0, mode: 0, value: 0, isEnable: function() { return false; }, isEnabled: function() { return false; }, isWorking: function() { return false; }, isValid: function() { return false; }, getMode: function() { return 0; }, getState: function() { return 0; }, getTargetSpeed: function() { return 0; }, getTargetDistance: function() { return 0; }, getDistance: function() { return 0; }, getStopDistance: function() { return 0; }, getSpeed: function() { return 0; }, getValue: function() { return 0; }, setTarget: function() {}, setEnable: function() {}, setEnabled: function() {}, enable: function() {}, disable: function() {}, update: function() {}, reset: function() {} }; };\n" +
                "var __ptPatchController = function(c) { c = c || {}; if (!c.tascController || typeof c.tascController.isEnable !== 'function') c.tascController = __ptCtl(); if (!c.atoController || typeof c.atoController.isEnable !== 'function') c.atoController = __ptCtl(); if (!c.atsController || typeof c.atsController.isEnable !== 'function') c.atsController = __ptCtl(); if (!c.atcController || typeof c.atcController.isEnable !== 'function') c.atcController = __ptCtl(); if (typeof c.isEnable !== 'function') c.isEnable = function() { return false; }; if (typeof c.isEnabled !== 'function') c.isEnabled = function() { return false; }; if (typeof c.getSpeedLimit !== 'function') c.getSpeedLimit = function() { return 0; }; if (!c.speedOrderList) c.speedOrderList = []; return c; };\n" +
                "if (typeof TrainControllerManager === 'undefined' || !TrainControllerManager) {\n" +  // 空ではなくダミーオブジェクトを返す。getTrainController(entity).tascController などへの
                // 連鎖アクセスが頻出するため、null 返しだと「Cannot get property X of null」で死ぬ。
                // 各サブコントローラ共通のダミーメソッド群。スクリプト(SD8200 等)は isEnable() など
                // 多様なメソッドを呼ぶため、未定義だと「X is not a function」で server script が毎tick死ぬ。
                // 真偽系は false、数値系は 0、設定/更新系は no-op を返す広めの stub にして根本的に潰す。
                "  var __ptDummyController = {\n" +
                "    tascController: __ptCtl(),\n" +
                "    atoController: __ptCtl(),\n" +
                "    atsController: __ptCtl(),\n" +
                "    atcController: __ptCtl(),\n" +
                "    isActive: false,\n" +
                "    speedOrderList: [],\n" +
                "    isEnable: function() { return false; },\n" +
                "    isEnabled: function() { return false; },\n" +
                "    update: function() {},\n" +
                "    getState: function() { return 0; },\n" +
                "    getSpeedLimit: function() { return 0; },\n" +
                "    getSpeed: function() { return 0; }\n" +
                "  };\n" +
                "  TrainControllerManager = {\n" +
                "    INSTANCE: {\n" +
                "      getController: function() { return __ptDummyController; },\n" +
                "      getTrainController: function() { return __ptDummyController; },\n" +
                "      registerController: function() {},\n" +
                "      unregisterController: function() {},\n" +
                "      hasController: function() { return false; }\n" +
                "    },\n" +
                "    getController: function() { return __ptDummyController; },\n" +
                "    getTrainController: function() { return __ptDummyController; }\n" +
                "  };\n" +
                "}\n" +
                "try { var __ptOldGetTC = TrainControllerManager.getTrainController; TrainControllerManager.getTrainController = function(e) { try { return __ptPatchController(__ptOldGetTC ? __ptOldGetTC(e) : null); } catch (ex) { return __ptPatchController(null); } }; } catch (e) { TrainControllerManager.getTrainController = function() { return __ptPatchController(null); }; }\n" +
                "try { var __ptOldGetC = TrainControllerManager.getController; TrainControllerManager.getController = function(e) { try { return __ptPatchController(__ptOldGetC ? __ptOldGetC(e) : null); } catch (ex) { return __ptPatchController(null); } }; } catch (e) { TrainControllerManager.getController = function() { return __ptPatchController(null); }; }\n" +  // 重要: LEGACY_API_PREPEND は user script と同じ eval 内で先頭に prepend される。
                // injectScriptCompatibility() の別 eval で定義した var NGTMath は別 binding に
                // 閉じてしまうことがあり、user script 実行時に「NGTMath.getSin is not a function」
                // になる事例 (C12 render_rod) があるため、ここで pure JS の NGTMath を定義し直す。
                // user script の importPackage(Packages.jp.ngt.ngtlib.math) を no-op 化済みなので
                // この再代入が user script より前に確実に効く。\n
                "var NGTMath = {\n" +
                "  toRadians: function(deg) { return deg * Math.PI / 180; },\n" +
                "  toDegrees: function(rad) { return rad * 180 / Math.PI; },\n" +
                "  getSin: function(rad) { return Math.sin(rad); },\n" +
                "  getCos: function(rad) { return Math.cos(rad); },\n" +
                "  getTan: function(rad) { return Math.tan(rad); },\n" +
                "  getAtan2: function(y, x) { return Math.atan2(y, x); },\n" +
                "  getSqrt: function(x) { return Math.sqrt(x); },\n" +
                "  sin: function(rad) { return Math.sin(rad); },\n" +
                "  cos: function(rad) { return Math.cos(rad); },\n" +
                "  tan: function(rad) { return Math.tan(rad); },\n" +
                "  atan2: function(y, x) { return Math.atan2(y, x); },\n" +
                "  sqrt: function(x) { return Math.sqrt(x); },\n" +
                "  floor: function(x) { return Math.floor(x); },\n" +
                "  ceil: function(x) { return Math.ceil(x); },\n" +
                "  clamp: function(v, a, b) { return Math.max(a, Math.min(b, v)); },\n" +
                "  normalizeAngle: function(a) { while(a>=180)a-=360; while(a<-180)a+=360; return a; }\n" +
                "};\n" +  // NGTUtilClient / MCWrapperClient も user script と同じ eval で確実に定義する(別 eval の
                // 定義は見えないため)。getMinecraft は __RTMU_MC__(クライアント実体)へ橋渡し。
                "function __rtmuMcShim() { return { field_71462_r: ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getCurrentScreen() : null), func_135016_M: function() { return { func_135041_c: function() { return { func_135034_a: function() { return ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getLanguageCode() : 'en_us'); } }; } }; } }; }\n" +
                "var NGTUtilClient = { getMinecraft: function() { return __rtmuMcShim(); }, getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, bindTexture: function(t) { try { if (typeof renderer !== 'undefined' && renderer) renderer.bindTexture(t); } catch(e){} } };\n" +
                "var MCWrapperClient = { getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, getMinecraft: function() { return __rtmuMcShim(); }, playSound: function(domain, name, volume, pitch) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.playSound(domain, name, volume == null ? 1.0 : volume, pitch == null ? 1.0 : pitch); }, playSoundAtRange: function(domain, name, volume, pitch, range) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.playSoundAtRange(domain, name, volume == null ? 1.0 : volume, pitch == null ? 1.0 : pitch, range == null ? 16.0 : range); } };\n" +  // RTM 共通ラッパー/レール系グローバルも user script eval で定義(別 eval の定義は見えないため)。
                // entity 位置などは null 安全に。レール系は SRB の preview/敷設で参照される。
                "function __srbNum(v){ return (v==null)?0:(v.doubleValue?v.doubleValue():v); }\n" +  // マーカー描画の基準 entityX は entity のレンダー補間位置(renderPosX)。PoseStack の原点も
                // 同じ補間位置なので相殺し、固定マーカーは真のワールド座標に完全固定される(移動・補間でブレない)。
                // getX()(tick位置)を使うと原点(補間)とズレて固定アンカーがドリフトする。フォールバックで getX。
                "var MCWrapper = { getPosX: function(e){ try{ if(typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__) return __RTMU_MC__.renderPosX(e); return e?e.getX():0; }catch(x){return e?e.getX():0;} }, getPosY: function(e){ try{ if(typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__) return __RTMU_MC__.renderPosY(e); return e?e.getY():0; }catch(x){return e?e.getY():0;} }, getPosZ: function(e){ try{ if(typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__) return __RTMU_MC__.renderPosZ(e); return e?e.getZ():0; }catch(x){return e?e.getZ():0;} }, getWorld: function(e){ try{ return e?e.level():null; }catch(x){return null;} } };\n" +  // BlockUtil.getMOPFromPlayer = プレイヤー視線レイキャスト。RTM の MOP 形状(field_72307_f=hitVec, func_178782_a=BlockPos)で返す。
                "var BlockUtil = { setBlock: function() {},\n" +
                "  getMOPFromPlayer: function(player, dist, includeFluids){ try{ if(typeof __RTMU_MC__==='undefined'||!__RTMU_MC__) return null; var r=__RTMU_MC__.raycast(dist||512); if(!r) return null; var hx=r.getHitX(),hy=r.getHitY(),hz=r.getHitZ(); var bx=r.getBlockX(),by=r.getBlockY(),bz=r.getBlockZ(); return { field_72307_f:{ field_72450_a:hx, field_72448_b:hy, field_72449_c:hz }, field_72311_b:bx, field_72312_c:by, field_72309_d:bz, func_178782_a:function(){ return { func_177958_n:function(){return bx;}, func_177956_o:function(){return by;}, func_177952_p:function(){return bz;} }; } }; }catch(e){ return null; } } };\n" +
                "if (typeof Mouse === 'undefined') Mouse = { isButtonDown: function(b){ try{ return (typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__)?__RTMU_MC__.isMouseDown(b|0):false; }catch(e){return false;} } };\n" +
                "if (typeof RTMBlock === 'undefined') RTMBlock = { marker: { __rtmuToken: 'marker' } };\n" +
                "if (typeof RTMItem === 'undefined') RTMItem = { itemLargeRail: { __rtmuToken: 'itemLargeRail' } };\n" +
                "if (typeof RTMResource === 'undefined') RTMResource = { RAIL: { defaultName: 'default', __rtmuToken: 'RAIL' } };\n" +
                "if (typeof ResourceStateRail === 'undefined') ResourceStateRail = function(type, x){ this.type=type; this.modelId=''; this.setResourceName=function(n){ this.modelId=n; }; this.readFromNBT=function(nbt){}; this.writeToNBT=function(){ return (typeof NBTTagCompound!=='undefined')?new NBTTagCompound():{}; }; };\n" +
                "if (typeof ItemRail === 'undefined') ItemRail = { getProperty: function(item){ return null; } };\n" +
                "if (typeof RailDir === 'undefined') RailDir = { STRAIGHT: 0, NORTH: 0, EAST: 2, SOUTH: 4, WEST: 6 };\n" +
                "if (typeof NBTTagCompound === 'undefined') NBTTagCompound = function(){ this.__m={}; this.func_74782_a=function(k,v){ this.__m[k]=v; }; this.func_74775_l=function(k){ return this.__m[k]||new NBTTagCompound(); }; this.func_74778_a=function(k,v){ this.__m[k]=v; }; this.func_74779_i=function(k){ return this.__m[k]||''; }; };\n" +  // TileEntityLargeRailBase は RTMU の LargeRailCoreBlockEntity 型に束ねる(Java.type)。
                // これで `tile instanceof TileEntityLargeRailBase` が実レールBEで true になり、レール接続検出が効く。
                "try { TileEntityLargeRailBase = Java.type('cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity'); } catch(e) { if (typeof TileEntityLargeRailBase === 'undefined') TileEntityLargeRailBase = function(){}; }\n" +
                "if (typeof TileEntitySign === 'undefined') TileEntitySign = function(){};\n" +  // RailPosition は __SRB__ ブリッジ経由で RTMU の実 RailPosition を生成(new で返り値オブジェクトが採用される)。\n
                "if (typeof RailPosition === 'undefined') RailPosition = function(x,y,z,dir,type){ try{ return __SRB__.createRailPosition(x|0,y|0,z|0,dir|0,(type!=null?__srbNum(type):0),-1,0,0,0,0,0); }catch(e){ return { blockX:x,blockY:y,blockZ:z,direction:dir,switchType:type,anchorYaw:0,anchorPitch:0,anchorLengthHorizontal:-1,anchorLengthVertical:-1,cantCenter:0,cantEdge:0,height:0,setHeight:function(h){this.height=h;},init:function(){} }; } };\n" +  // RailPosition.REVISION: 8方向の[dx,dz]オフセット(RTMU RailPosition.REVISION と同値)。render の getNearestEdgePos 等が参照。
                "RailPosition.REVISION = [[0.0,-0.5],[-0.5,-0.5],[-0.5,0.0],[-0.5,0.499999],[0.0,0.499999],[0.499999,0.499999],[0.499999,0.0],[0.499999,-0.5]];\n" +  // セキュリティ: Java.type を無効化。エイリアス設定は完了済みのため、
                // 以降のユーザースクリプトからは Java クラスへの直接アクセスを防止する。
                "Java = undefined;\n"
        private const val SCRIPT_MODEL_KEY = "__ptScriptModel"

        @Volatile
        private var graalPolyglotUnavailable = false
        private const val SCRIPT_CORE_VERSION = "2.4.24"
        private val DISABLED_SCRIPT_ENGINES: MutableSet<Int?> = ConcurrentHashMap.newKeySet<Int?>()
        private val SCRIPT_FAILURE_COUNTS: MutableMap<Int?, Int?> = ConcurrentHashMap<Int?, Int?>()
        private val REPORTED_SCRIPT_ERRORS: MutableSet<String?> = ConcurrentHashMap.newKeySet<String?>()

        @Volatile
        private var reportedScriptEngineFactories = false

        @JvmStatic
        fun doScriptCompat(script: String?): ScriptEngine? {
            val scriptEngine: ScriptEngine? = createScriptEngine()
            if (scriptEngine == null) {
                return null
            }
            try {
                injectScriptCompatibility(scriptEngine, ScriptModelRenderer(null, null))
                scriptEngine.eval(LEGACY_API_PREPEND + normalizeLegacyScriptReferences(if (script == null) "" else script))
                return scriptEngine
            } catch (e: Exception) {
                throw RuntimeException("Script exec error", e)
            }
        }

        fun doScriptFunctionCompat(scriptEngine: Any?, functionName: String?, args: Any?): Any? {
            if ((scriptEngine !is Invocable) || functionName == null || functionName.isBlank()) {
                return null
            }
            try {
                return scriptEngine.invokeFunction(functionName, *toObjectArray(args))
            } catch (e: NoSuchMethodException) {
                throw RuntimeException("Script exec error : " + functionName, e)
            } catch (e: ScriptException) {
                throw RuntimeException("Script exec error : " + functionName, e)
            }
        }

        fun doScriptIgnoreErrorCompat(scriptEngine: Any?, functionName: String?, args: Any?): Any? {
            try {
                return doScriptFunctionCompat(scriptEngine, functionName, args)
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.debug("Ignoring legacy script function failure: {}", functionName, e)
                return null
            }
        }

        fun getScriptFieldCompat(scriptEngine: Any?, fieldName: String?): Any? {
            if (scriptEngine is ScriptEngine && fieldName != null) {
                return scriptEngine.get(fieldName)
            }
            return null
        }

        private fun toObjectArray(args: Any?): Array<Any?> {
            if (args == null) {
                return arrayOfNulls<Any>(0)
            }
            if (args is Array<*>) {
                val out = arrayOfNulls<Any>(args.size)
                for (i in args.indices) {
                    out[i] = args[i]
                }
                return out
            }
            if (args is MutableList<*>) {
                return args.toTypedArray()
            }
            val type: Class<*> = args.javaClass
            if (type.isArray()) {
                val length = java.lang.reflect.Array.getLength(args)
                val out = arrayOfNulls<Any>(length)
                for (i in 0..<length) {
                    out[i] = java.lang.reflect.Array.get(args, i)
                }
                return out
            }
            return arrayOf<Any?>(args)
        }

        private val scriptEngine: ScriptEngine?
            get() {
                val system: TrainScriptSystem =
                    INSTANCE
                if (system.engine != null) {
                    return system.engine
                }

                return createScriptEngine()
            }

        private fun createScriptEngine(): ScriptEngine? {
            val manager = ScriptEngineManager(Thread.currentThread().getContextClassLoader())
            var engine: ScriptEngine? = getAvailableScriptEngine(manager)
            if (engine == null) {
                engine = getAvailableScriptEngine(ScriptEngineManager(TrainScriptSystem::class.java.getClassLoader()))
            }
            return engine
        }

        @JvmStatic
        fun loadScript(scriptPath: String, model: Any?) {
            loadScriptFromPath(scriptPath, model, null)
        }

        @JvmStatic
        fun loadScriptFromPath(scriptPath: String, model: Any?, modelName: String?) {
            RealTrainModRenewed.LOGGER.info(
                "legacy script load requested: {} for model {}",
                scriptPath,
                if (model == null) "null" else model.javaClass.getSimpleName()
            )
            try {
                val scriptEngine: ScriptEngine? = createScriptEngine()
                if (scriptEngine == null) {
                    RealTrainModRenewed.LOGGER.warn("JavaScript engine not available for model script: {}", scriptPath)
                    return
                }

                val path = Path.of(scriptPath)
                if (Files.exists(path)) {
                    RealTrainModRenewed.LOGGER.info("Loading script from filesystem path: {}", path)
                    val script = PackTextDecoder.readText(path)
                    loadScript(scriptPath, script, model, modelName, scriptEngine)
                } else {
                    RealTrainModRenewed.LOGGER.info(
                        "Script path not found on filesystem, skipping direct load: {}",
                        scriptPath
                    )
                }
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.error("Failed to load script for model: {}", scriptPath, e)
            }
        }

        @JvmOverloads
        @JvmStatic
        fun loadScript(scriptPath: String?, script: String?, model: Any?, modelName: String? = null) {
            RealTrainModRenewed.LOGGER.info(
                "legacy script load requested from content: {} for model {}",
                scriptPath,
                if (model == null) "null" else model.javaClass.getSimpleName()
            )
            try {
                val scriptEngine: ScriptEngine? = createScriptEngine()
                if (scriptEngine == null) {
                    RealTrainModRenewed.LOGGER.warn("JavaScript engine not available for model script: {}", scriptPath)
                    return
                }
                loadScript(scriptPath, script, model, modelName, scriptEngine)
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.error("Failed to load script for model: {}", scriptPath, e)
            }
        }

        private fun getAvailableScriptEngine(manager: ScriptEngineManager?): ScriptEngine? {
            if (manager == null) {
                return null
            }

            if (!graalPolyglotUnavailable) {
                for (ecmaVersion in PREFERRED_ECMA_VERSIONS) {
                    try {
                        val polyglotEngine = Engine.newBuilder()
                            .allowExperimentalOptions(true)
                            .build()
                        val contextBuilder = Context.newBuilder("js")
                            .allowAllAccess(true)
                            .allowExperimentalOptions(true)
                            .option("js.nashorn-compat", "true")
                            .option("js.syntax-extensions", "true")
                            .option("js.ecmascript-version", ecmaVersion)
                        val scriptEngine: ScriptEngine = GraalJSScriptEngine.create(polyglotEngine, contextBuilder)
                        if (scriptEngine != null) {
                            RealTrainModRenewed.LOGGER.info(
                                "Using Graal.js with RTM compatibility on ECMAScript {}.",
                                ecmaVersion
                            )
                            return scriptEngine
                        }
                    } catch (e: Throwable) {
                        RealTrainModRenewed.LOGGER.debug(
                            "Graal.js polyglot unavailable (ECMAScript {}): {}",
                            ecmaVersion,
                            e.message
                        )
                    }
                }
                graalPolyglotUnavailable = true
                RealTrainModRenewed.LOGGER.info("Graal.js polyglot API not available on module-path; using ScriptEngineManager fallback.")
            }

            val engineNames = arrayOf<String?>("javascript", "js", "Graal.js", "graal.js", "nashorn")
            for (name in engineNames) {
                val scriptEngine = manager.getEngineByName(name)
                if (scriptEngine != null) {
                    RealTrainModRenewed.LOGGER.info("Using JavaScript engine '{}'.", name)
                    return scriptEngine
                }
            }

            for (factory in manager.getEngineFactories()) {
                val engineName = factory.getEngineName()
                val names = java.lang.String.join(",", factory.getNames())
                val mimeTypes = java.lang.String.join(",", factory.getMimeTypes())
                val probe = (engineName + "," + names + "," + mimeTypes).lowercase()
                if (probe.contains("graal") || probe.contains("javascript") || probe.contains("ecmascript")) {
                    try {
                        val scriptEngine = factory.scriptEngine
                        if (scriptEngine != null) {
                            RealTrainModRenewed.LOGGER.info(
                                "Using JavaScript engine factory '{}' (aliases: {}).",
                                engineName,
                                names
                            )
                            return scriptEngine
                        }
                    } catch (e: RuntimeException) {
                        RealTrainModRenewed.LOGGER.debug(
                            "JavaScript engine factory '{}' failed: {}",
                            engineName,
                            e.message
                        )
                    }
                }
            }

            if (!manager.getEngineFactories().isEmpty()) {
                if (!reportedScriptEngineFactories) {
                    reportedScriptEngineFactories = true
                    RealTrainModRenewed.LOGGER.warn(
                        "Available script engines: {}",
                        manager.getEngineFactories().stream()
                            .map<String?> { factory: ScriptEngineFactory? -> factory!!.getEngineName() + " aliases=" + factory.getNames() }
                            .collect(Collectors.joining(", "))
                    )
                }
            } else {
                if (!reportedScriptEngineFactories) {
                    reportedScriptEngineFactories = true
                    RealTrainModRenewed.LOGGER.warn("No script engine providers found on the classpath.")
                }
            }

            try {
                val factoryClass = Class.forName("org.graalvm.polyglot.js.jsr223.GraalJSScriptEngineFactory")
                val factory = factoryClass.getDeclaredConstructor().newInstance() as ScriptEngineFactory
                val scriptEngine = factory.scriptEngine
                if (scriptEngine != null) {
                    RealTrainModRenewed.LOGGER.info("Using Graal.js ScriptEngineFactory directly.")
                    return scriptEngine
                }
            } catch (ignored: ClassNotFoundException) {
                // Graal.js is not available on the classpath.
            } catch (e: Exception) {
                RealTrainModRenewed.LOGGER.debug(
                    "Failed to instantiate Graal.js ScriptEngineFactory, falling back to other engines: {}",
                    e.message
                )
            }

            return null
        }

        private fun loadScript(
            scriptPath: String?,
            script: String?,
            model: Any?,
            modelName: String?,
            scriptEngine: ScriptEngine
        ) {
            var script = script
            RealTrainModRenewed.LOGGER.info(
                "Executing model script: {} (model={})",
                scriptPath,
                if (model == null) "null" else model.javaClass.getSimpleName()
            )
            try {
                val renderer = ScriptModelRenderer(model, modelName)
                injectScriptCompatibility(scriptEngine, renderer)
                scriptEngine.put(SCRIPT_PATH_KEY, if (scriptPath == null) "" else scriptPath)
                scriptEngine.put(SCRIPT_MODEL_KEY, if (modelName == null) "" else modelName)
                script = normalizeLegacyScriptReferences(script)
                renderer.configureReplaySafety(script)
                script = LEGACY_API_PREPEND + (if (script == null) "" else script)
                scriptEngine.eval(script)
                prepareScriptRuntimeBeforeInit(scriptEngine)

                if (model is MQOModel) {
                    model.setScriptEngine(scriptEngine)
                } else if (model is MqoModel) {
                    model.setScriptEngine(scriptEngine, renderer)
                } else {
                    RealTrainModRenewed.LOGGER.warn(
                        "legacy script model is not recognized type: {}",
                        if (model == null) "null" else model.javaClass.getName()
                    )
                }
                invokeScriptInit(scriptEngine, renderer)
                prepareScriptRuntimeAfterInit(scriptEngine)
                RealTrainModRenewed.LOGGER.info("Script loaded for model: {}", scriptPath)
            } catch (e: ScriptException) {
                reportScriptError(scriptEngine, "load(model)", e)
                RealTrainModRenewed.LOGGER.error(
                    "Failed to execute script for model: {}, continuing without script",
                    scriptPath,
                    e
                )
            } catch (e: Exception) {
                reportScriptError(scriptEngine, "load(model)", e)
                RealTrainModRenewed.LOGGER.error(
                    "Unexpected error loading script for model: {}, continuing without script",
                    scriptPath,
                    e
                )
            }
        }

        @JvmStatic
        fun loadStandaloneScript(scriptPath: String?, script: String?, modelName: String?): ScriptEngine? {
            var script = script
            val scriptEngine: ScriptEngine? = createScriptEngine()
            if (scriptEngine == null) {
                return null
            }
            RealTrainModRenewed.LOGGER.info("Executing standalone script: {}", scriptPath)
            try {
                val renderer = ScriptModelRenderer(null, modelName)
                injectScriptCompatibility(scriptEngine, renderer)
                scriptEngine.put(SCRIPT_PATH_KEY, if (scriptPath == null) "" else scriptPath)
                scriptEngine.put(SCRIPT_MODEL_KEY, if (modelName == null) "" else modelName)
                script = normalizeLegacyScriptReferences(script)
                // ユーザースクリプトの先頭に Java 注入オブジェクトのリバインドを prepend する。
                // injectScriptCompatibility 内の eval で var 宣言しても、別の eval をまたぐと
                // Nashorn でグローバルが期待通りに見えないケースがあるため、確実性を最大化。
                script = LEGACY_API_PREPEND + script
                RealTrainModRenewed.LOGGER.info(
                    "[PREPEND-CHECK] {} prependLen={} scriptLen={} firstChars={}",
                    scriptPath, LEGACY_API_PREPEND.length, script.length,
                    script.substring(0, min(80, script.length))
                )
                scriptEngine.eval(script)
                prepareScriptRuntimeBeforeInit(scriptEngine)
                invokeScriptInit(scriptEngine, renderer)
                prepareScriptRuntimeAfterInit(scriptEngine)
                RealTrainModRenewed.LOGGER.info("Standalone script loaded: {}", scriptPath)
                return scriptEngine
            } catch (e: ScriptException) {
                reportScriptError(scriptEngine, "load(standalone)", e)
                RealTrainModRenewed.LOGGER.error("Failed to execute standalone script: {}", scriptPath, e)
            } catch (e: Exception) {
                reportScriptError(scriptEngine, "load(standalone)", e)
                RealTrainModRenewed.LOGGER.error("Unexpected error loading standalone script: {}", scriptPath, e)
            }
            return null
        }

        private fun normalizeLegacyScriptReferences(script: String?): String? {
            if (script == null || script.isEmpty()) {
                return script
            }
            val oldRoot = "n" + "gt"
            val oldLibRoot = oldRoot + "lib"
            val oldVehicleRoot = "r" + "tm"
            val oldCoreName = "R" + "T" + "MCore"
            val oldClientUtilName = "N" + "G" + "TUtilClient"
            val oldUtilName = "N" + "G" + "TUtil"
            val oldLogName = "N" + "G" + "TLog"
            val oldFileLoaderName = "N" + "G" + "TFileLoader"
            val oldTessellatorName = "N" + "G" + "TTessellator"
            val packages = "Packages.jp." + oldRoot + "."
            var result: String? = script
            result = result.replace("var GLHelper = " + packages + oldLibRoot + ".renderer.GLHelper;", "")
            result = result.replace(
                "var " + oldClientUtilName + " = " + packages + oldLibRoot + ".util." + oldClientUtilName + ";",
                ""
            )
            result =
                result.replace("var " + oldUtilName + " = " + packages + oldLibRoot + ".util." + oldUtilName + ";", "")
            result = result.replace(packages + oldVehicleRoot + "." + oldCoreName, oldCoreName)
            result = result.replace(packages + oldVehicleRoot + ".modelpack.ModelPackManager", "ModelPackManager")
            result = result.replace(packages + oldLibRoot + ".util." + oldClientUtilName, oldClientUtilName)
            result = result.replace(packages + oldLibRoot + ".util." + oldUtilName, oldUtilName)
            result = result.replace(packages + oldLibRoot + ".io." + oldLogName, oldLogName)
            result = result.replace(
                packages + oldLibRoot + ".io." + oldFileLoaderName + ".getInputStream",
                oldFileLoaderName + "_getInputStream"
            )
            result = result.replace(packages + oldLibRoot + ".renderer.GLHelper", "GLHelper")
            // Packages.jp.ngt.ngtlib.math.Vec3 → グローバル Vec3 (LEGACY_API_PREPEND で JS 実装)
            // Nashorn の Packages は JavaPackage で、JS から property を上書きできないため、
            // ソース側で直接置換するのが確実。
            result = result.replace(packages + oldLibRoot + ".math.Vec3", "Vec3")
            result = result.replace(packages + oldLibRoot + ".math.NGTMath", "NGTMath")
            result = result.replace(packages + oldLibRoot + ".io.ScriptUtil", "ScriptUtil")
            result = result.replace(packages + oldLibRoot + ".renderer." + oldTessellatorName, "TessellatorCompat")
            result = result.replace(packages + oldLibRoot + ".renderer.model.ModelLoader", "ModelLoader")
            result = result.replace(packages + oldLibRoot + ".renderer.model.VecAccuracy", "VecAccuracy")
            result = result.replace(packages + oldLibRoot + ".math.Vec3", "Vec3")
            result = result.replace("Packages.net.minecraft.util.Identifier", "IdentifierCompat")
            // 1.12.2 Forge の Loader (mod 存在チェック) は 1.21.1(NeoForge)に無いのでスタブへ。
            result = result.replace("Packages.net.minecraftforge.fml.common.Loader", "LoaderCompat")
            result = result.replace("if (!stream) return null;", "if (!stream) return __ptDummyTextureData();")
            result = result.replace("Java.from(", "__ptJavaFrom(")
            result = appendSuperRailBuilderOverrides(result)
            return result
        }

        /**
         * SuperRailBuilder3 のサーバスクリプトを検出したら、レール生成/削除/レール所持判定の各関数を
         * RTMU ネイティブ敷設(__SRB__ ブリッジ)へ差し替える上書き定義を末尾に追加する。
         * GUI・制御フロー(onUpdate/dataMap)・render はそのまま活かし、低レベル RTM/MCP API の不一致を回避する。
         */
        internal fun appendSuperRailBuilderOverrides(script: String?): String? {
            if (script == null || !script.contains("SuperRailBuilderVersion")) {
                return script
            }
            val isServer = script.contains("function buildNormalRail")
            val sb = StringBuilder()
            sb.append("\n;(function(){ try {\n")
            // --- server/render 共通: プレイヤー/インベントリの MCP API を触る関数を差し替える ---
            // getPlayerRail は「手持ちレールのモデルID(真偽値兼用)」を __SRB__ 経由で返す。
            // player は server では rider ラッパー(__srbReal)、render では実 LocalPlayer。
            sb.append("  getSelectedSlotItem = function(player){ return null; };\n")
            sb.append("  hasPlayerMarker = function(player){ return false; };\n")
            sb.append("  getPlayerRail = function(player) { try { var p = (player && player.__srbReal)?player.__srbReal:player; var id = __SRB__.heldRailModelId(p); return (id && (''+id).length>0)?(''+id):null; } catch(e){ return null; } };\n")
            // doFollowing: ホストプレイヤーの上へ車体をテレポート(MCP field を避け getX/Y/Z を使う)。
            // doFollowing はサーバ側のみで車をプレイヤー上へ移動させ、クライアントはサーバ同期＋補間で
            // 滑らかに追従する。マーカーは MCWrapper.getPosX(=レンダー補間位置)基準で描くので一致して荒ぶらない。
            // クライアントで毎フレーム動かすと描画とズレるため、ここでは動かさない。
            sb.append("  doFollowing = function(entity, hostPlayer){ try{ if(!entity||!hostPlayer) return; var w=entity.field_70170_p; if(w && w.isClientSide() && w.isClientSide()) return; var p=hostPlayer.__srbReal?hostPlayer.__srbReal:hostPlayer; if(!p||!p.getX) return; entity.func_70107_b(p.getX(), p.getY()+2, p.getZ()); try{entity.field_70159_w=0; entity.field_70181_x=0; entity.field_70179_y=0;}catch(e2){} }catch(e){} };\n")
            // getTileEntity: 1.12.2 の net.minecraft.util.math.BlockPos を new せず、座標直接版 func_175625_s を使う。
            // 当たり判定/道床ブロックはコアに解決して返す(__SRB__.railCoreAt)。レール沿いどこでも接続検出が効き、
            // 接続マーカーが接線ロックされる(本家挙動)。フォールバックで素の func_175625_s。
            sb.append("  getTileEntity = function(world, x, y, z){ try{ if(typeof __SRB__!=='undefined'&&__SRB__) return __SRB__.railCoreAt(world, Math.floor(x), Math.floor(y), Math.floor(z)); return world.func_175625_s(Math.floor(x), Math.floor(y), Math.floor(z)); }catch(e){ try{ return world.func_175625_s(Math.floor(x), Math.floor(y), Math.floor(z)); }catch(e2){ return null; } } };\n")
            // getTileEntityPos は func_174877_v(MCP)を使うので、座標は __SRB__.tilePos 経由で取る(レール接続検出用)。
            sb.append("  getTileEntityPos = function(tile){ try{ var p=__SRB__.tilePos(tile); return {x:p[0],y:p[1],z:p[2]}; }catch(e){ return {x:0,y:0,z:0}; } };\n")
            if (isServer) {
                // --- server 専用: rider ラッパー + 敷設ブリッジ ---
                sb.append("  var __srbWrap = function(p){ if(!p) return null; if(p.__srbReal) return p; return { __srbReal:p, func_145782_y:function(){return p.getId();}, func_184210_p:function(){try{p.stopRiding();}catch(e){}}, func_70078_a:function(t){} }; };\n")
                sb.append("  getRider = function(entity){ try{ var ps=entity.func_184188_bt(); var r=(ps&&ps.size()>0)?ps.get(0):null; return __srbWrap(r); }catch(e){ return null; } };\n")
                sb.append("  getRidingEntity = function(entity){ try{ return __srbWrap(entity.func_184187_bx()); }catch(e){ return null; } };\n")
                sb.append("  createRailPosition = function(data) { return __SRB__.createRailPosition(data.blockX|0, data.blockY|0, data.blockZ|0, data.markerDir|0, (data.switchType!=null?Number(data.switchType):0), (data.anchorLength!=null?Number(data.anchorLength):-1), (data.anchorPitch!=null?Number(data.anchorPitch):0), (data.anchorYaw!=null?Number(data.anchorYaw):0), (data.cantCenter!=null?Number(data.cantCenter):0), (data.cantEdge!=null?Number(data.cantEdge):0), (data.height!=null?Number(data.height):0)); };\n")
                sb.append("  buildNormalRail = function(world, startRP, endRP, railItem) { try { __SRB__.buildNormalRail(world, startRP, endRP, railItem); } catch(e){ try{NGTLog.error('SRB buildNormalRail err: '+e);}catch(e2){} } };\n")
                sb.append("  buildBranchRail = function(world, rps, railItem) { try { var l=new java.util.ArrayList(); for(var i=0;i<rps.length;i++) l.add(rps[i]); __SRB__.buildBranchRail(world, l, railItem); } catch(e){} };\n")
                sb.append("  deleteRail = function(world, x, y, z) { try { return __SRB__.deleteRail(world, x|0, y|0, z|0); } catch(e){ return false; } };\n")
                sb.append("  deleteRailRP = function(world, rp) { return deleteRail(world, rp.blockX, rp.blockY, rp.blockZ); };\n")
                sb.append("  setBlock = function(world, x, y, z, block, meta, flag) { try { return __SRB__.placeSupportBlock(world, x|0, y|0, z|0); } catch(e){ return false; } };\n")
            }
            sb.append("} catch(e){} })();\n")
            return script + sb.toString()
        }

        private fun injectScriptCompatibility(scriptEngine: ScriptEngine, renderer: ScriptModelRenderer) {
            try {
                val oldRoot = "n" + "gt"
                val oldLibRoot = oldRoot + "lib"
                val oldVehicleRoot = "r" + "tm"
                val oldCoreName = "R" + "T" + "MCore"
                val oldClientUtilName = "N" + "G" + "TUtilClient"
                val oldUtilName = "N" + "G" + "TUtil"
                val oldLogName = "N" + "G" + "TLog"
                val oldFileLoaderName = "N" + "G" + "TFileLoader"
                val oldTessellatorName = "N" + "G" + "TTessellator"
                val coreCompat = ScriptCoreCompat()
                scriptEngine.put("renderer", renderer)
                scriptEngine.put("model", renderer.getModel())
                // GL11 シム
                scriptEngine.put("GL11", GL11Compat(renderer))
                scriptEngine.put("Parts", PartsBuilder::class.java)
                // NGTText / NGTLog 等の頻出ユーティリティは Java オブジェクトで直接注入する。
                // ただし scriptEngine.put("NGTText", ...) だけだと、ユーザースクリプトの
                //   importPackage(Packages.jp.ngt.ngtlib.io)
                // で global の NGTText が JavaPackage に上書きされる事例がある。
                // 対策: 衝突しないアンダースコア名で put し、後段の eval ブロックで
                //   var NGTText = __RTMU_NGTText__;
                // を実行することで、ユーザースクリプトの importPackage より先に
                // global var として確立しておく (var 宣言は importPackage よりも優先)。
                val ngtText = NGTTextCompat(scriptEngine)
                val ngtLog = NGTLogCompat()
                val ngtUtil = NGTUtilCompat()
                val ngtMath = NGTMathCompat()
                scriptEngine.put("__RTMU_NGTText__", ngtText)
                scriptEngine.put("__RTMU_NGTLog__", ngtLog)
                scriptEngine.put("__RTMU_NGTUtil__", ngtUtil)
                scriptEngine.put("__RTMU_NGTMath__", ngtMath)
                // 直接名前でも put (importPackage を no-op 化済みなので衝突なし)
                scriptEngine.put("NGTText", ngtText)
                scriptEngine.put("NGTLog", ngtLog)
                scriptEngine.put("NGTUtil", ngtUtil)
                scriptEngine.put("NGTMath", ngtMath)
                // ScriptCore を互換オブジェクトとしてバインド
                scriptEngine.put("ScriptCoreJava", coreCompat)
                scriptEngine.put("ScriptUtilJava", ScriptUtilCompat())
                // SRB3 等のキー駆動 GUI 用に LWJGL2 Keyboard.isKeyDown を実キー入力へ橋渡し(クライアントのみ)。
                // サーバ(クライアントクラス不在)では NoClassDefFoundError を握りつぶして false 動作にフォールバック。
                try {
                    scriptEngine.put("__RTMU_KEY__", ScriptKeyboardCompat())
                    scriptEngine.put("__RTMU_MC__", ScriptClientCompat())
                } catch (ignored: Throwable) {
                    // bind されなければ Keyboard/MC 系は null/false を返す(従来動作)。
                }
                // SuperRailBuilder3 のレール敷設/削除を RTMU ネイティブへ橋渡しするブリッジ。
                scriptEngine.put("__SRB__", SrbRailBridge())
                try {
                    scriptEngine.eval("load('nashorn:mozilla_compat.js');")
                } catch (ignored: Exception) {
                    RealTrainModRenewed.LOGGER.debug("mozilla_compat.js not available for current JS engine.")
                }
                scriptEngine.eval(
                    "var __trainCoreCompat = { VERSION: " + quoteJs(SCRIPT_CORE_VERSION) + ", getVERSION: function() { return this.VERSION; }, getVersion: function() { return this.VERSION; } };\n" +
                            "ScriptCore = __trainCoreCompat;\n" +
                            oldCoreName + " = __trainCoreCompat;\n" +  // RTMU 互換: Java 側で put した __RTMU_xxx__ を、グローバル var として NGTText/NGTLog/NGTUtil/NGTMath に束ねる。
                            // var 宣言なので、後段の typeof チェック (undefined のとき JS スタブで上書き) より前に存在し、
                            // かつ importPackage が no-op 化された後でも安定して残る。
                            "if (typeof __RTMU_NGTText__ !== 'undefined') var NGTText = __RTMU_NGTText__;\n" +
                            "if (typeof __RTMU_NGTLog__ !== 'undefined') var NGTLog = __RTMU_NGTLog__;\n" +
                            "if (typeof __RTMU_NGTUtil__ !== 'undefined') var NGTUtil = __RTMU_NGTUtil__;\n" +
                            "if (typeof __RTMU_NGTMath__ !== 'undefined') var NGTMath = __RTMU_NGTMath__;\n" +
                            "importPackage = function(pkg) {};\n" +
                            "importClass = function(pkg) {};\n" +
                            "JavaImporter = function() { return {}; };\n" +
                            "if (typeof java === 'undefined' && typeof Packages !== 'undefined') java = Packages.java;\n" +
                            "if (typeof Packages === 'undefined' && typeof java !== 'undefined') Packages = java;\n" +
                            "if (typeof Packages === 'undefined') Packages = {};\n" +
                            "if (typeof Packages.org === 'undefined') Packages.org = {};\n" +
                            "if (typeof Packages.org.lwjgl === 'undefined') Packages.org.lwjgl = {};\n" +
                            "if (typeof Packages.org.lwjgl.opengl === 'undefined') Packages.org.lwjgl.opengl = {};\n" +
                            "if (typeof Packages.jp === 'undefined') Packages.jp = {};\n" +
                            "if (typeof Packages.jp.legacy === 'undefined') Packages.jp.legacy = {};\n" +
                            "if (typeof Packages.jp.legacy.legacylib === 'undefined') Packages.jp.legacy.legacylib = {};\n" +
                            "if (typeof Packages.jp.legacy.legacylib.math === 'undefined') Packages.jp.legacy.legacylib.math = {};\n" +
                            "if (typeof Packages.jp.legacy.legacylib.renderer === 'undefined') Packages.jp.legacy.legacylib.renderer = {};\n" +
                            "if (typeof Packages.jp.legacy.legacylib.renderer.GLHelper === 'undefined') Packages.jp.legacy.legacylib.renderer.GLHelper = { disableLighting: function() {}, enableLighting: function() {}, setBrightness: function(v) {}, setLightmapMaxBrightness: function() {} };\n" +
                            "if (typeof Packages.jp.legacy.legacylib.renderer.model === 'undefined') Packages.jp.legacy.legacylib.renderer.model = {};\n" +
                            "if (typeof Packages.jp.legacy.legacy === 'undefined') Packages.jp.legacy.legacy = {};\n" +
                            "if (typeof Packages.jp.legacy.legacy.render === 'undefined') Packages.jp.legacy.legacy.render = {};\n" +
                            "if (typeof Packages.jp.legacy.legacy.entity === 'undefined') Packages.jp.legacy.legacy.entity = {};\n" +
                            "if (typeof Packages.jp.legacy.legacy.entity.train === 'undefined') Packages.jp.legacy.legacy.entity.train = {};\n" +
                            "if (typeof Packages.jp.legacy.legacy.entity.train.util === 'undefined') Packages.jp.legacy.legacy.entity.train.util = {};\n" +
                            "if (typeof Packages.jp.legacy.legacy.train === 'undefined') Packages.jp.legacy.legacy.train = {};\n" +
                            "if (typeof Packages.jp['" + oldRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "'] = {};\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'] = {};\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math = Packages.jp.legacy.legacylib.math;\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer = Packages.jp.legacy.legacylib.renderer;\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper = Packages.jp.legacy.legacylib.renderer.GLHelper;\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model = Packages.jp.legacy.legacylib.renderer.model;\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'] = {};\n" +
                            "Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'][" + quoteJs(oldCoreName) + "] = " + oldCoreName + ";\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack = {};\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager = { INSTANCE: { getResource: function(domain, path) { return { domain: domain, path: path, func_110624_b: function() { return domain; }, func_110623_a: function() { return path; } }; } } };\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].render === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].render = Packages.jp.legacy.legacy.render;\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].entity === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].entity = Packages.jp.legacy.legacy.entity;\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].train === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].train = Packages.jp.legacy.legacy.train;\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io = {};\n" +
                            "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util = {};\n" +
                            "var " + oldClientUtilName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util[" + quoteJs(
                        oldClientUtilName
                    ) + "] = { bindTexture: function(texture) { renderer.bindTexture(texture); } };\n" +
                            "var " + oldUtilName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util[" + quoteJs(
                        oldUtilName
                    ) + "] = { getUniqueId: function() { return new Date().getTime(); } };\n" +
                            "var " + oldLogName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io[" + quoteJs(
                        oldLogName
                    ) + "] = { debug: function(v) {}, info: function(v) {}, warn: function(v) {}, error: function(v) {} };\n" +
                            "if (typeof load === 'undefined') load = function(path) {};\n" +
                            "if (typeof Java === 'undefined') Java = {};\n" +
                            "if (typeof global === 'undefined' && typeof globalThis !== 'undefined') global = globalThis;\n" +
                            "if (typeof self === 'undefined' && typeof globalThis !== 'undefined') self = globalThis;\n" +
                            "if (typeof console === 'undefined') console = { log: function() {}, info: function() {}, warn: function() {}, error: function() {}, debug: function() {} };\n" +
                            "if (typeof print === 'undefined') print = function() {};\n" +
                            "var __ptOriginalJavaFrom = (typeof Java.from === 'function') ? Java.from : null;\n" +
                            "function __ptJavaFrom(value) { if (value == null) return []; if (Array.isArray && Array.isArray(value)) return Array.prototype.slice.call(value); if (Object.prototype.toString.call(value) === '[object Array]') return Array.prototype.slice.call(value); if (typeof value.length === 'number') { try { return Array.prototype.slice.call(value); } catch (e) {} } if (typeof value.toArray === 'function') return Array.prototype.slice.call(value.toArray()); if (__ptOriginalJavaFrom) { try { return __ptOriginalJavaFrom(value); } catch (e) {} } return [value]; }\n" +
                            "Java.from = __ptJavaFrom;\n" +
                            "var ScriptUtil = {\n" +
                            "  doScript: function(script) { return ScriptUtilJava.doScript(String(script || '')); },\n" +
                            "  doScriptFunction: function(se, func, args) { return ScriptUtilJava.doScriptFunction(se, String(func || ''), __ptJavaFrom(args)); },\n" +
                            "  doScriptIgnoreError: function(se, func, args) { return ScriptUtilJava.doScriptIgnoreError(se, String(func || ''), __ptJavaFrom(args)); },\n" +
                            "  getScriptField: function(se, field) { return ScriptUtilJava.getScriptField(se, String(field || '')); }\n" +
                            "};\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io.ScriptUtil = ScriptUtil; } catch (e) {}\n" +  // Vec3 は LEGACY_API_PREPEND の Vec3Impl で .sub/.add 等を含めて定義する。inject側では一切宣言しない。
                            "" +
                            "var VecAccuracy = { LOW: 0, MEDIUM: 1, HIGH: 2 };\n" +
                            "var ModelLoader = { loadModel: function(resource, accuracy, options) { return { renderAll: function() {}, renderOnly: function() {}, renderPart: function() {}, objects: [] }; } };\n" +
                            "var ModelPackManager = { INSTANCE: { getResource: function(domain, path) { return { domain: domain, path: path, func_110624_b: function() { return domain; }, func_110623_a: function() { return path; } }; } } };\n" +
                            "var TrainState = { getStateType: function(value) { return value; }, suggestState: function(value, fallback) { return value == null ? fallback : value; } };\n" +
                            "TrainState.TrainStateType = { Reverser: 0, Notch: 1, Rail: 2, Door: 4, Light: 5, Pantograph: 6, ChunkLoader: 7, Destination: 8, Sound: 9, Interior: 11 };\n" +
                            "var RenderPass = {\n" +
                            "  NORMAL: { id: 0 },\n" +
                            "  TRANSPARENT: { id: 1 },\n" +
                            "  LIGHT: { id: 2 },\n" +
                            "  LIGHT_FRONT: { id: 2 },\n" +
                            "  LIGHT_BACK: { id: 2 },\n" +
                            "  OUTLINE: { id: 3 },\n" +
                            "  PICK: { id: 4 }\n" +
                            "};\n" +
                            "var TessellatorCompat = { instance: { startDrawingQuads: function() { renderer.tessellatorStart(); }, startDrawing: function(mode) { renderer.tessellatorStart(); }, addVertex: function(x, y, z) { renderer.tessellatorAddVertex(x, y, z); }, addVertexWithUV: function(x, y, z, u, v) { renderer.tessellatorAddVertexWithUV(x, y, z, u, v); }, setColorRGBA_F: function(r, g, b, a) { renderer.tessellatorSetColor(r, g, b, a); }, setColorRGBA: function(r, g, b, a) { renderer.tessellatorSetColor((r || 0) / 255.0, (g || 0) / 255.0, (b || 0) / 255.0, (a || 0) / 255.0); }, setColorOpaque_F: function(r, g, b) { renderer.tessellatorSetColor(r, g, b, 1.0); }, setColorOpaque: function(r, g, b) { renderer.tessellatorSetColor((r || 0) / 255.0, (g || 0) / 255.0, (b || 0) / 255.0, 1.0); }, setNormal: function(x, y, z) { renderer.tessellatorSetNormal(x, y, z); }, draw: function() { renderer.tessellatorDraw(); } }, getInstance: function() { return this.instance; } };\n" +
                            "var NGTTessellator = TessellatorCompat;\n" +
                            "var Tessellator = TessellatorCompat;\n" +
                            "var GLHelper = { disableLighting: function() { renderer.disableLighting(); }, enableLighting: function() { renderer.enableLighting(); }, setBrightness: function(v) { renderer.setBrightness(v); }, setLightmapMaxBrightness: function() { renderer.setLightmapMaxBrightness(); }, preMoveTexUV: function(u, v) { renderer.setUvOffset(u, v); }, postMoveTexUV: function() { renderer.clearUvOffset(); } };\n" +
                            "var NGTMath = {\n" +
                            "  toRadians: function(deg) { return deg * Math.PI / 180; },\n" +
                            "  toDegrees: function(rad) { return rad * 180 / Math.PI; },\n" +
                            "  getSin: function(rad) { return Math.sin(rad); },\n" +
                            "  getCos: function(rad) { return Math.cos(rad); },\n" +
                            "  getAtan2: function(y, x) { return Math.atan2(y, x); },\n" +
                            "  atan2: function(y, x) { return Math.atan2(y, x); },\n" +
                            "  normalizeAngle: function(deg) { return ((deg % 360) + 360) % 360; },\n" +
                            "  clampAngle: function(deg, min, max) { return Math.max(min, Math.min(max, deg)); },\n" +
                            "  lerp: function(a, b, t) { return a + (b - a) * t; }\n" +
                            "};\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math.NGTMath = NGTMath; } catch (e) {}\n" +
                            "var IdentifierCompat = function(domain, path) { if (path === undefined) { var s = String(domain || ''); var i = s.indexOf(':'); this.domain = i >= 0 ? s.substring(0, i) : 'minecraft'; this.path = i >= 0 ? s.substring(i + 1) : s; } else { this.domain = domain || 'minecraft'; this.path = path || ''; } this.namespace = this.domain; this.resourcePath = this.path; this.func_110624_b = function() { return this.domain; }; this.func_110623_a = function() { return this.path; }; this.getNamespace = function() { return this.domain; }; this.getPath = function() { return this.path; }; this.toString = function() { return this.domain + ':' + this.path; }; };\n" +
                            "var Identifier = IdentifierCompat;\n" +  // LWJGL2 Keyboard stub (imported by some RTM packs via importPackage(Packages.org.lwjgl.input))
                            "if (typeof Keyboard === 'undefined') Keyboard = { KEY_ESCAPE: 1, KEY_O: 24, KEY_L: 38, KEY_Q: 16, KEY_I: 23, KEY_P: 25, KEY_U: 22, KEY_J: 36, KEY_K: 37, KEY_F: 33, KEY_G: 34, KEY_H: 35, KEY_C: 46, KEY_V: 47, KEY_B: 48, KEY_N: 49, KEY_M: 50, KEY_RIGHT: 205, KEY_LEFT: 203, KEY_UP: 200, KEY_DOWN: 208, KEY_HOME: 199, KEY_END: 207, KEY_INSERT: 210, KEY_DELETE: 211, KEY_LBRACKET: 26, KEY_RBRACKET: 27, KEY_RETURN: 28, KEY_SPACE: 57, KEY_LSHIFT: 42, KEY_LCONTROL: 29, KEY_RCONTROL: 157, isKeyDown: function(key) { try { return (typeof __RTMU_KEY__ !== 'undefined' && __RTMU_KEY__) ? __RTMU_KEY__.isKeyDown(key) : false; } catch (e) { return false; } } };\n" +
                            "function __rtmuMcShim() { return { field_71462_r: ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getCurrentScreen() : null), func_135016_M: function() { return { func_135041_c: function() { return { func_135034_a: function() { return ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getLanguageCode() : 'en_us'); } }; } }; } }; }\n" +
                            "if (typeof MCWrapperClient === 'undefined') MCWrapperClient = { getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, getMinecraft: function() { return __rtmuMcShim(); }, bindTexture: function(texture) { if (typeof renderer !== 'undefined' && renderer) renderer.bindTexture(texture); } };\n" +
                            "if (typeof NGTUtilClient === 'undefined') NGTUtilClient = { getMinecraft: function() { return __rtmuMcShim(); }, getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, bindTexture: function(texture) { if (typeof renderer !== 'undefined' && renderer) renderer.bindTexture(texture); } };\n" +
                            "if (typeof NGTLog === 'undefined') NGTLog = { debug: function() {}, info: function() {}, warn: function() {}, error: function() {} };\n" +
                            "if (typeof GuiChat === 'undefined') GuiChat = function() {};\n" +
                            "if (typeof Minecraft === 'undefined') Minecraft = { func_71410_x: function() { return null; }, getMinecraft: function() { return null; } };\n" +  // --- SuperRailBuilder3 等の 1.12.2 RTM スクリプト互換グローバル ---
                            // 本家 RTM の Java クラスは 1.21.1 に存在しないため、スクリプトが eval 時に
                            // 参照するトップレベルのグローバルを最小限スタブする。
                            "if (typeof RTMCore === 'undefined') RTMCore = { VERSION: 'RTMU-1.21.1', MODID: 'rtm' };\n" +
                            "if (typeof Blocks === 'undefined') Blocks = {};\n" +
                            "if (Blocks.field_150325_L === undefined) Blocks.field_150325_L = { __rtmuBlock: 'minecraft:white_wool' };\n" +
                            "if (typeof LoaderCompat === 'undefined') LoaderCompat = { isModLoaded: function(n) { return false; } };\n" +
                            "if (typeof BlockUtil === 'undefined') BlockUtil = { setBlock: function() {} };\n" +
                            "function __ptDummyTextureData() { return { images: [{}], size: 1, rate: 1, width: 1, height: 1 }; }\n" +
                            "function " + oldFileLoaderName + "_getInputStream(resource) { return null; }\n" +
                            "var " + oldFileLoaderName + " = { getInputStream: " + oldFileLoaderName + "_getInputStream };\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io[" + quoteJs(oldFileLoaderName) + "] = " + oldFileLoaderName + "; } catch (e) {}\n" +
                            "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                            "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math.Vec3 = Vec3; } catch (e) {}\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model.VecAccuracy = VecAccuracy; } catch (e) {}\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model.ModelLoader = ModelLoader; } catch (e) {}\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer[" + quoteJs(
                        oldTessellatorName
                    ) + "] = TessellatorCompat; } catch (e) {}\n" +
                            "try { Packages.jp.legacy.legacylib.renderer.NGTTessellator = TessellatorCompat; } catch (e) {}\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager = ModelPackManager; } catch (e) {}\n" +
                            "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper = GLHelper; } catch (e) {}\n" +
                            "try { renderer.renderer = renderer; } catch (e) {}\n" +
                            "var GL11 = {\n" +
                            "  glPushMatrix: function() { renderer.pushMatrix(); },\n" +
                            "  glPopMatrix: function() { renderer.popMatrix(); },\n" +
                            "  glTranslatef: function(x, y, z) { renderer.translate(x, y, z); },\n" +
                            "  glRotatef: function(angle, x, y, z) { renderer.rotate(angle, x, y, z); },\n" +
                            "  glScalef: function(x, y, z) { renderer.scale(x, y, z); },\n" +
                            "  glColor4f: function(r, g, b, a) { renderer.setColor(r, g, b, a); },\n" +
                            "  glColor3f: function(r, g, b) { renderer.setColor(r, g, b, 1.0); },\n" +
                            "  glEnable: function(cap) {},\n" +
                            "  glDisable: function(cap) {},\n" +
                            "  glBlendFunc: function(src, dst) {},\n" +
                            "  glAlphaFunc: function(func, ref) {},\n" +
                            "  glDepthMask: function(flag) {}\n" +
                            "};\n" +
                            "function __joinGroups(g) { return (g && typeof g.join === 'function') ? g.join(',') : g; }\n" +  // groupsStr is a pre-serialised comma-joined string stored on the object so that
                            // Java registerParts() can read the group list via getMember("groupsStr") even when
                            // the GraalJS JSR-223 object is opaque to normal Java reflection.
                            "function Parts() {\n" +
                            "  this.groups = Array.prototype.slice.call(arguments);\n" +
                            "  this.groupsStr = __joinGroups(this.groups);\n" +  // groupsStr を毎フレーム再計算せず、初回構築時の固定文字列をそのまま渡す。
                            // Java 側は同じ文字列インスタンスで来ればキャッシュ済みの解析結果を返せる。
                            "  this.render = function(renderer) { if (renderer) { try { renderer.renderParts(this.groupsStr); } catch(e) {} } };\n" +
                            "  this.getObjects = function(model) { return renderer ? renderer.getScriptModelObjects(this.groupsStr) : []; };\n" +
                            "  this.containsName = function(name) { return this.groups.indexOf(name) >= 0; };\n" +
                            "}\n" +
                            "function ModelParts() {\n" +
                            "  this.groups = Array.prototype.slice.call(arguments);\n" +
                            "  this.groupsStr = __joinGroups(this.groups);\n" +
                            "  this.render = function(renderer) { if (renderer) { try { renderer.renderParts(__joinGroups(this.groups)); } catch(e) {} } };\n" +
                            "  this.getObjects = function(model) { return renderer ? renderer.getScriptModelObjects(this.groupsStr) : []; };\n" +
                            "  this.containsName = function(name) { return this.groups.indexOf(name) >= 0; };\n" +
                            "}\n" +
                            "function ActionParts(type) {\n" +
                            "  this.groups = Array.prototype.slice.call(arguments, 1);\n" +
                            "  this.groupsStr = __joinGroups(this.groups);\n" +
                            "  this.render = function(renderer) { if (renderer) { try { renderer.renderParts(__joinGroups(this.groups)); } catch(e) {} } };\n" +
                            "  this.getObjects = function(model) { return renderer ? renderer.getScriptModelObjects(this.groupsStr) : []; };\n" +
                            "  this.containsName = function(name) { return this.groups.indexOf(name) >= 0; };\n" +
                            "}\n" +
                            "var PartsRenderer = renderer;\n" +
                            "var ModelRenderer = renderer;\n" +
                            "function __ptNoopPart() { return { render: function() {}, renderLight: function() {}, setOption: function() {}, addEntriesSet: function() {}, addMotionData: function() {}, addState: function() {}, getDoorState: function() { return false; }, getDoorPosZ: function() { return 0; }, getFlashState: function() { return false; } }; }\n" +
                            "var ActionType = { DRAG_X: 0, DRAG_Y: 1, DRAG_Z: 2, ROTATE_X: 3, ROTATE_Y: 4, ROTATE_Z: 5, TOGGLE: 6 };\n" +
                            "var Axis = { NONE: 0, POSITIVE_X: 1, NEGATIVE_X: 2, POSITIVE_Y: 3, NEGATIVE_Y: 4, POSITIVE_Z: 5, NEGATIVE_Z: 6 };\n" +
                            "if (typeof NGTRenderer === 'undefined') NGTRenderer = { renderFire: function() {}, renderDesktop: function() {}, renderWebCamera: function() {}, renderTweet: function() {}, renderEBB: function() {}, renderPicture: function() {}, renderMap: function() {}, renderLightEffect: function() {}, renderFlame: function() {}, renderPortal: function() {} };\n" +
                            "try { Packages.jp['" + "ngt" + "']['" + "ngtlib" + "'].renderer.NGTRenderer = NGTRenderer; } catch(e) {}\n" +
                            "try { Packages.jp.legacy.legacylib.renderer.NGTRenderer = NGTRenderer; } catch(e) {}\n" +
                            "if (typeof BlockScaffold === 'undefined') BlockScaffold = { getConnectionType: function() { return 0; } };\n" +
                            "if (typeof BlockScaffoldStairs === 'undefined') BlockScaffoldStairs = { getConnectionType: function() { return 0; } };\n" +
                            "if (typeof MCWrapperClient === 'undefined') MCWrapperClient = { getPlayer: function() { return null; }, bindTexture: function() {} };\n" +
                            "MCWrapperClient.playSound = function(domain, name, volume, pitch) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.playSound(domain, name, volume == null ? 1.0 : volume, pitch == null ? 1.0 : pitch); };\n" +  // NGTText: Packages プロキシで undefined ではなくなる場合があるので、readText が
                            "MCWrapperClient.playSoundAtRange = function(domain, name, volume, pitch, range) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.playSoundAtRange(domain, name, volume == null ? 1.0 : volume, pitch == null ? 1.0 : pitch, range == null ? 16.0 : range); };\n" +
                            // 関数として呼べるかも確認して、無理なら自前のスタブで上書きする。
                            "if (typeof NGTText === 'undefined' || typeof NGTText.readText !== 'function') {\n" +
                            "  NGTText = { createText: function() { return ''; }, getText: function() { return ''; }, getFormattedText: function() { return ''; }, getString: function() { return ''; }, appendSibling: function() {}, appendText: function() {}, applyTextStyles: function() {}, readText: function() { return ''; }, writeText: function() {}, loadText: function() { return ''; } };\n" +
                            "}\n" +
                            "if (typeof NGTSound === 'undefined') NGTSound = {};\n" +
                            "NGTSound.playSound = function(domain, name, volume, pitch) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.playSound(domain, name, volume == null ? 1.0 : volume, pitch == null ? 1.0 : pitch); };\n" +
                            "NGTSound.playSoundAtRange = function(domain, name, volume, pitch, range) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.playSoundAtRange(domain, name, volume == null ? 1.0 : volume, pitch == null ? 1.0 : pitch, range == null ? 16.0 : range); };\n" +
                            "NGTSound.playSoundRange = NGTSound.playSoundAtRange;\n" +
                            "NGTSound.stopSound = function(domain, name) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.stopSound(domain, name); };\n" +
                            "NGTSound.setSoundVolume = function(domain, name, volume) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.setSoundVolume(domain, name, volume == null ? 0.0 : volume); };\n" +
                            "NGTSound.setSoundPitch = function(domain, name, pitch) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.setSoundPitch(domain, name, pitch == null ? 1.0 : pitch); };\n" +
                            "NGTSound.setSoundRange = function(domain, name, range) { if (typeof __RTMU_SoundBridge__ !== 'undefined') __RTMU_SoundBridge__.setSoundRange(domain, name, range == null ? 16.0 : range); };\n" +
                            "if (typeof BlockHandler === 'undefined') BlockHandler = { getBlock: function() { return null; }, getTileEntity: function() { return null; } };\n" +  // SoundState: ANSL系スクリプトで使われるサウンド状態クラス
                            "if (typeof SoundState === 'undefined') SoundState = function(su, trackData) {\n" +
                            "  this.su = su; this.trackData = trackData || {};\n" +
                            "  this.isPlaying = false; this.volume = 0; this.pitch = 1;\n" +
                            "  this.update = function() {}; this.play = function() {}; this.stop = function() {};\n" +
                            "  this.setVolume = function(v) { this.volume = v; }; this.setPitch = function(p) { this.pitch = p; };\n" +
                            "  this.getVolume = function() { return this.volume; }; this.getPitch = function() { return this.pitch; };\n" +
                            "  this.isActive = function() { return this.isPlaying; };\n" +
                            "};\n" +  // 未知クラスアクセスを無音化するProxyラッパーを Packages の末端に適用
                            "if (typeof Proxy !== 'undefined') {\n" +
                            "  var __ptNoopCls = function() { return new Proxy(function(){}, { get: function(t,p) { if (p === 'prototype') return {}; if (typeof p === 'string') return function() { return null; }; return undefined; }, apply: function() { return null; }, construct: function() { return {}; } }); };\n" +
                            "  var __ptPkgProxy = function(base) {\n" +
                            "    return new Proxy(base || {}, {\n" +
                            "      get: function(t, p) {\n" +
                            "        if (p === 'then' || p === Symbol.toPrimitive) return undefined;\n" +
                            "        var v = t[p];\n" +
                            "        if (v !== undefined) return v;\n" +
                            "        if (typeof p === 'string') {\n" +
                            "          var first = p.charAt(0);\n" +
                            "          if (first >= 'A' && first <= 'Z') return __ptNoopCls();\n" +
                            "          return __ptPkgProxy({});\n" +
                            "        }\n" +
                            "        return undefined;\n" +
                            "      }\n" +
                            "    });\n" +
                            "  };\n" +
                            "  if (typeof Packages !== 'undefined') Packages = __ptPkgProxy(Packages);\n" +
                            "}\n"
                )

                // model.body_f など任意グループ名アクセスをサポートするProxyラッパー
                // RTMスクリプトは model.body_f を直接アクセスするが、ScriptModelはJavaフィールドを持たないため
                // GraalJS Proxyでインターセプトし、グループ名文字列を持つアクセサオブジェクトを返す
                scriptEngine.eval(
                    "if (typeof Proxy !== 'undefined' && typeof model !== 'undefined' && model !== null) {\n" +
                            "  var __ptGA = function(n) {\n" +
                            "    return new Proxy({ groupsStr: n }, {\n" +
                            "      get: function(t, p) {\n" +
                            "        if (p === 'groupsStr') return n;\n" +
                            "        if (p === 'toString') return function() { return n; };\n" +
                            "        if (p === 'valueOf') return function() { return n; };\n" +
                            "        if (p === 'then') return undefined;\n" +
                            "        if (p === 'render') return function(r) { if (r && typeof r.renderParts === 'function') { try { r.renderParts(n); } catch(e) {} } };\n" +
                            "        if (typeof p === 'string' && !p.startsWith('__') && p !== 'constructor') return __ptGA(p);\n" +
                            "        return t[p];\n" +
                            "      },\n" +
                            "      has: function(t, p) { return true; }\n" +
                            "    });\n" +
                            "  };\n" +
                            "  var __ptOM = model;\n" +
                            "  model = new Proxy({}, {\n" +
                            "    get: function(t, n) {\n" +
                            "      if (typeof n !== 'string') return undefined;\n" +
                            "      if (n === 'then') return undefined;\n" +
                            "      try { var v = __ptOM[n]; if (v !== null && v !== undefined) return v; } catch(e) {}\n" +
                            "      return __ptGA(n);\n" +
                            "    },\n" +
                            "    has: function(t, n) { return true; }\n" +
                            "  });\n" +
                            "}\n"
                )

                // scripts that expect 1.12-style methods will call these against entity
                scriptEngine.eval(
                    "if (typeof __legacy_compat_once === 'undefined') {\n" +
                            "  __legacy_compat_once = true;\n" +
                            "  function __safeCall(obj, fn, d) { try { return (obj && typeof obj[fn] === 'function') ? obj[fn]() : d; } catch (e) { return d; } }\n" +
                            "  var ScriptCondition = {\n" +
                            "    count: function(executer) { try { return executer && typeof executer.getCount === 'function' ? executer.getCount() : (executer && executer.count ? executer.count : 0); } catch (e) { return 0; } },\n" +
                            "    once: function(executer) { try { return !!(executer && typeof executer.once === 'function' ? executer.once() : this.count(executer) <= 0); } catch (e) { return false; } },\n" +
                            "    every: function(executer, interval) { interval = Math.max(1, interval | 0); try { return !!(executer && typeof executer.every === 'function' ? executer.every(interval) : (this.count(executer) % interval) === 0); } catch (e) { return false; } },\n" +
                            "    between: function(executer, start, endExclusive) { try { return !!(executer && typeof executer.between === 'function' ? executer.between(start, endExclusive) : (this.count(executer) >= start && (endExclusive < 0 || this.count(executer) < endExclusive))); } catch (e) { return false; } },\n" +
                            "    times: function(executer, maxCount) { try { return !!(executer && typeof executer.times === 'function' ? executer.times(maxCount) : (maxCount < 0 || this.count(executer) < maxCount)); } catch (e) { return false; } }\n" +
                            "  };\n" +
                            "}\n"
                )
            } catch (e: ScriptException) {
                RealTrainModRenewed.LOGGER.error("Failed to inject script compatibility helpers", e)
            }
        }

        private fun prepareScriptRuntimeBeforeInit(scriptEngine: ScriptEngine) {
            try {
                scriptEngine.eval(
                    "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                            "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                            "if (typeof __ptNoopPart !== 'function') __ptNoopPart = function() { return { render: function() {}, renderLight: function() {}, setOption: function() {}, addEntriesSet: function() {}, addMotionData: function() {}, addState: function() {}, getDoorState: function() { return false; }, getDoorPosZ: function() { return 0; }, getFlashState: function() { return false; } }; };\n" +
                            "if (typeof __ptDummyTextureData !== 'function') __ptDummyTextureData = function() { return { images: [{}], size: 1, rate: 1, width: 1, height: 1 }; };\n" +
                            "if (typeof playComplessorSound !== 'function') {\n" +
                            "  playComplessorSound = function(su, soundDomain, soundName) {\n" +
                            "    if (!su) return;\n" +
                            "    if (su.isComplessorActive && su.isComplessorActive()) {\n" +
                            "      var count = su.complessorCount ? su.complessorCount() : 0;\n" +
                            "      var c0 = 50;\n" +
                            "      var vol = 1.0;\n" +
                            "      if (count < c0) { var c1 = c0 * c0; vol = -((((count - c0) * (count - c0)) + c1) / c1); }\n" +
                            "      var pitch = count < c0 ? (vol * 0.5) + 0.5 : 1.0;\n" +
                            "      if (su.playSound) su.playSound(soundDomain, soundName, vol, pitch);\n" +
                            "    } else if (su.stopSound) {\n" +
                            "      su.stopSound(soundDomain, soundName);\n" +
                            "    }\n" +
                            "  };\n" +
                            "}\n" +
                            "if (typeof playCompressorSound !== 'function' && typeof playComplessorSound === 'function') playCompressorSound = playComplessorSound;\n" +
                            "if (typeof CustomTexture !== 'undefined') {\n" +
                            "  CustomTexture._load = function(path) {\n" +
                            "    var size = 1, width = 1, height = 1;\n" +
                            "    try { if (renderer && typeof renderer.getScriptTextureFrameCount === 'function') size = Math.max(1, renderer.getScriptTextureFrameCount('minecraft', String(path))); } catch (e) { size = path && String(path).toLowerCase().indexOf('.gif') >= 0 ? 64 : 1; }\n" +
                            "    try { if (renderer && typeof renderer.getScriptTextureWidth === 'function') width = Math.max(1, renderer.getScriptTextureWidth('minecraft', String(path))); } catch (e) {}\n" +
                            "    try { if (renderer && typeof renderer.getScriptTextureHeight === 'function') height = Math.max(1, renderer.getScriptTextureHeight('minecraft', String(path))); } catch (e) {}\n" +
                            "    var images = [];\n" +
                            "    for (var i = 0; i < size; i++) images.push(path);\n" +
                            "    return { images: images, size: size, rate: 8, width: width, height: height };\n" +
                            "  };\n" +
                            "  if (CustomTexture.prototype) {\n" +
                            "    CustomTexture.prototype.bindTexture = function(entity, frameIndex) { if (renderer && typeof renderer.bindScriptTexture === 'function') renderer.bindScriptTexture('minecraft', this.texturePath, frameIndex || 0); };\n" +
                            "    CustomTexture.prototype.bindDefaultTexture = function(renderer) { if (renderer && typeof renderer.clearScriptTexture === 'function') renderer.clearScriptTexture(); if (renderer && typeof renderer.clearUvWindow === 'function') renderer.clearUvWindow(); };\n" +
                            "    CustomTexture.prototype._uploadTexture = function(entity, bufferedImage) {};\n" +
                            "    CustomTexture.prototype._bindTextureChached = function(frameIndex, textureId) {};\n" +
                            "  }\n" +
                            "}\n"
                )
                scriptEngine.eval(
                    "if (typeof CustomAnimator !== 'undefined' && CustomAnimator.prototype && !CustomAnimator.prototype.__ptAnimatorFacesWrapped) {\n" +
                            "  CustomAnimator.prototype.__ptAnimatorFacesWrapped = true;\n" +
                            "  CustomAnimator.prototype.__ptOldRender = CustomAnimator.prototype.render;\n" +
                            "  CustomAnimator.prototype.setFacesFromParts = function(part) {\n" +
                            "    this.__ptParts = part;\n" +
                            "    this.preVertexList = [];\n" +
                            "    try { if (renderer && typeof renderer.markScriptManagedParts === 'function') renderer.markScriptManagedParts(part); } catch (e0) {}\n" +
                            "    if (!renderer || typeof renderer.getScriptQuadVertexLists !== 'function') return;\n" +
                            "    var faces = Java.from(renderer.getScriptQuadVertexLists(part));\n" +
                            "    for (var i = 0; i < faces.length; i++) {\n" +
                            "      var face = Java.from(faces[i]);\n" +
                            "      var v = [];\n" +
                            "      for (var j = 0; j < face.length; j++) {\n" +
                            "        var p = Java.from(face[j]);\n" +
                            "        v.push([+p[0], +p[1], +p[2]]);\n" +
                            "      }\n" +
                            "      if (v.length === 4) this.preVertexList.push(v);\n" +
                            "    }\n" +
                            "  };\n" +
                            "  CustomAnimator.prototype.render = function(renderer, entity, pass, isLit) {\n" +
                            "    if (renderer && typeof renderer.disableReplayCacheForFrame === 'function') renderer.disableReplayCacheForFrame();\n" +
                            "    return this.__ptOldRender ? this.__ptOldRender.apply(this, arguments) : undefined;\n" +
                            "  };\n" +
                            "}\n"
                )
                scriptEngine.eval(
                    "if (typeof CustomMonitor_LCD !== 'undefined') {\n" +
                            "  CustomMonitor_LCD = function(modelSet, modelObj, baseParts, texturePath) { this.baseParts = baseParts; this.texturePath = texturePath; this.gif = new CustomTexture(modelObj, texturePath); };\n" +
                            "  CustomMonitor_LCD.prototype = { constructor: CustomMonitor_LCD, render: function(renderer, entity, pass, partialTick) { if (!entity || pass > 2 || !this.baseParts) return; var id = 0; try { id = Math.floor(entity.getTrainStateData(8)); } catch (e) {} if (typeof lcdDisplaySet !== 'undefined' && lcdDisplaySet[id]) { var set = lcdDisplaySet[id]; var tick = 0; try { tick = renderer.getTick(entity); } catch (e) {} id = set[Math.floor((tick % (set.length * 200)) / 200)] || set[0] || id; } else { try { var frames = renderer.getScriptTextureFrameCount('minecraft', this.texturePath); var tick2 = renderer.getTick(entity); if (frames > 0) id = Math.floor(tick2 / 2) % frames; } catch (e2) {} } try { if (typeof renderer.setLightmapMaxBrightness === 'function') renderer.setLightmapMaxBrightness(); if (renderer && typeof renderer.renderGifOnParts === 'function') renderer.renderGifOnParts(this.baseParts, 'minecraft', this.texturePath, id); else { this.gif.bindTexture(entity, id); this.baseParts.render(renderer); } } finally { if (typeof renderer.enableLighting === 'function') renderer.enableLighting(); renderer.clearUvWindow(); renderer.clearScriptTexture(); } } };\n" +
                            "}\n"
                )
                scriptEngine.eval(
                    "if (typeof DoorRenderer !== 'undefined' && DoorRenderer.prototype && !DoorRenderer.prototype.__ptDoorUpdateWrapped) {\n" +
                            "  DoorRenderer.prototype.__ptDoorUpdateWrapped = true;\n" +
                            "  DoorRenderer.prototype._isUpdateTick = function(entity, pass, renderer) { if (!entity || pass !== 0) return false; var currentTick = renderer.getTick(entity); var key = 'prevTick_' + (entity.getUUID ? entity.getUUID() : 'entity'); var prevTick = this.hashMap.get(key); this.hashMap.put(key, currentTick); return prevTick !== currentTick; };\n" +
                            "  DoorRenderer.prototype._calcZPos = function(entity, pass, partialTick) { if (!entity || pass > 2) return 0; var m = 0; try { if (this.dir === DoorRenderer.dir.left && renderer && typeof renderer.getDoorMovementL === 'function') m = renderer.getDoorMovementL(entity); else if (this.dir === DoorRenderer.dir.right && renderer && typeof renderer.getDoorMovementR === 'function') m = renderer.getDoorMovementR(entity); else { var state = Math.floor(entity.getTrainStateData(4)); var open = this.dir === DoorRenderer.dir.left ? ((state & 2) === 2) : ((state & 1) === 1); m = open ? 1 : 0; } } catch (e) { m = 0; } if (m < 0) m = 0; if (m > 1) m = 1; var pos = this.moveMaxZ * m; var map = this.hashMap.get(entity) || new java.util.HashMap(); map.put('posZ', pos); map.put('cachedRenderPos', pos); this.hashMap.put(entity, map); return this.isInvertMove ? -pos : pos; };\n" +
                            "}\n"
                )
                scriptEngine.eval(
                    "if (typeof CustomMonitor_JRE_1 !== 'undefined') {\n" +
                            "  CustomMonitor_JRE_1 = function(modelSet, modelObj, baseParts) { this.baseParts = baseParts; this.hashMap = new java.util.HashMap(); };\n" +
                            "  CustomMonitor_JRE_1.prototype = { constructor: CustomMonitor_JRE_1, setOption: function() {}, render: function() {}, getHashMap: function(entity) { return this.hashMap.get(entity) || {}; }, setHashMapData: function(entity, key, value) { var data = this.getHashMap(entity); data[key] = value; this.hashMap.put(entity, data); }, getHashMapData: function(entity, key) { return this.getHashMap(entity)[key]; } };\n" +
                            "}\n" +
                            "if (typeof CustomMonitor_JRE_2 !== 'undefined') {\n" +
                            "  var __ptJre2EntrySet = CustomMonitor_JRE_2.EntrySet || {};\n" +
                            "  CustomMonitor_JRE_2 = function(modelSet, modelObj, baseParts) { this.baseParts = baseParts; this.entrySet = {}; this.hashMap = new java.util.HashMap(); };\n" +
                            "  CustomMonitor_JRE_2.EntrySet = __ptJre2EntrySet;\n" +
                            "  ['tc','mc1','mc2','t','tsd','m1','m2','m3','m4','m5','m6','m7','m8'].forEach(function(k) { if (!CustomMonitor_JRE_2.EntrySet[k]) CustomMonitor_JRE_2.EntrySet[k] = { iconF: 'entry_' + k, iconB: 'entry_' + k }; });\n" +
                            "  CustomMonitor_JRE_2.prototype = { constructor: CustomMonitor_JRE_2, addEntrySet: function(trainName, type, options) { this.entrySet[trainName] = { type: type || {}, options: options || {} }; }, addEntriesSet: function(trainNameList, type, options) { if (!trainNameList) return; for (var i = 0; i < trainNameList.length; i++) this.addEntrySet(trainNameList[i], type, options); }, setOption: function(options, entity) { var data = this.getHashMap(entity); data.options = options || {}; this.hashMap.put(entity, data); }, render: function() {}, getHashMap: function(entity) { return this.hashMap.get(entity) || {}; }, setHashMapData: function(entity, key, value) { var data = this.getHashMap(entity); data[key] = value; this.hashMap.put(entity, data); }, getHashMapData: function(entity, key) { return this.getHashMap(entity)[key]; } };\n" +
                            "}\n"
                )
                scriptEngine.eval(
                    "if (typeof CustomLightParts !== 'undefined' && CustomLightParts.prototype && !CustomLightParts.prototype.__ptLightModeWrapped) {\n" +
                            "  CustomLightParts.prototype.__ptLightModeWrapped = true;\n" +
                            "  CustomLightParts.prototype.__ptOldRenderLight = CustomLightParts.prototype.renderLight;\n" +
                            "  CustomLightParts.prototype.__ptOldRender = CustomLightParts.prototype.render;\n" +
                            "  CustomLightParts.prototype.__ptLightAllowed = function(entity) {\n" +
                            "    var mode = 0;\n" +
                            "    try { mode = Math.floor(entity.getTrainStateData(5)); } catch (e) {}\n" +
                            "    if (this.lightTextureSuffix === '_headLight') return mode === 1 || mode === 2 || mode === 3;\n" +
                            "    if (this.lightTextureSuffix === '_tailLight') return mode === 2 || mode === 3;\n" +
                            "    return true;\n" +
                            "  };\n" +
                            "  CustomLightParts.prototype.render = function(renderer, entity, pass, isObjectGlow) {\n" +
                            "    if (entity && isObjectGlow && !this.__ptLightAllowed(entity)) { if (this.parts && typeof this.parts.render === 'function') this.parts.render(renderer); return; }\n" +
                            "    return this.__ptOldRender.apply(this, arguments);\n" +
                            "  };\n" +
                            "  CustomLightParts.prototype.renderLight = function(renderer, entity, pass) {\n" +
                            "    if (!this.__ptLightAllowed(entity)) return;\n" +
                            "    return this.__ptOldRenderLight.apply(this, arguments);\n" +
                            "  };\n" +
                            "}\n"
                )
            } catch (e: ScriptException) {
                RealTrainModRenewed.LOGGER.warn("Failed to prepare script runtime before init", e)
            }
        }

        private fun prepareScriptRuntimeAfterInit(scriptEngine: ScriptEngine) {
            try {
                scriptEngine.eval(
                    "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                            "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                            "if (typeof __ptNoopPart === 'function') {\n" +
                            "  if (typeof lcd1 === 'undefined') lcd1 = __ptNoopPart();\n" +
                            "  if (typeof lcd2 === 'undefined') lcd2 = __ptNoopPart();\n" +
                            "  if (typeof monitor1 === 'undefined') monitor1 = __ptNoopPart();\n" +
                            "  if (typeof monitor2 === 'undefined') monitor2 = __ptNoopPart();\n" +
                            "  if (typeof timsMonitor === 'undefined') timsMonitor = __ptNoopPart();\n" +
                            "}\n"
                )
            } catch (e: ScriptException) {
                RealTrainModRenewed.LOGGER.warn("Failed to prepare script runtime after init", e)
            }
        }

        private fun quoteJs(value: String): String {
            return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
        }

        private fun invokeScriptInit(scriptEngine: ScriptEngine, renderer: ScriptModelRenderer) {
            val initModel = renderer.getModel()
            if (scriptEngine is Invocable) {
                try {
                    scriptEngine.invokeFunction("init", renderer, initModel)
                    return
                } catch (ignored: NoSuchMethodException) {
                    try {
                        scriptEngine.invokeFunction("init")
                        return
                    } catch (ignored2: NoSuchMethodException) {
                        // no init function with either signature
                    } catch (e: ScriptException) {
                        RealTrainModRenewed.LOGGER.error("Failed to invoke init(renderer, model) for model script", e)
                        return
                    } catch (e: RuntimeException) {
                        RealTrainModRenewed.LOGGER.warn(
                            "Old model script init failed; keeping script available for render fallback",
                            e
                        )
                        return
                    }
                } catch (e: ScriptException) {
                    RealTrainModRenewed.LOGGER.error("Failed to invoke init for model script", e)
                    return
                } catch (e: RuntimeException) {
                    RealTrainModRenewed.LOGGER.warn(
                        "Old model script init failed; keeping script available for render fallback",
                        e
                    )
                    return
                }
            }

            try {
                scriptEngine.eval("if (typeof init === 'function') init();")
            } catch (e: ScriptException) {
                RealTrainModRenewed.LOGGER.error("Failed to invoke init() fallback for model script", e)
            }
        }

        private fun isScriptDisabled(scriptEngine: ScriptEngine?): Boolean {
            return scriptEngine != null && DISABLED_SCRIPT_ENGINES.contains(System.identityHashCode(scriptEngine))
        }

        @JvmStatic
        fun isLegacyScriptDisabled(scriptEngine: ScriptEngine?): Boolean {
            return isScriptDisabled(scriptEngine)
        }

        private fun disableBrokenScript(scriptEngine: ScriptEngine?, phase: String?, error: Throwable?) {
            if (scriptEngine == null) {
                return
            }
            val key = System.identityHashCode(scriptEngine)
            val failures: Int = SCRIPT_FAILURE_COUNTS.merge(
                key,
                1
            ) { a: kotlin.Int?, b: kotlin.Int? -> java.lang.Integer.sum(a ?: 0, b ?: 0) }!!
            reportScriptError(scriptEngine, phase + " (" + failures + "/8)", error)
            if (failures >= 8 && DISABLED_SCRIPT_ENGINES.add(key)) {
                RealTrainModRenewed.LOGGER.warn(
                    "Disabling legacy train script after repeated {} failures",
                    phase,
                    error
                )
            }
        }

        private fun reportScriptError(scriptEngine: ScriptEngine?, phase: String?, error: Throwable?) {
            if (scriptEngine == null || error == null) {
                return
            }
            val rawScriptPath = scriptEngine.get(SCRIPT_PATH_KEY)
            val rawModelName = scriptEngine.get(SCRIPT_MODEL_KEY)
            val scriptPath: String? = if (rawScriptPath == null) "(unknown script)" else rawScriptPath.toString()
            val modelName = if (rawModelName == null) "" else rawModelName.toString()
            var detail = error.message
            if (detail == null || detail.isBlank()) {
                detail = error.javaClass.getSimpleName()
            }
            val summary = (scriptPath
                    + (if (modelName.isBlank()) "" else " [" + modelName + "]")
                    + " @ " + phase + " : " + detail)
            if (REPORTED_SCRIPT_ERRORS.add(summary)) {
                ClientHooks.showScriptErrorMessage(summary)
            }
        }

        /**
         * SRB3 等のサーバスクリプトを毎tick実行する用。
         * entity を `onUpdate(entity, scriptExecuter)` の形式で呼び出す。
         * scriptExecuter はスクリプト側で任意に使われる helper。現状は null を渡す。
         */
        @JvmStatic
        fun invokeServerScriptOnUpdate(scriptEngine: ScriptEngine?, entity: Any?) {
            if (scriptEngine == null || isScriptDisabled(scriptEngine)) return
            try {
                scriptEngine.put("entity", entity)
                scriptEngine.put("executer", null)
                scriptEngine.put("executor", null)
            } catch (ignored: Throwable) {
            }
            val invocable = scriptEngine as Invocable
            try {
                invocable.invokeFunction("onUpdate", entity, null)
                return
            } catch (ignored: NoSuchMethodException) {
            } catch (e: ScriptException) {
                disableBrokenScript(scriptEngine, "onUpdate(entity, executer) [server]", e)
                return
            } catch (t: Throwable) {
                disableBrokenScript(scriptEngine, "onUpdate(entity, executer) [server-runtime]", t)
                return
            }
            try {
                invocable.invokeFunction("onUpdate", entity)
            } catch (ignored: NoSuchMethodException) {
            } catch (e: ScriptException) {
                disableBrokenScript(scriptEngine, "onUpdate(entity) [server]", e)
            } catch (t: Throwable) {
                disableBrokenScript(scriptEngine, "onUpdate(entity) [server-runtime]", t)
            }
        }

        @JvmStatic
        fun invokeScriptTick(scriptEngine: ScriptEngine?, entity: Any?, soundScript: Boolean = false) {
            if (scriptEngine == null || isScriptDisabled(scriptEngine)) return
            val compat = if (entity is TrainEntity) LegacyScriptExecutor(entity) else null
            val soundBridge = LegacySoundBridge(compat)
            try {
                scriptEngine.put("executer", compat)
                scriptEngine.put("executor", compat)
                scriptEngine.put("__RTMU_SoundBridge__", soundBridge)
            } catch (ignored: Throwable) {
            }
            if (entity is TrainEntity) {
                try {
                    val frontList: MutableList<LegacyScriptExecutor?> = ArrayList<LegacyScriptExecutor?>()
                    var cur = entity.coupledLeader
                    while (cur != null && frontList.size < 64) {
                        frontList.add(LegacyScriptExecutor(cur))
                        cur = cur.coupledLeader
                    }
                    val rearList: MutableList<LegacyScriptExecutor?> = ArrayList<LegacyScriptExecutor?>()
                    cur = entity.coupledFollower
                    while (cur != null && rearList.size < 64) {
                        rearList.add(LegacyScriptExecutor(cur))
                        cur = cur.coupledFollower
                    }
                    scriptEngine.put("__rtmFrontArr", frontList.toTypedArray<LegacyScriptExecutor?>())
                    scriptEngine.put("__rtmRearArr", rearList.toTypedArray<LegacyScriptExecutor?>())
                    scriptEngine.eval("frontSideTrainList = Array.from(__rtmFrontArr); rearSideTrainList = Array.from(__rtmRearArr);")
                } catch (ignored: Throwable) {
                }
            }
            if (scriptEngine is Invocable) {
                try {
                    scriptEngine.invokeFunction("updateSoundMaker", soundBridge)
                } catch (ignored: NoSuchMethodException) {
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "updateSoundMaker(soundUpdater)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("updateSoundEffects", soundBridge)
                } catch (ignored: NoSuchMethodException) {
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "updateSoundEffects(soundUpdater)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("tick", entity)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no tick function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "tick(entity)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("tick")
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no zero-arg tick function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "tick()", e)
                    return
                }
                if (compat != null) {
                    try {
                        scriptEngine.invokeFunction("onUpdate", if (soundScript) soundBridge else compat)
                        return
                    } catch (ignored: NoSuchMethodException) {
                        // no one-argument compat onUpdate
                    } catch (e: ScriptException) {
                        disableBrokenScript(scriptEngine, "onUpdate(compat)", e)
                        return
                    }
                    try {
                        scriptEngine.invokeFunction("onUpdate", entity, compat)
                        return
                    } catch (ignored: NoSuchMethodException) {
                        // no two-argument onUpdate
                    } catch (e: ScriptException) {
                        disableBrokenScript(scriptEngine, "onUpdate(entity, compat)", e)
                        return
                    }
                }
                try {
                    scriptEngine.invokeFunction("onUpdate", entity)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no one-argument entity onUpdate
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "onUpdate(entity)", e)
                    return
                }
            }
            try {
                scriptEngine.put("__ptTickEntity", entity)
                scriptEngine.put("__ptCompat", compat)
                scriptEngine.put("__ptOnUpdateCompat", if (soundScript) soundBridge else compat)
                scriptEngine.eval(
                    "if (typeof tick === 'function') tick(__ptTickEntity);" +
                            " else if (typeof onUpdate === 'function') {" +
                            "   if (__ptOnUpdateCompat != null) {" +
                            "     try { onUpdate(__ptOnUpdateCompat); } catch (e1) {" +
                            "       try { onUpdate(__ptTickEntity, __ptCompat); } catch (e2) { onUpdate(__ptTickEntity); }" +
                            "     }" +
                            "   } else { onUpdate(__ptTickEntity); }" +
                            " }"
                )
            } catch (e: ScriptException) {
                disableBrokenScript(scriptEngine, "tick/onUpdate fallback", e)
            }
        }

        @JvmStatic
        fun invokeScriptUpdate(scriptEngine: ScriptEngine?, entity: Any?, partialTicks: Float) {
            if (scriptEngine == null || isScriptDisabled(scriptEngine)) return
            if (scriptEngine is Invocable) {
                try {
                    scriptEngine.invokeFunction("update", entity, partialTicks)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no update function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "update(entity, partialTicks)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("update", entity)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no entity-only update function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "update(entity)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("update", partialTicks)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no partialTick-only update function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "update(partialTicks)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("update")
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no zero-arg update function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "update()", e)
                    return
                }
            }
            try {
                scriptEngine.eval("if (typeof update === 'function') update();")
            } catch (e: ScriptException) {
                disableBrokenScript(scriptEngine, "update() fallback", e)
            }
        }

        @JvmStatic
        fun invokeScriptRender(scriptEngine: ScriptEngine?, entity: Any?, partialTicks: Float) {
            if (scriptEngine == null || isScriptDisabled(scriptEngine)) return
            var pass = 0
            val rendererObj = scriptEngine.get("renderer")
            if (rendererObj is ScriptModelRenderer) {
                pass = rendererObj.currentPass
            }
            if (scriptEngine is Invocable) {
                try {
                    scriptEngine.invokeFunction("render", entity, pass, partialTicks)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no 3-arg render function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "render(entity, pass, partialTicks)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("render", entity, partialTicks)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no 2-arg render function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "render(entity, partialTicks)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("render", entity)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no entity-only render function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "render(entity)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("render", partialTicks)
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no partialTick-only render function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "render(partialTicks)", e)
                    return
                }
                try {
                    scriptEngine.invokeFunction("render")
                    return
                } catch (ignored: NoSuchMethodException) {
                    // no zero-arg render function
                } catch (e: ScriptException) {
                    disableBrokenScript(scriptEngine, "render()", e)
                    return
                }
            }
            try {
                scriptEngine.eval("if (typeof render === 'function') render();")
            } catch (e: ScriptException) {
                disableBrokenScript(scriptEngine, "render() fallback", e)
            }
        }
    }
}

