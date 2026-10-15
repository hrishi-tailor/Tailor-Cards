package com.tailorcards.api.buylistchat.resolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Latency budget for the AI buylist: a 60-card pasted lot, end to end (Claude normalising, lines added, every card
 * matched and priced), against stubbed APIs with production timings. TCGdex answers from recorded real responses
 * after 110 ms (the measured median) under the production rate limit of 8 requests a second; Claude's delay follows
 * the fit of 40 live runs (see StubClaude). Before the speed-ups this took 35.3 s.
 */
@DisplayName("Buylist latency benchmark")
class BuylistLatencyBenchmarkTest {

    /** Agreed limit for a 60-card lot. Measured 16.6 s after the speed-ups; the floor is ~15 s of TCGdex requests at 8/s. */
    static final long LIMIT_60_MS = 20_000;
    /** TCGdex requests for the lot (142 before; 119 after). More means a regression in matching efficiency. */
    static final long MAX_REQUESTS_60 = 125;

    @Test
    @DisplayName("A 60-card lot is matched and priced within the limit, with exactly the same results as before")
    void sixtyCardLot() throws Exception {
        BuylistBench.PipelineRun run = BuylistBench.paste(new BuylistBench.ReplayProvider(BuylistLatencyReport.TCGDEX_MS),
                new StubClaude(), 60);

        System.out.printf("BENCH 60 cards: %d ms (normalise %d ms, %d Claude calls), %d TCGdex requests%n",
                run.ms(), run.normaliseMs(), run.claudeCalls(), run.stats().requests());
        assertThat(BuylistBench.comparable(BuylistBench.snapshot(run.lines())))
                .as("pricing results must not change").isEqualTo(BuylistBench.expected());
        assertThat(run.stats().requests()).as("TCGdex requests").isLessThanOrEqualTo(MAX_REQUESTS_60);
        assertThat(run.ms()).as("60-card lot, end to end").isLessThan(LIMIT_60_MS);
    }
}
