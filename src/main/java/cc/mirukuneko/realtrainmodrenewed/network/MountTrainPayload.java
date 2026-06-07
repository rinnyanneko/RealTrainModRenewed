package cc.mirukuneko.realtrainmodrenewed.network;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MountTrainPayload() implements CustomPacketPayload {
    public static final MountTrainPayload INSTANCE = new MountTrainPayload();
    public static final Type<MountTrainPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "mount_train")
    );
    public static final StreamCodec<ByteBuf, MountTrainPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(MountTrainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                boolean holdingCrowbar = player.getMainHandItem().is(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                    || player.getOffhandItem().is(RealTrainModRenewedItems.CROWBAR_ITEM.get());
                if (holdingCrowbar) {
                    TrainEntity.tryEnterCouplingModeFromPlayerView(player);
                } else {
                    TrainEntity.tryRideFromPlayerView(player);
                }
            }
        });
    }
}
