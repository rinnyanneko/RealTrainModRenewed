package cc.mirukuneko.realtrainmodrenewed.client

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.client.screen.ModelSelectScreen
import cc.mirukuneko.realtrainmodrenewed.client.screen.TrainFormationScreen
import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectRegistry
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem
import cc.mirukuneko.realtrainmodrenewed.network.SelectModelPayload
import cc.mirukuneko.realtrainmodrenewed.rail.RailRegistry
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.util.Locale

object ClientItemHelper {
    private const val HIDDEN_TRAIN_PACK = "basic_train"

    @JvmStatic
    fun openRailSelectScreen(player: Player, stack: ItemStack) {
        val infos = RailRegistry.getAll()
            .map { definition ->
                ModelSelectScreen.ModelInfo(
                    RailRegistry.getSelectionId(definition),
                    definition.displayName,
                    definition.packName,
                    definition.buttonTexture,
                )
            }
        logSelector("rail", infos)
        Minecraft.getInstance().setScreen(
            ModelSelectScreen(
                Component.translatable("screen.realtrainmodrenewed.select_rail"),
                infos,
                ::sendSelectedModel,
                LegacyItemStackBridge.getSelectedModelId(stack),
                LegacyItemStackBridge.getSelectedDataMap(stack),
            ),
        )
    }

    @JvmStatic
    fun openTrainSelectScreen(player: Player, stack: ItemStack) {
        openTrainSelectScreen(player, stack, TrainItem.Category.ELECTRIC)
    }

    @JvmStatic
    fun openTrainSelectScreen(player: Player, stack: ItemStack, category: TrainItem.Category) {
        val infos = getVisibleTrainModels()
            .filter { info -> TrainItem.accepts(category, VehicleRegistry.getById(info.id ?: "")) }
        logSelector("train/${category.name.lowercase(Locale.ROOT)}", infos)
        Minecraft.getInstance().setScreen(
            ModelSelectScreen(
                Component.translatable("screen.realtrainmodrenewed.select_train"),
                infos,
                ::sendSelectedModel,
                LegacyItemStackBridge.getSelectedModelId(stack),
                LegacyItemStackBridge.getSelectedDataMap(stack),
            ),
        )
    }

    @JvmStatic
    fun openTrainSelectScreen(formationScreen: TrainFormationScreen) {
        val infos = getVisibleTrainModels()
        logSelector("train/formation", infos)
        Minecraft.getInstance().setScreen(
            ModelSelectScreen(
                Component.translatable("screen.realtrainmodrenewed.select_train"),
                infos,
                { selection -> formationScreen.updateFormationWithVehicle(selection.modelId ?: "") },
                null,
                "",
            ),
        )
    }

    @JvmStatic
    fun openTrainSelectScreen() {
        val infos = getVisibleTrainModels()
        logSelector("train", infos)
        Minecraft.getInstance().setScreen(
            ModelSelectScreen(
                Component.translatable("screen.realtrainmodrenewed.select_train"),
                infos,
                ::sendSelectedModel,
                null,
                "",
            ),
        )
    }

    private fun getVisibleTrainModels(): List<ModelSelectScreen.ModelInfo> {
        // basic_train は初期確認用の内蔵車両なので、通常の選択画面には出さない。
        return VehicleRegistry.getAll()
            .filter(::shouldShowTrainModel)
            .sortedWith(
                compareBy<VehicleDefinition, String>(CASE_INSENSITIVE_ORDER) { safe(it.getVehicleType()) }
                    .thenBy(CASE_INSENSITIVE_ORDER) { safe(it.getDisplayName()) },
            )
            .map { definition ->
                ModelSelectScreen.ModelInfo(
                    VehicleRegistry.getSelectionId(definition),
                    definition.getDisplayName(),
                    definition.getPackName(),
                    definition.getButtonTexture(),
                    definition.getVehicleType(),
                )
            }
    }

    private fun safe(value: String?): String = value ?: ""

