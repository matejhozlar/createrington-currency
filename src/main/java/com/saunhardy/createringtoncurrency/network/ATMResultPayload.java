package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMResultPayload(int kind, String message) implements CustomPacketPayload {
    public static final Type<ATMResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_result"));

    public static final StreamCodec<ByteBuf, ATMResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ATMResultPayload::kind,
                    ByteBufCodecs.STRING_UTF8, ATMResultPayload::message,
                    ATMResultPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
