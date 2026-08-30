package com.saunhardy.createringtoncurrency.network;

import com.saunhardy.createringtoncurrency.util.Bills;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public record ATMWithdrawPayload(List<Integer> counts) implements CustomPacketPayload {
    public static final Type<ATMWithdrawPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "atm_withdraw"));

    public static final StreamCodec<ByteBuf, ATMWithdrawPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(Bills.DENOMINATIONS.length)), ATMWithdrawPayload::counts,
                    ATMWithdrawPayload::new
            );

    public static ATMWithdrawPayload of(int[] counts) {
        return new ATMWithdrawPayload(Arrays.stream(counts).boxed().toList());
    }

    public int[] toArray() {
        return counts.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
