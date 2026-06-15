package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.InstalledObjectCategory
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object ClientHooks {
    private const val CLIENT_HOOKS_CLASS = "cc.mirukuneko.realtrainmodrenewed.client.ClientHooksClient"

    @JvmStatic
    fun openRailSelectScreen(player: Player, stack: ItemStack) {
        invokeClient("openRailSelectScreen", arrayOf(Player::class.java, ItemStack::class.java), player, stack)
    }

    @JvmStatic
    fun openTrainSelectScreen(player: Player, stack: ItemStack, category: TrainItem.Category) {
        invokeClient(
            "openTrainSelectScreen",
            arrayOf(Player::class.java, ItemStack::class.java, TrainItem.Category::class.java),
            player,
            stack,
            category,
        )
    }

    @JvmStatic
    fun openTrainSelectScreen(player: Player, stack: ItemStack) {
        invokeClient("openTrainSelectScreen", arrayOf(Player::class.java, ItemStack::class.java), player, stack)
    }

    @JvmStatic
    fun openVehicleFormationScreen(stack: ItemStack) {
        invokeClient("openVehicleFormationScreen", arrayOf(ItemStack::class.java), stack)
    }

    @JvmStatic
    fun openCarSelectScreen(player: Player, stack: ItemStack) {
        invokeClient("openCarSelectScreen", arrayOf(Player::class.java, ItemStack::class.java), player, stack)
    }

    @JvmStatic
    fun openInstalledObjectSelectScreen(player: Player, stack: ItemStack, category: InstalledObjectCategory) {
        invokeClient(
            "openInstalledObjectSelectScreen",
            arrayOf(Player::class.java, ItemStack::class.java, InstalledObjectCategory::class.java),
            player,
            stack,
            category,
        )
    }

    @JvmStatic
    fun openSignalChangerScreen(pos: BlockPos) {
        invokeClient("openSignalChangerScreen", arrayOf(BlockPos::class.java), pos)
    }

    @JvmStatic
    fun openSignalReceiverScreen(pos: BlockPos) {
        invokeClient("openSignalReceiverScreen", arrayOf(BlockPos::class.java), pos)
    }

    @JvmStatic
    fun openSignalValueScreen(pos: BlockPos) {
        invokeClient("openSignalValueScreen", arrayOf(BlockPos::class.java), pos)
    }

    @JvmStatic
    fun openTrainDetectorScreen(pos: BlockPos) {
        invokeClient("openTrainDetectorScreen", arrayOf(BlockPos::class.java), pos)
    }

    @JvmStatic
    fun openMarkerConfigScreen(pos: BlockPos) {
        invokeClient("openMarkerConfigScreen", arrayOf(BlockPos::class.java), pos)
    }

    @JvmStatic
    fun openSpeakerScreen(pos: BlockPos) {
        invokeClient("openSpeakerScreen", arrayOf(BlockPos::class.java), pos)
    }

    @JvmStatic
    fun openScriptBlockScreen(pos: BlockPos) {
        invokeClient("openScriptBlockScreen", arrayOf(BlockPos::class.java), pos)
    }

    @JvmStatic
    fun stopCrossingGateSound(level: Level, pos: BlockPos) {
        invokeClient("stopCrossingGateSound", arrayOf(Level::class.java, BlockPos::class.java), level, pos)
    }

    @JvmStatic
    fun tickCrossingGateSound(blockEntity: InstalledObjectBlockEntity) {
        invokeClient("tickCrossingGateSound", arrayOf(InstalledObjectBlockEntity::class.java), blockEntity)
    }

    @JvmStatic
    fun showScriptErrorMessage(message: String) {
        invokeClient("showScriptErrorMessage", arrayOf(String::class.java), message)
    }

    private fun invokeClient(methodName: String, parameterTypes: Array<Class<*>>, vararg args: Any?) {
        try {
            val hooks = Class.forName(CLIENT_HOOKS_CLASS)
            hooks.getMethod(methodName, *parameterTypes).invoke(null, *args)
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.debug("Client hook {} failed", methodName, e)
        }
    }
}
