package com.tailorcards.api.service;

import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.buylistchat.resolution.GradedPriceService;
import com.tailorcards.api.buylistchat.resolution.SealedPriceService;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Official images for products. Cards come from TCGdex (the card database the buylist search uses),
 * found by the linked card id or by set and card number, with the RapidAPI card image as a fallback
 * when TCGdex has no picture. Sealed products come from the RapidAPI product catalog.
 *
 * <p>Inventory set names are written the way collectors say them ("SV Black Star Promo",
 * "Crown Zenith (Galarian Gallery)", "Scarlet &amp; Violet 151"), so they are mapped to TCGdex names first.
 */
@Slf4j
@Service
public class ProductImageService {

    /** Inventory set name patterns -> TCGdex set name. */
    private static final List<Alias> ALIASES = List.of(
            new Alias("^(sv|svp|scarlet\\s*(&|and)?\\s*violet)\\b.*promo", "SVP Black Star Promos"),
            new Alias("^(swsh|sword\\s*(&|and)?\\s*shield)\\b.*promo", "SWSH Black Star Promos"),
            new Alias("^(sm|sun\\s*(&|and)?\\s*moon)\\b.*promo", "SM Black Star Promos"),
            new Alias("^xy\\b.*promo", "XY Black Star Promos"),
            new Alias("^(bw|black\\s*(&|and)?\\s*white)\\b.*promo", "BW Black Star Promos"),
            new Alias("^(me|mep|mega\\s*evolution)\\b.*promo", "MEP Black Star Promos"),
            new Alias("^(wizards|wotc)\\b.*promo", "Wizards Black Star Promos"),
            new Alias("^scarlet\\s*(&|and)?\\s*violet\\s*151$", "151"),
            new Alias("^sun\\s*(&|and)\\s*moon(\\s*base\\s*set)?$", "Sun & Moon"));

