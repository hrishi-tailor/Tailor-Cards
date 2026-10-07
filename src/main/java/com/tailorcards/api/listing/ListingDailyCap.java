package com.tailorcards.api.listing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Store-wide cap on listing drafts per UTC day. Held in memory: it resets when the service
 * restarts and is per instance, which matches the single-instance deployment.
 */
@Slf4j
@Component
public class ListingDailyCap {

    private final int dailyCap;
    private final Clock clock;
    private LocalDate day;
    private int used;

    @Autowired
    public ListingDailyCap(@Value("${app.listing-generator.daily-cap:100}") int dailyCap) {
        this(dailyCap, Clock.systemUTC());
    }

    ListingDailyCap(int dailyCap, Clock clock) {
        this.dailyCap = Math.max(dailyCap, 0);
        this.clock = clock;
    }

    /** Counts one draft request, or throws 429 when today's cap is used up. */
    public synchronized void acquire() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
        if (used >= dailyCap) {
            log.warn("Listing generator daily cap reached ({} drafts)", dailyCap);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily listing draft limit reached (" + dailyCap + " per day). It resets at 00:00 UTC; "
                            + "you can still create the listing manually.");
        }
        used++;
    }
}
