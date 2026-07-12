// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.item

import jp.kaiz.atsassistmod.client.ATSAModClientHooks
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

/**
 * Opens the train-protection selector screen on use.
 */
open class TrainProtectionSelectorItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) {
            ATSAModClientHooks.openTrainProtectionSelector()
        }
        return InteractionResult.SUCCESS
    }
}
