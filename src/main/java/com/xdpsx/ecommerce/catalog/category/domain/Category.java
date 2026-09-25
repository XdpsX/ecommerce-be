package com.xdpsx.ecommerce.catalog.category.domain;

import jakarta.persistence.*;

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

    /**
     * Lifecycle of this node only. It is never cascaded to descendants, so an {@code ACTIVE} child under an
     * {@code INACTIVE} parent keeps its stored status.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private CategoryStatus status;

    @Column(length = 160, nullable = false, unique = true)
    private String slug;

    /**
     * Zero-based position inside the sibling group. Root categories (no parent) form their own group.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @OneToOne
    @JoinColumn(name = "image_id", referencedColumnName = "id")
    private Media image;

    /**
     * Adjacency list parent. Children are never mapped as a collection; they are read through repository
     * queries so a node can be loaded without its whole subtree.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private Category parent;

    public static final int MAX_DEPTH = 3;
}
