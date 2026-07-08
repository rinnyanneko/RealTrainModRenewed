// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.formation

class TrainFormation @JvmOverloads constructor(
    vehicleIds: List<String> = emptyList(),
    name: String? = "",
) {
    private val vehicleIds: MutableList<String> = ArrayList(vehicleIds)
    var name: String = name ?: ""

    fun addVehicle(vehicleId: String?) {
        if (vehicleIds.size < 30 && vehicleId != null) {
            vehicleIds.add(vehicleId)
        }
    }

    fun removeVehicle(index: Int) {
        if (index in vehicleIds.indices) {
            vehicleIds.removeAt(index)
        }
    }

    fun setVehicle(index: Int, vehicleId: String) {
        if (index in vehicleIds.indices) {
            vehicleIds[index] = vehicleId
        }
    }

    fun getVehicle(index: Int): String? {
        return if (index in vehicleIds.indices) vehicleIds[index] else null
    }

    fun getAllVehicles(): List<String> = ArrayList(vehicleIds)

    fun getCarCount(): Int = vehicleIds.size

    fun isEmpty(): Boolean = vehicleIds.isEmpty()

    fun isFull(): Boolean = vehicleIds.size >= 30

    fun getDisplayName(): String = name.ifEmpty { "Unnamed Formation" }

    fun copy(): TrainFormation = TrainFormation(vehicleIds, name)
}
