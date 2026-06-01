package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.item.CarItem;
import com.portofino.realtrainmodunofficial.item.CrowbarItem;
import com.portofino.realtrainmodunofficial.item.IcCardItem;
import com.portofino.realtrainmodunofficial.item.MarkerItem;
import com.portofino.realtrainmodunofficial.item.RailItem;
import com.portofino.realtrainmodunofficial.item.TrainItem;
import com.portofino.realtrainmodunofficial.item.InstalledObjectItem;
import com.portofino.realtrainmodunofficial.item.SignalCommunicatorItem;
import com.portofino.realtrainmodunofficial.item.TrainVehicleItem;
import com.portofino.realtrainmodunofficial.item.WireItem;
import com.portofino.realtrainmodunofficial.item.WrenchItem;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModUnofficialItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealTrainModUnofficial.MODID);

    public static final DeferredItem<InstalledObjectItem> CROSSING_GATE_ITEM = ITEMS.register(
        "crossing_gate", () -> new InstalledObjectItem(InstalledObjectCategory.CROSSING)
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SIGNAL_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem(
        "signal_receiver", RealTrainModUnofficialBlocks.SIGNAL_RECEIVER
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SIGNAL_VALUE_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem(
        "signal_value_receiver", RealTrainModUnofficialBlocks.SIGNAL_VALUE_RECEIVER
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> TRAIN_DETECTOR_ITEM = ITEMS.registerSimpleBlockItem(
        "train_detector", RealTrainModUnofficialBlocks.TRAIN_DETECTOR
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SIGNAL_STATE_ITEM = ITEMS.registerSimpleBlockItem(
        "signal_state", RealTrainModUnofficialBlocks.SIGNAL_STATE
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SCRIPT_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
        "script_block", RealTrainModUnofficialBlocks.SCRIPT_BLOCK
    );
    // 道床(ballast)アイテムは廃止 (ユーザー要望「道床を消す・アイテムからも」)。
    // 道床ブロックはレール敷設時に配置せず(不可視の当たり判定のみ)、入手アイテムも登録しない。
    public static final DeferredItem<SignalCommunicatorItem> SIGNAL_COMMUNICATOR_ITEM = ITEMS.register(
        "signal_communicator", SignalCommunicatorItem::new
    );
    public static final DeferredItem<MarkerItem> MARKER_ITEM = ITEMS.register(
        "marker", () -> new MarkerItem(RealTrainModUnofficialBlocks.MARKER.get(), false)
    );
    public static final DeferredItem<MarkerItem> MARKER_DIAGONAL_ITEM = ITEMS.register(
        "marker_diagonal", () -> new MarkerItem(RealTrainModUnofficialBlocks.MARKER.get(), true)
    );
    public static final DeferredItem<MarkerItem> MARKER_SWITCH_ITEM = ITEMS.register(
        "marker_switch", () -> new MarkerItem(RealTrainModUnofficialBlocks.MARKER_SWITCH.get(), false)
    );
    public static final DeferredItem<MarkerItem> MARKER_SWITCH_DIAGONAL_ITEM = ITEMS.register(
        "marker_switch_diagonal", () -> new MarkerItem(RealTrainModUnofficialBlocks.MARKER_SWITCH.get(), true)
    );
    public static final DeferredItem<RailItem> RAIL_ITEM = ITEMS.register(
        "rail", RailItem::new
    );
    public static final DeferredItem<TrainItem> TRAIN_ITEM = ITEMS.register(
        "train", () -> new TrainItem(TrainItem.Category.ELECTRIC)
    );
    public static final DeferredItem<TrainItem> TEST_TRAIN_ITEM = ITEMS.register(
        "test_train", () -> new TrainItem(TrainItem.Category.TEST)
    );
    public static final DeferredItem<TrainVehicleItem> TRAIN_VEHICLE_ITEM = ITEMS.register(
        "train_vehicle", TrainVehicleItem::new
    );
    public static final DeferredItem<CarItem> CAR_ITEM = ITEMS.register(
        "car", CarItem::new
    );
    public static final DeferredItem<IcCardItem> IC_CARD_ITEM = ITEMS.register(
        "ic_card", IcCardItem::new
    );
    public static final DeferredItem<CrowbarItem> CROWBAR_ITEM = ITEMS.register(
        "crowbar", CrowbarItem::new
    );
    public static final DeferredItem<WrenchItem> WRENCH_ITEM = ITEMS.register(
        "wrench", WrenchItem::new
    );
    public static final DeferredItem<WireItem> WIRE_ITEM = ITEMS.register(
        "wire", WireItem::new
    );
    public static final DeferredItem<InstalledObjectItem> LIGHT_ITEM = ITEMS.register(
        "light", () -> new InstalledObjectItem(InstalledObjectCategory.LIGHT)
    );
    public static final DeferredItem<InstalledObjectItem> SIGNBOARD_ITEM = ITEMS.register(
        "signboard", () -> new InstalledObjectItem(InstalledObjectCategory.SIGNBOARD)
    );
    public static final DeferredItem<InstalledObjectItem> INSULATOR_ITEM = ITEMS.register(
        "insulator", () -> new InstalledObjectItem(InstalledObjectCategory.INSULATOR)
    );
    public static final DeferredItem<InstalledObjectItem> SIGNAL_ITEM = ITEMS.register(
        "signal", () -> new InstalledObjectItem(InstalledObjectCategory.SIGNAL)
    );
    public static final DeferredItem<InstalledObjectItem> OVERHEAD_LINE_POLE_ITEM = ITEMS.register(
        "overhead_line_pole", () -> new InstalledObjectItem(InstalledObjectCategory.INSULATOR)
    );
    public static final DeferredItem<InstalledObjectItem> TICKET_GATE_ITEM = ITEMS.register(
        "ticket_gate", () -> new InstalledObjectItem(InstalledObjectCategory.TICKET_GATE)
    );
    public static final DeferredItem<InstalledObjectItem> SPEAKER_ITEM = ITEMS.register(
        "speaker", () -> new InstalledObjectItem(InstalledObjectCategory.SPEAKER)
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ATSA_GROUND_UNIT_ITEM = ITEMS.registerSimpleBlockItem(
        "atsa_ground_unit", RealTrainModUnofficialBlocks.ATSA_GROUND_UNIT
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ATSA_IFTTT_ITEM = ITEMS.registerSimpleBlockItem(
        "atsa_ifttt", RealTrainModUnofficialBlocks.ATSA_IFTTT
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ATSA_STATION_ANNOUNCE_ITEM = ITEMS.registerSimpleBlockItem(
        "atsa_station_announce", RealTrainModUnofficialBlocks.ATSA_STATION_ANNOUNCE
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.compat.atsassist.item.TrainProtectionSelectorItem> ATSA_TRAIN_PROTECTION_SELECTOR = ITEMS.register(
        "atsa_train_protection_selector", com.portofino.realtrainmodunofficial.compat.atsassist.item.TrainProtectionSelectorItem::new
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.compat.atsassist.item.DataMapEditorItem> ATSA_DATA_MAP_EDITOR = ITEMS.register(
        "atsa_data_map_editor", com.portofino.realtrainmodunofficial.compat.atsassist.item.DataMapEditorItem::new
    );
}
