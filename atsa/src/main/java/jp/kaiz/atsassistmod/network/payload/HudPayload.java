package jp.kaiz.atsassistmod.network.payload;

import jp.kaiz.atsassistmod.ATSAssistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client HUD state for a formation (mirrors PacketTrainControllerToClient).
 * {@code type == 0} removes the entry, {@code type == 1} updates it.
 */
public record HudPayload(int updateType, long formationId, boolean ato, boolean tasc, int tpType,
                         int atoSpeed, int tascDistance, int atcSpeed, int tpLimit, boolean manual)
        implements CustomPacketPayload {

    public static final Type<HudPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "train_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.updateType);
                buf.writeVarLong(p.formationId);
                buf.writeBoolean(p.ato);
                buf.writeBoolean(p.tasc);
                buf.writeVarInt(p.tpType);
                buf.writeVarInt(p.atoSpeed);
                buf.writeVarInt(p.tascDistance);
                buf.writeVarInt(p.atcSpeed);
                buf.writeVarInt(p.tpLimit);
                buf.writeBoolean(p.manual);
            },
            buf -> new HudPayload(
                    buf.readVarInt(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean()));

    public static HudPayload remove(long formationId) {
        return new HudPayload(0, formationId, false, false, 0, 0, 0, 0, 0, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
