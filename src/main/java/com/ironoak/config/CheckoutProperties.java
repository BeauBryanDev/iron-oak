package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds app.checkout.*: how long unpaid orders hold stock and which currency orders use. */
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {

    /** Minutes a PENDING_PAYMENT order keeps its product stock before it expires. */
    private int reservationMinutes = 360;

    /** How long a Stripe Checkout page stays open. Stripe's minimum is 30 minutes. */
    private int sessionMinutes = 31;

    /** An order's stock hold can be extended for new payment attempts only up to this age. */
    private int maxHoldMinutes = 360;

    /** Where the storefront lives; Stripe returns the customer to /checkout/success or /checkout/cancel here. */
    private String frontendBaseUrl = "http://localhost:3000";

    /** ISO 4217 code stamped on every new order and payment. */
    private String currency = "USD";

    public int getReservationMinutes() {
        return reservationMinutes;
    }

    public void setReservationMinutes(int reservationMinutes) {
        this.reservationMinutes = reservationMinutes;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getSessionMinutes() {
        return sessionMinutes;
    }

    public void setSessionMinutes(int sessionMinutes) {
        this.sessionMinutes = sessionMinutes;
    }

    public int getMaxHoldMinutes() {
        return maxHoldMinutes;
    }

    public void setMaxHoldMinutes(int maxHoldMinutes) {
        this.maxHoldMinutes = maxHoldMinutes;
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }
}
