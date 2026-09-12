package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class AfkStatus {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "afkstatus";
    private static final String AFK_TEAM = "afkstatus";

    private static volatile Method isAfkMethod;
    private static volatile boolean resolved;

    public static boolean isInstalled() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isAfk(ServerPlayer player) {
        Method method = resolve();
        if (method != null) {
            try {
                return Boolean.TRUE.equals(method.invoke(null, player.getUUID()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.error("AFKStatus lookup failed, falling back to the scoreboard team: {}", e.toString());
                isAfkMethod = null;
            }
        }

        Team team = player.getTeam();
        return team != null && AFK_TEAM.equals(team.getName());
    }

    private static Method resolve() {
        if (!resolved) {
            resolved = true;
            try {
                isAfkMethod = Class.forName("com.saunhardy.afkstatus.AFKManager").getMethod("isAFK", UUID.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (isInstalled()) {
                    LOGGER.warn("AFKStatus is installed but its AFK lookup could not be resolved, falling back to the '{}' scoreboard team: {}",
                            AFK_TEAM, e.toString());
                }
            }
        }
        return isAfkMethod;
    }

    private AfkStatus() {}
}
