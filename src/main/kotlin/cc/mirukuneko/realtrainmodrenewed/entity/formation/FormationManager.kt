// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
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

    fun getNewId(): Long {
        var id = System.currentTimeMillis()
        while (formations.containsKey(id.toString())) {
            id++
        }
        return id
    }

    fun createNewFormation(train: TrainEntity?): Formation? {
        if (train == null) return null
        val id = getNewId().toString()
        val formation = Formation(id, 1)
        formation.entries[0] = FormationEntry(train, 0, 0)
        return formation
    }

    companion object {
        @JvmField
        val instance: FormationManager = FormationManager()

        @JvmStatic
        fun getInstance(): FormationManager = instance
    }
}