    private fun shouldShowTrainModel(definition: VehicleDefinition?): Boolean {
        if (definition == null) {
            return false
        }
        if (definition.isCarType()) {
            return false
        }
        if (definition.getModelFile().isBlank() || definition.getBogies().isEmpty()) {
            return false
        }
        // 内蔵の basic_train 系は packName だけでなく id / displayName 由来で残る場合がある。
        val packName = definition.getPackName()
        val id = definition.getId().lowercase(Locale.ROOT)
        val displayName = definition.getDisplayName()
        val displayNameLower = displayName.lowercase(Locale.ROOT)
        val buttonTexture = definition.getButtonTexture().lowercase(Locale.ROOT)
        // [RTM]SL_D51_v1.2 contains stale DD51-498 entries whose button textures are not bundled.
        // The pack author confirmed they are not intended selectable vehicles.
        if ((id.startsWith("dd51-498") || displayNameLower.startsWith("dd51-498")) &&
            buttonTexture.contains("button_dd51-498")
        ) {
            return false
        }
        return !HIDDEN_TRAIN_PACK.equals(packName, ignoreCase = true) &&
            !id.contains(HIDDEN_TRAIN_PACK) &&
            !displayNameLower.contains(HIDDEN_TRAIN_PACK)
    }

    @JvmStatic
    fun openCarSelectScreen(player: Player, stack: ItemStack) {
        val infos = VehicleRegistry.getAll()
            .filter { definition -> definition.isCarType() }
            .sortedWith(compareBy<VehicleDefinition, String>(CASE_INSENSITIVE_ORDER) { definition -> safe(definition.getDisplayName()) })
            .map { definition ->
                ModelSelectScreen.ModelInfo(
                    VehicleRegistry.getSelectionId(definition),
                    definition.getDisplayName(),
                    definition.getPackName(),
                    definition.getButtonTexture(),
                )
            }
        logSelector("car", infos)
        Minecraft.getInstance().setScreen(
            ModelSelectScreen(
                Component.translatable("screen.realtrainmodrenewed.select_car"),
                infos,
                ::sendSelectedModel,
                LegacyItemStackBridge.getSelectedModelId(stack),
                LegacyItemStackBridge.getSelectedDataMap(stack),
            ),
        )
    }

    @JvmStatic
    fun openVehicleFormationScreen(stack: ItemStack) {
        Minecraft.getInstance().setScreen(TrainFormationScreen(stack))
    }

    @JvmStatic
    fun openInstalledObjectSelectScreen(player: Player, stack: ItemStack, category: InstalledObjectCategory) {
        val infos = InstalledObjectRegistry.getByCategory(category)
            .map { definition ->
                ModelSelectScreen.ModelInfo(
                    definition.id,
                    definition.displayName,
                    definition.packName,
                    definition.buttonTexture,
                )
            }
        logSelector("installed_object/${category.name.lowercase(Locale.ROOT)}", infos)
        Minecraft.getInstance().setScreen(
            ModelSelectScreen(
                Component.translatable(getInstalledObjectTitleKey(category)),
                infos,
                ::sendSelectedModel,
                LegacyItemStackBridge.getSelectedModelId(stack),
                LegacyItemStackBridge.getSelectedDataMap(stack),
            ),
        )
    }

    private fun getInstalledObjectTitleKey(category: InstalledObjectCategory): String =
        when (category) {
            InstalledObjectCategory.LIGHT -> "screen.realtrainmodrenewed.select_light"
            InstalledObjectCategory.SIGNBOARD -> "screen.realtrainmodrenewed.select_signboard"
            InstalledObjectCategory.INSULATOR -> "screen.realtrainmodrenewed.select_insulator"
            InstalledObjectCategory.OVERHEAD_LINE_POLE -> "screen.realtrainmodrenewed.select_overhead_line_pole"
            InstalledObjectCategory.WIRE -> "screen.realtrainmodrenewed.select_wire"
            InstalledObjectCategory.SIGNAL -> "screen.realtrainmodrenewed.select_signal"
            InstalledObjectCategory.CROSSING -> "screen.realtrainmodrenewed.select_crossing"
            InstalledObjectCategory.TICKET_GATE -> "screen.realtrainmodrenewed.select_ticket_gate"
            InstalledObjectCategory.SPEAKER -> "screen.realtrainmodrenewed.select_speaker"
        }

    private fun sendSelectedModel(selection: ModelSelectScreen.SelectionResult) {
        val payload: CustomPacketPayload = SelectModelPayload(selection.modelId ?: "", selection.dataMapValue ?: "")
        val connection = Minecraft.getInstance().connection
        if (connection != null) {
            connection.send(payload)
        }
    }

    private fun logSelector(kind: String, infos: List<ModelSelectScreen.ModelInfo>) {
        val packCount = infos.map { it.packName ?: "" }.filter { it.isNotBlank() }.distinct().size
        val iconCount = infos.count { !it.buttonTexture.isNullOrBlank() }
        RealTrainModRenewed.LOGGER.info(
            "Opening {} selector with {} model(s), {} pack(s), {} explicit button texture(s)",
            kind, infos.size, packCount, iconCount,
        )
    }
}
