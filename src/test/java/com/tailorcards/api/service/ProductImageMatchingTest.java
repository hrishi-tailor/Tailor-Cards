package com.tailorcards.api.service;

import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Official images: matching inventory names to the card database")
class ProductImageMatchingTest {

    @Test
    @DisplayName("Inventory set names map to TCGdex names: promos, galleries in parentheses, 151")
    void setNames() {
        assertThat(ProductImageService.setNames("SV Black Star Promo")).first().isEqualTo("SVP Black Star Promos");
        assertThat(ProductImageService.setNames("Scarlet & Violet 151 (Promo)")).first().isEqualTo("SVP Black Star Promos");
        assertThat(ProductImageService.setNames("SM Black Star Promo")).first().isEqualTo("SM Black Star Promos");
        assertThat(ProductImageService.setNames("XY Black Star Promo")).first().isEqualTo("XY Black Star Promos");
        assertThat(ProductImageService.setNames("Mega Evolution Black Star Promo")).first().isEqualTo("MEP Black Star Promos");
        assertThat(ProductImageService.setNames("Crown Zenith (Galarian Gallery)")).first().isEqualTo("Crown Zenith Galarian Gallery");
        assertThat(ProductImageService.setNames("Scarlet & Violet 151")).first().isEqualTo("151");
        assertThat(ProductImageService.setNames("Sun and Moon Base Set")).first().isEqualTo("Sun & Moon");
        assertThat(ProductImageService.setNames("Black Bolt")).containsExactly("Black Bolt");
    }

    @Test
    @DisplayName("Numbers: as written, plus the digits for SV promos only")
    void numbers() {
        assertThat(ProductImageService.numbers("SV052", List.of("svp"))).containsExactly("SV052", "052");
        assertThat(ProductImageService.numbers("TG16/TG30", List.of("swsh11", "swsh11tg"))).containsExactly("TG16");
        assertThat(ProductImageService.numbers("106/086", List.of("sv10.5b"))).containsExactly("106");
    }

    @Test
    @DisplayName("A set + number match must be the same Pokemon (Mew ex is not Mewtwo)")
    void samePokemon() {
        assertThat(ProductImageService.samePokemon("Mew ex", "Mewtwo")).isFalse();
        assertThat(ProductImageService.samePokemon("Dragonite EX", "Mega Dragonite ex")).isTrue();
        assertThat(ProductImageService.samePokemon("Zoroark VSTAR (Hisuian)", "Hisuian Zoroark VSTAR")).isTrue();
        assertThat(ProductImageService.samePokemon("Mega Charizard EX", "Mega Charizard X ex")).isTrue();
    }

    @Test
    @DisplayName("Sealed boxes use the product catalog; a numbered promo still in its packaging is a card")
    void sealedVsCard() {
        Category sealed = Category.builder().name("Sealed").build();
        Product etb = Product.builder().name("Elite Trainer Box").set("Prismatic Evolutions").category(sealed)
                .price(BigDecimal.TEN).stock(1).build();
        Product sealedPromo = Product.builder().name("Pikachu").set("SV Black Star Promo").cardNumber("190")
                .condition("Sealed").category(sealed).price(BigDecimal.TEN).stock(1).build();
        assertThat(ProductImageService.isSealedProduct(etb)).isTrue();
        assertThat(ProductImageService.isSealedProduct(sealedPromo)).isFalse();
    }
}
