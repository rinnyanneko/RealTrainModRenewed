package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge
import cc.mirukuneko.realtrainmodrenewed.ClientHooks
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity
import cc.mirukuneko.realtrainmodrenewed.registry.RealTrainModRenewedEntities
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class CarItem : Item {
    constructor() : this(Properties().stacksTo(1))
    constructor(properties: Properties) : super(properties)

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val stack = context.itemInHand
        val selectedId = LegacyItemStackBridge.getSelectedModelId(stack)
        if (selectedId.isNullOrBlank()) {
            if (level.isClientSide && context.player != null)
                ClientHooks.openCarSelectScreen(context.player!!, stack)
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }
        val def = VehicleRegistry.getById(selectedId)
        if (def == null || !def.isCarType()) {
            if (level.isClientSide && context.player != null)
                ClientHooks.openCarSelectScreen(context.player!!, stack)
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.SUCCESS_SERVER
        }
        if (level.isClientSide) return InteractionResult.SUCCESS
        val pos = context.clickedPos
        val spawnPos = Vec3.atBottomCenterOf(pos.above())
        val type = RealTrainModRenewedEntities.CAR.get()
        val car = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE) ?: return InteractionResult.FAIL
        car.setPos(spawnPos.x, spawnPos.y, spawnPos.z)
        car.yRot = context.player?.yRot ?: 0f
        car.xRot = 0f
        car.setVehicleId(selectedId)
        level.addFreshEntity(car)
        return InteractionResult.SUCCESS_SERVER
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) ClientHooks.openCarSelectScreen(player, player.getItemInHand(hand))
        return InteractionResult.SUCCESS
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: net.minecraft.world.item.component.TooltipDisplay, tooltip: java.util.function.Consumer<Component>, flag: TooltipFlag) {
        val selectedId = LegacyItemStackBridge.getSelectedModelId(stack)
        if (!selectedId.isNullOrBlank()) {
            val def = VehicleRegistry.getById(selectedId)
            val name = def?.displayName ?: selectedId
            tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.selected", name))
        } else {
            tooltip.accept(Component.translatable("tooltip.realtrainmodrenewed.model.none"))
        }
    }
}
