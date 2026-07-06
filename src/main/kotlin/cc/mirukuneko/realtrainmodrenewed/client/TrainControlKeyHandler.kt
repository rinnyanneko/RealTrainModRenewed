package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.client.screen.TrainControlScreen
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import cc.mirukuneko.realtrainmodrenewed.network.MountTrainPayload
import cc.mirukuneko.realtrainmodrenewed.network.TrainControlPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.max

@EventBusSubscriber(modid = RealTrainModRenewed.MODID, value = [Dist.CLIENT])
object TrainControlKeyHandler {
    private const val HOLD_REPEAT_INITIAL_DELAY_TICKS = 7
    private const val HOLD_REPEAT_INTERVAL_TICKS = 2
    private var doorLeftChordDown = false
    private var doorRightChordDown = false
    private var shiftWasDown = false
    private var powerHoldTicks = -1
    private var brakeHoldTicks = -1

    @JvmStatic
    @SubscribeEvent
    fun onKeyInput(event: InputEvent.Key) {
        if (event.action != GLFW.GLFW_PRESS) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.screen != null) return

        val train = getControlledTrain(mc) ?: return
        val keyEvent = KeyEvent(event.key, event.scanCode, event.modifiers)
        if (TrainControlKeyMappings.matchesSneak(keyEvent)) {
            ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "dismount", 0))
            return
        }
        if (!train.isLikelyDriverPassenger(mc.player)) return

        if (TrainControlKeyMappings.OPEN_CONTROL.matches(keyEvent)) {
            mc.setScreen(TrainControlScreen(train))
            return
        }
        if (TrainControlKeyMappings.TOGGLE_CAB.matches(keyEvent)) {
            TrainHudOverlay.toggleCabHidden()
            return
        }
        if (TrainControlKeyMappings.POWER_OFF.matches(keyEvent)) {
            sendControl(train, "mascon_power")
            powerHoldTicks = 0
            brakeHoldTicks = -1
            return
        }
        if (TrainControlKeyMappings.BRAKE_OFF.matches(keyEvent)) {
            sendControl(train, "mascon_brake")
            brakeHoldTicks = 0
            powerHoldTicks = -1
            return
        }
        if (TrainControlKeyMappings.NEUTRAL.matches(keyEvent)) {
            sendControl(train, "mascon_neutral")
        }

        val jumpDown = mc.options.keyJump.isDown
        if (jumpDown && event.key == GLFW.GLFW_KEY_LEFT) {
            ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "toggle_door_left", 0))
            doorLeftChordDown = true
            return
        }
        if (jumpDown && event.key == GLFW.GLFW_KEY_RIGHT) {
            ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "toggle_door_right", 0))
            doorRightChordDown = true
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        if (mc.player == null) {
            shiftWasDown = false
            resetHoldState()
            return
        }

        if (TrainControlKeyMappings.TOGGLE_RENDER_PROFILER.consumeClick()) {
            ClientRenderProfiler.toggleOverlay()
        }

        if (mc.screen != null) {
            doorLeftChordDown = false
            doorRightChordDown = false
            resetHoldState()
            return
        }

        val train = getControlledTrain(mc)
        if (train == null) {
            shiftWasDown = false
            doorLeftChordDown = false
            doorRightChordDown = false
            resetHoldState()
            return
        }

        val shiftDown = mc.options.keyShift.isDown
        if (shiftDown && !shiftWasDown) {
            ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "dismount", 0))
            shiftWasDown = true
            return
        }
        shiftWasDown = shiftDown

        if (!train.isLikelyDriverPassenger(mc.player)) {
            doorLeftChordDown = false
            doorRightChordDown = false
            resetHoldState()
            return
        }

        if (TrainControlKeyMappings.OPEN_CONTROL.consumeClick()) {
            mc.setScreen(TrainControlScreen(train))
        }
        if (TrainControlKeyMappings.TOGGLE_CAB.consumeClick()) {
            TrainHudOverlay.toggleCabHidden()
        }
        if (TrainControlKeyMappings.PLAY_ANNOUNCEMENT.consumeClick()) {
            ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "play_selected_announcement", 0))
        }
        if (TrainControlKeyMappings.PLAY_HORN.consumeClick()) {
            ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "play_horn", 0))
        }
        if (TrainControlKeyMappings.NEUTRAL.consumeClick()) {
            sendControl(train, "mascon_neutral")
        }

        val jumpDown = mc.options.keyJump.isDown
        val leftArrowDown = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS
        val rightArrowDown = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS
        if (jumpDown && leftArrowDown) {
            if (!doorLeftChordDown) {
                ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "toggle_door_left", 0))
                doorLeftChordDown = true
            }
        } else {
            doorLeftChordDown = false
        }
        if (jumpDown && rightArrowDown) {
            if (!doorRightChordDown) {
                ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, "toggle_door_right", 0))
                doorRightChordDown = true
            }
        } else {
            doorRightChordDown = false
        }

        val powerHeld = TrainControlKeyMappings.POWER_OFF.isDown
        val brakeHeld = TrainControlKeyMappings.BRAKE_OFF.isDown
        if (powerHeld && !brakeHeld) {
            powerHoldTicks = max(0, powerHoldTicks + 1)
            brakeHoldTicks = -1
            if (shouldSendRepeat(powerHoldTicks)) sendControl(train, "mascon_power")
        } else if (brakeHeld && !powerHeld) {
            brakeHoldTicks = max(0, brakeHoldTicks + 1)
            powerHoldTicks = -1
            if (shouldSendRepeat(brakeHoldTicks)) sendControl(train, "mascon_brake")
        } else {
            resetHoldState()
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onUseKey(event: InputEvent.InteractionKeyMappingTriggered) {
        if (!event.isUseItem || event.hand != InteractionHand.MAIN_HAND) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.screen != null || player.vehicle != null) return
        if (mc.hitResult is EntityHitResult) return

        val holdingCrowbar = player.mainHandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get()) ||
            player.offhandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
        if (!holdingCrowbar) return

        ClientNetworkHelper.sendToServer(MountTrainPayload.INSTANCE)
    }

    private fun shouldSendRepeat(heldTicks: Int): Boolean {
        if (heldTicks < HOLD_REPEAT_INITIAL_DELAY_TICKS) return false
        return (heldTicks - HOLD_REPEAT_INITIAL_DELAY_TICKS) % HOLD_REPEAT_INTERVAL_TICKS == 0
    }

    private fun sendControl(train: TrainEntity, action: String) {
        applyLocalControl(train, action)
        ClientNetworkHelper.sendToServer(TrainControlPayload(train.id, action, 0))
    }

    private fun applyLocalControl(train: TrainEntity, action: String) {
        Minecraft.getInstance().player?.let { train.ensureDriverReady(it) }
        when (action) {
            "mascon_power" -> train.stepMascon(1)
            "mascon_brake" -> train.stepMascon(-1)
            "mascon_neutral" -> train.notch = 0
        }
    }

    private fun getControlledTrain(mc: Minecraft): TrainEntity? {
        val vehicle = mc.player?.vehicle ?: return null
        return when (vehicle) {
            is TrainEntity -> vehicle
            is TrainSeatEntity -> vehicle.train
            else -> null
        }
    }

    private fun resetHoldState() {
        powerHoldTicks = -1
        brakeHoldTicks = -1
    }
}
