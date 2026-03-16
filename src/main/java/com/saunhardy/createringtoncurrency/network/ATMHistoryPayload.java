package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ATMHistoryPayload(int page, int hasMore, String data) implements CustomPacketPayload {
    public static final Type<ATMHistoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_history"));

    public static final StreamCodec<ByteBuf, ATMHistoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ATMHistoryPayload::page,
                    ByteBufCodecs.VAR_INT, ATMHistoryPayload::hasMore,
                    ByteBufCodecs.STRING_UTF8, ATMHistoryPayload::data,
                    ATMHistoryPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
