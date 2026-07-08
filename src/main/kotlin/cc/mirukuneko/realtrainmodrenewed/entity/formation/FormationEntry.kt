// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.entity.formation

import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity

class FormationEntry @JvmOverloads constructor(
    @JvmField val train: TrainEntity,
    @JvmField var entryId: Int,
    @JvmField var dir: Int,
    @JvmField var leaderSide: Int = -1,
    @JvmField var followerSide: Int = 1,
)
