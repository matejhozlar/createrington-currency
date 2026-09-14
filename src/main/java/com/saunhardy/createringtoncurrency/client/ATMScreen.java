package com.saunhardy.createringtoncurrency.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.network.ATMDepositPayload;
import com.saunhardy.createringtoncurrency.network.ATMQueryBalancePayload;
import com.saunhardy.createringtoncurrency.network.ATMQueryHistoryPayload;
import com.saunhardy.createringtoncurrency.network.ATMResultPayload;
import com.saunhardy.createringtoncurrency.network.ATMWithdrawPayload;
import com.saunhardy.createringtoncurrency.util.Bills;
import com.saunhardy.createringtoncurrency.util.TransactionFormat;
import com.sighs.apricityui.event.KeyEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ATMScreen extends ApricityScreen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PAGE = "createringtoncurrency/atm.html";
    private static final String CHOOSE_BILLS_TAB = "choose-bills-menu";
    private static final int[] DENOMS = Bills.DENOMINATIONS;
    private static final int MAX_BILL_COUNT = 999;
    private static final int MAX_BILL_DIGITS = 3;
    private static final int MAX_AMOUNT_DIGITS = 9;
    private static final int STATUS_TICKS = 60;
    private static final int HOLD_DELAY_TICKS = 20;
    private static final int CONNECT_TIMEOUT_TICKS = 20 * 40;
    private static final int CONNECT_DOT_TICKS = 10;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;

    private enum View { CONNECTING, OUT_OF_SERVICE, HOME, DEPOSIT, WITHDRAW, HISTORY }

    private record HistoryEntry(String type, String amount, String createdAt) {}

    private View view = View.CONNECTING;
    private String withdrawTab = CHOOSE_BILLS_TAB;
    private final int[] billCounts = Bills.none();
    private String withdrawAmount = "";
    private String depositAmount = "";
    private int balance = -1;
    private boolean probeStarted = false;
    private boolean probing = false;
    private int connectTicks = 0;

    private List<HistoryEntry> historyEntries = new ArrayList<>();
    private int historyPage = 1;
    private boolean historyHasMore = false;
    private boolean historyLoading = false;

    private String statusText = "";
    private String statusColor = "#ffffff";
    private boolean statusInfo = false;
    private int statusTicks = 0;

    private Document doc;
    private Document boundDoc;
    private long boundGeneration = -1;
    private int holdIndex = -1;
    private int holdDelta = 0;
    private int holdTicks = 0;
    private boolean holdRepeated = false;

    public ATMScreen() {
        super(PAGE);
        setPauseGame(false);
        setShowDefaultBackground(true);
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new ATMScreen()));
    }

    @Override
    protected void init() {
        super.init();
        doc = getLinkedDocument();
        holdIndex = -1;
        if (doc == null) {
            LOGGER.error("ATM page {} could not be loaded; check the [AUI HTML] log lines", PAGE);
            return;
        }
        ensureBound();
        if (!probeStarted) {
            probeStarted = true;
            probe();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (doc == null) {
            onClose();
            return;
        }
        ensureBound();
        if (statusTicks > 0 && --statusTicks == 0) refreshStatus();
        if (holdIndex >= 0 && ++holdTicks >= HOLD_DELAY_TICKS) {
            holdRepeated = true;
            step(holdIndex, holdDelta);
        }
        if (probing) {
            connectTicks++;
            if (connectTicks >= CONNECT_TIMEOUT_TICKS) {
                probing = false;
                enterView(View.OUT_OF_SERVICE);
            } else if (connectTicks % CONNECT_DOT_TICKS == 0) {
                refreshConnecting();
            }
        }
    }

    public void updateBalance(int value, boolean available) {
        balance = available ? value : -1;
        if (probing || (view == View.OUT_OF_SERVICE && available)) {
            probing = false;
            enterView(available ? View.HOME : View.OUT_OF_SERVICE);
            return;
        }
        if (doc != null) refreshBalance();
    }

    public void updateHistory(int page, boolean hasMore, String jsonData) {
        historyPage = page;
        historyHasMore = hasMore;
        historyLoading = false;
        historyEntries = parseHistory(jsonData);
        if (doc != null) refreshHistory();
    }

    public void showResult(int kind, int op, String message) {
        showStatus(kind, message);
        if (kind != ATMResultPayload.KIND_SUCCESS) return;
        if (op == ATMResultPayload.OP_DEPOSIT) {
            depositAmount = "";
            if (doc != null) setValue("deposit-amount", "");
        } else if (op == ATMResultPayload.OP_WITHDRAW) {
            Arrays.fill(billCounts, 0);
            withdrawAmount = "";
            if (doc != null) {
                refreshBillInputs();
                setValue("withdraw-amount", "");
                refreshWithdrawBreakdown();
            }
        }
        if (doc != null) refreshDepositBreakdown();
        requestBalance();
    }

    private void showStatus(int kind, String message) {
        statusText = message == null ? "" : message;
        statusInfo = kind == ATMResultPayload.KIND_INFO;
        statusColor = switch (kind) {
            case ATMResultPayload.KIND_SUCCESS -> "#2ecc71";
            case ATMResultPayload.KIND_ERROR -> "#e74c3c";
            default -> "#ffffff";
        };
        statusTicks = STATUS_TICKS;
        if (doc != null) refreshStatus();
    }

    private void ensureBound() {
        if (doc == boundDoc && doc.getRefreshGeneration() == boundGeneration) return;
        boundDoc = doc;
        boundGeneration = doc.getRefreshGeneration();
        holdIndex = -1;
        bind();
        applyState();
    }

    private void bind() {
        for (Element button : doc.querySelectorAll(".atm-button")) {
            button.addEventListener("mouseenter", e -> swapClass(button, "is-unhovering", "is-hovering"));
            button.addEventListener("mouseleave", e -> swapClass(button, "is-hovering", "is-unhovering"));
        }
        for (Element close : doc.querySelectorAll(".close-view")) {
            close.addEventListener("click", e -> {
                clickSound();
                showView(View.HOME);
            });
        }
        onClick("go-deposit", () -> showView(View.DEPOSIT));
        onClick("go-withdraw", () -> showView(View.WITHDRAW));
        onClick("go-history", this::goHistory);
        onClick("retry", this::probe);
        onClick("leave", this::onClose);

        onClick("deposit-all", this::performDepositAll);
        onClick("deposit-amount-go", this::performDepositAmount);
        bindAmountInput("deposit-amount", value -> {
            depositAmount = value;
            refreshDepositBreakdown();
        }, this::performDepositAmount);

        for (Element tab : doc.querySelectorAll(".cash-tab")) {
            tab.addEventListener("click", e -> {
                clickSound();
                withdrawTab = tab.getAttribute("data-submenu");
                applyWithdrawTab();
            });
        }
        for (Element control : doc.querySelectorAll(".amount-control")) {
            int index = Bills.indexOfDenomination(parseIntOrZero(control.getAttribute("data-denomination")));
            if (index < 0) continue;
            bindStepper(control.querySelector(".minus"), index, -1);
            bindStepper(control.querySelector(".plus"), index, 1);
            Element input = control.querySelector(".amount");
            if (input == null) continue;
            input.addEventListener("input", e -> {
                String digits = digitsOnly(input.getValue(), MAX_BILL_DIGITS);
                if (!digits.equals(input.getValue())) input.setValue(digits);
                billCounts[index] = Math.min(MAX_BILL_COUNT, parseIntOrZero(digits));
                refreshBillTotal();
            });
            input.addEventListener("blur", e -> refreshBillInput(index));
        }
        onClick("withdraw-bills", this::performWithdrawBills);
        onClick("withdraw-amount-go", this::performWithdrawAmount);
        bindAmountInput("withdraw-amount", value -> {
            withdrawAmount = value;
            refreshWithdrawBreakdown();
        }, this::performWithdrawAmount);

        onClick("history-prev", () -> {
            if (historyPage > 1 && !historyLoading) requestHistory(historyPage - 1);
        });
        onClick("history-next", () -> {
            if (historyHasMore && !historyLoading) requestHistory(historyPage + 1);
        });

        Element popup = doc.getElementById("status-popup");
        if (popup != null) {
            popup.addEventListener("click", e -> {
                statusTicks = 0;
                refreshStatus();
            });
        }
    }

    private void onClick(String id, Runnable action) {
        Element element = doc.getElementById(id);
        if (element == null) return;
        element.addEventListener("click", e -> {
            clickSound();
            action.run();
        });
    }

    private void bindAmountInput(String id, Consumer<String> onChange, Runnable onEnter) {
        Element input = doc.getElementById(id);
        if (input == null) return;
        input.addEventListener("input", e -> {
            String digits = digitsOnly(input.getValue(), MAX_AMOUNT_DIGITS);
            if (!digits.equals(input.getValue())) input.setValue(digits);
            onChange.accept(digits);
        });
        input.addEventListener("keydown", e -> {
            if (e instanceof KeyEvent key && (key.keyCode == KEY_ENTER || key.keyCode == KEY_KP_ENTER)) {
                clickSound();
                onEnter.run();
            }
        });
    }

    private void bindStepper(Element button, int index, int delta) {
        if (button == null) return;
        button.addEventListener("mousedown", e -> {
            holdIndex = index;
            holdDelta = delta;
            holdTicks = 0;
            holdRepeated = false;
        });
        button.addEventListener("mouseup", e -> holdIndex = -1);
        button.addEventListener("mouseleave", e -> holdIndex = -1);
        button.addEventListener("click", e -> {
            if (!holdRepeated) {
                clickSound();
                step(index, delta);
            }
            holdRepeated = false;
        });
    }

    private void step(int index, int delta) {
        billCounts[index] = Math.max(0, Math.min(MAX_BILL_COUNT, billCounts[index] + delta));
        refreshBillInput(index);
        refreshBillTotal();
    }

    private void probe() {
        probing = true;
        connectTicks = 0;
        balance = -1;
        enterView(View.CONNECTING);
        send(new ATMQueryBalancePayload());
    }

    private void enterView(View next) {
        view = next;
        if (doc != null) {
            applyView();
            refreshBalance();
            refreshConnecting();
        }
    }

    private void showView(View next) {
        enterView(next);
        if (next == View.HOME) requestBalance();
    }

    private void goHistory() {
        showView(View.HISTORY);
        requestHistory(1);
    }

    private void applyState() {
        applyView();
        refreshConnecting();
        refreshWelcome();
        refreshBalance();
        refreshBillInputs();
        setValue("deposit-amount", depositAmount);
        setValue("withdraw-amount", withdrawAmount);
        refreshDepositBreakdown();
        refreshWithdrawBreakdown();
        refreshHistory();
        refreshStatus();
    }

    private void applyView() {
        setActive("view-connecting", view == View.CONNECTING);
        setActive("view-out-of-service", view == View.OUT_OF_SERVICE);
        setActive("view-home", view == View.HOME);
        setActive("home-logo", view == View.HOME || view == View.CONNECTING);
        setActive("view-deposit", view == View.DEPOSIT);
        setActive("view-withdraw", view == View.WITHDRAW);
        setActive("view-history", view == View.HISTORY);
        applyWithdrawTab();
    }

    private void applyWithdrawTab() {
        for (Element menu : doc.querySelectorAll(".cash-menu")) {
            menu.getClassList().toggle("is-active", withdrawTab.equals(menu.getAttribute("id")));
        }
        for (Element tab : doc.querySelectorAll(".cash-tab")) {
            tab.getClassList().toggle("is-active", withdrawTab.equals(tab.getAttribute("data-submenu")));
        }
    }

    private void refreshConnecting() {
        setText("connecting-title", "Connecting" + ".".repeat((connectTicks / CONNECT_DOT_TICKS) % 4));
    }

    private void refreshWelcome() {
        Minecraft mc = Minecraft.getInstance();
        String name = mc.player != null ? mc.player.getGameProfile().getName() : "Player";
        setText("welcome", "Welcome " + name + "!");
    }

    private void refreshBalance() {
        String label = balance < 0 ? "Balance: —" : "Balance: $" + Bills.fmt(balance);
        for (Element element : doc.querySelectorAll(".balance-label")) element.setTextContent(label);
    }

    private void refreshBillInputs() {
        for (int i = 0; i < DENOMS.length; i++) refreshBillInput(i);
        refreshBillTotal();
    }

    private void refreshBillInput(int index) {
        Element input = doc.querySelector(".amount-control[data-denomination=\"" + DENOMS[index] + "\"] .amount");
        if (input == null) return;
        String value = billCounts[index] == 0 ? "" : String.valueOf(billCounts[index]);
        if (!value.equals(input.getValue())) input.setValue(value);
    }

    private void refreshBillTotal() {
        setText("bills-total", "Total selected: $" + Bills.fmt(Bills.value(billCounts)));
    }

    private void refreshWithdrawBreakdown() {
        Element target = doc.getElementById("withdraw-breakdown");
        if (target == null) return;
        int amount = parseIntOrZero(withdrawAmount);
        target.setInnerHTML(amount <= 0 ? "" : chips(Bills.breakdown(amount)));
    }

    private void refreshDepositBreakdown() {
        Element target = doc.getElementById("deposit-breakdown");
        if (target == null) return;
        int amount = parseIntOrZero(depositAmount);
        Minecraft mc = Minecraft.getInstance();
        if (amount <= 0 || mc.player == null) {
            target.setInnerHTML("");
            return;
        }
        int[] held = Bills.count(mc.player.getInventory());
        long carried = Bills.value(held);
        if (carried < amount) {
            target.setInnerHTML("<span class=\"hint\">You only carry $" + Bills.fmt(carried) + ".</span>");
            return;
        }
        int[] pick = Bills.exactChange(held, amount);
        target.setInnerHTML(pick == null
                ? "<span class=\"hint\">Your bills can't make exactly this amount.</span>"
                : chips(pick));
    }

    private void refreshHistory() {
        Element list = doc.getElementById("history-list");
        if (list != null) {
            StringBuilder html = new StringBuilder();
            if (historyLoading) {
                html.append("<div class=\"history-empty\">Loading...</div>");
            } else if (historyEntries.isEmpty()) {
                html.append("<div class=\"history-empty\">No transactions found.</div>");
            } else {
                for (HistoryEntry entry : historyEntries) {
                    boolean negative = entry.amount.startsWith("-");
                    String amount = negative ? "-$" + entry.amount.substring(1) : "+$" + entry.amount;
                    html.append("<div class=\"history-row\"><span class=\"history-type\">")
                            .append(escape(TransactionFormat.type(entry.type)))
                            .append("</span><span class=\"history-amount ").append(negative ? "neg" : "pos").append("\">")
                            .append(escape(amount))
                            .append("</span><span class=\"history-date\">")
                            .append(escape(TransactionFormat.relativeDate(entry.createdAt)))
                            .append("</span></div>");
                }
            }
            list.setInnerHTML(html.toString());
        }
        setText("history-page", "Page " + historyPage);
        setDisabled("history-prev", historyPage <= 1 || historyLoading);
        setDisabled("history-next", !historyHasMore || historyLoading);
    }

    private void refreshStatus() {
        boolean showing = statusTicks > 0 && !statusText.isEmpty();
        setActive("status-popup", showing);
        Element popup = doc.getElementById("status-popup");
        if (popup != null) popup.getClassList().toggle("is-info", statusInfo);
        Element text = doc.getElementById("status-text");
        if (text != null) {
            text.setTextContent(statusText);
            text.setInlineStyleProperty("color", statusColor);
        }
    }

    private void performDepositAll() {
        send(ATMDepositPayload.all());
    }

    private void performDepositAmount() {
        int amount = parseIntOrZero(depositAmount);
        if (amount <= 0) {
            showStatus(ATMResultPayload.KIND_ERROR, "Enter an amount.");
            return;
        }
        send(new ATMDepositPayload(amount));
    }

    private void performWithdrawBills() {
        if (Bills.isEmpty(billCounts)) {
            showStatus(ATMResultPayload.KIND_ERROR, "Enter at least one bill count.");
            return;
        }
        send(ATMWithdrawPayload.of(billCounts));
    }

    private void performWithdrawAmount() {
        int amount = parseIntOrZero(withdrawAmount);
        if (amount <= 0) {
            showStatus(ATMResultPayload.KIND_ERROR, "Enter an amount.");
            return;
        }
        send(ATMWithdrawPayload.of(Bills.breakdown(amount)));
    }

    private void requestBalance() {
        send(new ATMQueryBalancePayload());
    }

    private void requestHistory(int page) {
        historyPage = page;
        historyLoading = true;
        historyEntries = new ArrayList<>();
        if (doc != null) refreshHistory();
        send(new ATMQueryHistoryPayload(page));
    }

    private static void send(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(new ServerboundCustomPayloadPacket(payload));
    }

    private static List<HistoryEntry> parseHistory(String json) {
        List<HistoryEntry> list = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                list.add(new HistoryEntry(
                        obj.get("transactionType").getAsString(),
                        obj.get("amount").getAsString(),
                        obj.get("createdAt").getAsString()
                ));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Ignoring unreadable ATM history payload: {}", e.getMessage());
        }
        return list;
    }

    private static String chips(int[] counts) {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < DENOMS.length; i++) {
            if (counts[i] <= 0) continue;
            html.append("<span>").append(counts[i]).append("x $").append(DENOMS[i])
                    .append(" <texture src=\"createringtoncurrency:textures/item/bill_").append(DENOMS[i])
                    .append(".png\"></span>");
        }
        return html.toString();
    }

    private void setActive(String id, boolean active) {
        Element element = doc.getElementById(id);
        if (element != null) element.getClassList().toggle("is-active", active);
    }

    private void setText(String id, String text) {
        Element element = doc.getElementById(id);
        if (element != null) element.setTextContent(text);
    }

    private void setValue(String id, String value) {
        Element element = doc.getElementById(id);
        if (element != null && !value.equals(element.getValue())) element.setValue(value);
    }

    private void setDisabled(String id, boolean disabled) {
        Element element = doc.getElementById(id);
        if (element == null) return;
        element.setDisabled(disabled);
        if (disabled) {
            element.setAttribute("disabled", "");
        } else {
            element.removeAttribute("disabled");
        }
    }

    private static void swapClass(Element element, String remove, String add) {
        element.getClassList().remove(remove);
        element.getClassList().add(add);
    }

    private static String digitsOnly(String raw, int maxDigits) {
        if (raw == null) return "";
        StringBuilder digits = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= '0' && c <= '9' && digits.length() < maxDigits) digits.append(c);
        }
        return digits.toString();
    }

    private static int parseIntOrZero(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
