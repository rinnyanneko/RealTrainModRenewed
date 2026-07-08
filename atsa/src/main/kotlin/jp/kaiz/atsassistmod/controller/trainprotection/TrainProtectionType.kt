// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.controller.trainprotection

enum class TrainProtectionType(
    @JvmField val translationKey: String,
    @JvmField val id: Int,
    @JvmField val aClass: Class<out TrainProtection>,
) {
    NONE("atsassistmod.trainprotection.none", 0, TrainProtection::class.java),
    STATION_PREMISES("atsassistmod.trainprotection.station_premises", 1, StationPremisesController::class.java),
    ATACS("atsassistmod.trainprotection.atacs", 10, ATACSController::class.java),
    ATSPs("atsassistmod.trainprotection.atsps", 11, ATSPsController::class.java),
    RATS("atsassistmod.trainprotection.rats", 12, RATSController::class.java),
    RnATS("atsassistmod.trainprotection.rnats", 13, RnATSController::class.java);

    /** Translation key; UI code wraps with `Component.translatable`. */
    fun getTranslationKey(): String = translationKey

    fun newInstance(): TrainProtection {
        try {
            return aClass.getDeclaredConstructor().newInstance()
        } catch (exception: ReflectiveOperationException) {
            throw RuntimeException("Failed to instantiate TrainProtection $name", exception)
        }
    }

    companion object {
        @JvmStatic
        fun getType(id: Int): TrainProtectionType {
            return entries.firstOrNull { it.id == id } ?: NONE
        }
    }
}
