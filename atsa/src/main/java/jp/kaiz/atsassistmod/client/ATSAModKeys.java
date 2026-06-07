package jp.kaiz.atsassistmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import jp.kaiz.atsassistmod.ATSAssistMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client key mappings. EB = emergency brake (was bound to RTM's KEY_EB). */
public final class ATSAModKeys {
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "main"));

    public static final KeyMapping EMERGENCY_BRAKE = new KeyMapping(
            "key.atsassistmod.emergency_brake",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    private ATSAModKeys() {}
}
