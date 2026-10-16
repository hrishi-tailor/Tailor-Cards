package com.tailorcards.api.buylistchat.resolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prints timings for 1, 10 and 60 cards against replayed TCGdex responses (110 ms per request, the measured
 * median) and the timed Claude stand-in. Not part of the normal run: -Dbench.report=true
 */
@DisplayName("Buylist latency report")
class BuylistLatencyReport {

    static final long TCGDEX_MS = 110;

    @Test
    void report() throws Exception {
        assumeTrue(Boolean.getBoolean("bench.report"), "run with -Dbench.report=true");
        for (int n : new int[]{1, 10, 60}) {
            BuylistBench.Run resolution = BuylistBench.resolve(new BuylistBench.ReplayProvider(TCGDEX_MS), n);
            BuylistBench.PipelineRun paste = BuylistBench.paste(new BuylistBench.ReplayProvider(TCGDEX_MS), new StubClaude(), n);
            System.out.printf("BENCH cards=%d resolutionMs=%d tcgdexRequests=%d | pasteMs=%d normaliseMs=%d claudeCalls=%d claudeOutTokens=%d tcgdexRequests=%d%n",
                    n, resolution.ms(), resolution.stats().requests(), paste.ms(), paste.normaliseMs(), paste.claudeCalls(),
                    paste.claudeOutputTokens(), paste.stats().requests());
        }
    }
}
