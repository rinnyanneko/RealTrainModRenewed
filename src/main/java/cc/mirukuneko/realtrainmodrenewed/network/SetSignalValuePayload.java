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

public record SetSignalValuePayload(BlockPos pos, int signalValue) implements CustomPacketPayload {
    public static final Type<SetSignalValuePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "set_signal_value")
    );

    public static final StreamCodec<ByteBuf, SetSignalValuePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetSignalValuePayload::pos,
        ByteBufCodecs.INT,
        SetSignalValuePayload::signalValue,
        SetSignalValuePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetSignalValuePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof SignalRemoteBlockEntity blockEntity)) {
                return;
            }
            int linkedChannel = blockEntity.getLinkedChannel();
            if (linkedChannel <= 0) {
                player.sendOverlayMessage(Component.literal("先に信号番号を入力してください"));
                return;
            }
            SignalNetworkSavedData data = SignalNetworkSavedData.get(player.level());
            if (!data.hasChannel(linkedChannel)) {
                player.sendOverlayMessage(Component.literal("信号番号が無効になっています"));
                return;
            }
            SignalAspect aspect = SignalAspect.byLegacyValue(payload.signalValue());
            data.setAspect(player.level().getServer(), linkedChannel, aspect);
            player.sendOverlayMessage(Component.literal("signal値 " + payload.signalValue() + " を送信しました"));
        });
    }
}

