package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record DepositorSetPricePayload(BlockPos pos, int denomination, int count) implements CustomPacketPayload {
    public static final Type<DepositorSetPricePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "depositor_set_price"));

    public static final StreamCodec<ByteBuf, DepositorSetPricePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DepositorSetPricePayload::pos,
                    ByteBufCodecs.VAR_INT, DepositorSetPricePayload::denomination,
                    ByteBufCodecs.VAR_INT, DepositorSetPricePayload::count,
                    DepositorSetPricePayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
