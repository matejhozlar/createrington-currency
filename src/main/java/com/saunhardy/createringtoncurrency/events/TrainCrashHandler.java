package com.saunhardy.createringtoncurrency.events;

import com.mojang.logging.LogUtils;
import com.saunhardy.createrington.api.trains.BackwardsDriver;
import com.saunhardy.createrington.api.trains.CrashPassenger;
import com.saunhardy.createrington.api.trains.CrashRequest;
import com.saunhardy.createrington.api.trains.Position;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.api.CurrencyApi;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrainCrashHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record PlayerInfo(UUID uuid, @Nullable String name, boolean isDriver) {}

    public static void reportCrash(UUID trainId, String trainName, double speed,
                                   int carriageCount, double[] position, String dimension,
                                   @Nullable UUID owner, @Nullable UUID driverUuid,
                                   List<PlayerInfo> passengers,
                                   @Nullable UUID backwardsDriverUuid,
                                   @Nullable String backwardsDriverName) {
        if (!Config.TRAIN_CRASH_REPORTING_ENABLED.get()) return;

        UUID authUuid = pickAuthUuid(driverUuid, owner, passengers);
        if (authUuid == null) {
            LOGGER.warn("Skipping train crash report for {}: no authenticated player UUID available", trainName);
            return;
        }

        Position pos = position != null ? new Position(position[0], position[1], position[2]) : null;

        List<CrashPassenger> passengerRecords = new ArrayList<>();
        for (PlayerInfo p : passengers) {
            passengerRecords.add(new CrashPassenger(p.uuid().toString(), p.name(), p.isDriver()));
        }

        BackwardsDriver bd = backwardsDriverUuid != null
                ? new BackwardsDriver(backwardsDriverUuid.toString(), backwardsDriverName)
                : null;

        CrashRequest req = new CrashRequest(
                trainId.toString(),
                trainName,
                speed,
                carriageCount,
                pos,
                dimension,
                System.currentTimeMillis(),
                owner != null ? owner.toString() : null,
                driverUuid != null ? driverUuid.toString() : null,
                passengerRecords.isEmpty() ? null : passengerRecords,
                bd
        );

        CurrencyApi.trainCrash(authUuid, req)
                .thenAccept(resp -> {
                    if (resp.isSuccess()) {
                        LOGGER.info("Train crash reported: {} ({})", trainName, trainId);
                    } else {
                        LOGGER.warn("Train crash report failed: {}", resp.getMessage());
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to report train crash: {}", ex.getMessage());
                    return null;
                });
    }

    private static UUID pickAuthUuid(@Nullable UUID driver, @Nullable UUID owner, List<PlayerInfo> passengers) {
        if (driver != null) return driver;
        if (owner != null) return owner;
        for (PlayerInfo p : passengers) {
            if (p.uuid() != null) return p.uuid();
        }
        return null;
    }
}
