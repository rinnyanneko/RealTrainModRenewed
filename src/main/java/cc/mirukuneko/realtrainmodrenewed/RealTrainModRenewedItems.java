package cc.mirukuneko.realtrainmodrenewed;

import cc.mirukuneko.realtrainmodrenewed.item.CarItem;
import cc.mirukuneko.realtrainmodrenewed.item.CrowbarItem;
import cc.mirukuneko.realtrainmodrenewed.item.IcCardItem;
import cc.mirukuneko.realtrainmodrenewed.item.MarkerItem;
import cc.mirukuneko.realtrainmodrenewed.item.RailItem;
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem;
import cc.mirukuneko.realtrainmodrenewed.item.InstalledObjectItem;
import cc.mirukuneko.realtrainmodrenewed.item.TrainVehicleItem;
import cc.mirukuneko.realtrainmodrenewed.item.WireItem;
import cc.mirukuneko.realtrainmodrenewed.item.WrenchItem;
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModRenewedItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealTrainModRenewed.MODID);

    public static final DeferredItem<InstalledObjectItem> CROSSING_GATE_ITEM = ITEMS.registerItem(
        "crossing_gate", props -> new InstalledObjectItem(InstalledObjectCategory.CROSSING, props)
    );
    // 以下のアイテムはユーザー要望により削除:
    //   受信機(signal_receiver) / 受信機シグナル値(signal_value_receiver) / 電車検知ブロック(train_detector)
    //   状態ブロック(signal_state) / スクリプトブロック(script_block) / 通信機(signal_communicator)
    // 道床(ballast)アイテムも廃止済み。ブロック自体は残るがアイテム(入手手段)は登録しない。
    public static final DeferredItem<MarkerItem> MARKER_ITEM = ITEMS.registerItem(
        "marker", props -> new MarkerItem(RealTrainModRenewedBlocks.MARKER.get(), false, props)
    );
    public static final DeferredItem<MarkerItem> MARKER_DIAGONAL_ITEM = ITEMS.registerItem(
        "marker_diagonal", props -> new MarkerItem(RealTrainModRenewedBlocks.MARKER.get(), true, props)
    );
    public static final DeferredItem<MarkerItem> MARKER_SWITCH_ITEM = ITEMS.registerItem(
        "marker_switch", props -> new MarkerItem(RealTrainModRenewedBlocks.MARKER_SWITCH.get(), false, props)
    );
    public static final DeferredItem<MarkerItem> MARKER_SWITCH_DIAGONAL_ITEM = ITEMS.registerItem(
        "marker_switch_diagonal", props -> new MarkerItem(RealTrainModRenewedBlocks.MARKER_SWITCH.get(), true, props)
    );
    public static final DeferredItem<RailItem> RAIL_ITEM = ITEMS.registerItem(
        "rail", RailItem::new
    );
    public static final DeferredItem<TrainItem> TRAIN_ITEM = ITEMS.registerItem(
        "train", props -> new TrainItem(TrainItem.Category.ELECTRIC, props)
    );
    // 試験用車両(test_train)はユーザー要望により削除。
    public static final DeferredItem<TrainVehicleItem> TRAIN_VEHICLE_ITEM = ITEMS.registerItem(
        "train_vehicle", props -> new TrainVehicleItem(props.stacksTo(1))
    );
    public static final DeferredItem<CarItem> CAR_ITEM = ITEMS.registerItem(
        "car", props -> new CarItem(props.stacksTo(1))
    );
    public static final DeferredItem<IcCardItem> IC_CARD_ITEM = ITEMS.registerItem(
        "ic_card", props -> new IcCardItem(props.stacksTo(1))
    );
    public static final DeferredItem<CrowbarItem> CROWBAR_ITEM = ITEMS.registerItem(
        "crowbar", props -> new CrowbarItem(props.stacksTo(1))
    );
    public static final DeferredItem<WrenchItem> WRENCH_ITEM = ITEMS.registerItem(
        "wrench", props -> new WrenchItem(props.stacksTo(1))
    );
    public static final DeferredItem<WireItem> WIRE_ITEM = ITEMS.registerItem(
        "wire", WireItem::new
    );
    // 照明(light): 本家RTM の照明アイテム。外部パックのモデルを使用し、レッドストーン
    // 信号を受けると点灯する(InstalledObjectBlock 側で発光処理)。
    public static final DeferredItem<InstalledObjectItem> LIGHT_ITEM = ITEMS.registerItem(
        "light", props -> new InstalledObjectItem(InstalledObjectCategory.LIGHT, props)
    );
    public static final DeferredItem<InstalledObjectItem> INSULATOR_ITEM = ITEMS.registerItem(
        "insulator", props -> new InstalledObjectItem(InstalledObjectCategory.INSULATOR, props)
    );
    public static final DeferredItem<InstalledObjectItem> SIGNAL_ITEM = ITEMS.registerItem(
        "signal", props -> new InstalledObjectItem(InstalledObjectCategory.SIGNAL, props)
    );
    public static final DeferredItem<InstalledObjectItem> OVERHEAD_LINE_POLE_ITEM = ITEMS.registerItem(
        "overhead_line_pole", props -> new InstalledObjectItem(InstalledObjectCategory.OVERHEAD_LINE_POLE, props)
    );
    public static final DeferredItem<InstalledObjectItem> TICKET_GATE_ITEM = ITEMS.registerItem(
        "ticket_gate", props -> new InstalledObjectItem(InstalledObjectCategory.TICKET_GATE, props)
    );
    public static final DeferredItem<InstalledObjectItem> SPEAKER_ITEM = ITEMS.registerItem(
        "speaker", props -> new InstalledObjectItem(InstalledObjectCategory.SPEAKER, props)
    );
}
