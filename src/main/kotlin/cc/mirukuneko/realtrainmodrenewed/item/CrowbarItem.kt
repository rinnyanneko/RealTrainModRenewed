package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.level.block.BreakBlockEvent

class CrowbarItem : Item {
    constructor() : this(Properties().stacksTo(1))
    constructor(properties: Properties) : super(properties)

    companion object {
        @JvmStatic
        fun onBreakBlock(event: BreakBlockEvent) {
            val player = event.player ?: return
            if (isHoldingCrowbar(player)) event.isCanceled = true
        }

        @JvmStatic
        fun onAttackEntity(event: AttackEntityEvent) {
            val player = event.entity ?: return
            if (!isHoldingCrowbar(player)) return
            val target = event.target
            if (target is TrainEntity || target is TrainBogieEntity
                || target is TrainSeatEntity || target is CarEntity) return
            event.isCanceled = true
        }

        private fun isHoldingCrowbar(player: Player): Boolean =
            player.mainHandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                || player.offhandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
    }
}
