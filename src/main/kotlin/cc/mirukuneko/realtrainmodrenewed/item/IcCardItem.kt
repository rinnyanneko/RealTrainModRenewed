// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.item

import net.minecraft.world.item.Item

class IcCardItem : Item {
    constructor() : this(Properties().stacksTo(1))
    constructor(properties: Properties) : super(properties)
}
