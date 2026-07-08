package cc.mirukuneko.realtrainmodrenewed.item

import net.minecraft.world.item.Item

class IcCardItem : Item {
    constructor() : this(Properties().stacksTo(1))
    constructor(properties: Properties) : super(properties)
}
