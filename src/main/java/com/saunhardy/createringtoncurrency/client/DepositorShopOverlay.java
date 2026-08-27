package com.saunhardy.createringtoncurrency.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.saunhardy.createringtoncurrency.block.DepositorTerminalBlockEntity;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class DepositorShopOverlay {
    private static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/widgets.png");
    private static final int SHEET = 256;

    private static final int[] FRAME = {128, 98, 96, 46};
    private static final int[] SLOT = {0, 68, 22, 22};
    private static final int[] ARROW = {24, 51, 20, 12};

    private static final int CONTENT_WIDTH = 72;
    private static final int SLOT_INSET = 3;

    private static final int BACKDROP_COLOR = 0x88000000;

    private DepositorShopOverlay() {}

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof DepositorTerminalBlockEntity be)) return;
        if (be.getOwner() == null || !be.hasPrice()) return;
        if (be.getOwner().equals(mc.player.getUUID())) return;

        GuiGraphics g = event.getGuiGraphics();
        int x0 = (g.guiWidth() - CONTENT_WIDTH) / 2;
        int y = g.guiHeight() - 100;
        int frameX = g.guiWidth() / 2 - 48;

        RenderSystem.enableBlend();

        g.fill(frameX + 2, y - 1, frameX + FRAME[2] - 2, y + SLOT[3], BACKDROP_COLOR);
        blit(g, FRAME, frameX, y - 19);
        blit(g, SLOT, x0, y);
        blit(g, ARROW, x0 + 26, y + 4);
        blit(g, SLOT, x0 + 51, y);

        ItemStack bill = new ItemStack(Bills.itemFor(be.getPriceDenomination()), Math.min(be.getPriceCount(), 999));
        g.renderItem(bill, x0 + SLOT_INSET, y + SLOT_INSET);
        g.renderItemDecorations(mc.font, bill, x0 + SLOT_INSET, y + SLOT_INSET);

        PlayerSkin skin = mc.getSkinManager().getInsecureSkin(new GameProfile(be.getOwner(), be.getOwnerName()));
        PlayerFaceRenderer.draw(g, skin, x0 + 51 + SLOT_INSET, y + SLOT_INSET, 16);
    }

    private static void blit(GuiGraphics g, int[] region, int x, int y) {
        g.blit(WIDGETS, x, y, (float) region[0], (float) region[1], region[2], region[3], SHEET, SHEET);
    }
}
