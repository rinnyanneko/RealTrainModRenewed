package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

object TrainControlKeyMappings {
    private val CATEGORY: KeyMapping.Category =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "controls"))

    @JvmField val OPEN_CONTROL: KeyMapping = key("open_control", GLFW.GLFW_KEY_E)
    @JvmField val POWER_OFF: KeyMapping = key("power_off", GLFW.GLFW_KEY_S)
    @JvmField val BRAKE_OFF: KeyMapping = key("brake_off", GLFW.GLFW_KEY_W)
    @JvmField val NEUTRAL: KeyMapping = key("neutral", GLFW.GLFW_KEY_X)
    @JvmField val TOGGLE_CAB: KeyMapping = key("toggle_cab", GLFW.GLFW_KEY_U)
    @JvmField val PLAY_ANNOUNCEMENT: KeyMapping = key("play_announcement", GLFW.GLFW_KEY_I)
    @JvmField val PLAY_HORN: KeyMapping = key("play_horn", GLFW.GLFW_KEY_P)
    @JvmField val TOGGLE_RENDER_PROFILER: KeyMapping = key("toggle_render_profiler", GLFW.GLFW_KEY_F8)

    @JvmStatic
    fun register(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_CONTROL)
        event.register(POWER_OFF)
        event.register(BRAKE_OFF)
        event.register(NEUTRAL)
        event.register(TOGGLE_CAB)
        event.register(PLAY_ANNOUNCEMENT)
        event.register(PLAY_HORN)
        event.register(TOGGLE_RENDER_PROFILER)
    }

    @JvmStatic
    fun matchesSneak(event: KeyEvent): Boolean {
        return Minecraft.getInstance().options.keyShift.matches(event)
    }

    private fun key(name: String, defaultKey: Int): KeyMapping {
        return KeyMapping("key.realtrainmodrenewed.$name", defaultKey, CATEGORY)
    }
}
