package com.xdpsx.ecommerce.catalog.category.domain;

import java.util.List;

import jakarta.persistence.*;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
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
@Table(name = "categories")
public class Category extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 128, nullable = false, unique = true)
    private String name;

    private boolean publicFlg;

    @OneToOne
    @JoinColumn(name = "image_id", referencedColumnName = "id")
    private Media image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private Category parent;

    @OneToMany(mappedBy = "parent")
    //    @OrderBy("name asc")
    private List<Category> children;

    @ManyToMany(mappedBy = "categories")
    private List<Brand> brands;

    public static final int MAX_DEPTH = 3;
}
