package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record VoteOpenPayload(String starter, String voteType, int durationDays, VoteTallyPayload tally)
        implements CustomPacketPayload {
    public static final Type<VoteOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "vote_open"));

    public static final StreamCodec<ByteBuf, VoteOpenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, VoteOpenPayload::starter,
                    ByteBufCodecs.STRING_UTF8, VoteOpenPayload::voteType,
                    ByteBufCodecs.VAR_INT, VoteOpenPayload::durationDays,
                    VoteTallyPayload.STREAM_CODEC, VoteOpenPayload::tally,
                    VoteOpenPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
