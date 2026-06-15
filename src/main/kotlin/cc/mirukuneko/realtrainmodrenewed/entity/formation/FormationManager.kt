package cc.mirukuneko.realtrainmodrenewed.entity.formation

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity

class FormationManager private constructor() {
    private val formations: MutableMap<String, Formation> = HashMap()

    fun getFormation(id: String): Formation? = formations[id]

    fun register(id: String, formation: Formation) {
        formations[id] = formation
    }

    fun remove(id: String) {
        formations.remove(id)
    }

    fun getNewId(): String {
        var id = System.currentTimeMillis().toString()
        while (formations.containsKey(id)) {
            id = (id.toLongOrNull()?.plus(1) ?: System.currentTimeMillis()).toString()
        }
        return id
    }

    fun createNewFormation(train: TrainEntity?): Formation? {
        if (train == null) return null
        val id = getNewId()
        val formation = Formation(id, 1)
        formation.entries[0] = FormationEntry(train, 0, 0)
        return formation
    }

    companion object {
        @JvmField
        val instance: FormationManager = FormationManager()
    }
}
