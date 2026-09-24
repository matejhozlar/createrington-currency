package com.saunhardy.createringtoncurrency.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record VoteTallyPayload(int yes, int no, int ticksRemaining, int status)
        implements CustomPacketPayload {
    public static final int STATUS_OPEN = 0;
    public static final int STATUS_VOTED_YES = 1;
    public static final int STATUS_VOTED_NO = 2;
    public static final int STATUS_SPECTATOR = 3;

    public static final Type<VoteTallyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "vote_tally"));

    public static final StreamCodec<ByteBuf, VoteTallyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, VoteTallyPayload::yes,
                    ByteBufCodecs.VAR_INT, VoteTallyPayload::no,
                    ByteBufCodecs.VAR_INT, VoteTallyPayload::ticksRemaining,
                    ByteBufCodecs.VAR_INT, VoteTallyPayload::status,
                    VoteTallyPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
