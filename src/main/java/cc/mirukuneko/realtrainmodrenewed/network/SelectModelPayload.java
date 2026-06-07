package cc.mirukuneko.realtrainmodrenewed.network;

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed;
import cc.mirukuneko.realtrainmodrenewed.item.CarItem;
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem;
import cc.mirukuneko.realtrainmodrenewed.item.TrainVehicleItem;
import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge;
import cc.mirukuneko.realtrainmodrenewed.item.ModelSelectableItem;
import cc.mirukuneko.realtrainmodrenewed.item.RailItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectModelPayload(String modelId, String dataMapValue) implements CustomPacketPayload {
    public static final Type<SelectModelPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "select_model")
    );
    public static final StreamCodec<ByteBuf, SelectModelPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        SelectModelPayload::modelId,
        ByteBufCodecs.STRING_UTF8,
        SelectModelPayload::dataMapValue,
        SelectModelPayload::new
    );

    public SelectModelPayload(String modelId) {
        this(modelId, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SelectModelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            String safeModelId = payload.modelId() == null ? "" : payload.modelId();
            String safeDataMap = payload.dataMapValue() == null ? "" : payload.dataMapValue();
            
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof TrainVehicleItem) {
                    LegacyItemStackBridge.setSelectedModelData(stack, safeModelId, safeDataMap);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Selected model: " + safeModelId + ". Now right-click on rail to spawn."));
                    break;
                }
            }

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof RailItem
                    || stack.getItem() instanceof TrainItem
                    || stack.getItem() instanceof CarItem
                    || stack.getItem() instanceof ModelSelectableItem) {
                    LegacyItemStackBridge.setSelectedModelData(stack, safeModelId, safeDataMap);
                    break;
                }
            }
        });
    }
}