    private record Alias(Pattern pattern, String tcgdexName) {
        Alias(String regex, String tcgdexName) {
            this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), tcgdexName);
        }
    }

    private static final Pattern SV_PROMO_NUMBER = Pattern.compile("^(?i:SV)(\\d{1,4})$");

    private final CardLookupService lookup;
    private final GradedPriceService rapidApiCards;
    private final SealedPriceService sealedProducts;

    @Autowired
    public ProductImageService(CardLookupService lookup, GradedPriceService rapidApiCards,
                               SealedPriceService sealedProducts) {
        this.lookup = lookup;
        this.rapidApiCards = rapidApiCards;
        this.sealedProducts = sealedProducts;
    }

    public ProductImageService(CardLookupService lookup) {
        this(lookup, null, null);
    }

    public Optional<String> officialImage(Product product) {
        try {
            if (isSealedProduct(product)) {
                return sealedImage(product);
            }
            return cardImage(product);
        } catch (Exception e) {
            log.warn("Official image lookup failed for product {}: {}", product.getId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<String> cardImage(Product product) {
        Optional<CardMarketPrice> card = Optional.empty();
        String cardId = product.getPokemontcgId();
        if (notBlank(cardId)) {
            card = lookup.card(cardId.trim());
        }
        boolean linked = card.isPresent();
        if (card.isEmpty() && notBlank(product.getSet()) && notBlank(product.getCardNumber())) {
            List<String> setIds = new ArrayList<>();
            for (String name : setNames(product.getSet())) {
                lookup.setIdsFor(name).stream().filter(id -> !setIds.contains(id)).forEach(setIds::add);
            }
            for (String number : numbers(product.getCardNumber(), setIds)) {
                card = lookup.findBySetAndNumber(setIds, number);
                if (card.isPresent()) {
                    break;
                }
            }
        }
        // A set + number match must be the same Pokemon: inventory numbers can be off (a "Mew ex" saved
        // as SV052 would otherwise show Mewtwo). A card linked by id is trusted as is.
        if (card.isPresent() && !linked && !samePokemon(product.getName(), card.get().name())) {
            log.info("Product {} '{}': {} is {}, not using its image", product.getId(), product.getName(),
                    card.get().cardId(), card.get().name());
            return Optional.empty();
        }
        Optional<String> image = card.map(c -> c.largeImageUrl() != null ? c.largeImageUrl() : c.imageUrl());
        if (image.isPresent() || card.isEmpty() || rapidApiCards == null) {
            return image;
        }
        // TCGdex knows the exact card but has no picture (some promos and gallery cards): the RapidAPI
        // image for that same card id. No name search, which could pick a different print.
        CardMarketPrice exact = card.get();
        return rapidApiCards.exactCardImage(exact.cardId(), exact.name(), exact.setName(), exact.cardNumber());
    }

    private static final Set<String> GENERIC_WORDS = Set.of("ex", "gx", "v", "vmax", "vstar", "mega", "break", "prime",
            "lv", "x", "y", "hisuian", "galarian", "alolan", "paldean", "radiant", "shiny", "team", "dark", "light",
            "the", "of", "and", "promo", "card", "holo", "reverse");

    /** True when the two names share the Pokemon (or trainer) name: "Dragonite EX" and "Mega Dragonite ex". */
    static boolean samePokemon(String productName, String cardName) {
        if (productName == null || cardName == null) {
            return false;
        }
        Set<String> card = words(cardName);
        return words(productName).stream().anyMatch(card::contains);
    }

    private static Set<String> words(String text) {
        String folded = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        Set<String> out = new java.util.HashSet<>();
        for (String w : folded.split("[^a-z0-9]+")) {
            if (w.length() >= 3 && !GENERIC_WORDS.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private Optional<String> sealedImage(Product product) {
        if (sealedProducts == null) {
            return Optional.empty();
        }
        String name = product.getName() == null ? "" : product.getName().trim();
        for (String set : setNames(product.getSet())) {
            String query = name.toLowerCase(Locale.ROOT).contains(set.toLowerCase(Locale.ROOT)) ? name : set + " " + name;
            Optional<String> image = sealedProducts.bestMatch(query).map(SealedPriceService.SealedProduct::imageUrl)
                    .filter(ProductImageService::notBlank);
            if (image.isPresent()) {
                return image;
            }
        }
        return sealedProducts.bestMatch(name).map(SealedPriceService.SealedProduct::imageUrl).filter(ProductImageService::notBlank);
    }

    /** Sealed boxes and bundles; a listing with a card number is a card, even one still in its packaging. */
    static boolean isSealedProduct(Product product) {
        if (notBlank(product.getCardNumber())) {
            return false;
        }
        String category = product.getCategory() == null ? "" : String.valueOf(product.getCategory().getName());
        return category.toLowerCase(Locale.ROOT).contains("seal")
                || "SEALED".equalsIgnoreCase(product.getCondition())
                || (!notBlank(product.getCardNumber()) && ItemNormalizer.isSealed(product.getName()));
    }

    /**
     * TCGdex names to try for an inventory set name, best first: an alias for promo sets and renamed
     * sets, the name with parentheses opened up ("Crown Zenith (Galarian Gallery)" -> "Crown Zenith
     * Galarian Gallery"), then the name as written.
     */
    static List<String> setNames(String set) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (!notBlank(set)) {
            return List.of();
        }
        String trimmed = set.trim();
        String opened = trimmed.replaceAll("[()\\[\\]]", " ").replaceAll("\\s+", " ").trim();
        for (Alias alias : ALIASES) {
            if (alias.pattern().matcher(opened).find() || alias.pattern().matcher(trimmed).find()) {
                names.add(alias.tcgdexName());
            }
        }
        names.add(opened);
        names.add(trimmed);
        return List.copyOf(names);
    }

    /**
     * Numbers to try: as written ("SM60", "GG56/GG70" -> "GG56"), then for SV promos the digits alone,
     * because TCGdex numbers them "svp-052" while inventories write "SV052".
     */
    static List<String> numbers(String number, List<String> setIds) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String head = number.split("/")[0].replaceAll("[\\s#]+", "");
        out.add(head);
        Matcher sv = SV_PROMO_NUMBER.matcher(head);
        if (sv.matches() && setIds.contains("svp")) {
            out.add(sv.group(1));
        }
        return List.copyOf(out);
    }

    /** True for card-database images (TCGdex, pokemontcg.io, tcggo) as opposed to the seller's own photos. */
    public static boolean isOfficialImage(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("tcgdex.net") || u.contains("pokemontcg.io") || u.contains("tcggo.com");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
