package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMWithdrawPayload(int mode, int a, int b) implements CustomPacketPayload {
    public static final Type<ATMWithdrawPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_withdraw"));

    public static final StreamCodec<ByteBuf, ATMWithdrawPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ATMWithdrawPayload::mode,
                    ByteBufCodecs.VAR_INT, ATMWithdrawPayload::a,
                    ByteBufCodecs.VAR_INT, ATMWithdrawPayload::b,
                    ATMWithdrawPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
