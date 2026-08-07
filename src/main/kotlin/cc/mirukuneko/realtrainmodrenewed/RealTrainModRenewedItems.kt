// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.item.CarItem
import cc.mirukuneko.realtrainmodrenewed.item.CrowbarItem
import cc.mirukuneko.realtrainmodrenewed.item.IcCardItem
import cc.mirukuneko.realtrainmodrenewed.item.MarkerItem
import cc.mirukuneko.realtrainmodrenewed.item.RailItem
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem
import cc.mirukuneko.realtrainmodrenewed.item.InstalledObjectItem
import cc.mirukuneko.realtrainmodrenewed.item.TrainVehicleItem
import cc.mirukuneko.realtrainmodrenewed.item.WireItem
import cc.mirukuneko.realtrainmodrenewed.item.WrenchItem
import cc.mirukuneko.realtrainmodrenewed.item.SignalConverterItem
import cc.mirukuneko.realtrainmodrenewed.electric.SignalConverterType
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import net.minecraft.world.item.Item
import net.minecraft.world.item.BlockItem
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Function
import java.util.function.Supplier

object RealTrainModRenewedItems {
    @JvmField
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(RealTrainModRenewed.MODID)

    @JvmField val CROSSING_GATE_ITEM: DeferredItem<InstalledObjectItem> = ITEMS.registerItem("crossing_gate", Function { props: Item.Properties -> InstalledObjectItem(InstalledObjectCategory.CROSSING, props) })
    @JvmField val MARKER_ITEM: DeferredItem<MarkerItem> = ITEMS.registerItem("marker", Function { props: Item.Properties -> MarkerItem(RealTrainModRenewedBlocks.MARKER.get(), false, props) })
    @JvmField val MARKER_DIAGONAL_ITEM: DeferredItem<MarkerItem> = ITEMS.registerItem("marker_diagonal", Function { props: Item.Properties -> MarkerItem(RealTrainModRenewedBlocks.MARKER.get(), true, props) })
    @JvmField val MARKER_SWITCH_ITEM: DeferredItem<MarkerItem> = ITEMS.registerItem("marker_switch", Function { props: Item.Properties -> MarkerItem(RealTrainModRenewedBlocks.MARKER_SWITCH.get(), false, props) })
    @JvmField val MARKER_SWITCH_DIAGONAL_ITEM: DeferredItem<MarkerItem> = ITEMS.registerItem("marker_switch_diagonal", Function { props: Item.Properties -> MarkerItem(RealTrainModRenewedBlocks.MARKER_SWITCH.get(), true, props) })
    @JvmField val RAIL_ITEM: DeferredItem<RailItem> = ITEMS.registerItem("rail", Function { props: Item.Properties -> RailItem(props) })
    @JvmField val TRAIN_ITEM: DeferredItem<TrainItem> = ITEMS.registerItem("train", Function { props: Item.Properties -> TrainItem(TrainItem.Category.ELECTRIC, props) })
    @JvmField val TRAIN_VEHICLE_ITEM: DeferredItem<TrainVehicleItem> = ITEMS.registerItem("train_vehicle", Function { props: Item.Properties -> TrainVehicleItem(props) })
    @JvmField val CAR_ITEM: DeferredItem<CarItem> = ITEMS.registerItem("car", Function { props: Item.Properties -> CarItem(props) })
    @JvmField val IC_CARD_ITEM: DeferredItem<IcCardItem> = ITEMS.registerItem("ic_card", Function { props: Item.Properties -> IcCardItem(props) })
    @JvmField val CROWBAR_ITEM: DeferredItem<CrowbarItem> = ITEMS.registerItem("crowbar", Function { props: Item.Properties -> CrowbarItem(props) })
    @JvmField val WRENCH_ITEM: DeferredItem<WrenchItem> = ITEMS.registerItem("wrench", Function { props: Item.Properties -> WrenchItem(props) })
    @JvmField val WIRE_ITEM: DeferredItem<WireItem> = ITEMS.registerItem("wire", Function { props: Item.Properties -> WireItem(props) })
    @JvmField val SIGNAL_CONVERTER_ITEM: DeferredItem<SignalConverterItem> = ITEMS.registerItem("signal_converter", Function { props: Item.Properties -> SignalConverterItem(RealTrainModRenewedBlocks.SIGNAL_CONVERTER.get(), SignalConverterType.RS_INPUT, props) })
    @JvmField val SIGNAL_CONVERTER_RS_ITEM: DeferredItem<SignalConverterItem> = ITEMS.registerItem("signal_converter_rs", Function { props: Item.Properties -> SignalConverterItem(RealTrainModRenewedBlocks.SIGNAL_CONVERTER.get(), SignalConverterType.RS_OUTPUT, props) })
    @JvmField val SIGNAL_CONVERTER_INCREMENT_ITEM: DeferredItem<SignalConverterItem> = ITEMS.registerItem("signal_converter_increment", Function { props: Item.Properties -> SignalConverterItem(RealTrainModRenewedBlocks.SIGNAL_CONVERTER.get(), SignalConverterType.INCREMENT, props) })
    @JvmField val SIGNAL_CONVERTER_DECREMENT_ITEM: DeferredItem<SignalConverterItem> = ITEMS.registerItem("signal_converter_decrement", Function { props: Item.Properties -> SignalConverterItem(RealTrainModRenewedBlocks.SIGNAL_CONVERTER.get(), SignalConverterType.DECREMENT, props) })
    @JvmField val SIGNAL_CONVERTER_WIRELESS_ITEM: DeferredItem<SignalConverterItem> = ITEMS.registerItem("signal_converter_wireless", Function { props: Item.Properties -> SignalConverterItem(RealTrainModRenewedBlocks.SIGNAL_CONVERTER.get(), SignalConverterType.WIRELESS, props) })
    @JvmField val SIGNAL_RECEIVER_ITEM: DeferredItem<BlockItem> = ITEMS.registerItem("signal_receiver", Function { props: Item.Properties -> BlockItem(RealTrainModRenewedBlocks.SIGNAL_RECEIVER.get(), props) })
    @JvmField val SIGNAL_CHANGER_ITEM: DeferredItem<BlockItem> = ITEMS.registerItem("signal_changer", Function { props: Item.Properties -> BlockItem(RealTrainModRenewedBlocks.SIGNAL_CHANGER.get(), props) })
    @JvmField val SIGNAL_VALUE_RECEIVER_ITEM: DeferredItem<BlockItem> = ITEMS.registerItem("signal_value_receiver", Function { props: Item.Properties -> BlockItem(RealTrainModRenewedBlocks.SIGNAL_VALUE_RECEIVER.get(), props) })
    @JvmField val TRAIN_DETECTOR_ITEM: DeferredItem<BlockItem> = ITEMS.registerItem("train_detector", Function { props: Item.Properties -> BlockItem(RealTrainModRenewedBlocks.TRAIN_DETECTOR.get(), props) })
    @JvmField val SIGNAL_STATE_ITEM: DeferredItem<BlockItem> = ITEMS.registerItem("signal_state", Function { props: Item.Properties -> BlockItem(RealTrainModRenewedBlocks.SIGNAL_STATE.get(), props) })
    @JvmField val LIGHT_ITEM: DeferredItem<InstalledObjectItem> = ITEMS.registerItem("light", Function { props: Item.Properties -> InstalledObjectItem(InstalledObjectCategory.LIGHT, props) })
    @JvmField val INSULATOR_ITEM: DeferredItem<InstalledObjectItem> = ITEMS.registerItem("insulator", Function { props: Item.Properties -> InstalledObjectItem(InstalledObjectCategory.INSULATOR, props) })
    @JvmField val SIGNAL_ITEM: DeferredItem<InstalledObjectItem> = ITEMS.registerItem("signal", Function { props: Item.Properties -> InstalledObjectItem(InstalledObjectCategory.SIGNAL, props) })
    @JvmField val OVERHEAD_LINE_POLE_ITEM: DeferredItem<InstalledObjectItem> = ITEMS.registerItem("overhead_line_pole", Function { props: Item.Properties -> InstalledObjectItem(InstalledObjectCategory.OVERHEAD_LINE_POLE, props) })
    @JvmField val TICKET_GATE_ITEM: DeferredItem<InstalledObjectItem> = ITEMS.registerItem("ticket_gate", Function { props: Item.Properties -> InstalledObjectItem(InstalledObjectCategory.TICKET_GATE, props) })
    @JvmField val SPEAKER_ITEM: DeferredItem<InstalledObjectItem> = ITEMS.registerItem("speaker", Function { props: Item.Properties -> InstalledObjectItem(InstalledObjectCategory.SPEAKER, props) })
    @JvmField val SCRIPT_BLOCK_ITEM: DeferredItem<BlockItem> = ITEMS.registerItem("script_block", Function { props: Item.Properties -> BlockItem(RealTrainModRenewedBlocks.SCRIPT_BLOCK.get(), props) })
}
