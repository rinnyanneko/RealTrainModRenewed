package cc.mirukuneko.realtrainmodrenewed.client;

import cc.mirukuneko.realtrainmodrenewed.client.screen.MarkerConfigScreen;
import cc.mirukuneko.realtrainmodrenewed.client.screen.SpeakerScreen;
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.client.screen.ScriptBlockScreen;
import cc.mirukuneko.realtrainmodrenewed.client.screen.SignalChangerScreen;
import cc.mirukuneko.realtrainmodrenewed.client.screen.SignalReceiverScreen;
import cc.mirukuneko.realtrainmodrenewed.client.screen.SignalValueScreen;
import cc.mirukuneko.realtrainmodrenewed.client.screen.TrainDetectorScreen;
import cc.mirukuneko.realtrainmodrenewed.client.sound.CrossingGateSoundManager;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem;
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
            new MarkerConfigScreen(pos));
    }

    public static void openSpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(
            new SpeakerScreen(pos));
    }

    public static void openScriptBlockScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ScriptBlockScreen(pos));
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
        minecraft.player.sendSystemMessage(Component.literal("[RTMU Script] " + message));
    }
}
