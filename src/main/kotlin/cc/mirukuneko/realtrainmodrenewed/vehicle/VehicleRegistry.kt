package cc.mirukuneko.realtrainmodrenewed.vehicle

import java.util.Locale

object VehicleRegistry {
    private val definitions: MutableList<VehicleDefinition> = ArrayList()
    private val byId: MutableMap<String, VehicleDefinition> = HashMap()
    private val idCounts: MutableMap<String, Int> = HashMap()
    private var selectedIndex = 0

    @JvmStatic
    fun setDefinitions(defs: List<VehicleDefinition?>) {
        definitions.clear()
        byId.clear()
        idCounts.clear()
        val unique = LinkedHashMap<String, VehicleDefinition>()
        for (definition in defs) {
            if (definition != null) {
                unique.putIfAbsent(dedupeKey(definition), definition)
            }
        }
        for (definition in unique.values) {
            idCounts[definition.id] = (idCounts[definition.id] ?: 0) + 1
        }
        for (definition in unique.values) {
            definitions.add(definition)
            byId.putIfAbsent(definition.id, definition)
            byId[packScopedId(definition)] = definition
        }
        if (selectedIndex >= definitions.size || isHiddenDefault(getSelected())) {
            selectedIndex = firstUsableIndex()
        }
    }

    @JvmStatic
    fun getAll(): List<VehicleDefinition> = definitions.toList()

    @JvmStatic
    fun getById(id: String?): VehicleDefinition? = if (id == null) null else byId[id]

    @JvmStatic
    fun getSelectionId(definition: VehicleDefinition): String =
        if ((idCounts[definition.id] ?: 0) > 1) packScopedId(definition) else definition.id

    @JvmStatic
    fun getSelected(): VehicleDefinition? {
        if (definitions.isEmpty()) {
            return null
        }
        if (selectedIndex !in definitions.indices) {
            selectedIndex = firstUsableIndex()
        }
        return definitions[selectedIndex]
    }

    @JvmStatic
    fun setSelectedIndex(i: Int) {
        if (i in definitions.indices) {
            selectedIndex = i
        }
    }

    private fun firstUsableIndex(): Int {
        for (i in definitions.indices) {
            if (!isHiddenDefault(definitions[i])) {
                return i
            }
        }
        return 0
    }

    private fun isHiddenDefault(definition: VehicleDefinition?): Boolean {
        if (definition == null) {
            return false
        }
        val packName = safe(definition.packName)
        val id = safe(definition.id)
        val displayName = safe(definition.displayName)
        return packName.equals("basic_train", ignoreCase = true) ||
            id.lowercase(Locale.ROOT).contains("basic_train") ||
            displayName.lowercase(Locale.ROOT).contains("basic_train")
    }

    private fun safe(value: String?): String = value ?: ""

    private fun dedupeKey(definition: VehicleDefinition): String {
        val id = safe(definition.id)
        val pack = safe(definition.packName)
        val model = safe(definition.modelFile)
        return "$pack|$id|$model".lowercase(Locale.ROOT)
    }

    private fun packScopedId(definition: VehicleDefinition): String =
        "${definition.packName}:${definition.id}"
}
