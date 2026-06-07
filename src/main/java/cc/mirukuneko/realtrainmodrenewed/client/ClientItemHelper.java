package cc.mirukuneko.realtrainmodrenewed.client;

import cc.mirukuneko.realtrainmodrenewed.client.screen.ModelSelectScreen;
import cc.mirukuneko.realtrainmodrenewed.client.screen.TrainFormationScreen;
import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry;
import cc.mirukuneko.realtrainmodrenewed.network.SelectModelPayload;
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem;
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Comparator;
import java.util.Locale;

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
            ClientItemHelper::sendSelectedModel,
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
            ClientItemHelper::sendSelectedModel,
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
            ClientItemHelper::sendSelectedModel,
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
        String id = definition.getId() == null ? "" : definition.getId().toLowerCase(Locale.ROOT);
        String displayName = definition.getDisplayName() == null ? "" : definition.getDisplayName();
        String displayNameLower = displayName.toLowerCase(Locale.ROOT);
        String buttonTexture = definition.getButtonTexture() == null ? "" : definition.getButtonTexture().toLowerCase(Locale.ROOT);
        // [RTM]SL_D51_v1.2 contains stale DD51-498 entries whose button textures are not bundled.
        // The pack author confirmed they are not intended selectable vehicles.
        if ((id.startsWith("dd51-498") || displayNameLower.startsWith("dd51-498"))
                && buttonTexture.contains("button_dd51-498")) {
            return false;
        }
        return !HIDDEN_TRAIN_PACK.equalsIgnoreCase(packName)
            && !id.contains(HIDDEN_TRAIN_PACK)
            && !displayNameLower.contains(HIDDEN_TRAIN_PACK);
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
            ClientItemHelper::sendSelectedModel,
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
            ClientItemHelper::sendSelectedModel,
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    private static String getInstalledObjectTitleKey(InstalledObjectCategory category) {
        return switch (category) {
            case LIGHT -> "screen.realtrainmodunofficial.select_light";
            case SIGNBOARD -> "screen.realtrainmodunofficial.select_signboard";
            case INSULATOR -> "screen.realtrainmodunofficial.select_insulator";
            case OVERHEAD_LINE_POLE -> "screen.realtrainmodunofficial.select_overhead_line_pole";
            case WIRE -> "screen.realtrainmodunofficial.select_wire";
            case SIGNAL -> "screen.realtrainmodunofficial.select_signal";
            case CROSSING -> "screen.realtrainmodunofficial.select_crossing";
            case TICKET_GATE -> "screen.realtrainmodunofficial.select_ticket_gate";
            case SPEAKER -> "screen.realtrainmodunofficial.select_speaker";
        };
    }

    private static void sendSelectedModel(ModelSelectScreen.SelectionResult selection) {
        CustomPacketPayload payload = new SelectModelPayload(selection.modelId(), selection.dataMapValue());
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(payload);
        }
    }
}
