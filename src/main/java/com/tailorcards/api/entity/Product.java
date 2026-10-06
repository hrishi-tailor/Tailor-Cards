package com.tailorcards.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "category")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @ColumnDefault("0")
    @Column(name = "version")
    private Long version;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private Integer stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "card_set")
    private String set;

    @Column(name = "condition")
    private String condition;

    @Column(name = "grading")
    private String grading;

    @Column(name = "cost_basis", precision = 10, scale = 2)
    private BigDecimal costBasis;

    @Column(name = "pokemontcg_id")
    private String pokemontcgId;

    @Builder.Default
    @Column(name = "status")
    private String status = "AVAILABLE";

    @PrePersist
    @PreUpdate
    public void validateStatus() {
        if (this.status == null || this.status.isBlank()) {
            this.status = "AVAILABLE";
        } else if (!"AVAILABLE".equals(this.status) && !"SOLD".equals(this.status)) {
            throw new IllegalArgumentException("Status must be either AVAILABLE or SOLD");
        }
    }

    public void setStatus(String status) {
        if (status != null && !status.isBlank()) {
            if (!"AVAILABLE".equals(status) && !"SOLD".equals(status)) {
                throw new IllegalArgumentException("Status must be either AVAILABLE or SOLD");
            }
            this.status = status;
        } else {
            this.status = "AVAILABLE";
        }
    }

    public void decrementStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to decrement must be greater than zero");
        }
        if (this.stock == null || this.stock < quantity) {
            throw new IllegalArgumentException(String.format(
                    "Insufficient stock for product '%s'. Requested: %d, Available: %d",
                    this.name, quantity, this.stock != null ? this.stock : 0
            ));
        }
        this.stock -= quantity;
        if (this.stock == 0) {
            this.status = "SOLD";
        }
    }
}
