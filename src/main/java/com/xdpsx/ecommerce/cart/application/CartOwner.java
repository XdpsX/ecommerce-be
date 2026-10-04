package com.xdpsx.ecommerce.cart.application;

public final class CartOwner {
    private final String customerEmail;
    private final String guestCredential;

    private CartOwner(String customerEmail, String guestCredential) {
        this.customerEmail = customerEmail;
        this.guestCredential = guestCredential;
    }

    public static CartOwner customer(String email) {
        return new CartOwner(email, null);
    }

    public static CartOwner guest(String credential) {
        return new CartOwner(null, credential);
    }

    public boolean isCustomer() {
        return customerEmail != null;
    }

    public String customerEmail() {
        return customerEmail;
    }

    public String guestCredential() {
        return guestCredential;
    }
}
