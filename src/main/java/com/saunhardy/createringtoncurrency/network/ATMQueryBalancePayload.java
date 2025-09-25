package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMQueryBalancePayload() implements CustomPacketPayload {
    public static final Type<ATMQueryBalancePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_query_balance"));

    public static final StreamCodec<ByteBuf, ATMQueryBalancePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull ATMQueryBalancePayload decode(@NotNull ByteBuf buf) { return new ATMQueryBalancePayload(); }
        @Override public void encode(@NotNull ByteBuf buf, @NotNull ATMQueryBalancePayload value) {}
    };

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
