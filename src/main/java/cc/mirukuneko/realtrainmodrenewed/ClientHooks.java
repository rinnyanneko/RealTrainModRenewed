package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ClientHooks {
    private static final String CLIENT_HOOKS_CLASS = "cc.mirukuneko.realtrainmodrenewed.client.ClientHooksClient";

    private ClientHooks() {
    }

    public static void openRailSelectScreen(Player player, ItemStack stack) {
        invokeClient("openRailSelectScreen", new Class<?>[]{Player.class, ItemStack.class}, player, stack);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack, TrainItem.Category category) {
        invokeClient("openTrainSelectScreen", new Class<?>[]{Player.class, ItemStack.class, TrainItem.Category.class}, player, stack, category);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack) {
        invokeClient("openTrainSelectScreen", new Class<?>[]{Player.class, ItemStack.class}, player, stack);
    }

    public static void openVehicleFormationScreen(ItemStack stack) {
        invokeClient("openVehicleFormationScreen", new Class<?>[]{ItemStack.class}, stack);
    }

    public static void openCarSelectScreen(Player player, ItemStack stack) {
        invokeClient("openCarSelectScreen", new Class<?>[]{Player.class, ItemStack.class}, player, stack);
    }

    public static void openInstalledObjectSelectScreen(Player player, ItemStack stack, InstalledObjectCategory category) {
        invokeClient("openInstalledObjectSelectScreen", new Class<?>[]{Player.class, ItemStack.class, InstalledObjectCategory.class}, player, stack, category);
    }

    public static void openSignalChangerScreen(BlockPos pos) {
        invokeClient("openSignalChangerScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openSignalReceiverScreen(BlockPos pos) {
        invokeClient("openSignalReceiverScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openSignalValueScreen(BlockPos pos) {
        invokeClient("openSignalValueScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openTrainDetectorScreen(BlockPos pos) {
        invokeClient("openTrainDetectorScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openMarkerConfigScreen(BlockPos pos) {
        invokeClient("openMarkerConfigScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openSpeakerScreen(BlockPos pos) {
        invokeClient("openSpeakerScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openScriptBlockScreen(BlockPos pos) {
        invokeClient("openScriptBlockScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void stopCrossingGateSound(Level level, BlockPos pos) {
        invokeClient("stopCrossingGateSound", new Class<?>[]{Level.class, BlockPos.class}, level, pos);
    }

    public static void tickCrossingGateSound(InstalledObjectBlockEntity blockEntity) {
        invokeClient("tickCrossingGateSound", new Class<?>[]{InstalledObjectBlockEntity.class}, blockEntity);
    }

    public static void showScriptErrorMessage(String message) {
        invokeClient("showScriptErrorMessage", new Class<?>[]{String.class}, message);
    }

    private static void invokeClient(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> hooks = Class.forName(CLIENT_HOOKS_CLASS);
            hooks.getMethod(methodName, parameterTypes).invoke(null, args);
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.debug("Client hook {} failed", methodName, e);
        }
    }
}

