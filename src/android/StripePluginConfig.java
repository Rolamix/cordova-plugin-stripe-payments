package com.rolamix.plugins.stripe;

import java.util.HashMap;

public class StripePluginConfig {
    private static final StripePluginConfig mInstance = new StripePluginConfig();

    public static StripePluginConfig getInstance() {
        return mInstance;
    }

    public String publishableKey = "";
    public String ephemeralKeyUrl = "";
    public String companyName = "";

    public HashMap<String, String> extraHTTPHeaders = new HashMap<>();

    public boolean validate() {
      return !publishableKey.isEmpty() && !ephemeralKeyUrl.isEmpty() && StripePluginUtils.validateStripeKey(publishableKey) >= 0;
    }

    private StripePluginConfig() {
    }
}
