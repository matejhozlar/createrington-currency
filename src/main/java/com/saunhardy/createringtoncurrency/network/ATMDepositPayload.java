package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMDepositPayload(int amount) implements CustomPacketPayload {
    public static final Type<ATMDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_deposit"));

    public static final StreamCodec<ByteBuf, ATMDepositPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ATMDepositPayload::amount,
                    ATMDepositPayload::new
            );

    public static ATMDepositPayload all() {
        return new ATMDepositPayload(0);
    }

    public boolean isAll() {
        return amount <= 0;
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
