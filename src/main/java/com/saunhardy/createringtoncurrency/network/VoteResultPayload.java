package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record VoteResultPayload(boolean passed, int yes, int no, int needed, int eligible, int reason)
        implements CustomPacketPayload {
    public static final int REASON_NONE = 0;
    public static final int REASON_OUTVOTED = 1;
    public static final int REASON_TURNOUT = 2;

    public static final Type<VoteResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "vote_result"));

    public static final StreamCodec<ByteBuf, VoteResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, VoteResultPayload::passed,
                    ByteBufCodecs.VAR_INT, VoteResultPayload::yes,
                    ByteBufCodecs.VAR_INT, VoteResultPayload::no,
                    ByteBufCodecs.VAR_INT, VoteResultPayload::needed,
                    ByteBufCodecs.VAR_INT, VoteResultPayload::eligible,
                    ByteBufCodecs.VAR_INT, VoteResultPayload::reason,
                    VoteResultPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
