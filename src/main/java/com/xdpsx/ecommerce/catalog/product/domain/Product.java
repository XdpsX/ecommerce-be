package com.xdpsx.ecommerce.catalog.product.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.common.persistence.AuditEntity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class Product extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private BigDecimal price;

    private double discountPercent;

    private boolean inStock;

    private boolean published;

    @Column(length = 4096)
    private String description;

    private String mainImage;

    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "brand_id", referencedColumnName = "id")
    private Brand brand;

    @Builder.Default
    @OneToMany(
            mappedBy = "product",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    public BigDecimal getDiscountedPrice() {
        if (this.discountPercent > 0) {
            BigDecimal discountAmount = price.multiply(BigDecimal.valueOf(discountPercent / 100));
            BigDecimal discountedPrice = price.subtract(discountAmount);

            return discountedPrice
                    .divide(BigDecimal.valueOf(100000), 0, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100000));
        }
        return null;
    }
}
