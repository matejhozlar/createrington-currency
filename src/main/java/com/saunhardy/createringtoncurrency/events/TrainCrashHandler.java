package com.saunhardy.createringtoncurrency.events;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.MoneyCommands;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;

public class TrainCrashHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public record PlayerInfo(UUID uuid, @Nullable String name, boolean isDriver) {}

    public static void reportCrash(UUID trainId, String trainName, double speed,
                                   int carriageCount, double[] position, String dimension,
                                   @Nullable UUID owner, @Nullable UUID driverUuid,
                                   List<PlayerInfo> passengers,
                                   @Nullable UUID backwardsDriverUuid,
                                   @Nullable String backwardsDriverName) {
        if (!Config.TRAIN_CRASH_REPORTING_ENABLED.get()) return;

        MoneyCommands.EXECUTOR.submit(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("trainId", trainId.toString());
                payload.put("trainName", trainName);
                payload.put("speed", speed);
                payload.put("carriageCount", carriageCount);
                payload.put("timestamp", System.currentTimeMillis());

                if (position != null) {
                    Map<String, Double> pos = new HashMap<>();
                    pos.put("x", position[0]);
                    pos.put("y", position[1]);
                    pos.put("z", position[2]);
                    payload.put("position", pos);
                }

                if (dimension != null) {
                    payload.put("dimension", dimension);
                }

                if (owner != null) {
                    payload.put("owner", owner.toString());
                }

                if (driverUuid != null) {
                    payload.put("driverUuid", driverUuid.toString());
                }

                // Build passenger list
                List<Map<String, Object>> passengerList = new ArrayList<>();
                for (PlayerInfo p : passengers) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("uuid", p.uuid().toString());
                    if (p.name() != null) entry.put("name", p.name());
                    entry.put("isDriver", p.isDriver());
                    passengerList.add(entry);
                }
                if (!passengerList.isEmpty()) {
                    payload.put("passengers", passengerList);
                }

                if (backwardsDriverUuid != null) {
                    Map<String, String> bd = new HashMap<>();
                    bd.put("uuid", backwardsDriverUuid.toString());
                    if (backwardsDriverName != null) bd.put("name", backwardsDriverName);
                    payload.put("backwardsDriver", bd);
                }

                String json = GSON.toJson(payload);
                URL url = URI.create(MoneyCommands.safeJoin(
                        Config.API_BASE_URL.get(), Config.API_TRAIN_CRASH_URL.get())).toURL();

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(Config.API_TIMEOUT_MS.get());
                    conn.setReadTimeout(Config.API_TIMEOUT_MS.get());

                    try (var os = conn.getOutputStream()) {
                        os.write(json.getBytes());
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        LOGGER.info("Train crash reported: {} ({})", trainName, trainId);
                    } else {
                        LOGGER.warn("Train crash report failed with status {}", responseCode);
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to report train crash: {}", e.getMessage());
            }
        });
    }
}
