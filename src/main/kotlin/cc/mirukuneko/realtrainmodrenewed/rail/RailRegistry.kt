package cc.mirukuneko.realtrainmodrenewed.rail

import java.util.Locale

object RailRegistry {
    private val definitions: MutableList<RailDefinition> = ArrayList()
    private val byId: MutableMap<String, RailDefinition> = HashMap()
    private val idCounts: MutableMap<String, Int> = HashMap()
    private var selectedIndex = 0

    @JvmStatic
    fun setDefinitions(defs: List<RailDefinition?>) {
        definitions.clear()
        byId.clear()
        idCounts.clear()
        val unique = LinkedHashMap<String, RailDefinition>()
        for (definition in defs) {
            if (definition == null || definition.id.isBlank()) {
                continue
            }
            unique.putIfAbsent(dedupeKey(definition), definition)
        }
        for (definition in unique.values) {
            idCounts[definition.id] = (idCounts[definition.id] ?: 0) + 1
        }
        for (definition in unique.values) {
            definitions.add(definition)
            byId.putIfAbsent(definition.id, definition)
            byId[packScopedId(definition)] = definition
        }
        if (selectedIndex >= definitions.size) {
            selectedIndex = 0
        }
    }

    @JvmStatic
    fun getAll(): List<RailDefinition> = definitions.toList()

    @JvmStatic
    fun getById(id: String?): RailDefinition? = if (id == null) null else byId[id]

    @JvmStatic
    fun getSelectionId(definition: RailDefinition): String =
        if ((idCounts[definition.id] ?: 0) > 1) packScopedId(definition) else definition.id

    @JvmStatic
    fun getSelected(): RailDefinition? {
        if (definitions.isEmpty()) {
            return null
        }
        if (selectedIndex !in definitions.indices) {
            selectedIndex = 0
        }
        return definitions[selectedIndex]
    }

    @JvmStatic
    fun setSelectedIndex(i: Int) {
        if (i in definitions.indices) {
            selectedIndex = i
        }
    }

    private fun dedupeKey(definition: RailDefinition): String =
        "${definition.packName}|${definition.id}|${definition.modelFile}".lowercase(Locale.ROOT)

    private fun packScopedId(definition: RailDefinition): String =
        "${definition.packName}:${definition.id}"
}
