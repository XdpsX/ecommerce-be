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
     * Lifecycle of this node only. It is never cascaded to descendants, so an
     * {@code ACTIVE} child under an
     * {@code INACTIVE} parent keeps its stored status.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private CategoryStatus status;

    @Column(length = 160, nullable = false, unique = true)
    private String slug;

    /**
     * Zero-based position inside the sibling group. Root categories (no parent)
     * form their own group.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @OneToOne
    @JoinColumn(name = "image_id", referencedColumnName = "id")
    private Media image;

    /**
     * Adjacency list parent. Children are never mapped as a collection; they are
     * read through repository
     * queries so a node can be loaded without its whole subtree.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private Category parent;

    public static final int MAX_DEPTH = 3;

    /**
     * Derived storefront visibility: true only when this node and every ancestor are stored {@code ACTIVE}.
     *
     * <p>The chain walk is pure in-memory and hard-capped at {@link #MAX_DEPTH} hops; the caller must have loaded
     * the parent chain already (repository read queries fetch it), otherwise each lazy hop would issue a query.
     * Malformed data deeper than the cap (or cyclic) reads as not effectively active instead of walking further.
     * The value is never persisted and a parent status change is never cascaded to descendants.
     */
    public boolean isEffectivelyActive() {
        Category node = this;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            if (node == null) {
                return true;
            }
            if (node.getStatus() != CategoryStatus.ACTIVE) {
                return false;
            }
            node = node.getParent();
        }
        // A remaining parent means the chain exceeds the depth invariant; treat it as not visible.
        return node == null;
    }
}
