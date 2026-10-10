package com.ironoak.exceptions;

/** The payment provider is not configured (503) or refused or failed a request (502). */
public class PaymentProviderException extends RuntimeException {

    private final boolean notConfigured;

    private PaymentProviderException(String message, boolean notConfigured, Throwable cause) {
        super(message, cause);
        this.notConfigured = notConfigured;
    }

    public static PaymentProviderException notConfigured(String what) {
        return new PaymentProviderException("Payments are not available: " + what + " is not configured", true, null);
    }

    public static PaymentProviderException failed(String message, Throwable cause) {
        return new PaymentProviderException(message, false, cause);
    }

    public boolean isNotConfigured() {
        return notConfigured;
    }
}
