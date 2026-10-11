package com.tailorcards.api.buylistchat;

import com.tailorcards.api.buylistchat.entity.BuylistLlmSpend;
import com.tailorcards.api.buylistchat.repository.BuylistLlmSpendRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/** Global daily LLM spend ceiling for the buylist chat, persisted per America/Toronto day. */
@Slf4j
@Service
public class BuylistLlmBudget {

    private final BuylistLlmSpendRepository repository;
    private final BuylistChatProperties properties;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BuylistLlmBudget(BuylistLlmSpendRepository repository, BuylistChatProperties properties) {
        this(repository, properties, Clock.systemUTC());
    }

    BuylistLlmBudget(BuylistLlmSpendRepository repository, BuylistChatProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }

    @Transactional(readOnly = true)
    public boolean hasBudget() {
        BigDecimal spent = repository.findById(today()).map(BuylistLlmSpend::getCostUsd).orElse(BigDecimal.ZERO);
        boolean ok = spent.compareTo(properties.getLimits().getDailyLlmSpendUsd()) < 0;
        if (!ok) {
            log.warn("Buylist chat daily LLM spend ceiling reached (${})", spent);
        }
        return ok;
    }

    @Transactional
    public synchronized void record(BigDecimal costUsd, int calls) {
        if ((costUsd == null || costUsd.signum() == 0) && calls == 0) {
            return;
        }
        BuylistLlmSpend row = repository.findById(today())
                .orElseGet(() -> BuylistLlmSpend.builder().spendDate(today()).costUsd(BigDecimal.ZERO).calls(0).build());
        row.setCostUsd(row.getCostUsd().add(costUsd == null ? BigDecimal.ZERO : costUsd));
        row.setCalls(row.getCalls() + calls);
        repository.save(row);
    }
}
