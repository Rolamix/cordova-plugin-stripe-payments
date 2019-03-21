package com.rolamix.plugins.stripe;

import java.util.ArrayList;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.os.Build;
import com.google.gson.reflect.TypeToken;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.CardRequirements;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.stripe.android.CardUtils;
import com.stripe.android.SourceCallback;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.AccountParams;
import com.stripe.android.model.BankAccount;
import com.stripe.android.model.Card;
import com.stripe.android.model.Source;
import com.stripe.android.model.SourceParams;
import com.stripe.android.model.Token;
import com.stripe.android.view.CardInputWidget;

// https://stripe.com/docs/mobile/android
// https://github.com/stripe/stripe-android
// https://github.com/zyra/cordova-plugin-stripe/blob/v2/src/android/CordovaStripe.java
// https://github.com/stripe/stripe-connect-rocketrides/blob/master/server/routes/api/rides.js

public class StripePaymentsPlugin extends CordovaPlugin {

    private CallbackContext callbackContext;

    private String publishableKey;

    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 9972;

    public static final String ACTION_SET_KEY = "setKey";

    public static final String ACTION_SET_NAME = "setName";

    public static final String ACTION_PICK = "pick";

    public static final String ACTION_PICK_AND_STORE = "pickAndStore";

    public static final String ACTION_HAS_PERMISSION = "hasPermission";

    private static final String LOG_TAG = "FileStackPlugin";

    public StripePaymentsPlugin() {}

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        stripeInstance = new Stripe(webView.getContext());
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback context used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.executeArgs = args;
        this.action = action;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || action.equals(ACTION_HAS_PERMISSION)) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasPermission()));
            return true;
        }
        else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || action.equals(ACTION_SET_KEY) || action.equals(ACTION_SET_NAME)) {
                execute();
                return true;
            }
            else {
                if (hasPermission()) {
                    execute();
                } else {
                    requestPermission();
                }
                return true;
            }
        }
    }

    private boolean hasPermission() {
        return cordova.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission() {
        cordova.requestPermission(this, 0, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "User has denied permission"));
                return;
            }
        }
        execute();
    }

    public void execute() {
        final FileStackPlugin cdvPlugin = this;
        this.cordova.getThreadPool().execute(() -> {
            try {
                if (ACTION_SET_KEY.equals(cdvPlugin.getAction())) {
                    this.apiKey = cdvPlugin.getArgs().getString(0);
                    return;
                }

                Context context = cordova.getActivity().getApplicationContext();
                Intent intent = new Intent(context, FsActivity.class);
                Config config = new Config(this.apiKey);
                intent.putExtra(FsConstants.EXTRA_CONFIG, config);
                intent.putExtra(FsConstants.EXTRA_AUTO_UPLOAD, true);
                if (ACTION_PICK.equals(cdvPlugin.getAction()) || ACTION_PICK_AND_STORE.equals(cdvPlugin.getAction())) {
                    parseGlobalArgs(intent, cdvPlugin.getArgs());
                    if (ACTION_PICK_AND_STORE.equals(cdvPlugin.getAction())) {
                        parseStoreArgs(intent, cdvPlugin.getArgs());
                    }
                    cordova.startActivityForResult(cdvPlugin, intent, REQUEST_FILESTACK);
                }
            }
            catch(JSONException exception) {
                cdvPlugin.getCallbackContext().error("cannot parse json");
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILESTACK) {
            if (resultCode == Activity.RESULT_OK) {
                ArrayList<Selection> selections = data.getParcelableArrayListExtra(FsConstants.EXTRA_SELECTION_LIST);
                try{
                    callbackContext.success(toJSON(selections));
                }
                catch(JSONException exception) {
                    callbackContext.error("json exception");
                }
            } else {
                callbackContext.error("nok");
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void parseGlobalArgs(Intent intent, JSONArray args) throws JSONException {
        if (!args.isNull(0)) {
            intent.putExtra("mimetype", parseJSONStringArray(args.getJSONArray(0)));
        }
        if (!args.isNull(1)) {
            intent.putExtra("services", parseJSONStringArray(args.getJSONArray(1)));
        }
        if (!args.isNull(2)) {
            intent.putExtra("multiple", args.getBoolean(2));
        }
        if (!args.isNull(3)) {
            intent.putExtra("maxFiles", args.getInt(3));
        }
        if (!args.isNull(4)) {
            intent.putExtra("maxSize", args.getInt(4));
        }
    }

    public void parseStoreArgs(Intent intent, JSONArray args) throws JSONException {
        if (!args.isNull(5)) {
            intent.putExtra("location", args.getString(5));
        }
        if (!args.isNull(6)) {
            intent.putExtra("path", args.getString(6));
        }
        if (!args.isNull(7)) {
            intent.putExtra("container", args.getString(7));
        }
        if (!args.isNull(8)) {
            intent.putExtra("access", args.getString(8));
        }
    }

    public String[] parseJSONStringArray(JSONArray jSONArray) throws JSONException {
        String[] a = new String[jSONArray.length()];
        for(int i = 0; i < jSONArray.length(); i++){
            a[i] = jSONArray.getString(i);
        }
        return a;
    }

    public JSONArray toJSON(ArrayList<Selection> selections) throws JSONException {
        JSONArray res = new JSONArray();
        for (Selection selection : selections) {
            JSONObject f = new JSONObject();
            f.put("provider", selection.getProvider());
            f.put("url", selection.getUri());
            f.put("filename", selection.getName());
            f.put("mimetype", selection.getMimeType());
            f.put("localPath", selection.getPath());
            f.put("size", selection.getSize());

            res.put(f);
        }
        return res;
    }

    public String getAction() {
        return this.action;
    }

    public JSONArray getArgs() {
        return this.executeArgs;
    }

    public CallbackContext getCallbackContext() {
        return this.callbackContext;
    }
}
