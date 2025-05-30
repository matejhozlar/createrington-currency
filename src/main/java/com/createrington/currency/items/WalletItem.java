package com.createrington.currency.items;

import com.createrington.currency.menu.WalletMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public class WalletItem extends Item {
    public WalletItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (!level.isClientSide) {
            // Only open GUI on the server side
            ItemStack walletStack = player.getItemInHand(hand);
            // Provide a MenuProvider to open the Wallet's container menu
            player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInv, p) -> {
                        // Get the wallet's item handler capability
                        var handlerOpt = walletStack.getCapability(Capabilities.ItemHandler.ITEM);
                        assert handlerOpt != null;
                        return new WalletMenu(containerId, playerInv);
                    },
                    Component.translatable("container.createringtoncurrency.wallet") // Container title
            ));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
