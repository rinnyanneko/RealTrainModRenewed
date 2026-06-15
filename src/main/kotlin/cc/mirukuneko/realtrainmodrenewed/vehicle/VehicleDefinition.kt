package cc.mirukuneko.realtrainmodrenewed.vehicle

import net.minecraft.world.phys.Vec3

class VehicleDefinition(
    @JvmField val id: String,
    @JvmField val displayName: String,
    @JvmField val packName: String,
    @JvmField val modelFile: String,
    buttonTexture: String?,
    textureOverrides: Map<String, String>?,
    modelOffset: Vec3?,
    modelScale: Float,
    @JvmField val bogies: List<BogieDefinition>,
    @JvmField val seatMarkers: List<SeatMarker>,
    @JvmField val seatPositions: List<Vec3>,
    @JvmField val playerPositions: List<Vec3>,
    seatOffset: Vec3?,
    @JvmField val scriptPath: String,
    @JvmField val soundScriptPath: String,
    @JvmField var vehicleType: String,
    @JvmField val doorType: String,
    @JvmField val trainDistance: Float,
    @JvmField val driverSeatIndex: Int,
    @JvmField val frontDriverSeatIndex: Int,
    @JvmField val rearDriverSeatIndex: Int,
    @JvmField val leftDoors: List<DoorAnimationDefinition>,
    @JvmField val rightDoors: List<DoorAnimationDefinition>,
    @JvmField val notchMaxSpeeds: List<Float>,
    @JvmField val brakeDecelerations: List<Float>,
    @JvmField val acceleration: Float,
    @JvmField val smoothing: Boolean,
    @JvmField val rollsignNames: List<String>,
    @JvmField val customButtonNames: List<String>,
    @JvmField val customButtonOptions: List<List<String>>,
    @JvmField val rollsignTexture: String,
    @JvmField val rollsigns: List<RollsignDefinition>,
    @JvmField val headLights: List<LightDefinition>,
    @JvmField val tailLights: List<LightDefinition>,
    @JvmField val interiorLights: List<LightDefinition>,
    @JvmField val hornSound: String,
    @JvmField val announcementSounds: List<String>,
    @JvmField val doCulling: Boolean,
    @JvmField val renderLight: Boolean,
    @JvmField val notDisplayCab: Boolean,
    @JvmField val singleTrain: Boolean
) {
    data class BogieDefinition(
        @JvmField val modelFile: String,
        @JvmField val textureOverrides: Map<String, String>,
        @JvmField val position: Vec3,
        @JvmField val scriptPath: String
    ) {
        constructor(modelFile: String, textureOverrides: Map<String, String>, position: Vec3) :
            this(modelFile, textureOverrides, position, "")
    }

    data class SeatMarker(
        @JvmField val position: Vec3,
        @JvmField val type: Int,
        @JvmField val driverCab: Boolean
    ) {
        fun isRideable(): Boolean = driverCab || type != SEAT_TYPE_DISABLED
    }

    data class DoorAnimationDefinition(
        @JvmField val objects: List<String>,
        @JvmField val closedPosition: Vec3,
        @JvmField val openTranslation: Vec3
    )

    data class RollsignDefinition(
        @JvmField val uv: FloatArray,
        @JvmField val pos: Array<Array<FloatArray>>,
        @JvmField val doAnimation: Boolean,
        @JvmField val disableLighting: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RollsignDefinition) return false
            return uv.contentEquals(other.uv) && pos.contentDeepEquals(other.pos) &&
                doAnimation == other.doAnimation && disableLighting == other.disableLighting
        }
        override fun hashCode(): Int {
            var result = uv.contentHashCode()
            result = 31 * result + pos.contentDeepHashCode()
            result = 31 * result + doAnimation.hashCode()
            result = 31 * result + disableLighting.hashCode()
            return result
        }
    }

    data class LightDefinition(
        @JvmField val type: Byte,
        @JvmField val color: Int,
        @JvmField val position: Vec3,
        @JvmField val radius: Float,
        @JvmField val reverse: Boolean
    )

    companion object {
        const val SEAT_TYPE_DISABLED: Int = 0
        const val SEAT_TYPE_DRIVER_CAB: Int = -1

        private fun buildSeatMarkers(seatPositions: List<Vec3>?, playerPositions: List<Vec3>?): List<SeatMarker> {
            val markers = mutableListOf<SeatMarker>()
            playerPositions?.forEach { markers.add(SeatMarker(it, SEAT_TYPE_DRIVER_CAB, true)) }
            seatPositions?.forEach { markers.add(SeatMarker(it, 1, false)) }
            return markers.toList()
        }

        private fun toImmutableNestedList(options: List<List<String>>?): List<List<String>> {
            if (options.isNullOrEmpty()) return emptyList()
            return options.map { it?.toList() ?: emptyList() }.toList()
        }
    }

    @JvmField val buttonTexture: String = buttonTexture ?: ""
    @JvmField val textureOverrides: Map<String, String> = textureOverrides ?: emptyMap()
    @JvmField val modelOffset: Vec3 = modelOffset ?: Vec3.ZERO
    @JvmField val modelScale: Float = if (modelScale <= 0.0F) 1.0F else modelScale
    @JvmField val seatOffset: Vec3 = seatOffset ?: Vec3.ZERO

    @JvmField var serverScriptPath: String = ""
    @JvmField var doorOpenSound: String = ""
    @JvmField var doorCloseSound: String = ""
    @JvmField var soundStop: String = ""
    @JvmField var soundStartAcceleration: String = ""
    @JvmField var soundAcceleration: String = ""
    @JvmField var soundDeceleration: String = ""
    @JvmField var soundDecelerationStop: String = ""

    fun getBogiePositions(): List<Vec3> = bogies.map { it.position }

    fun getRideableSeatMarkers(): List<SeatMarker> = seatMarkers.filter { it.isRideable() }

    fun isDriverSeatIndex(index: Int): Boolean {
        val rideable = getRideableSeatMarkers()
        return index >= 0 && index < rideable.size && rideable[index].driverCab
    }

    fun getAllSeatPositions(): List<Vec3> {
        if (seatMarkers.isNotEmpty()) return seatMarkers.map { it.position }
        return when {
            playerPositions.isEmpty() -> seatPositions
            seatPositions.isEmpty() -> playerPositions
            else -> playerPositions + seatPositions
        }
    }

    fun getRideableSeatPositions(): List<Vec3> =
        getRideableSeatMarkers().map { it.position }

    fun hasSeatOffset(): Boolean = seatOffset != Vec3.ZERO
    fun hasScript(): Boolean = scriptPath.isNotBlank()
    fun hasSoundScript(): Boolean = soundScriptPath.isNotBlank()
    fun getServerScriptPath(): String = serverScriptPath
    fun hasServerScript(): Boolean = serverScriptPath.isNotBlank()

    fun setServerScriptPath(path: String?) { serverScriptPath = path ?: "" }

    fun isCarType(): Boolean = vehicleType.equals("car", ignoreCase = true)
    fun hasAutomaticDoor(): Boolean =
        doorType.equals("automatic", ignoreCase = true) || doorType.equals("auto", ignoreCase = true)
    fun hasDoorAnimations(): Boolean = leftDoors.isNotEmpty() || rightDoors.isNotEmpty()

    fun getDoorOpenSound(): String = doorOpenSound
    fun getDoorCloseSound(): String = doorCloseSound

    fun setDoorSounds(open: String?, close: String?) {
        doorOpenSound = open ?: ""
        doorCloseSound = close ?: ""
    }

    fun getSoundStop(): String = soundStop
    fun getSoundStartAcceleration(): String = soundStartAcceleration
    fun getSoundAcceleration(): String = soundAcceleration
    fun getSoundDeceleration(): String = soundDeceleration
    fun getSoundDecelerationStop(): String = soundDecelerationStop

    fun hasJsonRunningSounds(): Boolean =
        soundStop.isNotBlank() || soundStartAcceleration.isNotBlank() ||
        soundAcceleration.isNotBlank() || soundDeceleration.isNotBlank() ||
        soundDecelerationStop.isNotBlank()

    fun setJsonRunningSounds(stop: String?, startAccel: String?, accel: String?, decel: String?, decelStop: String?) {
        soundStop = stop ?: ""
        soundStartAcceleration = startAccel ?: ""
        soundAcceleration = accel ?: ""
        soundDeceleration = decel ?: ""
        soundDecelerationStop = decelStop ?: ""
    }

    // Simplified constructors matching Java overloads
    constructor(
        id: String, displayName: String, packName: String, modelFile: String,
        buttonTexture: String?, textureOverrides: Map<String, String>?, modelOffset: Vec3?,
        modelScale: Float, bogies: List<BogieDefinition>, seatPositions: List<Vec3>,
        playerPositions: List<Vec3>, seatOffset: Vec3?, scriptPath: String, soundScriptPath: String,
        vehicleType: String, doorType: String, trainDistance: Float, driverSeatIndex: Int,
        frontDriverSeatIndex: Int, rearDriverSeatIndex: Int, leftDoors: List<DoorAnimationDefinition>,
        rightDoors: List<DoorAnimationDefinition>, notchMaxSpeeds: List<Float>,
        acceleration: Float, smoothing: Boolean, rollsignNames: List<String>,
        customButtonNames: List<String>, customButtonOptions: List<List<String>>?,
        rollsignTexture: String, rollsigns: List<RollsignDefinition>, headLights: List<LightDefinition>,
        tailLights: List<LightDefinition>, interiorLights: List<LightDefinition>,
        hornSound: String, announcementSounds: List<String>, doCulling: Boolean,
        renderLight: Boolean, notDisplayCab: Boolean, singleTrain: Boolean
    ) : this(id, displayName, packName, modelFile, buttonTexture, textureOverrides, modelOffset, modelScale,
        bogies, buildSeatMarkers(seatPositions, playerPositions), seatPositions, playerPositions,
        seatOffset, scriptPath, soundScriptPath, vehicleType, doorType, trainDistance,
        driverSeatIndex, frontDriverSeatIndex, rearDriverSeatIndex, leftDoors, rightDoors,
        notchMaxSpeeds, emptyList(), acceleration, smoothing, rollsignNames,
        customButtonNames, toImmutableNestedList(customButtonOptions), rollsignTexture, rollsigns,
        headLights, tailLights, interiorLights, hornSound, announcementSounds,
        doCulling, renderLight, notDisplayCab, singleTrain)

    // Intentionally same signature as below - second overload for simpler usage
    @Suppress("UNUSED_PARAMETER")
    constructor(
        _unused: Unit = Unit,  // disambiguator - first of the simplified constructors without brakeDecelerations
        id: String, displayName: String, packName: String, modelFile: String,
        buttonTexture: String?, textureOverrides: Map<String, String>?, modelOffset: Vec3?,
        modelScale: Float, bogies: List<BogieDefinition>, seatMarkers: List<SeatMarker>,
        seatPositions: List<Vec3>, playerPositions: List<Vec3>, seatOffset: Vec3?,
        scriptPath: String, soundScriptPath: String, vehicleType: String, doorType: String,
        trainDistance: Float, driverSeatIndex: Int, frontDriverSeatIndex: Int,
        rearDriverSeatIndex: Int, leftDoors: List<DoorAnimationDefinition>,
        rightDoors: List<DoorAnimationDefinition>, notchMaxSpeeds: List<Float>,
        acceleration: Float, smoothing: Boolean, rollsignNames: List<String>,
        customButtonNames: List<String>, customButtonOptions: List<List<String>>?,
        rollsignTexture: String, rollsigns: List<RollsignDefinition>, headLights: List<LightDefinition>,
        tailLights: List<LightDefinition>, interiorLights: List<LightDefinition>,
        hornSound: String, announcementSounds: List<String>, doCulling: Boolean,
        renderLight: Boolean, notDisplayCab: Boolean, singleTrain: Boolean
    ) : this(id, displayName, packName, modelFile, buttonTexture, textureOverrides, modelOffset, modelScale,
        bogies, seatMarkers, seatPositions, playerPositions, seatOffset, scriptPath, soundScriptPath,
        vehicleType, doorType, trainDistance, driverSeatIndex, frontDriverSeatIndex, rearDriverSeatIndex,
        leftDoors, rightDoors, notchMaxSpeeds, emptyList(), acceleration, smoothing, rollsignNames,
        customButtonNames, toImmutableNestedList(customButtonOptions), rollsignTexture, rollsigns,
        headLights, tailLights, interiorLights, hornSound, announcementSounds,
        doCulling, renderLight, notDisplayCab, singleTrain)
}
