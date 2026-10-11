package com.tailorcards.api.buylistchat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Configuration for the AI buylist chat ({@code app.buylist-chat.*}). Defaults are safe for production. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.buylist-chat")
public class BuylistChatProperties {

    /** Kill switch (CHATBOT_ENABLED). When false every chat endpoint except /status returns 503. */
    private boolean enabled = false;
    /**
     * When false (TEMPORARY), customers chat without the email code: they get an anonymous session
     * and give an unverified contact email at Confirm. BUYLIST_CHAT_REQUIRE_EMAIL=true turns it back on.
     */
    private boolean requireEmailVerification = true;
    /** Calendar used for "one submission per day". */
    private String timezone = "America/Toronto";
    private String model = "claude-haiku-4-5-20251001";
    private int maxTokens = 1024;
    private int timeoutSeconds = 30;
    private int quoteValidHours = 48;

    private Otp otp = new Otp();
    private Turnstile turnstile = new Turnstile();
    private Email email = new Email();
    private Limits limits = new Limits();
    private Resolution resolution = new Resolution();
    private Scrap scrap = new Scrap();
    private Likelihood likelihood = new Likelihood();

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    @Getter
    @Setter
    public static class Otp {
        private int codeTtlMinutes = 10;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 60;
        private int maxCodesPerEmailPerHour = 5;
        private int sessionTtlHours = 24;
        /** HMAC key for code hashes (OTP_PEPPER); a random key is generated per process when blank. */
        private String pepper = "";
    }

    @Getter
    @Setter
    public static class Turnstile {
        /** Verification runs only when the secret is set (TURNSTILE_SECRET). */
        private String secret = "";
        /** Public site key handed to the browser widget (TURNSTILE_SITE_KEY). */
        private String siteKey = "";
        private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    }

    @Getter
    @Setter
    public static class Email {
        /** "log" (development: logs instead of sending) or "http" (Resend-compatible JSON API). */
        private String provider = "log";
        private String apiKey = "";
        private String apiUrl = "https://api.resend.com/emails";
        private String from = "";
        /** Where new-submission notifications go (BUYLIST_NOTIFY_EMAIL). Blank disables them. */
        private String notifyTo = "";
    }

    @Getter
    @Setter
    public static class Limits {
        private int submissionsPerIpPerDay = 3;
        private int chatMessagesPerMinute = 10;
        private int maxMessageLength = 2000;
        private int maxToolRounds = 5;
        private BigDecimal dailyLlmSpendUsd = new BigDecimal("5.00");
        private int maxLines = 1000;
        private int maxQuantityPerLine = 100;
        private int maxBulkQuantity = 10000;
        private int maxPasteChars = 100_000;
        private int transcriptHistory = 20;
        private int normalizeBatchSize = 100;
    }

    @Getter
    @Setter
    public static class Resolution {
        /** Run card resolution on a background pool (false: inline, used by tests). */
        private boolean async = true;
        private int cacheMinutes = 12;
        private int concurrency = 4;
        private int requestsPerSecond = 8;
        private int searchLimit = 250;
        private int maxCandidates = 5;
        /** Candidates fetched when the customer gave a set total ("4/102") to check against. */
        private int maxCandidatesWithSetTotal = 12;
    }

    @Getter
    @Setter
    public static class Scrap {
        private BigDecimal minUnitUsd = new BigDecimal("1.00");
        private BigDecimal minLineUsd = new BigDecimal("5.00");
        /** TCGdex categories we do not buy (case-insensitive), e.g. "Energy". */
        private List<String> excludedCategories = new ArrayList<>(List.of("Energy"));
        /** NEEDS_REVIEW or EXCLUDE for damaged cards. */
        private String damagedPolicy = "NEEDS_REVIEW";
        /** A bulk lot is one line; it is eligible from this many cards. */
        private int bulkMinQuantity = 100;
        /** Lines worth at least this much warrant a photo (optional; without one the estimate is capped). */
        private BigDecimal photoRequiredAboveUsd = new BigDecimal("50.00");
    }

    @Getter
    @Setter
    public static class Likelihood {
        private double base = 20;
        private double eligibleWeight = 45;
        private double identificationWeight = 15;
        private double photoWeight = 10;
        private double liquidityWeight = 10;
        private double bulkPenalty = 15;
        private double lineCountPenalty = 10;
        private int lineCountSoftCap = 300;
        /** Haircut at which a card counts as fully illiquid (card_liquidity LOW tier is 0.08). */
        private double maxLiquidityHaircut = 0.08;
        private int min = 5;
        private int max = 95;
        /** How far over our rates (as a fraction, 0.5 = 50%) an ask can go before the meter hits the floor. */
        private double overAskSpan = 0.5;
        /** Lowest multiplier applied to the meter for an over-the-rules request. */
        private double overAskFloor = 0.15;
        /** Most points added when the request is below our rates. */
        private double underAskBonus = 15;
        /** How far under our rates (0.3 = 30%) an ask must go for the full bonus. */
        private double underAskSpan = 0.3;
        /** Highest estimate while any card that warrants a photo has none (photos are optional). */
        private int maxWithoutPhotos = 90;
        /** Points lost when every priced card is a slab whose graded price rests on few sales. */
        private double thinGradedDataPenalty = 8;
        /** A graded price backed by fewer recent sales than this counts as thin data. */
        private int minGradedSales = 3;
    }
}
