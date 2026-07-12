// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.block

import net.minecraft.world.level.block.Block

/**
 * Station-announce base block. In the original this block had no tile entity and
 * its activation handler did nothing functional.
 */
open class StationAnnounceBlock @JvmOverloads constructor(
    properties: Properties = Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops(),
) : Block(properties)
