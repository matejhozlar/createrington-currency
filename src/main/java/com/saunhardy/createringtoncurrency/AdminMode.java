package com.saunhardy.createringtoncurrency;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminMode {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PERMISSION_LEVEL = 2;
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    public static boolean isActive(Player player) {
        return player.hasPermissions(PERMISSION_LEVEL) && ACTIVE.contains(player.getUUID());
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("createringtoncurrency")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.literal("admin-mode")
                                .executes(ctx -> toggle(ctx.getSource().getPlayerOrException()))));
    }

    private static int toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean enabled = ACTIVE.add(id);
        if (!enabled) ACTIVE.remove(id);

        if (enabled) {
            player.sendSystemMessage(Component.literal(
                            "Admin mode on: right-clicking any depositor terminal opens its owner menu until you log out.")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            player.sendSystemMessage(Component.literal(
                            "Admin mode off: depositor terminals treat you as a customer again.")
                    .withStyle(ChatFormatting.GRAY));
        }
        LOGGER.info("[DEPOSITOR] {} ({}) turned admin mode {}", player.getName().getString(), id, enabled ? "on" : "off");
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
