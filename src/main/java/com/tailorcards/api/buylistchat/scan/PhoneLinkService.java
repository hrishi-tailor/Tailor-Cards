package com.tailorcards.api.buylistchat.scan;

import com.tailorcards.api.buylistchat.BuylistDraftService;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.PhoneLinkResponse;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.PhoneLinkStatus;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanAddRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a customer on a computer scan cards with their phone: the computer shows a QR code with a link token, the
 * phone opens it and scans, and the cards land on the computer's list. A token is a capability for one draft only:
 * it can scan and add cards to that draft and nothing else (it can't read the list, confirm or change other lines).
 * Tokens live in memory for 30 minutes; one active token per draft.
 */
@Service
public class PhoneLinkService {

    static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_LINKS = 2000;
    private static final int MAX_ADDED_SHOWN = 20;

    /** One phone link. added lists the cards added from the phone, newest last. */
    static final class Link {
        final String token;
        final String email;
        final long sessionId;
        final String draftId;
        final Instant expiresAt;
        volatile boolean connected;
        final List<String> added = new ArrayList<>();

        Link(String token, String email, long sessionId, String draftId, Instant expiresAt) {
            this.token = token;
            this.email = email;
            this.sessionId = sessionId;
            this.draftId = draftId;
            this.expiresAt = expiresAt;
        }
    }

    private final CardScanService scanner;
    private final BuylistDraftService drafts;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Link> links = new ConcurrentHashMap<>();

    @Autowired
    public PhoneLinkService(CardScanService scanner, BuylistDraftService drafts) {
        this(scanner, drafts, Clock.systemUTC());
    }

    PhoneLinkService(CardScanService scanner, BuylistDraftService drafts, Clock clock) {
        this.scanner = scanner;
        this.drafts = drafts;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- computer side (chat session)

    /** A new link for the caller's open draft; replaces any earlier link for that draft. */
    public PhoneLinkResponse create(String email, long sessionId, String draftId) {
        drafts.ownedOpenDraft(draftId, email);
        evictExpired();
        if (links.size() >= MAX_LINKS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many phone links right now. Try again shortly.");
        }
        links.values().removeIf(l -> l.draftId.equals(draftId));
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Link link = new Link(token, email, sessionId, draftId, clock.instant().plus(TTL));
        links.put(token, link);
        return new PhoneLinkResponse(token, link.expiresAt);
    }

    /** Whether the phone has opened the link, and what it added; for the computer to poll. */
    public PhoneLinkStatus status(String email, String draftId, String token) {
        Link link = links.get(token);
        if (link == null || !link.email.equals(email) || !link.draftId.equals(draftId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That phone link has ended.");
        }
        boolean expired = link.expiresAt.isBefore(clock.instant());
        synchronized (link.added) {
            return new PhoneLinkStatus(link.connected, List.copyOf(link.added), link.expiresAt, expired);
        }
    }

    // ---------------------------------------------------------------- phone side (token only)

    /** Opening the link on the phone: marks it connected. */
    public PhoneLinkResponse open(String token) {
        Link link = live(token);
        link.connected = true;
        return new PhoneLinkResponse(null, link.expiresAt);
    }

    public ScanResponse scan(String token, MultipartFile front, MultipartFile back) throws IOException {
        Link link = live(token);
        link.connected = true;
        return scanner.scan(link.email, link.sessionId, link.draftId, front, back);
    }

    /** Adds a confirmed scan to the computer's draft; returns only the name of what was added. */
    public String add(String token, String scanId, ScanAddRequest request) {
        Link link = live(token);
        String name = scanner.addScan(link.email, link.draftId, scanId, request).label();
        synchronized (link.added) {
            link.added.add(name);
            if (link.added.size() > MAX_ADDED_SHOWN) {
                link.added.removeFirst();
            }
        }
        return name;
    }

    private Link live(String token) {
        Link link = token == null ? null : links.get(token);
        if (link == null || link.expiresAt.isBefore(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This link has expired. Open Scan a card on your computer for a new QR code.");
        }
        return link;
    }

    private void evictExpired() {
        Instant now = clock.instant();
        links.values().removeIf(l -> l.expiresAt.isBefore(now));
    }
}
