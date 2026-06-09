package cc.mirukuneko.realtrainmodrenewed.entity.formation

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity

class FormationManager private constructor() {
    private val formations: MutableMap<Long, Formation> = HashMap()

    fun getFormation(id: Long): Formation? = formations[id]

    fun register(id: Long, formation: Formation) {
        formations[id] = formation
    }

    fun remove(id: Long) {
        formations.remove(id)
    }

    fun getNewId(): Long {
        var id = System.currentTimeMillis()
        while (formations.containsKey(id)) {
            id++
        }
        return id
    }

    fun createNewFormation(train: TrainEntity): Formation {
        val id = getNewId()
        val formation = Formation(id, 1)
        formation.entries[0] = FormationEntry(train, 0, 0)
        train.setFormation(formation)
        return formation
    }

    companion object {
        private var INSTANCE: FormationManager? = null

        @JvmStatic
        fun getInstance(): FormationManager {
            var instance = INSTANCE
            if (instance == null) {
                instance = FormationManager()
                INSTANCE = instance
            }
            return instance
        }

        @JvmStatic
        fun reset() {
            INSTANCE = null
        }
    }
}
