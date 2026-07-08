package cc.mirukuneko.realtrainmodrenewed.item

import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * 信号へ番号を振り直すための通信機です。
 */
class SignalCommunicatorItem : Item {
    constructor() : this(Properties().stacksTo(1))
    constructor(properties: Properties) : super(properties)

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        if (level !is ServerLevel || context.player == null)
            return if (level.isClientSide) InteractionResult.SUCCESS else InteractionResult.PASS
        val pos = context.clickedPos
        val be = level.getBlockEntity(pos)
        if (be !is InstalledObjectBlockEntity || !be.isSignal) return InteractionResult.PASS
        val data = SignalNetworkSavedData.get(level)
        val newChannel = data.assignNewChannel(level, pos, be.signalChannel, SignalAspect.byId(be.signalAspect))
        be.setSignalChannel(newChannel, false)
        be.setSignalAspect(SignalAspect.STOP, true)
        context.player!!.sendSystemMessage(Component.literal("信号番号: $newChannel"))
        return InteractionResult.SUCCESS
    }
}
