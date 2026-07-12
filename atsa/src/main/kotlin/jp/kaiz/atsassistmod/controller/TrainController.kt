// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtection
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType
import jp.kaiz.atsassistmod.rtm.RtmTrains
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

open class TrainController {
    private val speedOrderList = ArrayList<SpeedOrder>()
    private val speedLimit = ArrayList<Int>()
    private var maxSpeed = 0

    private var ato = false
    private var acceleratorControlling = false

    @JvmField
    val tascController = TASCController()

    private var brakingControlling = false

    private lateinit var tp: TrainProtection
    private var tpType = TrainProtectionType.NONE
    private var train: TrainEntity? = null
    private var coordinates: Vec3? = null
    private val savedEntityID: Int
    private var controllerNotchA: Byte = -1
    private var controllerNotchB: Byte = 1
    private var controllerControl = false
    private var emergencyBrake = false
    private var manualDrive = false

    constructor() {
        savedEntityID = -1
        setTrainProtection(TrainProtectionType.NONE)
    }

    constructor(train: TrainEntity) {
        savedEntityID = train.id
        this.train = train
        setTrainProtection(TrainProtectionType.NONE)
    }

    /** Keeps the controller pointed at the live train instance for its formation. */
    fun bind(train: TrainEntity) {
        this.train = train
    }

    fun getTrain(): TrainEntity? = train

    fun setEB() {
        emergencyBrake = true
        train?.notch = -8
    }

    fun setManualDrive(manualDrive: Boolean) {
        this.manualDrive = manualDrive
    }

    fun isManualDrive(): Boolean = manualDrive

    fun setControllerNotch(notch: Byte) {
        controllerControl = true
        when {
            notch > 0 -> {
                controllerNotchA = notch
                controllerNotchB = 1
            }
            notch < 0 -> {
                controllerNotchA = 0
                controllerNotchB = notch
            }
            else -> {
                controllerNotchA = 0
                controllerNotchB = 1
            }
        }
    }

    fun getSavedEntityID(): Int = savedEntityID

    fun addSpeedOrder(speedOrder: SpeedOrder) {
        speedOrderList.add(speedOrder)
    }

    fun setMaxSpeed(maxSpeed: Int) {
        this.maxSpeed = maxSpeed
    }

    fun removeSpeedLimit() {
        if (speedLimit.isNotEmpty()) {
            speedLimit.removeAt(0)
        }
    }

    fun removeAllSpeedLimit() {
        if (speedLimit.isNotEmpty()) {
            speedLimit.clear()
        }
    }

    fun getSpeedLimit(): Int = speedLimit.minOrNull() ?: Int.MAX_VALUE

    fun getATOSpeedLimit(): Int {
        val minLimit = speedLimit.minOrNull() ?: maxSpeed
        return min(getTrainProtectionSpeedLimit(), min(minLimit, maxSpeed))
    }

    fun getTrainProtectionSpeedLimit(): Int = tp.getDisplaySpeed()

    fun enableATO(speed: Int) {
        ato = true
        maxSpeed = speed
    }

    fun disableATO() {
        ato = false
    }

    fun isATO(): Boolean = ato

    fun setTrainProtection(type: TrainProtectionType) {
        tpType = type
        tp = type.newInstance()
    }

    fun getTrainProtectionType(): TrainProtectionType = tp.getType()

