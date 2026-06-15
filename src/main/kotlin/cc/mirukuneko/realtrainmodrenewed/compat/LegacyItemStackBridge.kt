package cc.mirukuneko.realtrainmodrenewed.compat

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.Locale

object LegacyItemStackBridge {
    const val LEGACY_MODEL_NAME: String = "ModelName"
    const val LEGACY_STATE: String = "State"
    const val LEGACY_DATA_MAP: String = "DataMap"
    const val LEGACY_DATA_LIST: String = "DataList"
    const val LEGACY_DATA_MAP_ARG: String = "LegacyDataMapArg"
    const val LEGACY_STATE_NAME: String = "Name"

    @JvmStatic
    fun getSelectedModelId(stack: ItemStack?): String {
        if (stack == null || stack.isEmpty) return ""
        val modern = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get())
        if (!modern.isNullOrBlank()) return modern
        val tag = getLegacyCustomData(stack)
        return if (NbtCompat.contains(tag, LEGACY_MODEL_NAME, Tag.TAG_STRING.toInt()))
            NbtCompat.getString(tag, LEGACY_MODEL_NAME) else ""
    }

    @JvmStatic
    fun getSelectedDataMap(stack: ItemStack?): String {
        if (stack == null || stack.isEmpty) return ""
        val modern = stack.getOrDefault(RealTrainModRenewedComponents.SELECTED_MODEL_DATA_MAP.get(), "")
        if (!modern.isNullOrBlank()) return modern
        val tag = getLegacyCustomData(stack)
        if (NbtCompat.contains(tag, LEGACY_DATA_MAP_ARG, Tag.TAG_STRING.toInt()))
            return NbtCompat.getString(tag, LEGACY_DATA_MAP_ARG)
        return extractLegacyDataMapArg(tag)
    }

    @JvmStatic
    fun setSelectedModelData(stack: ItemStack?, modelId: String?, dataMapValue: String?) {
        if (stack == null || stack.isEmpty) return
        val safeModelId = modelId ?: ""
        val safeDataMap = dataMapValue ?: ""
        stack.set(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get(), safeModelId)
        stack.set(RealTrainModRenewedComponents.SELECTED_MODEL_DATA_MAP.get(), safeDataMap)
        val tag = getLegacyCustomData(stack)
        if (safeModelId.isNotBlank()) tag.putString(LEGACY_MODEL_NAME, safeModelId)
        if (safeDataMap.isNotBlank()) {
            tag.putString(LEGACY_DATA_MAP_ARG, safeDataMap)
            writeLegacyStateArg(tag, safeDataMap)
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag)
    }

    private fun getLegacyCustomData(stack: ItemStack): CompoundTag =
        stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

    private fun extractLegacyDataMapArg(tag: CompoundTag): String {
        if (!NbtCompat.contains(tag, LEGACY_STATE, Tag.TAG_COMPOUND.toInt())) return ""
        val state = NbtCompat.getCompound(tag, LEGACY_STATE)
        if (!NbtCompat.contains(state, LEGACY_DATA_MAP, Tag.TAG_COMPOUND.toInt())) return ""
        val list = NbtCompat.getList(NbtCompat.getCompound(state, LEGACY_DATA_MAP), LEGACY_DATA_LIST)
        if (list.isEmpty) return ""

        return buildString {
            for (i in 0 until list.size) {
                val entry = NbtCompat.getCompound(list, i)
                val name = NbtCompat.getString(entry, "Name")
                val type = legacyDataTypeKey(NbtCompat.getString(entry, "Type"))
                val value = readLegacyDataValue(entry)
                if (name.isBlank()) continue
                if (isNotEmpty()) append(',')
                append(name).append("=(").append(type).append(')').append(value)
            }
        }
    }

    private fun writeLegacyStateArg(tag: CompoundTag, dataMapValue: String) {
        val state = if (NbtCompat.contains(tag, LEGACY_STATE, Tag.TAG_COMPOUND.toInt()))
            NbtCompat.getCompound(tag, LEGACY_STATE) else CompoundTag()
        if (!NbtCompat.contains(state, LEGACY_STATE_NAME, Tag.TAG_STRING.toInt()))
            state.putString(LEGACY_STATE_NAME, "no_name")
        if (!state.contains("Color")) state.putInt("Color", 0xFFFFFF)
        val dataMap = if (NbtCompat.contains(state, LEGACY_DATA_MAP, Tag.TAG_COMPOUND.toInt()))
            NbtCompat.getCompound(state, LEGACY_DATA_MAP) else CompoundTag()
        val list = ListTag()
        for (part in dataMapValue.split(",")) {
            val entry = toLegacyDataEntry(part.trim())
            if (!entry.isEmpty) list.add(entry)
        }
        dataMap.put(LEGACY_DATA_LIST, list)
        state.put(LEGACY_DATA_MAP, dataMap)
        tag.put(LEGACY_STATE, state)
    }

    private fun toLegacyDataEntry(arg: String): CompoundTag {
        val entry = CompoundTag()
        val idxEq = arg.indexOf('=')
        val idxOpen = arg.indexOf('(')
        val idxClose = arg.indexOf(')')
        if (idxEq <= 0 || idxOpen != idxEq + 1 || idxClose <= idxOpen + 1) return entry
        val name = arg.substring(0, idxEq).trim()
        val type = normalizeDataType(arg.substring(idxOpen + 1, idxClose))
        val value = if (idxClose + 1 < arg.length) arg.substring(idxClose + 1).trim() else ""
        if (name.isEmpty()) return entry
        entry.putString("Name", name)
        entry.putString("Type", legacyDataTypeKey(type))
        entry.putInt("Flag", 3)
        writeLegacyDataValue(entry, type, value)
        return entry
    }

    private fun readLegacyDataValue(entry: CompoundTag): String {
        val type = normalizeDataType(NbtCompat.getString(entry, "Type"))
        return when (type) {
            "int", "hex" -> NbtCompat.getInt(entry, "Data").toString()
            "double" -> NbtCompat.getDouble(entry, "Data").toString()
            "boolean" -> NbtCompat.getBoolean(entry, "Data").toString()
            else -> NbtCompat.getString(entry, "Data")
        }
    }

    private fun writeLegacyDataValue(entry: CompoundTag, type: String, value: String) {
        try {
            when (type) {
                "int", "hex" -> entry.putInt("Data", if (value.isBlank()) 0 else Integer.decode(value))
                "double" -> entry.putDouble("Data", if (value.isBlank()) 0.0 else value.toDouble())
                "boolean" -> entry.putBoolean("Data", value.toBoolean())
                else -> entry.putString("Data", value)
            }
        } catch (_: NumberFormatException) {
            entry.putString("Data", value)
        }
    }

    private fun normalizeDataType(type: String?): String {
        val normalized = type?.trim()?.lowercase(Locale.ROOT) ?: ""
        return when (normalized) {
            "i", "integer" -> "int"
            "d", "float" -> "double"
            "b", "bool" -> "boolean"
            "s" -> "string"
            "v", "vec3" -> "vec"
            "h" -> "hex"
            else -> normalized.ifBlank { "string" }
        }
    }

    private fun legacyDataTypeKey(type: String): String = when (normalizeDataType(type)) {
        "int" -> "Int"
        "double" -> "Double"
        "boolean" -> "Boolean"
        "vec" -> "Vec"
        "hex" -> "Hex"
        else -> "String"
    }
}
