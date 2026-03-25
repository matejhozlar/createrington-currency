package com.saunhardy.createringtoncurrency.client;

import com.saunhardy.createringtoncurrency.menu.ATMMenu;
import com.saunhardy.createringtoncurrency.network.ATMDepositPayload;
import com.saunhardy.createringtoncurrency.network.ATMWithdrawPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ATMScreen extends AbstractContainerScreen<ATMMenu> {

    private EditBox totalBox;

    private EditBox[] bundleBoxes = new EditBox[8];

    private int wtSel = -1;
    private Rect hitWTAct, hitWTBack;


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
        WITHDRAW_BUNDLE,
        HISTORY
    }
    private View view = View.HOME;

    private int homeSel = 0;
    private Rect hitDeposit, hitWithdraw, hitHistory;

    private int depositSel = 0;
    private Rect hitDepAll, hitBack;

    private int withdrawSel = 0;
    private Rect hitWTotal, hitWBundle, hitWBack;

    private int balance = -1;
    public void updateBalance(int v) { this.balance = v; }

    private List<HistoryEntry> historyEntries = new ArrayList<>();
    private int historyPage = 1;
    private boolean historyHasMore = false;
    private boolean historyLoading = false;
    private Rect hitHistBack;

    private record HistoryEntry(String type, String amount, String description, String createdAt) {}

    private static final int KEY_UP=265, KEY_DOWN=264, KEY_LEFT=263, KEY_RIGHT=262,
            KEY_ENTER=257, KEY_SPACE=32, KEY_E=69, KEY_ESCAPE=256,
            KEY_W=87, KEY_S=83, KEY_A=65, KEY_D=68, KEY_BACKSPACE=259;

    private static final ResourceLocation ATM_BG =
            ResourceLocation.fromNamespaceAndPath("createringtoncurrency", "textures/gui/atm_bg.png");
    private static final int TEX_W = 320, TEX_H = 200;
    private static final int SCR_X = 16, SCR_Y = 22, SCR_W = 288, SCR_H = 162;

    private static final int[] DENOMS = com.saunhardy.createringtoncurrency.CreateringtonCurrency.DENOMINATIONS;

    private double bundleScroll = 0;
    private static final int BUNDLE_ROW_H = 16;
    private static final int BUNDLE_ROW_GAP = 4;

    private static final int ACTION_ROW_H = 20;
    private static final int ACTION_ROW_GAP = 8;
    private static final int HINT_H = 12;
    private static final int GAP_LIST_TO_ACTIONS = 6;

    // Custom button sizing
    private static final int BTN_MIN_W = 160;
    private static final int BTN_MAX_W = 240;
    private static final int BTN_H = 20;
    private static final int BTN_X_PAD = 8;

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private int uniformButtonW(Rect r) { return clamp(r.w - 2 * BTN_X_PAD, BTN_MIN_W, BTN_MAX_W); }
    private int buttonX(Rect r) { return r.x + BTN_X_PAD; }

    private static ResourceLocation tx(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                "createringtoncurrency", "textures/item/" + name + ".png"
        );
    }

    private static final ResourceLocation[] BILL_TEX = {
            tx("bill_1000"), tx("bill_500"), tx("bill_100"), tx("bill_50"),
            tx("bill_20"),   tx("bill_10"),  tx("bill_5"),   tx("bill_1")
    };

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

        totalBox.setFilter(s -> s.matches("\\d{0,9}"));
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
        boolean vTotal   = (view == View.WITHDRAW_TOTAL);
        boolean vBundle  = (view == View.WITHDRAW_BUNDLE);

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
        } else if (v == View.HISTORY) {
            historyLoading = false;
            historyEntries = new ArrayList<>();
        }

        setUiForView();

        switch (v) {
            case WITHDRAW_TOTAL  -> { wtSel = -1; focus(totalBox); }
            case WITHDRAW_BUNDLE -> { bundleScroll = 0; wbSel = -1; if (bundleBoxes[DENOMS.length - 1] != null) focus(bundleBoxes[DENOMS.length - 1]);}
            default -> {}
        }
    }

    private void goHome()            { setView(View.HOME); }
    private void goDeposit()         { setView(View.DEPOSIT); }
    private void goWithdrawMenu()    { setView(View.WITHDRAW_MENU); }
    private void goWithdrawTotal()   { setView(View.WITHDRAW_TOTAL); }
    private void goWithdrawBundle()  { setView(View.WITHDRAW_BUNDLE); }
    private void goHistory()         { setView(View.HISTORY); requestHistory(1); }

    private void requestBalance() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(
                    new com.saunhardy.createringtoncurrency.network.ATMQueryBalancePayload()));
        }
    }

    private void requestHistory(int page) {
        historyPage = page;
        historyLoading = true;
        historyEntries = new ArrayList<>();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(
                    new com.saunhardy.createringtoncurrency.network.ATMQueryHistoryPayload(page)));
        }
    }

    public void updateHistory(int page, boolean hasMore, String jsonData) {
        this.historyPage = page;
        this.historyHasMore = hasMore;
        this.historyLoading = false;
        this.historyEntries = parseHistory(jsonData);
    }

    private List<HistoryEntry> parseHistory(String json) {
        var list = new ArrayList<HistoryEntry>();
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                list.add(new HistoryEntry(
                        obj.get("transactionType").getAsString(),
                        obj.get("amount").getAsString(),
                        obj.has("description") && !obj.get("description").isJsonNull()
                                ? obj.get("description").getAsString() : "",
                        obj.get("createdAt").getAsString()
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private int focusedBundleIndex() {
        for (int i = 0; i < bundleBoxes.length; i++) {
            if (bundleBoxes[i] != null && bundleBoxes[i].isFocused()) return i;
        }
        return -1;
    }

    private int displayIndexForData(int dataIdx) {
        return DENOMS.length - 1 - dataIdx;
    }

    private void ensureBundleVisible(int dataIdx, int viewportH) {
        int totalH = DENOMS.length * (BUNDLE_ROW_H + BUNDLE_ROW_GAP) - BUNDLE_ROW_GAP;
        int maxScroll = Math.max(0, totalH - viewportH);

        int rowTop = displayIndexForData(dataIdx) * (BUNDLE_ROW_H + BUNDLE_ROW_GAP);
        int rowBottom = rowTop + BUNDLE_ROW_H;

        if (bundleScroll > rowTop) bundleScroll = rowTop;
        if (bundleScroll < rowBottom - viewportH) bundleScroll = rowBottom - viewportH;

        if (bundleScroll < 0) bundleScroll = 0;
        if (bundleScroll > maxScroll) bundleScroll = maxScroll;
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
            case WITHDRAW_BUNDLE -> renderWithdrawBundle(g, r);
            case HISTORY -> renderHistory(g, r);
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
        int rowH = BTN_H;
        int itemW = uniformButtonW(r);
        int x = buttonX(r);

        String[] items = {"Deposit", "Withdraw", "History"};
        for (int i = 0; i < items.length; i++) {
            int y = startY + i * (rowH + 8);
            boolean sel = (homeSel == i);

            g.fill(x, y, x + itemW, y + rowH, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + itemW, y + 1, 0x33FFFFFF);
            g.fill(x, y + rowH - 1, x + itemW, y + rowH, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (rowH - this.font.lineHeight)/2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            switch (i) {
                case 0 -> hitDeposit  = new Rect(x, y, itemW, rowH);
                case 1 -> hitWithdraw = new Rect(x, y, itemW, rowH);
                case 2 -> hitHistory  = new Rect(x, y, itemW, rowH);
            }
        }

        String hint = "Use ↑/↓ or W/S; Enter to select";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    private void renderDeposit(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Deposit", r.x, r.y, 0xFFFFFFFF, false);

        int startY = r.y + 24;
        int rowH = BTN_H;
        int itemW = uniformButtonW(r);
        int x = buttonX(r);

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
        int rowH = BTN_H;
        int itemW = uniformButtonW(r);
        int x = buttonX(r);

        String[] items = {
                "Enter Amount",
                "Choose Bills",
                "Back"
        };

        Rect[] hits = new Rect[3];
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
        hitWBack   = hits[2];

        String hint = "Use ↑/↓ or W/S; Enter to select";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    private void renderWithdrawTotal(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Withdraw • Enter Amount", r.x, r.y, 0xFFFFFFFF, false);

        int x = buttonX(r);

        int inputW = Math.min(r.w - 16, 220);
        int inputY = r.y + 22;
        move(totalBox, x, inputY, inputW);

        int rowH = BTN_H;
        int btnW = uniformButtonW(r);
        int yStart = inputY + 24;

        String[] items = { "Withdraw", "Back" };
        Rect[] hits = new Rect[items.length];

        for (int i = 0; i < items.length; i++) {
            int y = yStart + i * (rowH + 8);
            boolean sel = (wtSel == i);
            g.fill(x, y, x + btnW, y + rowH, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + btnW, y + 1, 0x33FFFFFF);
            g.fill(x, y + rowH - 1, x + btnW, y + rowH, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (rowH - this.font.lineHeight) / 2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            hits[i] = new Rect(x, y, btnW, rowH);
        }
        hitWTAct  = hits[0];
        hitWTBack = hits[1];

        String hint = "Type amount, then Enter on Withdraw";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }


    private void renderWithdrawBundle(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Withdraw • Choose Bills", r.x, r.y, 0xFFFFFFFF, false);

        int x = buttonX(r);
        int labelW = 88;

        int listTop = r.y + 22;

        int actionsTop = r.y + r.h
                - (2 * ACTION_ROW_H + ACTION_ROW_GAP)
                - HINT_H
                - 6;

        int listBottom = actionsTop - GAP_LIST_TO_ACTIONS;
        int viewportH = Math.max(0, listBottom - listTop);

        int totalH = DENOMS.length * (BUNDLE_ROW_H + BUNDLE_ROW_GAP) - BUNDLE_ROW_GAP;
        int maxScroll = Math.max(0, totalH - viewportH);
        bundleScroll = Math.max(0, Math.min(bundleScroll, maxScroll));

        int yBase = listTop - (int) bundleScroll;

        for (int i = 0; i < DENOMS.length; i++) {
            int dataIdx = DENOMS.length - 1 - i;
            int y = yBase + i * (BUNDLE_ROW_H + BUNDLE_ROW_GAP);

            boolean inView = (y >= listTop) && (y + BUNDLE_ROW_H <= listBottom);

            if (inView) {
                if (dataIdx < BILL_TEX.length && BILL_TEX[dataIdx] != null) {
                    g.blit(BILL_TEX[dataIdx], x, y, 0, 0, 16, 16, 16, 16);
                }
                int labelX = x + 20;
                g.drawString(this.font, "$" + DENOMS[dataIdx] + "  x", labelX, y + 3, 0xC0C0C0, false);
            }

            EditBox eb = bundleBoxes[dataIdx];
            if (eb != null) {
                int boxX = x + labelW;
                move(eb, boxX, y, 48);
                eb.visible = inView;
                eb.setEditable(inView);
                if (!inView && eb.isFocused()) { eb.setFocused(false); this.setFocused(null); }
            }
        }

        int itemW = uniformButtonW(r);
        int yStart = actionsTop;

        String[] items = { "Withdraw bundle", "Back" };
        Rect[] hits = new Rect[items.length];

        for (int i = 0; i < items.length; i++) {
            int y = yStart + i * (ACTION_ROW_H + ACTION_ROW_GAP);
            boolean sel = (wbSel == i);
            g.fill(x, y, x + itemW, y + ACTION_ROW_H, sel ? 0xFF2B3138 : 0xFF1D2227);
            g.fill(x, y, x + itemW, y + 1, 0x33FFFFFF);
            g.fill(x, y + ACTION_ROW_H - 1, x + itemW, y + ACTION_ROW_H, 0x33000000);

            String label = (sel ? "> " : "  ") + items[i];
            int ty = y + (ACTION_ROW_H - this.font.lineHeight) / 2;
            g.drawString(this.font, label, x + 8, ty, 0xFFFFFFFF, false);

            hits[i] = new Rect(x, y, itemW, ACTION_ROW_H);
        }
        hitWBAct  = hits[0];
        hitWBBack = hits[1];
    }

    private void renderHistory(GuiGraphics g, Rect r) {
        g.drawString(this.font, "Transaction History", r.x, r.y, 0xFFFFFFFF, false);

        String pageStr = "Page " + historyPage;
        int pageW = this.font.width(pageStr);
        g.drawString(this.font, pageStr, r.x + r.w - pageW, r.y, 0x80A0A0A0, false);

        int listY = r.y + 16;

        if (historyLoading) {
            g.drawString(this.font, "Loading...", r.x, listY, 0xC0C0C0, false);
        } else if (historyEntries.isEmpty()) {
            g.drawString(this.font, "No transactions found.", r.x, listY, 0xC0C0C0, false);
        } else {
            int rowH = 11;
            for (int i = 0; i < historyEntries.size(); i++) {
                int y = listY + i * rowH;
                var e = historyEntries.get(i);
                String type = formatType(e.type);
                boolean neg = e.amount.startsWith("-");
                String displayAmt = neg ? "-$" + e.amount.substring(1) : "+$" + e.amount;
                int amtColor = neg ? 0xE74C3C : 0x2ECC71;
                String date = formatDate(e.createdAt);

                g.drawString(this.font, type, r.x, y, 0xC0C0C0, false);
                int dateW = this.font.width(date);
                g.drawString(this.font, date, r.x + r.w - dateW, y, 0x808080, false);
                int amtW = this.font.width(displayAmt);
                g.drawString(this.font, displayAmt, r.x + r.w - dateW - 6 - amtW, y, amtColor, false);
            }
        }

        // Page navigation arrows
        int navY = listY + 5 * 11 + 6;
        int cx = r.x + r.w / 2;
        String pageText = "Page " + historyPage;
        int ptW = this.font.width(pageText);
        g.drawString(this.font, pageText, cx - ptW / 2, navY, 0xA0A0A0, false);
        if (historyPage > 1) {
            g.drawString(this.font, "<", cx - ptW / 2 - 12, navY, 0xFFFFFF, false);
        }
        if (historyHasMore) {
            g.drawString(this.font, ">", cx + ptW / 2 + 6, navY, 0xFFFFFF, false);
        }

        // Back button
        int x = buttonX(r);
        int itemW = uniformButtonW(r);
        int btnY = navY + 16;
        g.fill(x, btnY, x + itemW, btnY + BTN_H, 0xFF2B3138);
        g.fill(x, btnY, x + itemW, btnY + 1, 0x33FFFFFF);
        g.fill(x, btnY + BTN_H - 1, x + itemW, btnY + BTN_H, 0x33000000);
        int ty = btnY + (BTN_H - this.font.lineHeight) / 2;
        g.drawString(this.font, "> Back", x + 8, ty, 0xFFFFFFFF, false);
        hitHistBack = new Rect(x, btnY, itemW, BTN_H);

        String hint = "Use \u2190/\u2192 for pages; Enter = Back";
        g.drawString(this.font, hint, r.x, r.y + r.h - 10, 0x80A0A0A0, false);
    }

    private static String formatType(String type) {
        return switch (type) {
            case "transfer_send" -> "Sent";
            case "transfer_receive" -> "Received";
            case "deposit" -> "Deposit";
            case "withdraw" -> "Withdraw";
            case "admin_grant" -> "Admin Grant";
            case "admin_deduct" -> "Admin Deduct";
            case "reward" -> "Reward";
            case "daily_reward" -> "Daily";
            case "lottery_entry" -> "Lottery Entry";
            case "lottery_win" -> "Lottery Win";
            case "lottery_refund" -> "Lottery Refund";
            case "crypto_buy" -> "Crypto Buy";
            case "crypto_sell" -> "Crypto Sell";
            default -> type;
        };
    }

    private static String formatDate(String iso) {
        try {
            Instant then = Instant.parse(iso);
            long seconds = Duration.between(then, Instant.now()).getSeconds();
            if (seconds < 0) return "now";
            if (seconds < 60) return seconds + "s ago";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m ago";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            if (days < 30) return days + "d ago";
            return days / 30 + "mo ago";
        } catch (Exception e) {
            return iso.length() >= 10 ? iso.substring(0, 10) : iso;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (view == View.WITHDRAW_BUNDLE) {
            Rect r = contentArea();

            int listTop = r.y + 22;
            int actionsTop = r.y + r.h - (2 * ACTION_ROW_H + ACTION_ROW_GAP) - HINT_H - 6;
            int listBottom = actionsTop - GAP_LIST_TO_ACTIONS;
            int viewportH = Math.max(0, listBottom - listTop);

            int totalH = DENOMS.length * (BUNDLE_ROW_H + BUNDLE_ROW_GAP) - BUNDLE_ROW_GAP;
            int maxScroll = Math.max(0, totalH - viewportH);

            double wheel = Math.abs(dy) > 1e-6 ? dy : dx;
            if (my >= listTop && my <= listBottom) {
                bundleScroll = Math.max(0, Math.min(bundleScroll - wheel * 12.0, (double) maxScroll));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
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
            if (hitHistory != null && within(mx, my, hitHistory)) {
                goHistory(); return true;
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
            if (hitWBack   != null && within(mx,my,hitWBack))   { goHome();           return true; }
        }

        if (view == View.WITHDRAW_TOTAL) {
            if (hitWTAct  != null && within(mx,my,hitWTAct))  { performWithdrawTotal(); return true; }
            if (hitWTBack != null && within(mx,my,hitWTBack)) { goWithdrawMenu();       return true; }
        }

        if (view == View.WITHDRAW_BUNDLE) {
            if (hitWBAct  != null && within(mx,my,hitWBAct))  { performWithdrawBundle(); return true; }
            if (hitWBBack != null && within(mx,my,hitWBBack)) { goWithdrawMenu();        return true; }
        }

        if (view == View.HISTORY) {
            if (hitHistBack != null && within(mx, my, hitHistBack)) { goHome(); return true; }
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
            if (view == View.WITHDRAW_BUNDLE) {
                int dataIdx = focusedBundleIndex();
                if (dataIdx != -1) {
                    Rect r = contentArea();
                    int listTop = r.y + 22;
                    int actionsTop = r.y + r.h - (2 * ACTION_ROW_H + ACTION_ROW_GAP) - HINT_H - 6;
                    int listBottom = actionsTop - GAP_LIST_TO_ACTIONS;
                    int viewportH = Math.max(0, listBottom - listTop);

                    if (keyCode == KEY_DOWN || keyCode == KEY_S) {
                        if (dataIdx > 0) {
                            ensureBundleVisible(dataIdx - 1, viewportH);
                            focus(bundleBoxes[dataIdx - 1]);
                            clickSound();
                        } else {
                            clearTextFocus(); wbSel = 0; clickSound();
                        }
                        return true;
                    }
                    if (keyCode == KEY_UP || keyCode == KEY_W) {
                        if (dataIdx < DENOMS.length - 1) {
                            ensureBundleVisible(dataIdx + 1, viewportH);
                            focus(bundleBoxes[dataIdx + 1]);
                            clickSound();
                            return true;
                        }
                    }
                }
            }

            if (keyCode == KEY_BACKSPACE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }

            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E
                    || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                if (view == View.WITHDRAW_TOTAL)   { performWithdrawTotal();  return true; }
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

            if (view == View.WITHDRAW_BUNDLE) {
            if ((keyCode == KEY_UP || keyCode == KEY_W) && wbSel == 0) {
                Rect r = contentArea();
                int listTop = r.y + 22;
                int actionsTop = r.y + r.h - (2 * ACTION_ROW_H + ACTION_ROW_GAP) - HINT_H - 6;
                int listBottom = actionsTop - GAP_LIST_TO_ACTIONS;
                int viewportH = Math.max(0, listBottom - listTop);

                ensureBundleVisible(0, viewportH);
                focus(bundleBoxes[0]);
                wbSel = -1;
                clickSound();
                return true;
            }
        }

        if (view == View.HOME) {
            if (keyCode == KEY_UP || keyCode == KEY_W)   { homeSel = (homeSel + 3 - 1) % 3; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { homeSel = (homeSel + 1) % 3; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                switch (homeSel) {
                    case 0 -> goDeposit();
                    case 1 -> goWithdrawMenu();
                    case 2 -> goHistory();
                }
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
            if (keyCode == KEY_UP || keyCode == KEY_W)   { withdrawSel = (withdrawSel + 3 - 1) % 3; clickSound(); return true; }
            if (keyCode == KEY_DOWN || keyCode == KEY_S) { withdrawSel = (withdrawSel + 1) % 3; clickSound(); return true; }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_RIGHT || keyCode == KEY_D) {
                switch (withdrawSel) {
                    case 0 -> goWithdrawTotal();
                    case 1 -> goWithdrawBundle();
                    case 2 -> goHome();
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

        if (view == View.HISTORY) {
            if (keyCode == KEY_LEFT || keyCode == KEY_A) {
                if (historyPage > 1 && !historyLoading) { requestHistory(historyPage - 1); clickSound(); }
                return true;
            }
            if (keyCode == KEY_RIGHT) {
                if (historyHasMore && !historyLoading) { requestHistory(historyPage + 1); clickSound(); }
                return true;
            }
            if (keyCode == KEY_ENTER || keyCode == KEY_SPACE || keyCode == KEY_E || keyCode == KEY_D) {
                goHome(); return true;
            }
            if (keyCode == KEY_UP || keyCode == KEY_W || keyCode == KEY_DOWN || keyCode == KEY_S) {
                return true;
            }
            if (keyCode == KEY_BACKSPACE) { goHome(); return true; }
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
