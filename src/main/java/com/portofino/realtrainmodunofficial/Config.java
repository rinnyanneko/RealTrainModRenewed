package com.portofino.realtrainmodunofficial;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

// コンフィグ・クラスの例。これは必須ではありませんが、コンフィグを整理しておくためにあるとよいでしょう。
// NeoのコンフィグAPIの使い方を説明します。
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // アイテムのリソース位置として扱われる文字列のリスト
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    public static final ModConfigSpec.IntValue MODEL_CACHE_LIMIT_MIB = BUILDER
            .comment("Maximum in-memory size of cached polygon models in MiB. Recently used models are protected from eviction for a short time.")
            .defineInRange("modelCacheLimitMiB", 256, 16, 8192);

    public static final ModConfigSpec.IntValue MODEL_CACHE_PROTECT_SECONDS = BUILDER
            .comment("Do not evict cached polygon models that were used within this number of seconds.")
            .defineInRange("modelCacheProtectSeconds", 10, 0, 600);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
