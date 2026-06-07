package cc.mirukuneko.realtrainmodrenewed.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientNetworkHelper {
    private ClientNetworkHelper() {
    }

    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... ignored) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(payload);
        }
    }
}
