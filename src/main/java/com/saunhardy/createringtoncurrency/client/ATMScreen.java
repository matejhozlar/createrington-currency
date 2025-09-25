package com.saunhardy.createringtoncurrency.client;

import com.saunhardy.createringtoncurrency.menu.ATMMenu;
import com.saunhardy.createringtoncurrency.network.ATMDepositPayload;
import com.saunhardy.createringtoncurrency.network.ATMWithdrawPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class ATMScreen extends AbstractContainerScreen<ATMMenu> {

    private EditBox denomBox, countBox, totalBox;

    private EditBox[] bundleBoxes = new EditBox[8];

    private int wtSel = -1;
    private Rect hitWTAct, hitWTBack;

    private int wsSel = -1;
    private Rect hitWSAct, hitWSBack;

    private int wbSel = -1;
    private Rect hitWBAct, hitWBBack;

    private int _label1Y = 34, _label2Y = 70;

    private String statusText = "";
    private int statusColor = 0xFFFFFF;
    private int statusTicks = 0; // ~20 per second

    private enum Flow { INTRO, PIN, AUTH, READY }
    private Flow flow = Flow.INTRO;
    private int flowTicks = 0;
    private int pinProgress = 0;
    private int pinFlashTicks = 0;

    private enum View {
        HOME,
        DEPOSIT,
        WITHDRAW_MENU,
        WITHDRAW_TOTAL,
        WITHDRAW_SINGLE,
        WITHDRAW_BUNDLE
    }
    private View view = View.HOME;

    private int homeSel = 0;
    private Rect hitDeposit, hitWithdraw;

    private int depositSel = 0;
    private Rect hitDepAll, hitBack;

    private int withdrawSel = 0;
    private Rect hitWTotal, hitWSingle, hitWBundle, hitWBack;

    private int balance = -1;
    public void updateBalance(int v) { this.balance = v; }

    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262,
            KEY_ENTER=257, KEY_SPACE=32, KEY_E=69, KEY_ESCAPE=256,
            KEY_W=87, KEY_S=83, KEY_A=65, KEY_D=68, KEY_BACKSPACE=259;

    private static final ResourceLocation ATM_BG =
            ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "textures/gui/atm_bg.png");
    private static final int TEX_W = 320, TEX_H = 200;
    private static final int SCR_X = 16, SCR_Y = 22, SCR_W = 288, SCR_H = 162;

    private static final int[] DENOMS = {1000, 500, 100, 50, 20, 10, 5, 1};

    private static class Rect { int x,y,w,h; Rect(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;} }

    private Rect screenArea() {
        int x = leftPos + (SCR_X * imageWidth ) / TEX_W;
        int y = topPos  + (SCR_Y * imageHeight) / TEX_H;
        int w = (SCR_W * imageWidth ) / TEX_W;
        int h = (SCR_H * imageHeight) / TEX_H;
        return new Rect(x,y,w,h);
    }

    private void attachPlaceholder(EditBox eb, String placeholder) {
        eb.setSuggestion(placeholder);
        eb.setResponder(s -> eb.setSuggestion(s.isEmpty() ? placeholder : ""));
    }


    private static final int CONTENT_PAD = 10;
    private Rect contentArea() {
        Rect s = screenArea();
        return new Rect(s.x + CONTENT_PAD, s.y + CONTENT_PAD, s.w - CONTENT_PAD*2, s.h - CONTENT_PAD*2);
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

        int label1Y  = y0 + 20 + GAP;
        int fields1Y = label1Y + LABEL_TO_FIELD;

        int boxW = Math.max(48, (r.w - 72 - PAD*3) / 2);
        denomBox = new EditBox(this.font, r.x,              fields1Y, boxW, FIELD_H, Component.empty());
        countBox = new EditBox(this.font, r.x + boxW + PAD, fields1Y, boxW, FIELD_H, Component.empty());
        for (EditBox eb : bundleBoxes) {
            if (eb != null) eb.setSuggestion("0");
        }
        addRenderableWidget(denomBox);
        addRenderableWidget(countBox);

        int label2Y  = fields1Y + FIELD_H + GAP;
        int fields2Y = label2Y + LABEL_TO_FIELD;

        int totalW = r.w - 72 - PAD;
        totalBox = new EditBox(this.font, r.x, fields2Y, totalW, FIELD_H, Component.empty());
        addRenderableWidget(totalBox);

        int rowH = 16;
        int by = r.y + 28;
        for (int i = 0; i < DENOMS.length; i++) {
            EditBox eb = new EditBox(this.font, r.x + 100, by + i * (rowH + 4), 48, 14, Component.empty());
            eb.setFilter(s -> s.matches("\\d{0,4}"));
            eb.setValue("");
            bundleBoxes[i] = eb;
            addRenderableWidget(eb);
        }

        denomBox.setFilter(s -> s.matches("\\d{0,5}"));
        countBox.setFilter(s -> s.matches("\\d{0,4}"));
        totalBox.setFilter(s -> s.matches("\\d{0,9}"));

        attachPlaceholder(denomBox, "Denomination");
        attachPlaceholder(countBox, "Count");
        attachPlaceholder(totalBox, "Enter amount");
        for (EditBox eb : bundleBoxes) {
            if (eb != null) attachPlaceholder(eb, "0");
        }

        this._label1Y = label1Y - this.topPos;
        this._label2Y = label2Y - this.topPos;

        setView(View.HOME);
        setUiForView();
    }

    private void setUiForView() {
        boolean vSingle  = (view == View.WITHDRAW_SINGLE);
        boolean vTotal   = (view == View.WITHDRAW_TOTAL);
        boolean vBundle  = (view == View.WITHDRAW_BUNDLE);

        if (denomBox != null) { denomBox.visible = vSingle; denomBox.setEditable(vSingle); }
        if (countBox != null) { countBox.visible = vSingle; countBox.setEditable(vSingle); }

        if (totalBox != null) { totalBox.visible = vTotal;  totalBox.setEditable(vTotal); }

        for (EditBox eb : bundleBoxes) {
            if (eb != null) { eb.visible = vBundle; eb.setEditable(vBundle); }
        }
    }

    private void move(EditBox eb, int x, int y, int w) {
        eb.setX(x); eb.setY(y); eb.setWidth(w);
    }

    private void setView(View v) {
        clearTextFocus();
        this.view = v;

        if (v == View.HOME) {
            updateBalance(-1);
            requestBalance();
        } else if (v == View.DEPOSIT) {
            depositSel = 0;
        } else if (v == View.WITHDRAW_MENU) {
            withdrawSel = 0;
        }

        setUiForView();

        switch (v) {
            case WITHDRAW_TOTAL  -> { wtSel = -1; focus(totalBox); }
            case WITHDRAW_SINGLE -> { wsSel = -1; focus(denomBox); } // or focus(countBox) if you prefer
            case WITHDRAW_BUNDLE -> { wbSel = -1; if (bundleBoxes[0] != null) focus(bundleBoxes[0]); }
            default -> {}
        }
    }

    private void goHome()            { setView(View.HOME); }
    private void goDeposit()         { setView(View.DEPOSIT); }
    private void goWithdrawMenu()    { setView(View.WITHDRAW_MENU); }
    private void goWithdrawTotal()   { setView(View.WITHDRAW_TOTAL); }
    private void goWithdrawSingle()  { setView(View.WITHDRAW_SINGLE); }
    private void goWithdrawBundle()  { setView(View.WITHDRAW_BUNDLE); }

    private void requestBalance() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(
                    new com.saunhardy.createringtoncurrency.network.ATMQueryBalancePayload()));
        }
    }

    private void performDepositAll() {
        var c = Minecraft.getInstance().getConnection();
        if (c != null) {
            c.send(new ServerboundCustomPayloadPacket(new ATMDepositPayload()));
            clickSound();
        }
    }

    private void clearTextFocus() {
        this.setFocused(null);
        if (denomBox != null)  denomBox.setFocused(false);
        if (countBox != null)  countBox.setFocused(false);
        if (totalBox != null)  totalBox.setFocused(false);
        for (EditBox eb : bundleBoxes) if (eb != null) eb.setFocused(false);
    }

    private void focus(EditBox eb) {
        clearTextFocus();
        if (eb != null) {
            this.setFocused(eb);
            eb.setFocused(true);
            eb.setCursorPosition(eb.getValue().length());
        }
    }

    private void performWithdrawSingle() {
        try {
            int d = Integer.parseInt(denomBox.getValue().trim());
            int c = Integer.parseInt(countBox.getValue().trim());
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new ATMWithdrawPayload(0, d, c)));
        } catch (Exception ignored) {}
    }

    private void performWithdrawTotal() {
        try {
            int t = Integer.parseInt(totalBox.getValue().trim());
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) conn.send(new ServerboundCustomPayloadPacket(new ATMWithdrawPayload(1, t, 0)));
        } catch (Exception ignored) {}
    }

    private void performWithdrawBundle() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;

        for (int i = 0; i < DENOMS.length; i++) {
            String v = bundleBoxes[i].getValue().trim();
            if (!v.isEmpty()) {
                try {
                    int count = Integer.parseInt(v);
                    if (count > 0) {
                        conn.send(new ServerboundCustomPayloadPacket(new ATMWithdrawPayload(0, DENOMS[i], count)));
                    }
                } catch (Exception ignored) {}
            }
        }
        clickSound();
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
                    setView(View.HOME);
                }
            }
            case READY -> { /* no-op */ }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        g.blit(ATM_BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

        Rect r = contentArea();
        if (flow != Flow.READY) {
            drawIntro(g, r.x, r.y, r.w, r.h);
            return;
        }

        switch (view) {
            case HOME -> renderHome(g, r);
            case DEPOSIT -> renderDeposit(g, r);
            case WITHDRAW_MENU -> renderWithdrawMenu(g, r);
            case WITHDRAW_TOTAL -> renderWithdrawTotal(g, r);
            case WITHDRAW_SINGLE -> renderWithdrawSingle(g, r);
            case WITHDRAW_BUNDLE -> renderWithdrawBundle(g, r);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, "ATM", 6, 4, 0xFFFFFFFF, false);
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

        String hint = "Use ↑/↓ or W/S; Enter to select";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    private void renderWithdrawMenu(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Withdraw", r.x, r.y, 0xFFFFFFFF, false);

        int startY = r.y + 24;
        int rowH = 20;
        int itemW = Math.min(r.w, 240);
        int x = r.x + 8;

        String[] items = {
                "Enter Amount",
                "Choose Bills",
                "Single Denomination",
                "Back"
        };

        Rect[] hits = new Rect[4];
        for (int i = 0; i < items.length; i++) {
            int y = startY + i * (rowH + 8);
            boolean sel = (withdrawSel == i);

            g.fill(x, y, x + itemW, y + rowH, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + itemW, y + 1, 0x33FFFFFF);
            g.fill(x, y + rowH - 1, x + itemW, y + rowH, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (rowH - this.font.lineHeight) / 2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            hits[i] = new Rect(x, y, itemW, rowH);
        }
        hitWTotal  = hits[0];
        hitWBundle = hits[1];
        hitWSingle = hits[2];
        hitWBack   = hits[3];

        String hint = "Use ↑/↓ or W/S; Enter to select";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    private void renderWithdrawTotal(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Withdraw • Enter Amount", r.x, r.y, 0xFFFFFFFF, false);

        int x = r.x + 8;
        int inputW = Math.min(r.w - 16, 220);
        int inputY = r.y + 22;

        move(totalBox, x, inputY, inputW);

        int rowH = 20;
        int yStart = inputY + 24;

        String[] items = { "Withdraw", "Back" };
        Rect[] hits = new Rect[items.length];

        for (int i = 0; i < items.length; i++) {
            int y = yStart + i * (rowH + 8);
            boolean sel = (wtSel == i);
            g.fill(x, y, x + inputW, y + rowH, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + inputW, y + 1, 0x33FFFFFF);
            g.fill(x, y + rowH - 1, x + inputW, y + rowH, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (rowH - this.font.lineHeight) / 2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            hits[i] = new Rect(x, y, inputW, rowH);
        }
        hitWTAct  = hits[0];
        hitWTBack = hits[1];

        String hint = "Type amount, then Enter on Withdraw";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }


    private void renderWithdrawSingle(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Withdraw • Single Denomination", r.x, r.y, 0xFFFFFFFF, false);

        int x = r.x + 8;
        int rowH = 20;
        int inputY = r.y + 22;

        int boxW = 84;
        int gap = 6;

        move(denomBox, x, inputY, boxW);
        move(countBox, x + boxW + gap, inputY, boxW);

        int itemW = Math.min(r.w - 16, 220);
        int yStart = inputY + 24;

        String[] items = { "Withdraw", "Back" };
        Rect[] hits = new Rect[items.length];

        for (int i = 0; i < items.length; i++) {
            int y = yStart + i * (rowH + 8);
            boolean sel = (wsSel == i);
            g.fill(x, y, x + itemW, y + rowH, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + itemW, y + 1, 0x33FFFFFF);
            g.fill(x, y + rowH - 1, x + itemW, y + rowH, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (rowH - this.font.lineHeight) / 2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            hits[i] = new Rect(x, y, itemW, rowH);
        }
        hitWSAct  = hits[0];
        hitWSBack = hits[1];

        String hint = "Fill Denom & Count, then Withdraw";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    private void renderWithdrawBundle(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Withdraw • Choose Bills", r.x, r.y, 0xFFFFFFFF, false);

        int x = r.x + 8;
        int labelW = 88;
        int rowH = 16;
        int y0 = r.y + 22;

        for (int i = 0; i < DENOMS.length; i++) {
            int y = y0 + i * (rowH + 4);
            g.drawString(this.font, "$" + DENOMS[i] + "  x", x, y + 3, 0xC0C0C0, false);

            move(bundleBoxes[i], x + labelW, y, 48);
        }

        int itemW = Math.min(r.w - 16, 240);
        int yStart = r.y + r.h - (20 + 8 + 20) - 6;

        String[] items = { "Withdraw bundle", "Back" };
        Rect[] hits = new Rect[items.length];

        for (int i = 0; i < items.length; i++) {
            int y = yStart + i * (20 + 8);
            boolean sel = (wbSel == i);
            g.fill(x, y, x + itemW, y + 20, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + itemW, y + 1, 0x33FFFFFF);
            g.fill(x, y + 19, x + itemW, y + 20, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (20 - this.font.lineHeight) / 2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            hits[i] = new Rect(x, y, itemW, 20);
        }
        hitWBAct  = hits[0];
        hitWBBack = hits[1];

        String hint = "Fill counts, then Withdraw bundle";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (flow != Flow.READY) { flow = Flow.READY; setView(View.HOME); return true; }
        if (statusTicks > 0) { statusTicks = 0; return true; }

        if (view == View.HOME) {
            if (hitDeposit != null &&
                    mx >= hitDeposit.x && mx <= hitDeposit.x + hitDeposit.w &&
                    my >= hitDeposit.y && my <= hitDeposit.y + hitDeposit.h) {
                goDeposit(); return true;
            }
            if (hitWithdraw != null &&
                    mx >= hitWithdraw.x && mx <= hitWithdraw.x + hitWithdraw.w &&
                    my >= hitWithdraw.y && my <= hitWithdraw.y + hitWithdraw.h) {
                goWithdrawMenu(); return true;
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

        if (view == View.WITHDRAW_MENU) {
            if (hitWTotal  != null && within(mx,my,hitWTotal))  { goWithdrawTotal();  return true; }
            if (hitWBundle != null && within(mx,my,hitWBundle)) { goWithdrawBundle(); return true; }
            if (hitWSingle != null && within(mx,my,hitWSingle)) { goWithdrawSingle(); return true; }
            if (hitWBack   != null && within(mx,my,hitWBack))   { goHome();           return true; }
        }

        if (view == View.WITHDRAW_TOTAL) {
            if (hitWTAct  != null && within(mx,my,hitWTAct))  { performWithdrawTotal(); return true; }
            if (hitWTBack != null && within(mx,my,hitWTBack)) { goWithdrawMenu();       return true; }
        }

        if (view == View.WITHDRAW_SINGLE) {
            if (hitWSAct  != null && within(mx,my,hitWSAct))  { performWithdrawSingle(); return true; }
            if (hitWSBack != null && within(mx,my,hitWSBack)) { goWithdrawMenu();        return true; }
        }

        if (view == View.WITHDRAW_BUNDLE) {
            if (hitWBAct  != null && within(mx,my,hitWBAct))  { performWithdrawBundle(); return true; }
            if (hitWBBack != null && within(mx,my,hitWBBack)) { goWithdrawMenu();        return true; }
        }

        return super.mouseClicked(mx, my, button);
    }

    private static boolean within(double mx, double my, Rect r) {
        return mx >= r.x && mx <= r.x + r.w && my >= r.y && my <= r.y + r.h;
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isAnyFieldFocused() {
        if (denomBox != null && denomBox.isFocused()) return true;
        if (countBox != null && countBox.isFocused()) return true;
        if (totalBox != null && totalBox.isFocused()) return true;
        for (EditBox eb : bundleBoxes) {
            if (eb != null && eb.isFocused()) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        if (statusTicks > 0) { statusTicks = 0; return true; }

        if (flow != Flow.READY) { flow = Flow.READY; setView(View.HOME); return true; }
        if (isAnyFieldFocused()) {
            if (view == View.WITHDRAW_TOTAL && totalBox != null && totalBox.isFocused()) {
                if (keyCode == KEY_DOWN || keyCode == KEY_S) {
                    clearTextFocus();
                    wtSel = 0;
                    clickSound();
                    return true;
                }
            }
            if (view == View.WITHDRAW_SINGLE) {
                boolean inputFocused = (denomBox != null && denomBox.isFocused())
                        || (countBox != null && countBox.isFocused());
                if (inputFocused && (keyCode == KEY_DOWN || keyCode == KEY_S)) {
                    clearTextFocus();
                    wsSel = 0;
                    clickSound();
                    return true;
                }
            }
            if (view == View.WITHDRAW_BUNDLE) {
                boolean anyFocused = false;
                for (EditBox eb : bundleBoxes) if (eb != null && eb.isFocused()) { anyFocused = true; break; }
                if (anyFocused && (keyCode == KEY_DOWN || keyCode == KEY_S)) {
                    clearTextFocus();
                    wbSel = 0;
                    clickSound();
                    return true;
                }
            }

            if (keyCode == KEY_BACKSPACE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }

            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E
                    || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (view == View.WITHDRAW_TOTAL)   { performWithdrawTotal();  return true; }
                if (view == View.WITHDRAW_SINGLE)  { performWithdrawSingle(); return true; }
                if (view == View.WITHDRAW_BUNDLE)  { performWithdrawBundle(); return true; }
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }


        if (view == View.WITHDRAW_TOTAL) {
            if (totalBox != null && totalBox.isFocused()) {
                if (keyCode == KEY_DOWN || keyCode == KEY_S) {
                    totalBox.setFocused(false);
                    this.setFocused(null);
                    wtSel = 0;
                    clickSound();
                    return true;
                }
            } else {
                if ((keyCode == KEY_UP || keyCode == KEY_W) && wtSel == 0) {
                    focus(totalBox);
                    wtSel = -1;
                    clickSound();
                    return true;
                }
            }
        }

        if (view == View.WITHDRAW_SINGLE) {
            boolean inputFocused = (denomBox != null && denomBox.isFocused())
                    || (countBox != null && countBox.isFocused());

            if (inputFocused) {
                if (keyCode == KEY_DOWN || keyCode == KEY_S) {
                    clearTextFocus();
                    wsSel = 0;
                    clickSound();
                    return true;
                }
            } else {
                if ((keyCode == KEY_UP || keyCode == KEY_W) && wsSel == 0) {
                    if (countBox != null) focus(countBox); else focus(denomBox);
                    wsSel = -1;
                    clickSound();
                    return true;
                }
            }
        }

        if (view == View.WITHDRAW_BUNDLE) {
            boolean anyFocused = false;
            for (EditBox eb : bundleBoxes) if (eb != null && eb.isFocused()) { anyFocused = true; break; }

            if (anyFocused) {
                if (keyCode == KEY_DOWN || keyCode == KEY_S) {
                    clearTextFocus();
                    wbSel = 0;
                    clickSound();
                    return true;
                }
            } else {
                if ((keyCode == KEY_UP || keyCode == KEY_W) && wbSel == 0) {
                    if (bundleBoxes[0] != null) focus(bundleBoxes[0]);
                    wbSel = -1;
                    clickSound();
                    return true;
                }
            }
        }

        if (view == View.HOME) {
            if (keyCode == KEY_UP || keyCode == KEY_W)   { homeSel = (homeSel + 2 - 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { homeSel = (homeSel + 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (homeSel == 0) goDeposit(); else goWithdrawMenu();
                return true;
            }
            if (keyCode == KEY_BACKSPACE) { this.onClose(); return true; }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (view == View.DEPOSIT) {
            if (keyCode == KEY_UP || keyCode == KEY_W)   { depositSel = (depositSel + 2 - 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { depositSel = (depositSel + 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (depositSel == 0) performDepositAll(); else goHome();
                return true;
            }
            if (keyCode == KEY_BACKSPACE) { goHome(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (view == View.WITHDRAW_MENU) {
            if (keyCode == KEY_UP || keyCode == KEY_W)   { withdrawSel = (withdrawSel + 4 - 1) % 4; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { withdrawSel = (withdrawSel + 1) % 4; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                switch (withdrawSel) {
                    case 0 -> goWithdrawTotal();
                    case 1 -> goWithdrawBundle();
                    case 2 -> goWithdrawSingle();
                    case 3 -> goHome();
                }
                return true;
            }
            if (keyCode == KEY_BACKSPACE) { clearTextFocus(); goHome(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (view == View.WITHDRAW_TOTAL) {
            if (keyCode == KEY_UP || keyCode == KEY_W)   { wtSel = (wtSel + 2 - 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { wtSel = (wtSel + 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (wtSel == 0) performWithdrawTotal(); else goWithdrawMenu();
                return true;
            }
            if (keyCode == KEY_BACKSPACE) { clearTextFocus(); goWithdrawMenu(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (view == View.WITHDRAW_SINGLE) {
            if (keyCode == KEY_UP || keyCode == KEY_W)   { wsSel = (wsSel + 2 - 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { wsSel = (wsSel + 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (wsSel == 0) performWithdrawSingle(); else goWithdrawMenu();
                return true;
            }
            if (keyCode == KEY_BACKSPACE) { clearTextFocus(); goWithdrawMenu(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (view == View.WITHDRAW_BUNDLE) {
            if (keyCode == KEY_UP || keyCode == KEY_W)   { wbSel = (wbSel + 2 - 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { wbSel = (wbSel + 1) % 2; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (wbSel == 0) performWithdrawBundle(); else goWithdrawMenu();
                return true;
            }
            if (keyCode == KEY_BACKSPACE) { clearTextFocus(); goWithdrawMenu(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    private void drawIntro(GuiGraphics g, int x, int y, int w, int h) {
        final int TOP_PAD = 6;
        final int cx = x + w / 2;
        final int top = y + TOP_PAD;

        if (flow == Flow.INTRO) return;

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
