package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds app.shipping.*: the Google Routes API and how long special orders hold stock. */
@ConfigurationProperties(prefix = "app.shipping")
public class ShippingProperties {

    /** Server-side key restricted to the Routes API (GOOGLE_ROUTES_API_KEY). Blank: Google is never called. */
    private String googleApiKey = "";

    /** Hard ceiling on Google calls per UTC day; further lookups fall back to the estimate. */
    private int googleDailyCap = 200;

    /** Where every shipment starts, as Google should read it (the warehouse address). */
    private String originAddress = "Bogota, Colombia";

    /** Warehouse coordinates as "[lat, lon]" or "lat, lon"; used instead of the address when set. */
    private String originLocation = "";

    /** Stock hold of an order whose shipping staff must quote first (machines, unpriced air). */
    private int quoteHoldHours = 72;

    /** Stock hold of an order Piper created: the customer pays later with the order number. */
    private int piperHoldMinutes = 1440;

    public String getGoogleApiKey() {
        return googleApiKey;
    }

    public void setGoogleApiKey(String googleApiKey) {
        this.googleApiKey = googleApiKey;
    }

    public int getGoogleDailyCap() {
        return googleDailyCap;
    }

    public void setGoogleDailyCap(int googleDailyCap) {
        this.googleDailyCap = googleDailyCap;
    }

    public String getOriginAddress() {
        return originAddress;
    }

    public void setOriginAddress(String originAddress) {
        this.originAddress = originAddress;
    }

    public String getOriginLocation() {
        return originLocation;
    }

    public void setOriginLocation(String originLocation) {
        this.originLocation = originLocation;
    }

    public int getQuoteHoldHours() {
        return quoteHoldHours;
    }

    public void setQuoteHoldHours(int quoteHoldHours) {
        this.quoteHoldHours = quoteHoldHours;
    }

    public int getPiperHoldMinutes() {
        return piperHoldMinutes;
    }

    public void setPiperHoldMinutes(int piperHoldMinutes) {
        this.piperHoldMinutes = piperHoldMinutes;
    }
}
