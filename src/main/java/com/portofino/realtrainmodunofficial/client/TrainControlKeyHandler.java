package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import com.portofino.realtrainmodunofficial.client.screen.TrainControlScreen;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import com.portofino.realtrainmodunofficial.network.MountTrainPayload;
import com.portofino.realtrainmodunofficial.network.TrainControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class TrainControlKeyHandler {
    private static final int HOLD_REPEAT_INITIAL_DELAY_TICKS = 7;
    private static final int HOLD_REPEAT_INTERVAL_TICKS = 2;
    private static boolean doorLeftChordDown;
    private static boolean doorRightChordDown;
    private static boolean shiftWasDown;
    private static int powerHoldTicks = -1;
    private static int brakeHoldTicks = -1;

    private TrainControlKeyHandler() {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        TrainEntity train = getControlledTrain(mc);
        if (train == null) {
            return;
        }
        if (TrainControlKeyMappings.matchesSneak(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "dismount", 0));
            return;
        }
        if (!train.isLikelyDriverPassenger(mc.player)) {
            return;
        }
        if (TrainControlKeyMappings.OPEN_CONTROL.matches(event.getKey(), event.getScanCode())) {
            mc.setScreen(new TrainControlScreen(train));
            return;
        }
        if (TrainControlKeyMappings.TOGGLE_CAB.matches(event.getKey(), event.getScanCode())) {
            TrainHudOverlay.toggleCabHidden();
            return;
        }
        if (TrainControlKeyMappings.POWER_OFF.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_power", 0), new CustomPacketPayload[0]);
            powerHoldTicks = 0;
            brakeHoldTicks = -1;
            return;
        }
        if (TrainControlKeyMappings.BRAKE_OFF.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_brake", 0), new CustomPacketPayload[0]);
            brakeHoldTicks = 0;
            powerHoldTicks = -1;
            return;
        }
        if (TrainControlKeyMappings.NEUTRAL.matches(event.getKey(), event.getScanCode())) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_neutral", 0), new CustomPacketPayload[0]);
        }

        boolean jumpDown = mc.options.keyJump.isDown();
        if (jumpDown && event.getKey() == GLFW.GLFW_KEY_LEFT) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_left", 0));
            doorLeftChordDown = true;
            return;
        }
        if (jumpDown && event.getKey() == GLFW.GLFW_KEY_RIGHT) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_right", 0));
            doorRightChordDown = true;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            shiftWasDown = false;
            resetHoldState();
            return;
        }

        if (TrainControlKeyMappings.TOGGLE_RENDER_PROFILER.consumeClick()) {
            ClientRenderProfiler.toggleOverlay();
        }

        if (mc.screen != null) {
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            return;
        }

        TrainEntity train = getControlledTrain(mc);
        if (train == null) {
            shiftWasDown = false;
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            return;
        }
        boolean shiftDown = mc.options.keyShift.isDown();
        if (shiftDown && !shiftWasDown) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "dismount", 0));
            shiftWasDown = true;
            return;
        }
        shiftWasDown = shiftDown;
        if (!train.isLikelyDriverPassenger(mc.player)) {
            doorLeftChordDown = false;
            doorRightChordDown = false;
            resetHoldState();
            return;
        }
        if (TrainControlKeyMappings.OPEN_CONTROL.consumeClick()) {
            mc.setScreen(new TrainControlScreen(train));
        }
        if (TrainControlKeyMappings.TOGGLE_CAB.consumeClick()) {
            TrainHudOverlay.toggleCabHidden();
        }
        if (TrainControlKeyMappings.PLAY_ANNOUNCEMENT.consumeClick()) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "play_selected_announcement", 0));
        }
        if (TrainControlKeyMappings.PLAY_HORN.consumeClick()) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "play_horn", 0));
        }
        if (TrainControlKeyMappings.NEUTRAL.consumeClick()) {
            PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_neutral", 0), new CustomPacketPayload[0]);
        }

        boolean jumpDown = mc.options.keyJump.isDown();
        boolean leftArrowDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;
        boolean rightArrowDown = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
        if (jumpDown && leftArrowDown) {
            if (!doorLeftChordDown) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_left", 0));
                doorLeftChordDown = true;
            }
        } else {
            doorLeftChordDown = false;
        }
        if (jumpDown && rightArrowDown) {
            if (!doorRightChordDown) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "toggle_door_right", 0));
                doorRightChordDown = true;
            }
        } else {
            doorRightChordDown = false;
        }

        boolean powerHeld = TrainControlKeyMappings.POWER_OFF.isDown();
        boolean brakeHeld = TrainControlKeyMappings.BRAKE_OFF.isDown();
        if (powerHeld && !brakeHeld) {
            powerHoldTicks = Math.max(0, powerHoldTicks + 1);
            brakeHoldTicks = -1;
            if (shouldSendRepeat(powerHoldTicks)) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_power", 0), new CustomPacketPayload[0]);
            }
        } else if (brakeHeld && !powerHeld) {
            brakeHoldTicks = Math.max(0, brakeHoldTicks + 1);
            powerHoldTicks = -1;
            if (shouldSendRepeat(brakeHoldTicks)) {
                PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), "mascon_brake", 0), new CustomPacketPayload[0]);
            }
        } else {
            resetHoldState();
        }
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.player.getVehicle() != null) {
            return;
        }
        if (mc.hitResult instanceof EntityHitResult) {
            return;
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        boolean holdingCrowbar = mainHand.is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())
            || offHand.is(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
        if (!holdingCrowbar) {
            return;
        }
        PacketDistributor.sendToServer(MountTrainPayload.INSTANCE);
    }

    private static boolean shouldSendRepeat(int heldTicks) {
        if (heldTicks < HOLD_REPEAT_INITIAL_DELAY_TICKS) {
            return false;
        }
        return (heldTicks - HOLD_REPEAT_INITIAL_DELAY_TICKS) % HOLD_REPEAT_INTERVAL_TICKS == 0;
    }

    private static TrainEntity getControlledTrain(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        if (mc.player.getVehicle() instanceof TrainEntity train) {
            return train;
        }
        if (mc.player.getVehicle() instanceof TrainSeatEntity seat) {
            return seat.getTrain();
        }
        return null;
    }

    private static void resetHoldState() {
        powerHoldTicks = -1;
        brakeHoldTicks = -1;
    }
}
