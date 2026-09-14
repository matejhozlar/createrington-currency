package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMBalancePayload(int balance, boolean available) implements CustomPacketPayload {
    public static final Type<ATMBalancePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_balance"));

    public static final StreamCodec<ByteBuf, ATMBalancePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ATMBalancePayload::balance,
                    ByteBufCodecs.BOOL, ATMBalancePayload::available,
                    ATMBalancePayload::new
            );

    public static ATMBalancePayload unavailable() {
        return new ATMBalancePayload(0, false);
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