    /** Tick processing (server side). */
    @Throws(Exception::class)
    fun onUpdate() {
        val train = train ?: return
        val movedDistance = getMovedDistance()
        val speedH = RtmTrains.speedKmh(train)

        val brakeNotch = ArrayList<Int>()
        val acceleratorNotch = ArrayList<Int>()
        val removeList = ArrayList<SpeedOrder>()

        speedOrderList.forEach { speedOrder ->
            if (speedOrder.isEnable()) {
                speedLimit.add(speedOrder.getTargetSpeed())
                removeList.add(speedOrder)
            } else {
                speedOrder.moveDistance(movedDistance)
                if (speedOrder.isAutoBrake() || (ato && !isManualDrive())) {
                    brakeNotch.add(speedOrder.getNeedNotch(speedH))
                }
            }
        }
        speedOrderList.removeAll(removeList)

        if (speedH > getSpeedLimit()) {
            val overSpeed = speedH - getSpeedLimit()
            if (overSpeed < 5f) {
                brakeNotch.add(-4)
            } else {
                brakeNotch.add(-7)
            }
        } else if (ato && !isManualDrive()) {
            if (!acceleratorControlling) {
                if (getATOSpeedLimit() - speedH > 10 || speedH == 0f) {
                    acceleratorNotch.add(5)
                }
            } else if (getATOSpeedLimit() - speedH < 2) {
                acceleratorNotch.add(0)
            }
        }

        tascController.changeTargetDistance(movedDistance)
        if (tascController.isEnable()) {
            val needNotch = tascController.getNeedNotch(speedH)
            if (tascController.isBreaking()) {
                disableATO()
                if (!isManualDrive()) {
                    brakeNotch.add(needNotch)
                }
            }
        }

        tp.onTick(train, movedDistance)
        brakeNotch.add(tp.getNotch(speedH))

        if (emergencyBrake) {
            val notchLevel = train.notch
            if (notchLevel != -8) {
                emergencyBrake = false
                brakingControlling = false
            } else {
                return
            }
        }

        var minBrakeNotch = brakeNotch.minOrNull() ?: 1

        if (RtmTrains.rider(train) == null) {
            controllerNotchA = -1
            controllerNotchB = 1
            if (controllerControl) {
                controllerControl = false
                brakingControlling = false
            }
        } else if (controllerControl) {
            minBrakeNotch = min(minBrakeNotch, controllerNotchB.toInt())
        }

        if (minBrakeNotch > 0) {
            var maxAcceleratorNotch = acceleratorNotch.maxOrNull() ?: -1

            if (RtmTrains.rider(train) != null && controllerControl) {
                maxAcceleratorNotch = max(maxAcceleratorNotch, controllerNotchA.toInt())
            }

            when {
                maxAcceleratorNotch < 0 -> {
                    if (brakingControlling) {
                        brakingControlling = false
                        train.notch = 0
                    }
                }
                maxAcceleratorNotch == 0 -> {
                    brakingControlling = false
                    if (!controllerControl || controllerNotchA.toInt() != 0) {
                        acceleratorControlling = false
                    }
                    train.notch = 0
                }
                else -> {
                    brakingControlling = false
                    acceleratorControlling = true
                    train.notch = maxAcceleratorNotch
                }
            }
        } else if (minBrakeNotch == 0) {
            if (tascController.isEnable() && tascController.isStopPosition()) {
                brakingControlling = false
                ato = false
                if (speedH <= 0f) {
                    tascController.disable()
                    if (!isManualDrive()) {
                        return
                    }
                }
            }
            acceleratorControlling = false
            brakingControlling = false
            train.notch = 0
        } else {
            acceleratorControlling = false
            if (tascController.isEnable() && tascController.isStopPosition()) {
                brakingControlling = false
                ato = false
                if (speedH <= 0f) {
                    tascController.disable()
                    if (!isManualDrive()) {
                        return
                    }
                }
            }
            brakingControlling = true
            train.notch = minBrakeNotch
        }

        if (tascController.isEnable() && tascController.isStopPosition()) {
            ato = false
            if (isManualDrive()) {
                tascController.disable()
            }
        }
    }

    private fun getMovedDistance(): Double {
        val train = train ?: return 0.0
        val now = RtmTrains.pos(train)
        val old = coordinates
        if (old == null) {
            coordinates = now
            return 0.0
        }
        coordinates = now
        val dx = old.x - now.x
        val dz = old.z - now.z
        return sqrt(dx * dx + dz * dz)
    }

    fun isTASCEnable(): Boolean = tascController.isEnable()

    fun isEmergencyBrake(): Boolean = emergencyBrake

    fun getTpType(): TrainProtectionType = tpType

    companion object {
        @JvmField
        val NULL = TrainController()
    }
}
