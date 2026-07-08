package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
// ClientHooks import not available
import cc.mirukuneko.realtrainmodrenewed.entity.formation.FormationManager
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class VehicleFormationItem(properties: Properties) : Item(properties) {
    constructor() : this(Properties().stacksTo(1))

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val level = context.level
        val stack = context.itemInHand

        if (level.isClientSide) {
            // ClientHooks screen call removed
            return InteractionResult.SUCCESS
        }

        val formationId = stack.get(RealTrainModRenewedComponents.SELECTED_MODEL_ID.get()) ?: ""
        if (formationId.isEmpty()) return InteractionResult.PASS

        val formation = FormationManager.instance.getFormation(formationId)
        if (formation == null) {
            player.sendSystemMessage(Component.literal("Formation not found: $formationId"))
            return InteractionResult.FAIL
        }

        player.sendSystemMessage(Component.literal("Formation: $formationId, size: ${formation.size()}"))
        return InteractionResult.SUCCESS
    }
}
