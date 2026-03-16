package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMQueryHistoryPayload(int page) implements CustomPacketPayload {
    public static final Type<ATMQueryHistoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_query_history"));

    public static final StreamCodec<ByteBuf, ATMQueryHistoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ATMQueryHistoryPayload::page,
                    ATMQueryHistoryPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
