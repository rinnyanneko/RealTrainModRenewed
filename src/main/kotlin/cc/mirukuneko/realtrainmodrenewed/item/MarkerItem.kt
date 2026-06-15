package cc.mirukuneko.realtrainmodrenewed.item

import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block

class MarkerItem : BlockItem {
    @JvmField val diagonal: Boolean

    constructor(block: Block, diagonal: Boolean) : this(block, diagonal, Properties())
    constructor(block: Block, diagonal: Boolean, properties: Properties) : super(block, properties) {
        this.diagonal = diagonal
    }
}
