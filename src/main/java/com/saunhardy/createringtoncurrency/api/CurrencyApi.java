package com.saunhardy.createringtoncurrency.api;

import com.google.gson.Gson;
import com.saunhardy.createrington.api.Endpoints;
import com.saunhardy.createrington.api.currency.BalanceResponse;
import com.saunhardy.createrington.api.currency.DailyResponse;
import com.saunhardy.createrington.api.currency.DepositRequest;
import com.saunhardy.createrington.api.currency.DepositResponse;
import com.saunhardy.createrington.api.currency.HistoryResponse;
import com.saunhardy.createrington.api.currency.LotteryJoinRequest;
import com.saunhardy.createrington.api.currency.LotteryJoinResponse;
import com.saunhardy.createrington.api.currency.LotteryStartRequest;
import com.saunhardy.createrington.api.currency.LotteryStartResponse;
import com.saunhardy.createrington.api.currency.PayRequest;
import com.saunhardy.createrington.api.currency.PayResponse;
import com.saunhardy.createrington.api.currency.TopEntry;
import com.saunhardy.createrington.api.currency.WithdrawRequest;
import com.saunhardy.createrington.api.currency.WithdrawResponse;
import com.saunhardy.createrington.api.trains.CrashRequest;
import com.saunhardy.crnet.CRNetClient;
import com.saunhardy.crnet.auth.AuthStrategy;
import com.saunhardy.crnet.http.ApiResponse;
import com.saunhardy.createringtoncurrency.Config;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Single point of contact for every backend HTTP call the mod makes.
 *
 * CRNet owns the transport, JSON, envelope parsing, and the mod-JWT
 * lifecycle (via {@code /api/currency/login}). Typed request/response
 * records come from the bundled {@code createrington-api} library.
 */
public final class CurrencyApi {
    private static final Gson GSON = new Gson();
    private static volatile CRNetClient client;

    private CurrencyApi() {}

    /**
     * Builds the shared client from the current {@link Config}. Must be
     * called after the config is loaded (e.g. on server-starting).
     */
    public static void init() {
        String baseUrl = stripTrailingSlash(Config.API_BASE_URL.get());
        client = new CRNetClient.Builder()
                .baseUrl(baseUrl)
                .auth(AuthStrategy.loginEndpoint(Endpoints.CURRENCY_LOGIN))
                .build();
    }

    // ---- Currency endpoints ------------------------------------------------

    public static CompletableFuture<ApiResponse<BalanceResponse>> balance(UUID playerUuid) {
        CRNetClient c = client;
        return c == null ? unavailable() : c.get(Endpoints.CURRENCY_BALANCE, BalanceResponse.class, playerUuid);
    }

    public static CompletableFuture<ApiResponse<PayResponse>> pay(UUID fromUuid, String toUuid, double amount) {
        CRNetClient c = client;
        if (c == null) return unavailable();
        PayRequest req = new PayRequest(toUuid, amount, null);
        return c.post(Endpoints.CURRENCY_PAY, GSON.toJson(req), PayResponse.class, fromUuid);
    }

    public static CompletableFuture<ApiResponse<DepositResponse>> deposit(UUID playerUuid, double amount) {
        CRNetClient c = client;
        if (c == null) return unavailable();
        DepositRequest req = new DepositRequest(amount, null);
        return c.post(Endpoints.CURRENCY_DEPOSIT, GSON.toJson(req), DepositResponse.class, playerUuid);
    }

    public static CompletableFuture<ApiResponse<WithdrawResponse>> withdraw(UUID playerUuid, double denomination, int count) {
        CRNetClient c = client;
        if (c == null) return unavailable();
        WithdrawRequest req = new WithdrawRequest(denomination, count);
        return c.post(Endpoints.CURRENCY_WITHDRAW, GSON.toJson(req), WithdrawResponse.class, playerUuid);
    }

    public static CompletableFuture<ApiResponse<List<TopEntry>>> top(UUID playerUuid) {
        CRNetClient c = client;
        return c == null ? unavailable() : c.getList(Endpoints.CURRENCY_TOP, TopEntry.class, playerUuid);
    }

    public static CompletableFuture<ApiResponse<DailyResponse>> daily(UUID playerUuid) {
        CRNetClient c = client;
        return c == null ? unavailable() : c.post(Endpoints.CURRENCY_DAILY, "{}", DailyResponse.class, playerUuid);
    }

    public static CompletableFuture<ApiResponse<HistoryResponse>> history(UUID playerUuid, int page, int limit) {
        CRNetClient c = client;
        if (c == null) return unavailable();
        String path = Endpoints.CURRENCY_HISTORY + "?page=" + page + "&limit=" + limit;
        return c.get(path, HistoryResponse.class, playerUuid);
    }

    public static CompletableFuture<ApiResponse<LotteryStartResponse>> lotteryStart(UUID playerUuid, double amount) {
        CRNetClient c = client;
        if (c == null) return unavailable();
        LotteryStartRequest req = new LotteryStartRequest(amount);
        return c.post(Endpoints.CURRENCY_LOTTERY_START, GSON.toJson(req), LotteryStartResponse.class, playerUuid);
    }

    public static CompletableFuture<ApiResponse<LotteryJoinResponse>> lotteryJoin(UUID playerUuid, double amount) {
        CRNetClient c = client;
        if (c == null) return unavailable();
        LotteryJoinRequest req = new LotteryJoinRequest(amount);
        return c.post(Endpoints.CURRENCY_LOTTERY_JOIN, GSON.toJson(req), LotteryJoinResponse.class, playerUuid);
    }

    // ---- Trains ------------------------------------------------------------

    /**
     * Train crash reports now require a mod JWT, so the caller must supply a
     * UUID for auth. Pick any valid online player UUID (driver if available).
     */
    public static CompletableFuture<ApiResponse<Void>> trainCrash(UUID authUuid, CrashRequest req) {
        CRNetClient c = client;
        return c == null ? unavailable() : c.postAsync(Endpoints.TRAINS_CRASH, GSON.toJson(req), authUuid);
    }

    // ---- Internals ---------------------------------------------------------

    /**
     * Returns a failed future when the API is unavailable (e.g. integrated singleplayer,
     * where {@link #init()} is intentionally skipped). Callers' existing async error
     * handling (exceptionally / try-join-catch) absorbs it as a normal call failure.
     */
    private static <T> CompletableFuture<ApiResponse<T>> unavailable() {
        return CompletableFuture.failedFuture(
                new IllegalStateException("CurrencyApi unavailable — backend not initialised in this session"));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
