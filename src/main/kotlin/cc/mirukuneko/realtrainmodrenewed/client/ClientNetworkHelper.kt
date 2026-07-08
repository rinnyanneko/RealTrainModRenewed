package cc.mirukuneko.realtrainmodrenewed.client

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

object ClientNetworkHelper {
    @JvmStatic
    fun sendToServer(payload: CustomPacketPayload, vararg ignored: CustomPacketPayload) {
        Minecraft.getInstance().connection?.send(payload)
    }
}
