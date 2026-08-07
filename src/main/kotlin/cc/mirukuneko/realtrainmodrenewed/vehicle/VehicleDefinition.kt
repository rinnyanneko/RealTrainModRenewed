// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
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

        fun getModelFile(): String = modelFile
        fun getTextureOverrides(): Map<String, String> = textureOverrides
        fun getPosition(): Vec3 = position
        fun getScriptPath(): String = scriptPath

        fun modelFile(): String = modelFile
        fun textureOverrides(): Map<String, String> = textureOverrides
        fun position(): Vec3 = position
        fun scriptPath(): String = scriptPath
    }

    data class SeatMarker(
        @JvmField val position: Vec3,
        @JvmField val type: Int,
        @JvmField val driverCab: Boolean
    ) {
        fun getPosition(): Vec3 = position
        fun getType(): Int = type
        fun isDriverCab(): Boolean = driverCab
        fun position(): Vec3 = position
        fun type(): Int = type
        fun driverCab(): Boolean = driverCab

        fun isRideable(): Boolean = driverCab || type != SEAT_TYPE_DISABLED
    }

    data class DoorAnimationDefinition(
        @JvmField val objects: List<String>,
        @JvmField val closedPosition: Vec3,
        @JvmField val openTranslation: Vec3
    ) {
        fun getObjects(): List<String> = objects
        fun getClosedPosition(): Vec3 = closedPosition
        fun getOpenTranslation(): Vec3 = openTranslation
        fun objects(): List<String> = objects
        fun closedPosition(): Vec3 = closedPosition
        fun openTranslation(): Vec3 = openTranslation
    }

    data class RollsignDefinition(
        @JvmField val uv: FloatArray,
        @JvmField val pos: Array<Array<FloatArray>>,
        @JvmField val doAnimation: Boolean,
        @JvmField val disableLighting: Boolean
    ) {
        fun getUv(): FloatArray = uv
        fun getPos(): Array<Array<FloatArray>> = pos
        fun isDoAnimation(): Boolean = doAnimation
        fun isDisableLighting(): Boolean = disableLighting
        fun uv(): FloatArray = uv
        fun pos(): Array<Array<FloatArray>> = pos
        fun doAnimation(): Boolean = doAnimation
        fun disableLighting(): Boolean = disableLighting

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
    ) {
        fun getType(): Byte = type
        fun getColor(): Int = color
        fun getPosition(): Vec3 = position
        fun getRadius(): Float = radius
        fun isReverse(): Boolean = reverse
        fun type(): Byte = type
        fun color(): Int = color
        fun position(): Vec3 = position
        fun radius(): Float = radius
        fun reverse(): Boolean = reverse
    }

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
            return options.map { it.toList() }.toList()
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
    @JvmField var announcementNames: List<String> = emptyList()
    @JvmField var typeSignNames: List<String> = emptyList()
    @JvmField var typeSignTexture: String = ""
    @JvmField var typeSigns: List<RollsignDefinition> = emptyList()
    @JvmField var notchAccelerations: List<Float> = emptyList()
    @JvmField var useVariableAcceleration: Boolean = false
    @JvmField var useVariableDeceleration: Boolean = false

    fun getId(): String = id
    fun getDisplayName(): String = displayName
    fun getPackName(): String = packName
    fun getModelFile(): String = modelFile
    fun getButtonTexture(): String = buttonTexture
    fun getTextureOverrides(): Map<String, String> = textureOverrides
    fun getModelOffset(): Vec3 = modelOffset
    fun getModelScale(): Float = modelScale
    fun getBogies(): List<BogieDefinition> = bogies
    fun getSeatMarkers(): List<SeatMarker> = seatMarkers
    fun getSeatPositions(): List<Vec3> = seatPositions
    fun getPlayerPositions(): List<Vec3> = playerPositions
    fun getSeatOffset(): Vec3 = seatOffset
    fun getScriptPath(): String = scriptPath
    fun getSoundScriptPath(): String = soundScriptPath
    fun getVehicleType(): String = vehicleType
    fun getDoorType(): String = doorType
    fun getTrainDistance(): Float = trainDistance
    fun getDriverSeatIndex(): Int = driverSeatIndex
    fun getFrontDriverSeatIndex(): Int = frontDriverSeatIndex
    fun getRearDriverSeatIndex(): Int = rearDriverSeatIndex
    fun getLeftDoors(): List<DoorAnimationDefinition> = leftDoors
    fun getRightDoors(): List<DoorAnimationDefinition> = rightDoors
    fun getNotchMaxSpeeds(): List<Float> = notchMaxSpeeds
    fun getBrakeDecelerations(): List<Float> = brakeDecelerations
    fun getAcceleration(): Float = acceleration
    fun getNotchAccelerations(): List<Float> = notchAccelerations
    fun isUseVariableAcceleration(): Boolean = useVariableAcceleration
    fun isUseVariableDeceleration(): Boolean = useVariableDeceleration
    fun isSmoothing(): Boolean = smoothing
    fun getRollsignNames(): List<String> = rollsignNames
    fun getCustomButtonNames(): List<String> = customButtonNames
    fun getCustomButtonOptions(): List<List<String>> = customButtonOptions
    fun getRollsignTexture(): String = rollsignTexture
    fun getRollsigns(): List<RollsignDefinition> = rollsigns
    fun getHeadLights(): List<LightDefinition> = headLights
    fun getTailLights(): List<LightDefinition> = tailLights
    fun getInteriorLights(): List<LightDefinition> = interiorLights
    fun getHornSound(): String = hornSound
    fun getAnnouncementSounds(): List<String> = announcementSounds
    fun getAnnouncementNames(): List<String> = announcementNames
    fun getTypeSignNames(): List<String> = typeSignNames
    fun getTypeSignTexture(): String = typeSignTexture
    fun getTypeSigns(): List<RollsignDefinition> = typeSigns
    fun isDoCulling(): Boolean = doCulling
    fun isRenderLight(): Boolean = renderLight
    fun isNotDisplayCab(): Boolean = notDisplayCab
    fun isSingleTrain(): Boolean = singleTrain

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

    fun setAnnouncementNames(names: List<String>?) {
        announcementNames = names?.toList() ?: emptyList()
    }

    fun setTypeSign(names: List<String>?, texture: String?, signs: List<RollsignDefinition>?) {
        typeSignNames = names?.toList() ?: emptyList()
        typeSignTexture = texture ?: ""
        typeSigns = signs?.toList() ?: emptyList()
    }

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

    constructor(
        id: String, displayName: String, packName: String, modelFile: String,
        buttonTexture: String?, textureOverrides: Map<String, String>?, modelOffset: Vec3?,
        modelScale: Float, bogies: List<BogieDefinition>, seatPositions: List<Vec3>,
        playerPositions: List<Vec3>, seatOffset: Vec3?, scriptPath: String, soundScriptPath: String,
        vehicleType: String, doorType: String, trainDistance: Float, driverSeatIndex: Int,
        frontDriverSeatIndex: Int, rearDriverSeatIndex: Int, leftDoors: List<DoorAnimationDefinition>,
        rightDoors: List<DoorAnimationDefinition>, notchMaxSpeeds: List<Float>,
        brakeDecelerations: List<Float>, acceleration: Float, smoothing: Boolean,
        rollsignNames: List<String>, customButtonNames: List<String>,
        customButtonOptions: List<List<String>>?, rollsignTexture: String,
        rollsigns: List<RollsignDefinition>, headLights: List<LightDefinition>,
        tailLights: List<LightDefinition>, interiorLights: List<LightDefinition>,
        hornSound: String, announcementSounds: List<String>, doCulling: Boolean,
        renderLight: Boolean, notDisplayCab: Boolean, singleTrain: Boolean
    ) : this(id, displayName, packName, modelFile, buttonTexture, textureOverrides, modelOffset, modelScale,
        bogies, buildSeatMarkers(seatPositions, playerPositions), seatPositions, playerPositions,
        seatOffset, scriptPath, soundScriptPath, vehicleType, doorType, trainDistance,
        driverSeatIndex, frontDriverSeatIndex, rearDriverSeatIndex, leftDoors, rightDoors,
        notchMaxSpeeds, brakeDecelerations, acceleration, smoothing, rollsignNames,
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

