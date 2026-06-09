package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.ScriptBlockEntity
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

@JvmRecord
data class UpdateScriptBlockPayload(
    val pos: BlockPos,
    val script: String,
    val runOnRedstone: Boolean,
    val executeNow: Boolean,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<UpdateScriptBlockPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "update_script_block")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, UpdateScriptBlockPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            { payload -> payload.pos },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.script },
            ByteBufCodecs.BOOL,
            { payload -> payload.runOnRedstone },
            ByteBufCodecs.BOOL,
            { payload -> payload.executeNow },
            ::UpdateScriptBlockPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: UpdateScriptBlockPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val serverLevel = player.level()
                val blockEntity = serverLevel.getBlockEntity(payload.pos) as? ScriptBlockEntity ?: return@enqueueWork
                blockEntity.configure(payload.script, payload.runOnRedstone)
                val executed = payload.executeNow && blockEntity.runScript(serverLevel)
                serverLevel.sendBlockUpdated(payload.pos, blockEntity.blockState, blockEntity.blockState, 3)
                player.sendOverlayMessage(
                    Component.literal(if (executed) "スクリプトを実行しました" else "スクリプトブロックを保存しました")
                )
            }
        }
    }
}
