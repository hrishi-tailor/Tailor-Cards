package com.tailorcards.api.service;

import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.dto.ProductRequest;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.mapper.CategoryMapper;
import com.tailorcards.api.mapper.ProductMapper;
import com.tailorcards.api.repository.CategoryRepository;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Product photos and official card images")
class ProductPhotosTest {

    private static final String OFFICIAL = "https://assets.tcgdex.net/en/me/me02/125/high.webp";

    private ProductRepository products;
    private CardLookupService lookup;
    private ProductService service;
    private Category singles;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        lookup = mock(CardLookupService.class);
        singles = Category.builder().id(1L).name("Singles").build();
        when(categories.findById(1L)).thenReturn(Optional.of(singles));
        when(products.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(lookup.card(anyString())).thenReturn(Optional.empty());
        when(lookup.card("me02-125")).thenReturn(Optional.of(CardMarketPrice.builder().cardId("me02-125")
                .name("Mega Charizard X ex").imageUrl(OFFICIAL.replace("high", "low")).largeImageUrl(OFFICIAL).build()));
        service = new ProductService(products, categories, new ProductMapper(new CategoryMapper()),
                new ProductImageService(lookup));
    }

    private Product product(String imageUrl, String cardId) {
        Product p = Product.builder().id(7L).name("Mega Charizard X ex").price(BigDecimal.TEN).stock(1)
                .category(singles).imageUrl(imageUrl).pokemontcgId(cardId).build();
        when(products.findById(7L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    @DisplayName("New product without a picture gets the official card image; photos are kept in order")
    void createUsesOfficialImage() {
        ProductResponse created = service.createProduct(new ProductRequest("Mega Charizard X ex", null, BigDecimal.TEN,
                null, 1, 1L, "125", "Phantasmal Flames", "NM", null, "AVAILABLE", "me02-125",
                List.of("https://cdn.example/front.jpg", "https://cdn.example/back.jpg", "https://cdn.example/front.jpg")));
        assertThat(created.imageUrl()).isEqualTo(OFFICIAL);
        assertThat(created.photoUrls()).containsExactly("https://cdn.example/front.jpg", "https://cdn.example/back.jpg");
    }

    @Test
    @DisplayName("Using the official image moves the seller's old picture into the photos")
    void officialImageKeepsOldPhoto() {
        Product p = product("https://cdn.example/my-photo.jpg", "me02-125");
        ProductResponse res = service.useOfficialImage(7L);
        assertThat(res.imageUrl()).isEqualTo(OFFICIAL);
        assertThat(res.photoUrls()).containsExactly("https://cdn.example/my-photo.jpg");
        assertThat(p.getPhotoUrls()).hasSize(1);

        product(null, "nope-1");
        assertThatThrownBy(() -> service.useOfficialImage(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Photos append without duplicates, are capped, and can be removed by position")
    void addAndRemovePhotos() {
        Product p = product(OFFICIAL, null);
        service.addPhotos(7L, List.of("https://cdn.example/a.jpg", "https://cdn.example/b.jpg", "https://cdn.example/a.jpg"));
        assertThat(p.getPhotoUrls()).containsExactly("https://cdn.example/a.jpg", "https://cdn.example/b.jpg");
        assertThat(service.removePhoto(7L, 0).photoUrls()).containsExactly("https://cdn.example/b.jpg");
        assertThatThrownBy(() -> service.removePhoto(7L, 5)).isInstanceOf(ResourceNotFoundException.class);

        List<String> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add("https://cdn.example/" + i + ".jpg");
        }
        assertThatThrownBy(() -> service.addPhotos(7L, many)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Backfill updates products without an official image and lists the ones it couldn't match")
    void backfill() {
        Product matched = Product.builder().id(1L).name("Mega Charizard X ex").price(BigDecimal.TEN).stock(1)
                .imageUrl("https://cdn.example/mine.jpg").pokemontcgId("me02-125").build();
        Product already = Product.builder().id(2L).name("Pikachu").price(BigDecimal.TEN).stock(1)
                .imageUrl("https://images.pokemontcg.io/base1/58_hires.png").build();
        Product sealed = Product.builder().id(3L).name("Prismatic Evolutions ETB").price(BigDecimal.TEN).stock(1).build();
        when(products.findAll()).thenReturn(List.of(matched, already, sealed));

        ProductService.OfficialImageBackfill result = service.useOfficialImagesForAll();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unmatched()).containsExactly("Prismatic Evolutions ETB");
        assertThat(matched.getImageUrl()).isEqualTo(OFFICIAL);
        assertThat(matched.getPhotoUrls()).containsExactly("https://cdn.example/mine.jpg");
    }

    @Test
    @DisplayName("Official image hosts are told apart from seller photos")
    void officialHosts() {
        assertThat(ProductImageService.isOfficialImage(OFFICIAL)).isTrue();
        assertThat(ProductImageService.isOfficialImage("https://images.pokemontcg.io/base1/4_hires.png")).isTrue();
        assertThat(ProductImageService.isOfficialImage("https://xyz.supabase.co/storage/v1/object/public/buylist-images/a.jpg")).isFalse();
        assertThat(ProductImageService.isOfficialImage(null)).isFalse();
    }
}
