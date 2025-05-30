package com.createrington.currency.client;

import com.createrington.currency.CreateringtonCurrency;
import com.createrington.currency.menu.WalletMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class WalletScreen extends AbstractContainerScreen<WalletMenu> {
    // single-arg constructor is public in 1.21.1
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    CreateringtonCurrency.MODID,
                    "textures/gui/wallet.png"
            );

    public WalletScreen(WalletMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // match your png’s dimensions
        this.imageWidth = 176;
        this.imageHeight = 133;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // let AbstractContainerScreen do its full render: background + slots + labels
        super.render(graphics, mouseX, mouseY, partialTicks);
        // then draw the tooltips on top
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        // bind and draw your gui texture
        RenderSystem.setShaderTexture(0, TEXTURE);
        int left = (this.width  - this.imageWidth)  / 2;
        int top  = (this.height - this.imageHeight) / 2;
        graphics.blit(
                TEXTURE,          // ResourceLocation
                left, top,        // screen x,y
                0, 0,             // texture u,v
                this.imageWidth,  // width
                this.imageHeight  // height
        );
    }

    // (You can also override renderLabels(...) if you want custom title text positioning)
}
