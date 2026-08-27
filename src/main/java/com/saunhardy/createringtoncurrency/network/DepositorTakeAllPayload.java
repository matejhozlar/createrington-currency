package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record DepositorTakeAllPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<DepositorTakeAllPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "depositor_take_all"));

    public static final StreamCodec<ByteBuf, DepositorTakeAllPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, DepositorTakeAllPayload::pos, DepositorTakeAllPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
