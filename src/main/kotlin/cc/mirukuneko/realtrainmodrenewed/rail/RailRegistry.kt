package cc.mirukuneko.realtrainmodrenewed.rail

object RailRegistry {
    private val definitions: MutableList<RailDefinition> = ArrayList()
    private val byId: MutableMap<String, RailDefinition> = HashMap()
    private var selectedIndex = 0

    @JvmStatic
    fun setDefinitions(defs: List<RailDefinition?>) {
        definitions.clear()
        byId.clear()
        val unique = LinkedHashMap<String, RailDefinition>()
        for (definition in defs) {
            if (definition == null || definition.id.isBlank()) {
                continue
            }
            unique.putIfAbsent(definition.id, definition)
        }
        for (definition in unique.values) {
            definitions.add(definition)
            byId[definition.id] = definition
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
}
