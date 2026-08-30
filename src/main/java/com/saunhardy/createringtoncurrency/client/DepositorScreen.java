package com.saunhardy.createringtoncurrency.client;

import com.saunhardy.createringtoncurrency.network.DepositorNetworking;
import com.saunhardy.createringtoncurrency.network.DepositorSetPricePayload;
import com.saunhardy.createringtoncurrency.network.DepositorTakeAllPayload;
import com.saunhardy.createringtoncurrency.util.Bills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import com.saunhardy.createringtoncurrency.menu.DepositorMenu;

public class DepositorScreen extends AbstractContainerScreen<DepositorMenu> {
    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "textures/gui/atm_bg.png");
    private static final int TEX_W = 320, TEX_H = 200;

    private static final int CONTENT_X = 26, CONTENT_Y = 32, CONTENT_W = 268;
    private static final int ICON = 18;

    private static final int COLOR_ROW = 0xFF1D2227, COLOR_ROW_HOVER = 0xFF262B31, COLOR_ROW_SEL = 0xFF2B3138;
    private static final int COLOR_SLOT_EDGE = 0xFF0F1114, COLOR_SLOT = 0xFF1A1E23;
    private static final int COLOR_TEXT = 0xFFFFFFFF, COLOR_MUTED = 0xFFC0C0C0, COLOR_HINT = 0x80A0A0A0, COLOR_DISABLED = 0xFF6A6F75;
    private static final int COLOR_OK = 0x2ECC71, COLOR_ERR = 0xE74C3C;

    private static final int KEY_LEFT = 263, KEY_RIGHT = 262, KEY_UP = 265, KEY_DOWN = 264,
            KEY_ENTER = 257, KEY_KP_ENTER = 335, KEY_SPACE = 32, KEY_ESCAPE = 256,
            KEY_W = 87, KEY_S = 83, KEY_A = 65, KEY_D = 68;

    private static final int[] DENOMS = Bills.DENOMINATIONS;

    private static final class Rect {
        final int x, y, w, h;

        Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static final class Choice {
        final Component label;
        final Runnable action;
        final Rect rect;
        final boolean compact;

        Choice(Component label, Runnable action, Rect rect, boolean compact) {
            this.label = label;
            this.action = action;
            this.rect = rect;
            this.compact = compact;
        }
    }

    private enum Pending { NONE, SAVE_PRICE, TAKE_ALL }

    private final ItemStack[] icons = new ItemStack[DENOMS.length];

    private int savedDenomIndex;
    private int count;

    private int denomIndex;
    private int pendingDenomIndex, pendingCount;
    private Pending pending = Pending.NONE;

    private EditBox countBox;
    private Rect selectorRect;
    private final List<Choice> choices = new ArrayList<>();

    private int sel = -1;
    private boolean busy = false;

    private String statusText = "";
    private int statusColor = COLOR_TEXT;
    private int statusTicks = 0;

    public DepositorScreen(DepositorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        int idx = Bills.indexOfDenomination(menu.getPriceDenomination());
        this.savedDenomIndex = idx >= 0 ? idx : DENOMS.length - 1;
        this.count = idx >= 0 ? menu.getPriceCount() : 0;
        this.denomIndex = savedDenomIndex;
        for (int i = 0; i < DENOMS.length; i++) icons[i] = new ItemStack(Bills.itemFor(DENOMS[i]));
        this.imageWidth = TEX_W;
        this.imageHeight = TEX_H;
    }

    @Override
    protected void init() {
        this.imageWidth = TEX_W;
        this.imageHeight = TEX_H;
        super.init();

        int x0 = leftPos + CONTENT_X;
        int y0 = topPos + CONTENT_Y;

        String typed = countBox != null ? countBox.getValue() : Integer.toString(Math.max(1, count));
        selectorRect = new Rect(x0, y0, DENOMS.length * ICON, ICON);
        countBox = new EditBox(this.font, x0 + 166, y0, 34, 18, Component.empty());
        countBox.setMaxLength(3);
        countBox.setFilter(s -> s.matches("\\d{0,3}"));
        countBox.setValue(typed);
        addRenderableWidget(countBox);

        buildChoices();
    }

    private void buildChoices() {
        choices.clear();
        int x0 = leftPos + CONTENT_X;
        int y0 = topPos + CONTENT_Y;
        choices.add(new Choice(Component.literal("-"), () -> adjustCount(-1), new Rect(x0 + 150, y0, 14, 18), true));
        choices.add(new Choice(Component.literal("+"), () -> adjustCount(1), new Rect(x0 + 202, y0, 14, 18), true));
        choices.add(new Choice(t("save"), this::savePrice, new Rect(x0 + 222, y0, 46, 18), true));
        choices.add(new Choice(t("take_all"), this::takeAll, new Rect(x0 + 170, y0 + 38, 98, 18), false));
        sel = Math.min(sel, choices.size() - 1);
    }

    private int hoveredIcon(double mx, double my) {
        if (selectorRect == null || !selectorRect.contains(mx, my)) return -1;
        return Math.min(DENOMS.length - 1, (int) ((mx - selectorRect.x) / ICON));
    }

    private Component priceLine() {
        return count > 0
                ? t("price", count, fmt(DENOMS[savedDenomIndex]), fmt(DENOMS[savedDenomIndex] * count))
                : t("price_unset");
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mouseX, int mouseY) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);
        int x0 = leftPos + CONTENT_X;
        int y0 = topPos + CONTENT_Y;

        int hovered = hoveredIcon(mouseX, mouseY);
        for (int i = 0; i < DENOMS.length; i++) {
            int x = x0 + i * ICON;
            boolean selected = i == denomIndex;
            g.fill(x, y0, x + ICON, y0 + ICON, selected ? COLOR_ROW_SEL : i == hovered ? COLOR_ROW_HOVER : COLOR_ROW);
            if (selected) {
                g.fill(x, y0, x + ICON, y0 + 1, 0xCCFFFFFF);
                g.fill(x, y0 + ICON - 1, x + ICON, y0 + ICON, 0xCCFFFFFF);
                g.fill(x, y0, x + 1, y0 + ICON, 0xCCFFFFFF);
                g.fill(x + ICON - 1, y0, x + ICON, y0 + ICON, 0xCCFFFFFF);
            }
            g.renderItem(icons[i], x + 1, y0 + 1);
        }

        g.drawString(this.font, priceLine(), x0, y0 + 24, COLOR_TEXT, false);
        String stored = t("storage", fmt(Bills.value(Bills.count(this.menu.getStorage())))).getString();
        g.drawString(this.font, stored, x0 + CONTENT_W - this.font.width(stored), y0 + 24, COLOR_MUTED, false);

        for (Slot slot : this.menu.slots) {
            drawSlotFrame(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        drawWrapped(g, t("owner_hint"), x0 + 170, y0 + 66, 98, COLOR_HINT);

        for (int i = 0; i < choices.size(); i++) {
            drawChoice(g, choices.get(i), sel == i);
        }
    }

    private void drawChoice(GuiGraphics g, Choice c, boolean selected) {
        Rect r = c.rect;
        g.fill(r.x, r.y, r.x + r.w, r.y + r.h, selected ? COLOR_ROW_SEL : COLOR_ROW);
        g.fill(r.x, r.y, r.x + r.w, r.y + 1, 0x33FFFFFF);
        g.fill(r.x, r.y + r.h - 1, r.x + r.w, r.y + r.h, 0x33000000);
        int color = busy ? COLOR_DISABLED : COLOR_TEXT;
        int ty = r.y + (r.h - this.font.lineHeight) / 2;
        if (c.compact) {
            g.drawString(this.font, c.label, r.x + (r.w - this.font.width(c.label)) / 2, ty, color, false);
        } else {
            g.drawString(this.font, Component.literal(selected ? "> " : "  ").append(c.label), r.x + 8, ty, color, false);
        }
    }

    private void drawSlotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, COLOR_SLOT_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, COLOR_SLOT);
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
        int dy = 0;
        for (var line : this.font.split(text, w)) {
            g.drawString(this.font, line, x, y + dy, color, false);
            dy += this.font.lineHeight + 2;
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, t("title_owner"), 6, 4, COLOR_TEXT, false);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        boolean showing = statusTicks > 0 && !statusText.isEmpty();
        if (!showing) {
            this.renderTooltip(g, mouseX, mouseY);
            int hovered = hoveredIcon(mouseX, mouseY);
            if (hovered >= 0) g.renderTooltip(this.font, icons[hovered].getHoverName(), mouseX, mouseY);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 1000);
        renderStatusPopup(g);
        g.pose().popPose();
    }

    private void renderStatusPopup(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0x99000000);

        int cx = this.leftPos + this.imageWidth / 2;
        int cy = this.topPos + this.imageHeight / 2;

        int textW = this.font.width(statusText);
        int padX = 12, padY = 8;
        int boxW = Math.max(140, textW + padX * 2);
        int boxH = this.font.lineHeight + padY * 2;

        int x0 = cx - boxW / 2, y0 = cy - boxH / 2;
        int x1 = x0 + boxW, y1 = y0 + boxH;

        g.fill(x0, y0, x1, y1, 0xFF222228);
        g.fill(x0, y0, x1, y0 + 1, 0x99FFFFFF);
        g.fill(x0, y1 - 1, x1, y1, 0x99000000);
        g.fill(x0, y0, x0 + 1, y1, 0x99FFFFFF);
        g.fill(x1 - 1, y0, x1, y1, 0x99000000);

        g.drawString(this.font, statusText, x0 + (boxW - textW) / 2, y0 + (boxH - this.font.lineHeight) / 2, statusColor, false);
    }

    private void activate(int index) {
        if (busy || index < 0 || index >= choices.size()) return;
        sel = index;
        clickSound();
        choices.get(index).action.run();
    }

    private int typedCount() {
        String raw = countBox == null ? "" : countBox.getValue().trim();
        return raw.isEmpty() ? 0 : Integer.parseInt(raw);
    }

    private void adjustCount(int delta) {
        if (countBox == null) return;
        if (hasShiftDown()) delta *= 10;
        int next = Math.max(1, Math.min(DepositorNetworking.MAX_PRICE_COUNT, typedCount() + delta));
        countBox.setValue(Integer.toString(next));
    }

    private void savePrice() {
        int typed = typedCount();
        int clamped = Math.min(typed, DepositorNetworking.MAX_PRICE_COUNT);
        if (clamped != typed && countBox != null) countBox.setValue(Integer.toString(clamped));
        pendingDenomIndex = denomIndex;
        pendingCount = clamped;
        pending = Pending.SAVE_PRICE;
        busy = true;
        PacketDistributor.sendToServer(new DepositorSetPricePayload(this.menu.getPos(), DENOMS[denomIndex], pendingCount));
    }

    private void takeAll() {
        pending = Pending.TAKE_ALL;
        busy = true;
        PacketDistributor.sendToServer(new DepositorTakeAllPayload(this.menu.getPos()));
    }

    public void onResult(int kind, String message) {
        busy = false;
        Pending action = pending;
        pending = Pending.NONE;

        int color = switch (kind) {
            case DepositorNetworking.KIND_SUCCESS -> COLOR_OK;
            case DepositorNetworking.KIND_ERROR -> COLOR_ERR;
            default -> COLOR_TEXT;
        };
        showStatus(message, color);
        if (kind == DepositorNetworking.KIND_SUCCESS && action == Pending.SAVE_PRICE) {
            savedDenomIndex = pendingDenomIndex;
            count = pendingCount;
        }
    }

    public void showStatus(String msg, int color) {
        this.statusText = msg;
        this.statusColor = color;
        this.statusTicks = 60;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (statusTicks > 0) statusTicks--;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (statusTicks > 0) {
            statusTicks = 0;
            return true;
        }

        if (countBox != null && countBox.isFocused()) {
            if (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
                if (!busy) {
                    clickSound();
                    savePrice();
                }
                return true;
            }
            if (keyCode == KEY_DOWN) {
                this.setFocused(null);
                return true;
            }

            return countBox.keyPressed(keyCode, scanCode, modifiers) || countBox.canConsumeInput();
        }

        switch (keyCode) {
            case KEY_UP, KEY_W -> {
                this.setFocused(countBox);
                return true;
            }
            case KEY_LEFT, KEY_A -> {
                move(-1);
                return true;
            }
            case KEY_DOWN, KEY_S, KEY_RIGHT, KEY_D -> {
                move(1);
                return true;
            }
            case KEY_ENTER, KEY_KP_ENTER, KEY_SPACE -> {
                activate(sel);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void move(int delta) {
        if (choices.isEmpty()) return;
        if (sel < 0) sel = delta > 0 ? 0 : choices.size() - 1;
        else sel = Math.floorMod(sel + delta, choices.size());
        clickSound();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (statusTicks > 0) {
            statusTicks = 0;
            return true;
        }
        if (!busy && selectorRect != null && selectorRect.contains(mx, my)) {
            int carried = Bills.indexOf(this.menu.getCarried());
            denomIndex = carried >= 0 ? carried : hoveredIcon(mx, my);
            clickSound();
            return true;
        }
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).rect.contains(mx, my)) {
                activate(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        sel = -1;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).rect.contains(mx, my)) {
                sel = i;
                break;
            }
        }
        super.mouseMoved(mx, my);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("gui.createringtoncurrency.depositor." + key, args);
    }

    private static String fmt(long amount) {
        return NumberFormat.getInstance().format(amount);
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
