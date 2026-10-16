package com.tailorcards.api.buylistchat.resolution;

import com.tailorcards.api.buylistchat.repository.BuylistDraftLineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Streams card-checking progress to the page as server-sent events, one "progress" event per priced line
 * ({"total": 60, "done": 23}) and "done" when nothing is pending. The page uses it for a live count and refreshes
 * the list as lines fill in; polling is the fallback.
 */
@Slf4j
@Component
public class ResolutionProgress {

    static final long TIMEOUT_MS = 120_000;
    private static final int MAX_LISTENERS_PER_DRAFT = 4;

    private final BuylistDraftLineRepository lines;
    private final Map<String, List<SseEmitter>> listeners = new ConcurrentHashMap<>();

    public ResolutionProgress(BuylistDraftLineRepository lines) {
        this.lines = lines;
    }

    /** A stream for one draft; sends the current counts straight away. The caller has checked ownership. */
    public SseEmitter subscribe(String draftId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = listeners.computeIfAbsent(draftId, id -> new CopyOnWriteArrayList<>());
        while (list.size() >= MAX_LISTENERS_PER_DRAFT) {
            list.removeFirst().complete(); // an old tab or a reconnect: keep the newest
        }
        list.add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> current = listeners.get(draftId);
            if (current != null) {
                current.remove(emitter);
                if (current.isEmpty()) {
                    listeners.remove(draftId, current);
                }
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(draftId, emitter);
        return emitter;
    }

    /** Called after a line is saved; cheap when nobody is listening. */
    public void lineResolved(String draftId) {
        List<SseEmitter> list = listeners.get(draftId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            send(draftId, emitter);
        }
    }

    boolean hasListeners(String draftId) {
        List<SseEmitter> list = listeners.get(draftId);
        return list != null && !list.isEmpty();
    }

    private void send(String draftId, SseEmitter emitter) {
        long total = lines.countByDraftId(draftId);
        long pending = lines.countByDraftIdAndResolveState(draftId, CardResolutionService.PENDING);
        try {
            emitter.send(SseEmitter.event().name("progress")
                    .data(Map.of("total", total, "done", total - pending), MediaType.APPLICATION_JSON));
            if (pending == 0) {
                emitter.send(SseEmitter.event().name("done").data(Map.of("total", total), MediaType.APPLICATION_JSON));
                emitter.complete();
            }
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e); // the page went away; onError removes it
        }
    }
}
