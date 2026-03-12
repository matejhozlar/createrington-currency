package com.saunhardy.createringtoncurrency.events;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.Config;
import com.saunhardy.createringtoncurrency.MoneyCommands;
import org.slf4j.Logger;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrainCrashHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public static void reportCrash(UUID trainId, String trainName, double speed,
                                   int carriageCount, double[] position, String dimension) {
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
