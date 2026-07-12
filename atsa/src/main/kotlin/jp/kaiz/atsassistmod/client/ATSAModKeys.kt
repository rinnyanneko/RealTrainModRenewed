// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client

import com.mojang.blaze3d.platform.InputConstants
import jp.kaiz.atsassistmod.ATSAssistMod
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

/** Client key mappings. EB = emergency brake. */
object ATSAModKeys {
    @JvmField
    val CATEGORY: KeyMapping.Category =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "main"))

    @JvmField
    val EMERGENCY_BRAKE = KeyMapping(
        "key.atsassistmod.emergency_brake",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY,
    )
}
