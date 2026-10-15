# AI buylist latency

How long it takes from a customer listing cards to every card being matched and priced, where that time went, and what changed.

## Where the time goes

A list goes through three stages:

1. **Reading the list (Claude).** A pasted list is normalised by Claude into name, set, number, quantity, condition and grade. In chat, Claude adds cards with tool calls.
2. **Matching and pricing (TCGdex).** Each line is searched by name, narrowed by number and set, and the chosen card is fetched for its price. All TCGdex traffic shares one throttle of **8 requests a second**, a limit kept as it is.
3. **Graded and sealed prices (RapidAPI)** for slabs and sealed product, cached for 12 hours.

### Measurements (before)

**TCGdex, live, cold cache, production settings.** The test lot is a realistic 60-card list ([`BuylistBench.LOT`](../src/test/java/com/tailorcards/api/buylistchat/resolution/BuylistBench.java)): modern and vintage, gallery and promo numbers, duplicates and vague lines. Three runs agreed to within 10 ms.

| Cards | Time | TCGdex requests | Time waiting for a throttle slot (all threads) | Time in requests |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 0.44 s | 3 | 0.05 s | 0.37 s |
| 10 | 3.66 s | 30 | 10.7 s | 1.3 s |
| 60 | 17.66 s | 142 | 59.0 s | 6.5 s |

Time is exactly *requests ÷ 8*: resolution is bound by the rate limit, not by TCGdex (about 110 ms a request) or by thread count. Of the 142 requests, **24 were full-card fetches whose only use was reading the set's size and name**. The code fetched up to 12 candidates per line to compare set sizes ("Bulbasaur 1/102" alone made 10 requests), although the cached set list already has those values.

**Claude.** A least-squares fit over the 40 live evaluation runs on Claude Haiku 4.5 (R² 0.98) gives **385 ms per call + 6.02 ms per output token + 0.101 ms per input token**. Output tokens dominate.
- **Paste:** the normaliser read the whole list in one call (batches of 100 lines). The reply listed every field of every item (about 47 output tokens per line), so a 60-line list took **17.6 s** before matching could start. Lists over about 85 lines exceeded the call's 4,096-token output cap, the JSON was cut off, and the whole batch silently fell back to the Java parser after about 25 s.
- **Chat:** a pasted list under 2,000 characters goes to the chat. The model adds cards one tool call at a time, usually after a search: about 380 output tokens and 3 tool calls per card in the eval runs, with a cap of 1,024 output tokens per call and 5 tool rounds per turn. A 60-card message can't finish in one turn.

The stages ran **one after the other**: matching started only once the whole list had been read.

## What changed

Pricing results are unchanged. The benchmark checks every line's matched card, price, state, confidence and candidates against the output of the old code.

1. **Fewer TCGdex requests.** Candidates are narrowed by set size and set name using the cached set list (the same values a full card fetch returns), and only the chosen card is fetched. Identical requests in flight at the same moment are shared. Name searches carry no prices, so they're cached for 6 hours (`search-cache-minutes`). Full cards with prices keep their 12 minutes. **142 → 119 requests.**
2. **Lists are read in parallel chunks.** Each Claude call takes at most 15 lines (at least 5, spread over the parallel calls), with up to 4 calls at once. Replies leave out default fields (about 47 → 15 output tokens a line). Each chunk is added to the list **as soon as it's read**, in list order, so matching starts while later chunks are still with Claude.
3. **Chat lists take the same path.** A chat message that is a list (4+ short card-like lines, no questions) goes through the parallel reader instead of one tool call per card. For lists written as sentences, Claude has `add_items_to_draft` to add several cards in one call, and read-only lookups asked for in the same round run in parallel.
4. **Live progress.** Matching 60 cards still takes about 15 s at 8 requests a second. The page follows a server-sent-events stream (`/drafts/{id}/progress`) for a live count and time estimate, and refreshes the list about once a second as cards fill in. It falls back to polling if the stream can't connect.
5. **Hardening.** A line that fails unexpectedly is marked "needs review" instead of staying "Checking" forever.

## Results

End to end, from submitting the list to every card priced. Paste figures come from the [benchmark](../src/test/java/com/tailorcards/api/buylistchat/resolution/BuylistLatencyBenchmarkTest.java): recorded real TCGdex responses replayed at 110 ms under the 8/s limit, and Claude timed by the fit above. The TCGdex-only rows were measured live.

