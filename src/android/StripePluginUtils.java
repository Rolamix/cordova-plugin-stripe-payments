package com.rolamix.plugins.stripe;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.stripe.android.model.Source;
import com.stripe.android.model.SourceCardData;


public class StripePluginUtils {
    static final String LOG_TAG = "StripePluginUtils";

    static int validateStripeKey(String stripeKey) {
      if (stripeKey.contains("pk_test")) {
        return 1;
      } else if (stripeKey.contains("pk_live")) {
        return 2;
      } else {
        return -1;
      }
    }

    static JSONObject mapToJSON(HashMap<String, Object> map) {
      JSONObject message = new JSONObject();
      for (Map.Entry<String, Object> pairs : map.entrySet()) {
        try {
          message.put(pairs.getKey(), pairs.getValue());
        } catch (JSONException e) { }
      }
      return message;
    }

    static HashMap<String, String> parseExtraHeaders(JSONObject headers, HashMap<String, String> fallback) {
      if (headers != null && headers.length() > 0) {
        HashMap<String, String> storedHeaders = new HashMap<>();
        Iterator<String> headerIterator = headers.keys();

        while(headerIterator.hasNext()) {
          String key = headerIterator.next();
          String value = headers.optString(key, "");
          storedHeaders.put(key, value);
          Log.v(LOG_TAG, "Storing header:" + key + ", " + value);
        }

        return storedHeaders;
      }

      return fallback;
    }

    static String formatSourceDescription(Source source) {
      if (Source.CARD.equals(source.getType())) {
        final SourceCardData sourceCardData = (SourceCardData) source.getSourceTypeModel();
        return sourceCardData.getBrand() + " " + sourceCardData.getLast4();
      }
      return source.getType();
    }

}
