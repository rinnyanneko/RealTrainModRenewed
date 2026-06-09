package cc.mirukuneko.realtrainmodrenewed

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.common.ModConfigSpec

object Config {
    private val BUILDER = ModConfigSpec.Builder()

    @JvmField
    val LOG_DIRT_BLOCK: ModConfigSpec.BooleanValue = BUILDER
        .comment("Whether to log the dirt block on common setup")
        .define("logDirtBlock", true)

    @JvmField
    val MAGIC_NUMBER: ModConfigSpec.IntValue = BUILDER
        .comment("A magic number")
        .defineInRange("magicNumber", 42, 0, Int.MAX_VALUE)

    @JvmField
    val MAGIC_NUMBER_INTRODUCTION: ModConfigSpec.ConfigValue<String> = BUILDER
        .comment("What you want the introduction message to be for the magic number")
        .define("magicNumberIntroduction", "The magic number is... ")

    @JvmField
    val ITEM_STRINGS: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("A list of items to log on common setup.")
        .defineListAllowEmpty("items", listOf("minecraft:iron_ingot"), { "" }, ::validateItemName)

    @JvmField
    val MODEL_CACHE_LIMIT_MIB: ModConfigSpec.IntValue = BUILDER
        .comment("Maximum in-memory size of cached polygon models in MiB. Recently used models are protected from eviction for a short time.")
        .defineInRange("modelCacheLimitMiB", 256, 16, 8192)

    @JvmField
    val MODEL_CACHE_PROTECT_SECONDS: ModConfigSpec.IntValue = BUILDER
        .comment("Do not evict cached polygon models that were used within this number of seconds.")
        .defineInRange("modelCacheProtectSeconds", 10, 0, 600)

    @JvmField
    val SPEC: ModConfigSpec = BUILDER.build()

    @JvmStatic
    private fun validateItemName(obj: Any): Boolean {
        if (obj !is String) {
            return false
        }
        val id = Identifier.tryParse(obj) ?: return false
        return BuiltInRegistries.ITEM.containsKey(id)
    }
}