| Cards | Paste: before | Paste: after | Chat message: before | Chat message: after | TCGdex only (live): before → after |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1.1 s | 0.9 s | 4.3 s ¹ | 4.3 s ¹ | 0.44 s → 0.44 s |
| 10 | 7.0 s | 3.7 s | ~29 s ² | 3.7 s | 3.66 s → 2.78 s |
| 60 | 35.3 s | 16.6 s | ~3 min over 4 turns ² | 16.6 s | 17.66 s → 14.78 s |

¹ A single card in chat is a conversation, not a list: the median eval turn (3.9 s, measured) plus matching.
² Estimated from the latency fit and the eval runs' per-card behaviour (about 380 output tokens per card, 1,024 per call, 5 tool rounds per turn). Not measured.

The **benchmark test fails** if a 60-card lot takes 20 s or more, uses more than 125 TCGdex requests, or produces any different match or price. It runs with the normal test suite.

## Measuring in production (read-only)

**Logs (Render).** Search for `Buylist timing:`.

| Log line | Fields |
| :--- | :--- |
| `Buylist timing: resolution` | lines, totalMs, avgLineMs, maxLineMs, tcgdexRequests, cacheHits, throttleWaitMs, tcgdexMs |
| `Buylist timing: normalise` | lines, batches, firstBatchMs, totalMs |
| `Buylist timing: chat turn` | totalMs, modelMs, toolMs, toolCalls, rounds, inputTokens, outputTokens, hitRoundLimit |
| `Buylist timing: chat list` | lines, items, totalMs |
| `Anthropic call completed` | model, latency, inputTokens, outputTokens, cost (already logged) |

**Database.** Connect with a session that can't write:

```bash
PGOPTIONS='-c default_transaction_read_only=on' psql "$DATABASE_URL"
```

Any `INSERT`, `UPDATE` or `DELETE` then fails with "cannot execute … in a read-only transaction". `resolved_at` (migration V9) exists only for lines added after this change is deployed.

Chat reply time, from what's already recorded:

```sql
WITH m AS (
  SELECT draft_id, sender_role, content, created_at,
         LEAD(sender_role) OVER w AS next_role, LEAD(created_at) OVER w AS next_at
  FROM buylist_chat_messages
  WINDOW w AS (PARTITION BY draft_id ORDER BY created_at, id))
SELECT count(*) AS turns,
       round(percentile_cont(0.5)  WITHIN GROUP (ORDER BY extract(epoch FROM next_at - created_at))::numeric, 1) AS p50_s,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM next_at - created_at))::numeric, 1) AS p95_s
FROM m
WHERE sender_role = 'CUSTOMER' AND next_role = 'ASSISTANT'
  AND content NOT LIKE '[Scanned%' AND created_at > now() - interval '30 days';
```

Time per card, from added to priced:

```sql
SELECT count(*) AS cards,
       round(percentile_cont(0.5)  WITHIN GROUP (ORDER BY extract(epoch FROM resolved_at - created_at))::numeric, 2) AS p50_s,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM resolved_at - created_at))::numeric, 2) AS p95_s,
       round(max(extract(epoch FROM resolved_at - created_at))::numeric, 2) AS max_s
FROM buylist_draft_lines
WHERE resolved_at IS NOT NULL AND created_at > now() - interval '30 days';
```

Per submission: cards, processing time (first card added → last card priced) and the whole session:

```sql
SELECT s.id AS submission_id, s.created_at AS submitted_at, count(l.id) AS cards,
       round(extract(epoch FROM max(l.resolved_at) - min(l.created_at))::numeric, 1) AS processing_s,
       round((extract(epoch FROM max(l.resolved_at) - min(l.created_at)) / nullif(count(l.id), 0))::numeric, 2) AS s_per_card,
       round(extract(epoch FROM s.created_at - d.created_at)::numeric) AS session_s
FROM buylist_submissions s
JOIN buylist_drafts d ON d.id = s.draft_id
JOIN buylist_draft_lines l ON l.draft_id = d.id
WHERE s.created_at > now() - interval '30 days'
GROUP BY s.id, s.created_at, d.created_at
ORDER BY s.created_at DESC
LIMIT 50;
```

`processing_s` is empty for submissions made before V9. For those, the chat reply times above are the best measure.

## Re-running the measurements

```bash
./mvnw test -Dtest=BuylistLatencyBenchmarkTest                       # the budget check (runs in CI)
./mvnw test -Dtest=BuylistLatencyReport -Dbench.report=true          # 1, 10 and 60 cards, replayed
./mvnw test -Dtest=BuylistResolutionLiveBench -Dbench.live=true      # 1, 10 and 60 cards against the real TCGdex
```

Re-record the TCGdex responses with `-Dbench.record=true` on the live run. If the code that produces results changes on purpose, regenerate the pricing baseline from the old code with `-Dbench.writeExpected=true`.
