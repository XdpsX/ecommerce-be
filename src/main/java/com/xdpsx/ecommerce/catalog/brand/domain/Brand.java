package com.xdpsx.ecommerce.catalog.brand.domain;

import java.util.List;

import jakarta.persistence.*;

import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.common.persistence.AuditEntity;
import com.xdpsx.ecommerce.media.domain.Media;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "brands")
public class Brand extends AuditEntity implements HasImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 64, nullable = false, unique = true)
    private String name;

    /**
     * Admin-controlled lifecycle. Never cascaded to Category associations or Products.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private BrandStatus status;

    /**
     * JPA optimistic-lock token. Brand is a single-row aggregate with no hierarchy
     * locking requirement, so a plain
     * version column is sufficient; the admin API exposes it to clients as the
     * concurrency token.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToOne
    @JoinColumn(name = "image_id", referencedColumnName = "id")
    private Media image;

    @ManyToMany
    @JoinTable(
            name = "category_brands",
            joinColumns = @JoinColumn(name = "brand_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "category_id", referencedColumnName = "id"))
    private List<Category> categories;
}
