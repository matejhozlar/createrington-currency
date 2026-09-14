package com.saunhardy.createringtoncurrency.mixin;

import com.saunhardy.createringtoncurrency.util.RconSay;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerList.class)
public abstract class RconSayMixin {

    @ModifyVariable(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private ChatType.Bound createringtoncurrency$stripRconSayPrefix(ChatType.Bound value, PlayerChatMessage message,
                                                                   CommandSourceStack sender) {
        return RconSay.bound(sender, value);
    }
}
