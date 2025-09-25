package com.saunhardy.createringtoncurrency.client;

import com.saunhardy.createringtoncurrency.menu.ATMMenu;
import com.saunhardy.createringtoncurrency.network.ATMDepositPayload;
import com.saunhardy.createringtoncurrency.network.ATMWithdrawPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class ATMScreen extends AbstractContainerScreen<ATMMenu> {

    private EditBox denomBox, countBox, totalBox;
    private Button depositBtn, withdrawFixedBtn, withdrawTotalBtn;

    private int _label1Y = 34, _label2Y = 70;

    private String statusText = "";
    private int statusColor = 0xFFFFFF;
    private int statusTicks = 0; // ~20 per second

    private enum Flow { INTRO, PIN, AUTH, READY }
    private Flow flow = Flow.INTRO;
    private int flowTicks = 0;
    private int pinProgress = 0;
    private int pinFlashTicks = 0;

    private enum View { HOME, DEPOSIT, WITHDRAW }
    private View view = View.HOME;

    private int homeSel = 0;
    private Rect hitDeposit, hitWithdraw;

    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262,
            KEY_ENTER=257, KEY_SPACE=32, KEY_E=69, KEY_ESCAPE=256, KEY_W=87, KEY_S=83, KEY_A=65, KEY_D=68;

    private int balance = -1;
    public void updateBalance(int v) { this.balance = v; }

    private int depositSel = 0;
    private Rect hitDepAll, hitBack;

    private static final ResourceLocation ATM_BG =
            ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "textures/gui/atm_bg.png");
    private static final int TEX_W = 320, TEX_H = 200;
    private static final int SCR_X = 16, SCR_Y = 22, SCR_W = 288, SCR_H = 162;

    private static class Rect { int x,y,w,h; Rect(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;} }

    private Rect screenArea() {
        int x = leftPos + (SCR_X * imageWidth ) / TEX_W;
        int y = topPos  + (SCR_Y * imageHeight) / TEX_H;
        int w = (SCR_W * imageWidth ) / TEX_W;
        int h = (SCR_H * imageHeight) / TEX_H;
        return new Rect(x,y,w,h);
    }

    private static final int CONTENT_PAD = 10;

    private Rect contentArea() {
        Rect s = screenArea();
        return new Rect(s.x + CONTENT_PAD, s.y + CONTENT_PAD, s.w - CONTENT_PAD*2, s.h - CONTENT_PAD*2);
    }

    private void setView(View v) {
        this.view = v;

        boolean showDepositWidgets = false;
        boolean showWithdraw = (v == View.WITHDRAW);

        if (depositBtn != null)           { depositBtn.visible = showDepositWidgets;           depositBtn.active  = showDepositWidgets; }
        if (denomBox != null)             { denomBox.visible = showWithdraw;                   denomBox.setEditable(showWithdraw); }
        if (countBox != null)             { countBox.visible = showWithdraw;                   countBox.setEditable(showWithdraw); }
        if (totalBox != null)             { totalBox.visible = showWithdraw;                   totalBox.setEditable(showWithdraw); }
        if (withdrawFixedBtn != null)     { withdrawFixedBtn.visible = showWithdraw;           withdrawFixedBtn.active = showWithdraw; }
        if (withdrawTotalBtn != null)     { withdrawTotalBtn.visible = showWithdraw;           withdrawTotalBtn.active = showWithdraw; }

        if (v == View.HOME) {
            updateBalance(-1);
            requestBalance();
        } else if (v == View.DEPOSIT) {
            depositSel = 0;
        }
    }

    private void goHome()      { setView(View.HOME); }
    private void goDeposit()   { setView(View.DEPOSIT); }
    private void goWithdraw()  { setView(View.WITHDRAW); }

    private void performDepositAll() {
        var c = Minecraft.getInstance().getConnection();
        if (c != null) {
            c.send(new ServerboundCustomPayloadPacket(new ATMDepositPayload()));
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void renderDeposit(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Deposit", r.x, r.y, 0xFFFFFFFF, false);

        int startY = r.y + 24;
        int rowH = 20;
        int itemW = Math.min(r.w, 220);
        int x = r.x + 8;

        String[] items = {"Deposit all", "Back"};
        for (int i = 0; i < items.length; i++) {
            int y = startY + i * (rowH + 8);
            boolean sel = (depositSel == i);

            g.fill(x, y, x + itemW, y + rowH, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + itemW, y + 1, 0x33FFFFFF);
            g.fill(x, y + rowH - 1, x + itemW, y + rowH, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (rowH - this.font.lineHeight) / 2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            if (i == 0) hitDepAll = new Rect(x, y, itemW, rowH);
            else        hitBack   = new Rect(x, y, itemW, rowH);
        }

        String hint = "Use ↑/↓ or W/S; Enter to select   •   ESC/← to go back";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }


    public ATMScreen(ATMMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 120;
    }

    public void showStatus(String msg, int color) {
        this.statusText = msg;
        this.statusColor = color;
        this.statusTicks = 60;
    }

    @Override
    protected void init() {
        this.imageWidth = 320;
        this.imageHeight = 200;
        super.init();

        Rect r = contentArea();
        final int LABEL_TO_FIELD = 12, FIELD_H = 18, GAP = 6, PAD = 4;

        int y0 = r.y;
        depositBtn = Button.builder(Component.literal("Deposit all"), b -> {
            var c = Minecraft.getInstance().getConnection();
            if (c != null) c.send(new ServerboundCustomPayloadPacket(new ATMDepositPayload()));
        }).bounds(r.x, y0, r.w, 20).build();
        addRenderableWidget(depositBtn);

        int label1Y  = y0 + 20 + GAP;
        int fields1Y = label1Y + LABEL_TO_FIELD;

        int boxW = Math.max(48, (r.w - 72 - PAD*3) / 2);
        denomBox = new EditBox(this.font, r.x,              fields1Y, boxW, FIELD_H, Component.empty());
        countBox = new EditBox(this.font, r.x + boxW + PAD, fields1Y, boxW, FIELD_H, Component.empty());
        addRenderableWidget(denomBox);
        addRenderableWidget(countBox);

        withdrawFixedBtn = Button.builder(Component.literal("Withdraw"), b -> {
            try {
                int d = Integer.parseInt(denomBox.getValue().trim());
                int c = Integer.parseInt(countBox.getValue().trim());
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new ATMWithdrawPayload(0, d, c)));
            } catch (Exception ignored) {}
        }).bounds(r.x + r.w - 72, fields1Y, 72, 20).build();
        addRenderableWidget(withdrawFixedBtn);

        int label2Y  = fields1Y + FIELD_H + GAP;
        int fields2Y = label2Y + LABEL_TO_FIELD;

        int totalW = r.w - 72 - PAD;
        totalBox = new EditBox(this.font, r.x, fields2Y, totalW, FIELD_H, Component.empty());
        addRenderableWidget(totalBox);

        withdrawTotalBtn = Button.builder(Component.literal("Total"), b -> {
            try {
                int t = Integer.parseInt(totalBox.getValue().trim());
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new ATMWithdrawPayload(1, t, 0)));
            } catch (Exception ignored) {}
        }).bounds(r.x + r.w - 72, fields2Y, 72, 20).build();
        addRenderableWidget(withdrawTotalBtn);

        denomBox.setFilter(s -> s.matches("\\d{0,5}"));
        countBox.setFilter(s -> s.matches("\\d{0,4}"));
        totalBox.setFilter(s -> s.matches("\\d{0,9}"));

        this._label1Y = label1Y - this.topPos;
        this._label2Y = label2Y - this.topPos;

        setUiVisible(false);
    }


    private void setUiVisible(boolean vis) {
        if (depositBtn != null)        { depositBtn.visible = vis;        depositBtn.active = vis; }
        if (withdrawFixedBtn != null)  { withdrawFixedBtn.visible = vis;  withdrawFixedBtn.active = vis; }
        if (withdrawTotalBtn != null)  { withdrawTotalBtn.visible = vis;  withdrawTotalBtn.active = vis; }
        if (denomBox != null)          { denomBox.visible = vis;          denomBox.setEditable(vis); }
        if (countBox != null)          { countBox.visible = vis;          countBox.setEditable(vis); }
        if (totalBox != null)          { totalBox.visible = vis;          totalBox.setEditable(vis); }
    }

    @Override
    public void containerTick() {
        super.containerTick();

        if (statusTicks > 0) statusTicks--;

        flowTicks++;
        switch (flow) {
            case INTRO -> {
                if (flowTicks > 10) { flow = Flow.PIN; flowTicks = 0; }
            }
            case PIN -> {
                if (flowTicks % 10 == 0 && pinProgress < 4) {
                    pinProgress++;
                    pinFlashTicks = 8;
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                if (pinProgress >= 4 && flowTicks > 20) { flow = Flow.AUTH; flowTicks = 0; }
                if (pinFlashTicks > 0) pinFlashTicks--;
            }
            case AUTH -> {
                if (flowTicks > 20) {
                    flow = Flow.READY; flowTicks = 0;
                    setUiVisible(true);
                    setView(View.HOME);
                    requestBalance();
                }
            }
            case READY -> { /* no-op */ }
        }
    }

    private void requestBalance() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(new com.saunhardy.createringtoncurrency.network.ATMQueryBalancePayload()));
        }
    }

    private void renderHome(GuiGraphics g, Rect r) {
        var mc = Minecraft.getInstance();
        String name = (mc.player != null) ? mc.player.getGameProfile().getName() : "Player";
        g.drawString(this.font, "Welcome " + name + "!", r.x, r.y, 0xFFFFFFFF, false);

        String bal = (balance < 0) ? "Balance: —" : "Balance: $" + balance;
        g.drawString(this.font, bal, r.x, r.y + 12, 0xC0C0C0, false);

        int startY = r.y + 36;
        int rowH = 20;
        int itemW = Math.min(r.w, 200);
        int x = r.x + 8;

        String[] items = {"Deposit", "Withdraw"};
        for (int i = 0; i < items.length; i++) {
            int y = startY + i * (rowH + 8);
            boolean sel = (homeSel == i);

            g.fill(x, y, x + itemW, y + rowH, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + itemW, y + 1, 0x33FFFFFF);
            g.fill(x, y + rowH - 1, x + itemW, y + rowH, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (rowH - this.font.lineHeight)/2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            if (i == 0) hitDeposit  = new Rect(x, y, itemW, rowH);
            else        hitWithdraw = new Rect(x, y, itemW, rowH);
        }

        String hint = "Use ↑/↓ or W/S; Enter to select";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        g.blit(ATM_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

        Rect r = contentArea();
        if (flow != Flow.READY) {
            drawIntro(g, r.x, r.y, r.w, r.h);
            return;
        }

        if (view == View.HOME) {
            renderHome(g, r);
        } else if (view == View.DEPOSIT) {
            renderDeposit(g, r);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, "ATM", 6, 4, 0xFFFFFFFF, false);

        if (flow == Flow.READY && view == View.WITHDRAW) {
            Rect r = contentArea();
            int lx = r.x - this.leftPos;
            g.drawString(this.font, "Denom", lx,              _label1Y, 0xA0A0A0, false);
            g.drawString(this.font, "Count", lx + 8 + 48,     _label1Y, 0xA0A0A0, false);
            g.drawString(this.font, "Total", lx,              _label2Y, 0xA0A0A0, false);
        }
    }


    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        boolean showing = statusTicks > 0 && statusText != null && !statusText.isEmpty();
        if (!showing) this.renderTooltip(g, mouseX, mouseY);

        if (showing) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 1000);
            renderStatusPopup(g);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (flow != Flow.READY) { flow = Flow.READY; setView(View.HOME); return true; }
        if (statusTicks > 0) { statusTicks = 0; return true; }

        if (view == View.HOME) {
            if (hitDeposit != null &&
                    mx >= hitDeposit.x && mx <= hitDeposit.x + hitDeposit.w &&
                    my >= hitDeposit.y && my <= hitDeposit.y + hitDeposit.h) {
                goDeposit();
                return true;
            }
            if (hitWithdraw != null &&
                    mx >= hitWithdraw.x && mx <= hitWithdraw.x + hitWithdraw.w &&
                    my >= hitWithdraw.y && my <= hitWithdraw.y + hitWithdraw.h) {
                goWithdraw();
                return true;
            }
        }

        if (view == View.DEPOSIT) {
            if (hitDepAll != null && mx >= hitDepAll.x && mx <= hitDepAll.x + hitDepAll.w &&
                    my >= hitDepAll.y && my <= hitDepAll.y + hitDepAll.h) {
                performDepositAll(); return true;
            }
            if (hitBack   != null && mx >= hitBack.x && mx <= hitBack.x + hitBack.w &&
                    my >= hitBack.y && my <= hitBack.y + hitBack.h) {
                goHome(); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (flow != Flow.READY) { flow = Flow.READY; setView(View.HOME); return true; }
        if (statusTicks > 0) { statusTicks = 0; return true; }

        if (view == View.HOME) {
            if (keyCode == KEY_UP || keyCode == KEY_W) {
                homeSel = (homeSel + 2 - 1) % 2;
                clickSound();
                return true;
            }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) {
                homeSel = (homeSel + 1) % 2;
                clickSound();
                return true;
            }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (homeSel == 0) goDeposit(); else goWithdraw();
                return true;
            }
            if (keyCode == KEY_ESCAPE || keyCode == KEY_LEFT || keyCode == KEY_A) {
                this.onClose();
                return true;
            }
        }

        if (view == View.DEPOSIT) {
            if (keyCode == KEY_UP || keyCode == KEY_W) {
                depositSel = (depositSel + 2 - 1) % 2;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) {
                depositSel = (depositSel + 1) % 2;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (depositSel == 0) performDepositAll(); else goHome();
                return true;
            }
            if (keyCode == KEY_ESCAPE || keyCode == KEY_LEFT || keyCode == KEY_A) {
                goHome();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawIntro(GuiGraphics g, int x, int y, int w, int h) {

        final int TOP_PAD = 6;
        final int cx = x + w / 2;
        final int top = y + TOP_PAD;

        if (flow == Flow.INTRO) {
            return;
        }

        if (flow == Flow.PIN) {
            String title = "Enter PIN";
            g.drawString(this.font, title, cx - this.font.width(title) / 2, top, 0xA8E0FF, false);

            String dots   = "• ".repeat(Math.max(0, pinProgress)).trim();
            String ghosts = "◦ ".repeat(Math.max(0, 4 - pinProgress)).trim();
            String display = (dots + (dots.isEmpty() ? "" : " ") + ghosts).trim();
            int dispW = this.font.width(display);
            g.drawString(this.font, display, cx - dispW / 2, top + 16, 0xFFFFFFFF, false);

            int kW = 24, kH = 16, gap = 6;
            int gridW = 3 * kW + 2 * gap;
            int gridH = 4 * kH + 3 * gap;

            int kx = cx - gridW / 2;
            int ky = top + 32;

            if (kx < x) kx = x;
            if (kx + gridW > x + w) kx = x + w - gridW;
            if (ky + gridH > y + h) ky = y + h - gridH - 4;

            int pressed = (pinFlashTicks > 0) ? (pinProgress == 0 ? -1 : (pinProgress - 1)) : -1;
            int[] seq = {1, 5, 9, 2};

            for (int r = 0, i = 0; r < 4; r++) {
                for (int c = 0; c < 3; c++, i++) {
                    int bx = kx + c * (kW + gap), by = ky + r * (kH + gap);
                    int base = 0xFF3A3F46, hi = 0xFF55606D;
                    boolean flash = (pressed >= 0 && i == mapDigitToIndex(seq[pressed]));
                    g.fill(bx, by, bx + kW, by + kH, flash ? hi : base);
                    g.fill(bx, by, bx + kW, by + 1, 0x66FFFFFF);
                    g.fill(bx, by + kH - 1, bx + kW, by + kH, 0x66000000);
                }
            }
            return;
        }

        if (flow == Flow.AUTH) {
            int cxMid = x + w / 2;
            int cyMid = y + h / 2;
            String t = "Authorizing…";
            g.drawString(this.font, t, cxMid - this.font.width(t) / 2, cyMid - 10, 0xFFFFFF, false);
            drawSpinner(g, cxMid, cyMid + 10, (flowTicks % 20) / 20f);
        }
    }

    private int mapDigitToIndex(int d) {
        return switch (d) {
            case 1 -> 0;  case 2 -> 1;  case 3 -> 2;
            case 4 -> 3;  case 5 -> 4;  case 6 -> 5;
            case 7 -> 6;  case 8 -> 7;  case 9 -> 8;
            case 0 -> 10; default -> 9;
        };
    }

    private void drawSpinner(GuiGraphics g, int cx, int cy, float t) {
        for (int i = 0; i < 8; i++) {
            double ang = (i / 8.0 + t) * Math.PI * 2;
            int px = cx + (int)(Math.cos(ang) * 10);
            int py = cy + (int)(Math.sin(ang) * 10);
            int a = (i == 7 ? 0xFF : 0x66);
            g.fill(px - 1, py - 1, px + 2, py + 2, (a << 24) | 0xFFFFFF);
        }
    }

    private void renderStatusPopup(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0x99000000);

        int cx = this.leftPos + this.imageWidth / 2;
        int cy = this.topPos  + this.imageHeight / 2;

        int textW = this.font.width(statusText);
        int padX = 12, padY = 8;
        int boxW = Math.max(140, textW + padX * 2);
        int boxH = this.font.lineHeight + padY * 2;

        int x0 = cx - boxW / 2, y0 = cy - boxH / 2;
        int x1 = x0 + boxW,     y1 = y0 + boxH;

        g.fill(x0, y0, x1, y1, 0xFF222228);
        g.fill(x0, y0, x1, y0 + 1, 0x99FFFFFF);
        g.fill(x0, y1 - 1, x1, y1, 0x99000000);
        g.fill(x0, y0, x0 + 1, y1, 0x99FFFFFF);
        g.fill(x1 - 1, y0, x1, y1, 0x99000000);

        int tx = x0 + (boxW - textW) / 2;
        int ty = y0 + (boxH - this.font.lineHeight) / 2;
        g.drawString(this.font, statusText, tx, ty, statusColor, false);
    }
}
