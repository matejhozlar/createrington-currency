package com.saunhardy.createringtoncurrency.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.saunhardy.createringtoncurrency.network.VoteCastPayload;
import com.saunhardy.createringtoncurrency.network.VoteOpenPayload;
import com.saunhardy.createringtoncurrency.network.VoteResultPayload;
import com.saunhardy.createringtoncurrency.network.VoteTallyPayload;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.slf4j.Logger;

public final class VotePopup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PAGE = "createringtoncurrency/vote.html";
    private static final int KEY_YES = InputConstants.KEY_Y;
    private static final int KEY_NO = InputConstants.KEY_N;
    private static final int KEY_DISMISS = InputConstants.KEY_H;
    private static final int RESULT_TICKS = 100;
    private static final int URGENT_TICKS = 100;
    private static final int LEAVE_TICKS = 6;

    private static Document doc;
    private static long boundGeneration = -1;

    private static boolean active;
    private static String starter = "";
    private static String voteType = "";
    private static int durationDays;
    private static int yes;
    private static int no;
    private static int needed;
    private static int eligible;
    private static int ticksRemaining;
    private static int status = VoteTallyPayload.STATUS_OPEN;
    private static boolean dismissed;

    private static VoteResultPayload result;
    private static int resultTicks;

    private static boolean leaving;
    private static int leaveTicks;

    private VotePopup() {}

    public static void open(VoteOpenPayload pkt) {
        starter = pkt.starter();
        voteType = pkt.voteType();
        durationDays = pkt.durationDays();
        applyTally(pkt.tally());
        active = true;
        dismissed = false;
        leaving = false;
        leaveTicks = 0;
        result = null;
        resultTicks = 0;
        if (ensureDocument()) {
            applyState();
            sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4F);
        }
    }

    public static void updateTally(VoteTallyPayload pkt) {
        if (!active) return;
        int previous = status;
        applyTally(pkt);
        if (dismissed && previous == VoteTallyPayload.STATUS_OPEN && status != VoteTallyPayload.STATUS_OPEN) {
            dismissed = false;
        }
        if (doc != null) applyState();
    }

    public static void showResult(VoteResultPayload pkt) {
        active = false;
        result = pkt;
        resultTicks = RESULT_TICKS;
        leaving = false;
        leaveTicks = 0;
        if (ensureDocument()) {
            applyState();
            if (pkt.passed()) {
                sound(SoundEvents.PLAYER_LEVELUP, 1.0F);
            } else {
                sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F);
            }
        }
    }

    public static boolean captureKey(long window, int key, int scanCode, int action) {
        if (!capturing()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow()) return false;
        if (key != KEY_YES && key != KEY_NO && key != KEY_DISMISS) return false;
        if (action != InputConstants.PRESS) return true;
        if (key == KEY_DISMISS) {
            dismiss();
        } else if (status == VoteTallyPayload.STATUS_OPEN) {
            cast(key == KEY_YES);
        }
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (doc == null) return;
        if (doc.isDisposed()) {
            doc = null;
            boundGeneration = -1;
            if (active || result != null) ensureDocument();
            if (doc == null) return;
        }
        ensureBound();
        clearSelection();
        if (leaveTicks > 0 && --leaveTicks == 0) {
            leaving = false;
            if (result != null) {
                close();
                return;
            }
            dismissed = true;
            applyState();
        }
        if (result != null) {
            if (resultTicks > 0 && --resultTicks == 0) startLeaving();
            return;
        }
        if (!active) {
            close();
            return;
        }
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining % 20 == 0 || ticksRemaining == URGENT_TICKS) refreshTimer();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKeyInput(InputEvent.Key event) {
        clearSelection();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMouseInput(InputEvent.MouseButton.Post event) {
        clearSelection();
    }

    private static void clearSelection() {
        if (doc == null || doc.isDisposed()) return;
        if (doc.hasDocumentSelection()) doc.clearDocumentSelection();
        if (doc.hasAnyActiveSelection() || doc.hasAnyTextSelection()) {
            doc.clearAllTextSelections();
            doc.clearRichTextSelection();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        result = null;
        close();
    }

    private static boolean promptShowing() {
        return active && !dismissed && result == null;
    }

    private static boolean capturing() {
        return promptShowing() && !leaving && doc != null && Minecraft.getInstance().screen == null;
    }

    private static void applyTally(VoteTallyPayload tally) {
        yes = tally.yes();
        no = tally.no();
        needed = tally.needed();
        eligible = tally.eligible();
        ticksRemaining = tally.ticksRemaining();
        status = tally.status();
    }

    private static void cast(boolean voteYes) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(new ServerboundCustomPayloadPacket(new VoteCastPayload(voteYes)));
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
    }

    private static void dismiss() {
        if (leaving) return;
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F);
        startLeaving();
    }

    private static void startLeaving() {
        leaving = true;
        leaveTicks = LEAVE_TICKS;
        if (doc != null) applyState();
    }

    private static boolean ensureDocument() {
        if (doc != null && !doc.isDisposed()) return true;
        doc = ApricityUI.createDocument(PAGE);
        boundGeneration = -1;
        if (doc == null) {
            LOGGER.error("Vote popup page {} could not be loaded; check the [AUI HTML] log lines", PAGE);
            return false;
        }
        doc.setReloadPersistent(true);
        ensureBound();
        return true;
    }

    private static void ensureBound() {
        if (doc.getRefreshGeneration() == boundGeneration) return;
        boundGeneration = doc.getRefreshGeneration();
        bind();
        applyState();
    }

    private static void bind() {
        onClick("vote-yes", () -> {
            if (status == VoteTallyPayload.STATUS_OPEN && !leaving) cast(true);
        });
        onClick("vote-no", () -> {
            if (status == VoteTallyPayload.STATUS_OPEN && !leaving) cast(false);
        });
        setText("key-yes", keyName(KEY_YES));
        setText("key-no", keyName(KEY_NO));
        setText("key-dismiss", keyName(KEY_DISMISS));
    }

    private static void onClick(String id, Runnable action) {
        Element element = doc.getElementById(id);
        if (element != null) element.addEventListener("click", e -> action.run());
    }

    private static void close() {
        if (doc != null) {
            doc.remove();
            doc = null;
        }
        boundGeneration = -1;
        dismissed = false;
        leaving = false;
        leaveTicks = 0;
    }

    private static void applyState() {
        boolean showVote = promptShowing();
        setActive("vote-card", showVote);
        setActive("result-card", result != null);
        toggleClass("vote-card", "is-leaving", showVote && leaving);
        toggleClass("result-card", "is-leaving", result != null && leaving);
        if (showVote) refreshVote();
        if (result != null) refreshResult();
    }

    private static void refreshVote() {
        setText("vote-subtitle", starter + " wants " + describe(voteType, durationDays));
        setText("vote-yes-count", yes + " yes");
        setText("vote-no-count", no + " no");
        toggleClass("vote-card", "is-voted", status != VoteTallyPayload.STATUS_OPEN);
        refreshTimer();
    }

    private static void refreshTimer() {
        if (doc == null) return;
        int seconds = (ticksRemaining + 19) / 20;
        setText("vote-timer", seconds + "s");
        toggleClass("vote-card", "is-urgent", ticksRemaining <= URGENT_TICKS);
    }

    private static void refreshResult() {
        toggleClass("result-card", "is-passed", result.passed());
        toggleClass("result-card", "is-failed", !result.passed());
        setText("result-title", result.passed() ? "VOTE PASSED" : "VOTE FAILED");
        setText("result-yes", result.yes() + " yes");
        setText("result-no", result.no() + " no");
    }

    private static String describe(String type, int days) {
        String base = switch (type) {
            case "day" -> "daytime";
            case "night" -> "night";
            case "clear" -> "clear skies";
            case "rain" -> "rain";
            case "thunder" -> "a thunderstorm";
            default -> type;
        };
        if (days > 0) base += " for " + days + (days == 1 ? " day" : " days");
        return base;
    }

    private static String keyName(int key) {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    private static void setActive(String id, boolean isActive) {
        toggleClass(id, "is-active", isActive);
    }

    private static void toggleClass(String id, String className, boolean on) {
        Element element = doc.getElementById(id);
        if (element != null) element.getClassList().toggle(className, on);
    }

    private static void setText(String id, String text) {
        Element element = doc.getElementById(id);
        if (element != null) element.setTextContent(text);
    }

    private static void sound(SoundEvent event, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
    }
}
