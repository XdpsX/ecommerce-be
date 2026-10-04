package com.xdpsx.ecommerce.cart.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xdpsx.ecommerce.common.persistence.AuditEntity;
import com.xdpsx.ecommerce.user.domain.User;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "carts",
        uniqueConstraints = @UniqueConstraint(name = "uk_carts_user_id", columnNames = "user_id"),
        indexes = @Index(name = "ix_carts_guest_expiry", columnList = "guest_expires_at"))
@EntityListeners(AuditingEntityListener.class)
public class Cart extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_secret_hash", columnDefinition = "BINARY(32)")
    private byte[] guestSecretHash;

    @Column(name = "guest_expires_at")
    private Instant guestExpiresAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    public static Cart forCustomer(User user) {
        if (user == null) throw new IllegalArgumentException("user must not be null");
        return Cart.builder().user(user).build();
    }

    public static Cart forGuest(byte[] secretHash, Instant expiresAt) {
        if (secretHash == null || secretHash.length != 32) {
            throw new IllegalArgumentException("guest secret hash must contain 32 bytes");
        }
        if (expiresAt == null) throw new IllegalArgumentException("guest expiry must not be null");
        return Cart.builder()
                .guestSecretHash(secretHash.clone())
                .guestExpiresAt(expiresAt)
                .build();
    }

    public void renewGuestUntil(Instant expiresAt) {
        if (!isGuestOwned()) throw new IllegalStateException("Only guest Carts can renew guest expiry");
        if (expiresAt == null) throw new IllegalArgumentException("guest expiry must not be null");
        guestExpiresAt = expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return isGuestOwned() && !guestExpiresAt.isAfter(now);
    }

    public void claimFor(User customer) {
        if (customer == null) throw new IllegalArgumentException("customer must not be null");
        if (!isGuestOwned()) throw new IllegalStateException("Only guest Carts can be claimed");
        user = customer;
        guestSecretHash = null;
        guestExpiresAt = null;
    }

    public boolean isCustomerOwned() {
        return user != null && guestSecretHash == null && guestExpiresAt == null;
    }

    public boolean isGuestOwned() {
        return user == null && guestSecretHash != null && guestExpiresAt != null;
    }

    @PrePersist
    @PreUpdate
    private void validateOwnership() {
        if (!isCustomerOwned() && !isGuestOwned()) {
            throw new IllegalStateException("Cart must have exactly one owner mode");
        }
    }
}
