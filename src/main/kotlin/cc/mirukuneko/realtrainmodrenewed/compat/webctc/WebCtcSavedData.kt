// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.compat.webctc

import com.mojang.serialization.Codec
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

open class WebCtcSavedData : SavedData() {
    companion object {
        private val CODEC: Codec<WebCtcSavedData> = CompoundTag.CODEC.xmap(
            { tag -> load(tag) },
            { data -> data.saveTag() }
        )
        private val TYPE: SavedDataType<WebCtcSavedData> = SavedDataType(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "webctc"),
            { WebCtcSavedData() },
            CODEC,
            DataFixTypes.LEVEL
        )

        @JvmStatic
        fun get(level: ServerLevel): WebCtcSavedData =
            level.dataStorage.computeIfAbsent(TYPE)

        private fun load(tag: CompoundTag): WebCtcSavedData {
            val data = WebCtcSavedData()
            data.waypointsJson = safeArray(NbtCompat.getString(tag, "Waypoints"))
            data.railgroupsJson = safeArray(NbtCompat.getString(tag, "Railgroups"))
            data.teconsJson = safeArray(NbtCompat.getString(tag, "Tecons"))
            return data
        }

        private fun safeArray(json: String?): String {
            if (json.isNullOrBlank()) return "[]"
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return "[]"
            return trimmed
        }
    }

    private var waypointsJson: String = "[]"
    private var railgroupsJson: String = "[]"
    private var teconsJson: String = "[]"

    private fun saveTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("Waypoints", waypointsJson)
        tag.putString("Railgroups", railgroupsJson)
        tag.putString("Tecons", teconsJson)
        return tag
    }

    open fun get(name: String): String = when (name) {
        "waypoints" -> waypointsJson
        "railgroups" -> railgroupsJson
        "tecons" -> teconsJson
        else -> "[]"
    }

    open fun set(name: String, json: String?) {
        val safe = safeArray(json)
        when (name) {
            "waypoints" -> waypointsJson = safe
            "railgroups" -> railgroupsJson = safe
            "tecons" -> teconsJson = safe
            else -> return
        }
        setDirty()
    }
}
