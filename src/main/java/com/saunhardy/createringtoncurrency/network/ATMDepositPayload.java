package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMDepositPayload() implements CustomPacketPayload {
    public static final Type<ATMDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_deposit"));

    public static final StreamCodec<ByteBuf, ATMDepositPayload> STREAM_CODEC =
            StreamCodec.unit(new ATMDepositPayload());

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
