// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.formation

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.world.item.ItemStack

object TrainFormationData {
    const val TAG_VEHICLES: String = "vehicles"
    const val TAG_NAME: String = "name"

    @JvmStatic
    fun getFormation(stack: ItemStack): TrainFormation? {
        val tag: CompoundTag = stack.get(RealTrainModRenewedComponents.TRAIN_FORMATION.get()) ?: return null
        val formation = TrainFormation()
        formation.name = NbtCompat.getString(tag, TAG_NAME)

        val vehiclesList: ListTag = NbtCompat.getList(tag, TAG_VEHICLES)
        for (i in 0 until vehiclesList.size) {
            val vehicleId = NbtCompat.getString(vehiclesList, i)
            if (vehicleId.isNotEmpty()) {
                formation.addVehicle(vehicleId)
            }
        }
        return formation
    }

    @JvmStatic
    fun setFormation(stack: ItemStack, formation: TrainFormation?) {
        if (formation == null || formation.isEmpty()) {
            stack.set(RealTrainModRenewedComponents.TRAIN_FORMATION.get(), null)
            return
        }

        val tag = CompoundTag()
        tag.putString(TAG_NAME, formation.name)

        val vehiclesList = ListTag()
        for (vehicleId in formation.getAllVehicles()) {
            vehiclesList.add(StringTag.valueOf(vehicleId))
        }
        tag.put(TAG_VEHICLES, vehiclesList)

        stack.set(RealTrainModRenewedComponents.TRAIN_FORMATION.get(), tag)
    }

    @JvmStatic
    fun hasFormation(stack: ItemStack): Boolean {
        return stack.get(RealTrainModRenewedComponents.TRAIN_FORMATION.get()) != null
    }

    @JvmStatic
    fun clearFormation(stack: ItemStack) {
        stack.set(RealTrainModRenewedComponents.TRAIN_FORMATION.get(), null)
    }
}
