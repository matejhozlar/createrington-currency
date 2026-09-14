package com.saunhardy.createringtoncurrency.mixin;

import com.saunhardy.createringtoncurrency.CreateringtonCurrency;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.rcon.RconConsoleSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(PlayerList.class)
public abstract class RconSayMixin {

    private static final ResourceKey<ChatType> createringtoncurrency$RCON_SAY = ResourceKey.create(
            Registries.CHAT_TYPE, ResourceLocation.fromNamespaceAndPath(CreateringtonCurrency.MODID, "rcon_say"));

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void createringtoncurrency$stripRconSayPrefix(PlayerChatMessage message, CommandSourceStack sender,
                                                         ChatType.Bound bound, CallbackInfo ci) {
        if (!(sender.source instanceof RconConsoleSource) || !bound.chatType().is(ChatType.SAY_COMMAND)) {
            return;
        }
        Optional<Holder.Reference<ChatType>> rconSay = sender.registryAccess()
                .registryOrThrow(Registries.CHAT_TYPE)
                .getHolder(createringtoncurrency$RCON_SAY);
        if (rconSay.isEmpty()) {
            return;
        }
        ci.cancel();
        ((PlayerList) (Object) this).broadcastChatMessage(message, sender,
                new ChatType.Bound(rconSay.get(), bound.name(), bound.targetName()));
    }
}
