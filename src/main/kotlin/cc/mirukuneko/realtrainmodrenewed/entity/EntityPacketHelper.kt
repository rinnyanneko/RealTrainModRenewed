// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.entity

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.server.level.ServerEntity
import net.minecraft.world.entity.Entity

object EntityPacketHelper {
    @JvmStatic
    fun createAddEntityPacket(entity: Entity, serverEntity: ServerEntity): Packet<ClientGamePacketListener> =
        ClientboundAddEntityPacket(entity, serverEntity)
}
