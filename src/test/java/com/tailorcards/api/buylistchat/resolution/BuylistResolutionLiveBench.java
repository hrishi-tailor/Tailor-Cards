package com.tailorcards.api.buylistchat.resolution;

import com.tailorcards.api.trade.provider.TcgdexPriceProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Times card resolution against the real TCGdex API (cold cache, production settings) and records every response
 * for the replay benchmark. Not part of the normal test run:
 *
 * <pre>
 * ./mvnw test -Dtest=BuylistResolutionLiveBench -Dbench.live=true            time 1, 10 and 60 cards
 *   -Dbench.record=true          also save the responses to src/test/resources/bench/tcgdex-replay.json
 *   -Dbench.writeExpected=true   save today's results (from the replay) as the pricing baseline
 * </pre>
 */
@DisplayName("Buylist resolution: live TCGdex timing")
class BuylistResolutionLiveBench {

    @Test
    void live() throws Exception {
        assumeTrue(Boolean.getBoolean("bench.live"), "live benchmark: run with -Dbench.live=true");
        Map<String, Object> recorded = java.util.Collections.synchronizedMap(new TreeMap<>()); // allows null (not found)
        TcgdexPriceProvider real = new TcgdexPriceProvider("https://api.tcgdex.net/v2/en");
        for (int n : new int[]{1, 10, 60}) {
            BuylistBench.Run run = BuylistBench.resolve(new BuylistBench.RecordingProvider(real, recorded), n);
            System.out.printf("BENCH live cards=%d ms=%d tcgdexRequests=%d cacheHits=%d throttleWaitMs=%d tcgdexMs=%d%n",
                    n, run.ms(), run.stats().requests(), run.stats().cacheHits(), run.stats().throttleWaitMs(), run.stats().requestMs());
        }
        if (Boolean.getBoolean("bench.record")) {
            Map<String, Object> merged = new TreeMap<>();
            if (Files.exists(BuylistBench.REPLAY)) {
                merged.putAll(BuylistBench.JSON.readValue(BuylistBench.REPLAY.toFile(), Map.class));
            }
            merged.putAll(recorded);
            BuylistBench.write(BuylistBench.REPLAY, merged);
            System.out.println("BENCH recorded " + recorded.size() + " responses");
        }
    }

    @Test
    void writeExpected() throws Exception {
        assumeTrue(Boolean.getBoolean("bench.writeExpected"), "run with -Dbench.writeExpected=true");
        BuylistBench.Run run = BuylistBench.resolve(new BuylistBench.ReplayProvider(0), 60);
        BuylistBench.write(BuylistBench.EXPECTED, BuylistBench.snapshot(run.lines()));
        System.out.println("BENCH wrote expected results for " + run.lines().size() + " lines");
    }
}
