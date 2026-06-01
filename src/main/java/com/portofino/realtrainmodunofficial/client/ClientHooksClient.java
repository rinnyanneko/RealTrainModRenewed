package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.screen.ScriptBlockScreen;
import com.portofino.realtrainmodunofficial.client.screen.SignalChangerScreen;
import com.portofino.realtrainmodunofficial.client.screen.SignalReceiverScreen;
import com.portofino.realtrainmodunofficial.client.screen.SignalValueScreen;
import com.portofino.realtrainmodunofficial.client.screen.TrainDetectorScreen;
import com.portofino.realtrainmodunofficial.compat.atsassist.client.AtsaGroundUnitScreen;
import com.portofino.realtrainmodunofficial.compat.atsassist.client.AtsaSimpleBlockScreen;
import com.portofino.realtrainmodunofficial.compat.atsassist.client.AtsaTrainToolScreen;
import com.portofino.realtrainmodunofficial.client.sound.CrossingGateSoundManager;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.item.TrainItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientHooksClient {
    private ClientHooksClient() {
    }

    public static void openRailSelectScreen(Player player, ItemStack stack) {
        ClientItemHelper.openRailSelectScreen(player, stack);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack, TrainItem.Category category) {
        ClientItemHelper.openTrainSelectScreen(player, stack, category);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack) {
        ClientItemHelper.openTrainSelectScreen(player, stack);
    }

    public static void openVehicleFormationScreen(ItemStack stack) {
        ClientItemHelper.openVehicleFormationScreen(stack);
    }

    public static void openCarSelectScreen(Player player, ItemStack stack) {
        ClientItemHelper.openCarSelectScreen(player, stack);
    }

    public static void openInstalledObjectSelectScreen(Player player, ItemStack stack, InstalledObjectCategory category) {
        ClientItemHelper.openInstalledObjectSelectScreen(player, stack, category);
    }

    public static void openSignalChangerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SignalChangerScreen(pos));
    }

    public static void openSignalReceiverScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SignalReceiverScreen(pos));
    }

    public static void openSignalValueScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SignalValueScreen(pos));
    }

    public static void openTrainDetectorScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TrainDetectorScreen(pos));
    }

    public static void openMarkerConfigScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.MarkerConfigScreen(pos));
    }

    public static void openSpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.SpeakerScreen(pos));
    }

    public static void openScriptBlockScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ScriptBlockScreen(pos));
    }

    public static void openAtsaGroundUnitScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new AtsaGroundUnitScreen(pos));
    }

    public static void openAtsaSimpleBlockScreen(BlockPos pos, String title) {
        Minecraft.getInstance().setScreen(new AtsaSimpleBlockScreen(pos, Component.literal(title)));
    }

    public static void openAtsaTrainToolScreen(String mode) {
        Minecraft.getInstance().setScreen(new AtsaTrainToolScreen(mode));
    }

    public static void stopCrossingGateSound(Level level, BlockPos pos) {
        CrossingGateSoundManager.stop(level, pos);
    }

    public static void tickCrossingGateSound(InstalledObjectBlockEntity blockEntity) {
        CrossingGateSoundManager.tick(blockEntity);
    }

    public static void showScriptErrorMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || message == null || message.isBlank()) {
            return;
        }
        minecraft.player.displayClientMessage(Component.literal("[RTMU Script] " + message), false);
    }
}
