package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMOpenPayload() implements CustomPacketPayload {
    public static final Type<ATMOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_open"));

    public static final StreamCodec<ByteBuf, ATMOpenPayload> STREAM_CODEC =
            StreamCodec.unit(new ATMOpenPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
