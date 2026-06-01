package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.client.screen.ModelSelectScreen;
import com.portofino.realtrainmodunofficial.client.screen.TrainFormationScreen;
import com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.network.SelectModelPayload;
import com.portofino.realtrainmodunofficial.item.TrainItem;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Comparator;

@OnlyIn(Dist.CLIENT)
public final class ClientItemHelper {
    private static final String HIDDEN_TRAIN_PACK = "basic_train";

    private ClientItemHelper() {}

    public static void openRailSelectScreen(Player player, ItemStack stack) {
        List<ModelSelectScreen.ModelInfo> infos = RailRegistry.getAll().stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_rail"),
            infos,
            selection -> PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue())),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack) {
        openTrainSelectScreen(player, stack, TrainItem.Category.ELECTRIC);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack, TrainItem.Category category) {
        List<ModelSelectScreen.ModelInfo> infos = getVisibleTrainModels();
        infos = infos.stream()
            .filter(info -> TrainItem.accepts(category, VehicleRegistry.getById(info.id())))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_train"),
            infos,
            selection -> PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue())),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    public static void openTrainSelectScreen(TrainFormationScreen formationScreen) {
        List<ModelSelectScreen.ModelInfo> infos = getVisibleTrainModels();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_train"),
            infos,
            selection -> {
                formationScreen.updateFormationWithVehicle(selection.modelId());
            },
            null,
            ""
        ));
    }

    public static void openTrainSelectScreen() {
        List<ModelSelectScreen.ModelInfo> infos = getVisibleTrainModels();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_train"),
            infos,
            selection -> PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue())),
            null,
            ""
        ));
    }

    private static List<ModelSelectScreen.ModelInfo> getVisibleTrainModels() {
        // basic_train は初期確認用の内蔵車両なので、通常の選択画面には出さない。
        return VehicleRegistry.getAll().stream()
            .filter(ClientItemHelper::shouldShowTrainModel)
            .sorted(Comparator
                .comparing((VehicleDefinition d) -> safe(d.getVehicleType()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(d -> safe(d.getDisplayName()), String.CASE_INSENSITIVE_ORDER))
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture(), d.getVehicleType()))
            .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean shouldShowTrainModel(VehicleDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.isCarType()) {
            return false;
        }
        // 列車判定の本質: 台車(bogies)を持つこと。RTM の Wire/Insulator/Pipe/Signal/転轍機 等は
        // 台車を持たないため、これで非列車を一括除外できる。
        if (definition.getBogies() == null || definition.getBogies().isEmpty()) {
            return false;
        }
        // 内蔵の basic_train 系は packName だけでなく id / displayName 由来で残る場合がある。
        String packName = definition.getPackName() == null ? "" : definition.getPackName();
        String id = definition.getId() == null ? "" : definition.getId().toLowerCase();
        String displayName = definition.getDisplayName() == null ? "" : definition.getDisplayName();
        return !HIDDEN_TRAIN_PACK.equalsIgnoreCase(packName)
            && !id.contains(HIDDEN_TRAIN_PACK)
            && !displayName.toLowerCase().contains(HIDDEN_TRAIN_PACK);
    }

    public static void openCarSelectScreen(Player player, ItemStack stack) {
        List<ModelSelectScreen.ModelInfo> infos = VehicleRegistry.getAll().stream()
            .filter(d -> d != null && d.isCarType())
            .sorted(Comparator.comparing(d -> safe(d.getDisplayName()), String.CASE_INSENSITIVE_ORDER))
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_car"),
            infos,
            selection -> PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue())),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    public static void openVehicleFormationScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new TrainFormationScreen(stack));
    }

    public static void openInstalledObjectSelectScreen(Player player, ItemStack stack, InstalledObjectCategory category) {
        List<ModelSelectScreen.ModelInfo> infos = InstalledObjectRegistry.getByCategory(category).stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable(getInstalledObjectTitleKey(category)),
            infos,
            selection -> PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue())),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    private static String getInstalledObjectTitleKey(InstalledObjectCategory category) {
        return switch (category) {
            case LIGHT -> "screen.realtrainmodunofficial.select_light";
            case SIGNBOARD -> "screen.realtrainmodunofficial.select_signboard";
            case INSULATOR -> "screen.realtrainmodunofficial.select_insulator";
            case WIRE -> "screen.realtrainmodunofficial.select_wire";
            case SIGNAL -> "screen.realtrainmodunofficial.select_signal";
            case CROSSING -> "screen.realtrainmodunofficial.select_crossing";
            case TICKET_GATE -> "screen.realtrainmodunofficial.select_ticket_gate";
            case SPEAKER -> "screen.realtrainmodunofficial.select_speaker";
        };
    }
}
