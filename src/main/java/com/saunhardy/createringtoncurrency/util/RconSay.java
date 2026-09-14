package com.saunhardy.createringtoncurrency.util;

import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.rcon.RconConsoleSource;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RconSay {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<ChatType> RCON_SAY = ResourceKey.create(
            Registries.CHAT_TYPE, ResourceLocation.fromNamespaceAndPath(CreateringtonCurrency.MODID, "rcon_say"));
    private static final AtomicBoolean WARNED_MISSING = new AtomicBoolean();

    private RconSay() {
    }

    public static ChatType.Bound bound(CommandSourceStack sender, ChatType.Bound bound) {
        if (!(sender.source instanceof RconConsoleSource) || !bound.chatType().is(ChatType.SAY_COMMAND)) {
            return bound;
        }
        return sender.registryAccess().registryOrThrow(Registries.CHAT_TYPE)
                .getHolder(RCON_SAY)
                .map(holder -> new ChatType.Bound(holder, bound.name(), bound.targetName()))
                .orElseGet(() -> {
                    if (WARNED_MISSING.compareAndSet(false, true)) {
                        LOGGER.warn("Chat type {} is not registered, RCON /say keeps its [Rcon] prefix", RCON_SAY.location());
                    }
                    return bound;
                });
    }
}
