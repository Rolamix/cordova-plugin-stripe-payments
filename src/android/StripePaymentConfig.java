package com.rolamix.plugins.stripe;

public class StripePaymentConfig {
    private static final StripePaymentConfig mInstance = new StripePaymentConfig();

    public static StripePaymentConfig getInstance() {
        return mInstance;
    }

    public Long price = 0L;
    public String currency = "USD";
    public String country = "US";

    public boolean validate() {
      return price >= 0 && !currency.isEmpty() && !country.isEmpty();
    }

    private StripePaymentConfig() {
    }
}
