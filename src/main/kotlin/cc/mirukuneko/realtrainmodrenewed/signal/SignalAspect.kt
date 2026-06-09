package cc.mirukuneko.realtrainmodrenewed.signal

/**
 * Signal aspects used by RTM-style remote signals.
 * Button order, IDs, and legacy values are save/UI compatibility-sensitive.
 */
enum class SignalAspect(
    @get:JvmName("getId")
    val id: Int,
    @get:JvmName("getLabel")
    val label: String,
    @get:JvmName("getLegacyValue")
    val legacyValue: Int,
) {
    STOP(0, "停止(R)", 1),
    WARNING(1, "警戒(YY)", 4),
    CAUTION(2, "注意(Y)", 3),
    REDUCE(3, "減速(YG)", 6),
    RESTRICTED(4, "抑速", 2),
    PROCEED(5, "進行(G)", 5),
    HIGH_SPEED(6, "高速進行(GG)", 7);

    companion object {
        @JvmStatic
        fun byId(id: Int): SignalAspect {
            return entries.firstOrNull { it.id == id } ?: STOP
        }

        @JvmStatic
        fun byLegacyValue(legacyValue: Int): SignalAspect {
            return entries.firstOrNull { it.legacyValue == legacyValue } ?: STOP
        }
    }
}
