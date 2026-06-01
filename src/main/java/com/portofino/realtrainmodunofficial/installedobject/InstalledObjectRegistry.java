package com.portofino.realtrainmodunofficial.installedobject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InstalledObjectRegistry {
    private static final List<InstalledObjectDefinition> ALL = new ArrayList<>();
    private static final Map<String, InstalledObjectDefinition> BY_ID = new HashMap<>();
    private static final Map<InstalledObjectCategory, List<InstalledObjectDefinition>> BY_CATEGORY =
        new EnumMap<>(InstalledObjectCategory.class);

    private InstalledObjectRegistry() {
    }

    public static void setDefinitions(List<InstalledObjectDefinition> definitions) {
        ALL.clear();
        BY_ID.clear();
        BY_CATEGORY.clear();
        for (InstalledObjectDefinition definition : definitions) {
            InstalledObjectDefinition previous = BY_ID.put(definition.getId(), definition);
            if (previous != null) {
                ALL.remove(previous);
                List<InstalledObjectDefinition> existing = BY_CATEGORY.get(previous.getCategory());
                if (existing != null) {
                    existing.remove(previous);
                }
            }
            ALL.add(definition);
            BY_CATEGORY.computeIfAbsent(definition.getCategory(), key -> new ArrayList<>()).add(definition);
        }
        ALL.sort(Comparator.comparing(InstalledObjectDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        for (List<InstalledObjectDefinition> list : BY_CATEGORY.values()) {
            list.sort(Comparator.comparing(InstalledObjectDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        }
    }

    public static InstalledObjectDefinition getById(String id) {
        if (id == null) return null;
        InstalledObjectDefinition found = BY_ID.get(id);
        if (found != null) return found;
        // Legacy fallback: block entities from old saves may use a different packName
        // (e.g. "RTM-Official-Assets.zip" vs the current mod JAR path).
        // Try matching by category:name, ignoring the packName segment.
        int first = id.indexOf(':');
        int last = id.lastIndexOf(':');
        if (first >= 0 && last > first) {
            String category = id.substring(0, first);
            String name = id.substring(last + 1);
            for (InstalledObjectDefinition def : ALL) {
                String defId = def.getId();
                int dFirst = defId.indexOf(':');
                int dLast = defId.lastIndexOf(':');
                if (dFirst < 0 || dLast <= dFirst) continue;
                if (defId.substring(0, dFirst).equals(category)
                        && defId.substring(dLast + 1).equals(name)) {
                    return def;
                }
            }
        }
        return null;
    }

    public static List<InstalledObjectDefinition> getByCategory(InstalledObjectCategory category) {
        return List.copyOf(BY_CATEGORY.getOrDefault(category, List.of()));
    }

    public static List<InstalledObjectDefinition> getAll() {
        return List.copyOf(ALL);
    }
}
