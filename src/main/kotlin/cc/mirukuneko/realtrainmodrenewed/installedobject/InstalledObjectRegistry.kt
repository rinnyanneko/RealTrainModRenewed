// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.installedobject

import java.util.EnumMap

object InstalledObjectRegistry {
    private val all: MutableList<InstalledObjectDefinition> = ArrayList()
    private val byId: MutableMap<String, InstalledObjectDefinition> = HashMap()
    private val fallbackById: MutableMap<String, InstalledObjectDefinition> = HashMap()
    private val missingIds: MutableSet<String> = HashSet()
    private val byCategory: MutableMap<InstalledObjectCategory, MutableList<InstalledObjectDefinition>> =
        EnumMap(InstalledObjectCategory::class.java)

    @JvmStatic
    fun setDefinitions(definitions: List<InstalledObjectDefinition>) {
        all.clear()
        byId.clear()
        fallbackById.clear()
        missingIds.clear()
        byCategory.clear()
        for (definition in definitions) {
            val previous = byId.put(definition.id, definition)
            if (previous != null) {
                all.remove(previous)
                byCategory[previous.category]?.remove(previous)
            }
            all.add(definition)
            byCategory.computeIfAbsent(definition.category) { ArrayList() }.add(definition)
        }
        all.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        for (list in byCategory.values) {
            list.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }
    }

    @JvmStatic
    fun getById(id: String?): InstalledObjectDefinition? {
        if (id == null) {
            return null
        }
        byId[id]?.let { return it }
        fallbackById[id]?.let { return it }
        if (id in missingIds) {
            return null
        }

        val first = id.indexOf(':')
        val last = id.lastIndexOf(':')
        if (first >= 0 && last > first) {
            val category = id.substring(0, first)
            val name = id.substring(last + 1)
            for (definition in all) {
                val defId = definition.id
                val defFirst = defId.indexOf(':')
                val defLast = defId.lastIndexOf(':')
                if (defFirst < 0 || defLast <= defFirst) {
                    continue
                }
                if (defId.substring(0, defFirst) == category && defId.substring(defLast + 1) == name) {
                    fallbackById[id] = definition
                    return definition
                }
            }
        }
        missingIds.add(id)
        return null
    }

    @JvmStatic
    fun getByCategory(category: InstalledObjectCategory): List<InstalledObjectDefinition> {
        return byCategory.getOrDefault(category, emptyList()).toList()
    }

    @JvmStatic
    fun getAll(): List<InstalledObjectDefinition> = all.toList()
}
