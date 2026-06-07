package jp.kaiz.atsassistmod.network.payload;

import jp.kaiz.atsassistmod.ATSAssistMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Client → server save of an IFTTT block's rule lists (serialized containers). */
public record IftttPayloads() {

    public record SaveIfttt(BlockPos pos, boolean anyMatch, List<byte[]> thisData, List<byte[]> thatData)
            implements CustomPacketPayload {
        public static final Type<SaveIfttt> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "save_ifttt"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SaveIfttt> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.anyMatch);
                    writeList(buf, p.thisData);
                    writeList(buf, p.thatData);
                },
                buf -> new SaveIfttt(buf.readBlockPos(), buf.readBoolean(), readList(buf), readList(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void writeList(RegistryFriendlyByteBuf buf, List<byte[]> list) {
            buf.writeVarInt(list.size());
            for (byte[] b : list) {
                buf.writeByteArray(b);
            }
        }

        private static List<byte[]> readList(RegistryFriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<byte[]> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(buf.readByteArray());
            }
            return list;
        }
    }
}
