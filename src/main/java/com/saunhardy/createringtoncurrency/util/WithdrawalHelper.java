package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import com.saunhardy.crnet.http.ApiResponse;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class WithdrawalHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum WithdrawalResult {
        SUCCESS,
        FAILED_API,
        FAILED_CONNECTION,
        FAILED_INVENTORY,
        FAILED_DEV_MODE
    }

    public static class WithdrawalResponse {
        public final WithdrawalResult result;
        public final boolean success;

        private WithdrawalResponse(WithdrawalResult result) {
            this.result = result;
            this.success = result == WithdrawalResult.SUCCESS || result == WithdrawalResult.FAILED_DEV_MODE;
        }

        public static WithdrawalResponse success() {
            return new WithdrawalResponse(WithdrawalResult.SUCCESS);
        }

        public static WithdrawalResponse failed(WithdrawalResult result) {
            return new WithdrawalResponse(result);
        }
    }

    /**
     * Synchronously withdraws bills for a player via the backend and places
     * them in inventory on success. Blocks the calling thread until the API
     * call returns -- callers that cannot tolerate a network-latency stall
     * should schedule their own async wrapper.
     */
    public static WithdrawalResponse withdrawBills(ServerPlayer player, int count, int denomination) {
        int index = Bills.indexOfDenomination(denomination);
        if (index < 0 || count <= 0) {
            return WithdrawalResponse.failed(WithdrawalResult.FAILED_API);
        }
        try {
            ApiResponse<?> resp = CurrencyApi.withdraw(player.getUUID(), denomination, count).join();
            if (resp.isSuccess()) {
                BillDelivery.deliver(player.server, player.getUUID(), Bills.only(index, count), "a Stock Ticker purchase");
                return WithdrawalResponse.success();
            }
            LOGGER.error("Withdrawal API failed: uuid={}, denomination={}, count={}, message={}",
                    player.getUUID(), denomination, count, resp.getMessage());
            return WithdrawalResponse.failed(WithdrawalResult.FAILED_API);
        } catch (RuntimeException e) {
            LOGGER.error("Withdrawal connection error for uuid={}: {}", player.getUUID(), e.getMessage());
            return WithdrawalResponse.failed(WithdrawalResult.FAILED_CONNECTION);
        }
    }

    public static int calculateSlotsNeeded(int count, int maxStackSize) {
        return (int) Math.ceil((double) count / maxStackSize);
    }

    /**
     * Checks whether the player has enough inventory space (hotbar + main,
     * slots 0-35) to hold {@code slotsNeeded} new bill stacks.
     */
    public static boolean hasInventorySpace(ServerPlayer player, int slotsNeeded) {
        int freeSlots = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) freeSlots++;
        }
        return freeSlots >= slotsNeeded;
    }

    public static WithdrawalResponse withdrawBillsWithSpaceCheck(ServerPlayer player, int count, int denomination) {
        int slotsNeeded = calculateSlotsNeeded(count, 64);
        if (!hasInventorySpace(player, slotsNeeded)) {
            return WithdrawalResponse.failed(WithdrawalResult.FAILED_INVENTORY);
        }
        return withdrawBills(player, count, denomination);
    }
}
