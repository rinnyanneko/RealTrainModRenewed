package cc.mirukuneko.realtrainmodrenewed.network;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalRemoteBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect;
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetSignalAspectPayload(BlockPos pos, int aspectId) implements CustomPacketPayload {
    public static final Type<SetSignalAspectPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "set_signal_aspect")
    );

    public static final StreamCodec<ByteBuf, SetSignalAspectPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetSignalAspectPayload::pos,
        ByteBufCodecs.INT,
        SetSignalAspectPayload::aspectId,
        SetSignalAspectPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetSignalAspectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof SignalRemoteBlockEntity blockEntity)) {
                return;
            }
            int linkedChannel = blockEntity.getLinkedChannel();
            if (linkedChannel <= 0) {
                player.sendOverlayMessage(Component.literal("この変更機はまだ信号に接続されていません"));
                return;
            }
            SignalNetworkSavedData data = SignalNetworkSavedData.get(player.level());
            if (!data.hasChannel(linkedChannel)) {
                player.sendOverlayMessage(Component.literal("信号番号が無効になっています"));
                return;
            }
            SignalAspect aspect = SignalAspect.byId(payload.aspectId());
            data.setAspect(player.level().getServer(), linkedChannel, aspect);
            player.sendOverlayMessage(Component.literal(aspect.getLabel() + " に変更しました"));
        });
    }
}

