package com.portofino.realtrainmodunofficial.client.model.mqo.object;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record MQOVertex(float x, float y, float z) {
}
