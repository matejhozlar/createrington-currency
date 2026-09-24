package com.saunhardy.createringtoncurrency.network;

import com.saunhardy.createringtoncurrency.VoteCommand;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class VoteNetworking {
    private VoteNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        reg.playToClient(VoteOpenPayload.TYPE, VoteOpenPayload.STREAM_CODEC, VoteNetworking::handleOpenClient);
        reg.playToClient(VoteTallyPayload.TYPE, VoteTallyPayload.STREAM_CODEC, VoteNetworking::handleTallyClient);
        reg.playToClient(VoteResultPayload.TYPE, VoteResultPayload.STREAM_CODEC, VoteNetworking::handleResultClient);
        reg.playToServer(VoteCastPayload.TYPE, VoteCastPayload.STREAM_CODEC, VoteNetworking::handleCast);
    }

    private static void handleCast(final VoteCastPayload pkt, final IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !VoteCommand.isVoteActive()) return;
        VoteCommand.castVote(player, pkt.yes());
    }

    private static void handleOpenClient(final VoteOpenPayload pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.saunhardy.createringtoncurrency.client.VotePopup.open(pkt));
    }

    private static void handleTallyClient(final VoteTallyPayload pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.saunhardy.createringtoncurrency.client.VotePopup.updateTally(pkt));
    }

    private static void handleResultClient(final VoteResultPayload pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.saunhardy.createringtoncurrency.client.VotePopup.showResult(pkt));
    }
}
