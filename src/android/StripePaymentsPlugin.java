package com.rolamix.plugins.stripe;

import java.util.HashMap;
import java.util.concurrent.Callable;

import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.DialogInterface;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

// import com.stripe.android.CardUtils;
import com.stripe.android.model.Card;
import com.stripe.android.model.SourceParams;
import com.stripe.android.model.Customer;
import com.stripe.android.model.CustomerSource;
import com.stripe.android.model.Source;
import com.stripe.android.model.SourceCardData;
import com.stripe.android.Stripe;
import com.stripe.android.StripeError;
import com.stripe.android.CustomerSession;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.PaymentResultListener;
import com.stripe.android.PaymentSession;
import com.stripe.android.PaymentSessionConfig;
import com.stripe.android.PaymentSessionData;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;


// https://stripe.com/docs/mobile/android/standard
// https://github.com/stripe/stripe-android
// https://github.com/stripe/stripe-android/blob/master/example/
// https://github.com/stripe/stripe-android/tree/master/samplestore/
// https://github.com/zyra/cordova-plugin-stripe/blob/v2/src/android/CordovaStripe.java
// https://github.com/stripe/stripe-connect-rocketrides/blob/master/server/routes/api/rides.js
// Integrating Google Pay.
// https://developers.google.com/pay/api/android/overview
// https://stripe.com/docs/mobile/android/google-pay
// https://github.com/jack828/cordova-plugin-stripe-google-apple-pay

public class StripePaymentsPlugin extends CordovaPlugin {

    private static final String LOG_TAG = "StripePaymentsPlugin";

    @NonNull private final CompositeSubscription mCompositeSubscription = new CompositeSubscription();
    private CallbackContext paymentStatusCallback;
    private PaymentSession mPaymentSession;
    private Stripe stripeInstance;
    private Source mRedirectSource; // used for 3DS verifications

    private static final String ACTION_INIT_PLUGIN = "beginStripe";
    private static final String ACTION_ADD_STATUS_OBSERVER = "addPaymentStatusObserver";
    private static final String ACTION_SHOW_PAYMENT_DIALOG = "showPaymentDialog";
    private static final String ACTION_REQUEST_PAYMENT = "requestPayment";

    private static final String RETURN_SCHEMA = "stripe://";
    private static final String RETURN_HOST_SYNC = "stripe3ds"; // matches the value in plugin.xml
    private static final String QUERY_CLIENT_SECRET = "client_secret";
    private static final String QUERY_SOURCE_ID = "source";


    public StripePaymentsPlugin() {}

    @Override()
    protected void pluginInitialize() {
        super.pluginInitialize();
        stripeInstance = new Stripe(this.cordova.getActivity().getApplicationContext());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mPaymentSession.handlePaymentData(requestCode, resultCode, data);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null && intent.getData().getQuery() != null) {
            // The client secret and source ID found here is identical to
            // that of the source used to get the redirect URL.
            String clientSecret = intent.getData().getQueryParameter(QUERY_CLIENT_SECRET);
            String sourceId = intent.getData().getQueryParameter(QUERY_SOURCE_ID);

            if (clientSecret != null
                && sourceId != null
                && clientSecret.equals(mRedirectSource.getClientSecret())
                && sourceId.equals(mRedirectSource.getId())) {

                Log.i(LOG_TAG, "[StripePaymentsPlugin].requestPayment 3DS source verified:" + mRedirectSource.getId());
                HashMap<String, Object> message = new HashMap<>();
                message.put("status", "PAYMENT_CREATED");
                message.put("source", mRedirectSource.getId());
                successCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                mRedirectSource = null;
            }
            // if we had a progress dialog, we'd dismiss it here.
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPaymentSession.onDestroy();
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

        switch (action) {
            case ACTION_INIT_PLUGIN:
                args.optJSONObject(0);
                initPluginConfig(args.getJSONObject(0), callbackContext);
                break;

            case ACTION_ADD_STATUS_OBSERVER:
                addStatusObserver(callbackContext);
                break;

            case ACTION_SHOW_PAYMENT_DIALOG:
                showPaymentDialog(args.getJSONObject(0), callbackContext);
                break;

            case ACTION_REQUEST_PAYMENT:
                requestPayment(callbackContext);
                break;

            default:
                return false;
        }

        return true;
    }

    public void initPluginConfig(JSONObject pluginConfig, CallbackContext callbackContext) {
        HashMap<String, Object> message = new HashMap<>();
        message.put("status", "INIT_ERROR");
        message.put("error", "[CONFIG]: The Stripe Publishable Key and ephemeral key generation URL are required");

        if (pluginConfig == null || pluginConfig.length() == 0) {
            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
            return;
        }

        StripePluginConfig.getInstance().publishableKey = pluginConfig.optString("publishableKey", "");
        StripePluginConfig.getInstance().ephemeralKeyUrl = pluginConfig.optString("ephemeralKeyUrl", "");
        StripePluginConfig.getInstance().companyName = pluginConfig.optString("companyName", "");

        JSONObject headers = pluginConfig.optJSONObject("extraHTTPHeaders");
        StripePluginConfig.getInstance().extraHTTPHeaders = StripePluginUtils.parseExtraHeaders(headers, new HashMap<>());

        if (!StripePluginConfig.getInstance().validate()) {
            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
            return;
        }

        PaymentConfiguration.init(StripePluginConfig.getInstance().publishableKey);
        stripeInstance.setDefaultPublishableKey(StripePluginConfig.getInstance().publishableKey);

        setupCustomerSession(null);

        message.put("status", "INIT_SUCCESS");
        message.remove("error");
        successCallback(callbackContext, StripePluginUtils.mapToJSON(message));
    }

    public void addStatusObserver(CallbackContext callbackContext) {
        paymentStatusCallback = callbackContext;

        HashMap<String, Object> message = new HashMap<>();
        message.put("status", "LISTENER_ADDED");
        successCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
    }

    public void showPaymentDialog(JSONObject paymentConfig, CallbackContext callbackContext) {
        HashMap<String, Object> message = new HashMap<>();
        message.put("status", "PAYMENT_DIALOG_ERROR");
        message.put("error", "[CONFIG]: Error parsing payment options or they were not provided");

        if (paymentConfig == null || paymentConfig.length() == 0) {
            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
            return;
        }

        if (!StripePluginConfig.getInstance().validate()) {
            message.put("error", "[CONFIG]: Config is not set, init() must be called before using plugin");
            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
        }

        JSONObject headers = paymentConfig.optJSONObject("extraHTTPHeaders");
        StripePluginConfig.getInstance().extraHTTPHeaders = StripePluginUtils.parseExtraHeaders(headers, new HashMap<>());
        StripePaymentConfig.getInstance().price = paymentConfig.optLong("price", 0L);
        StripePaymentConfig.getInstance().currency = paymentConfig.optString("currency", "USD");
        StripePaymentConfig.getInstance().country = paymentConfig.optString("country", "US");

        setupCustomerSession(new CreateCustomerSessionListener() {
            @Override
            public void onKeyRetrieved(String key) {
                // For now, we won't bother checking if key is null or non-null (error or not)
                setupPaymentSession();
                cordova.setActivityResultCallback(StripePaymentsPlugin.this);
                mPaymentSession.setCartTotal(StripePaymentConfig.getInstance().price);
                mPaymentSession.presentPaymentMethodSelection();

                message.clear();
                message.put("status", "PAYMENT_DIALOG_SHOWN");
                successCallback(callbackContext, StripePluginUtils.mapToJSON(message));
            }
        });
    }

    // Android does in 1 step what requires 2 steps on iOS. Android saves the payment method
    // to the customer as soon as one is entered; on iOS the source is not created until AFTER
    // you requestPayment from the payment context (requiring the 2nd step).
    // However, Android still requires verifying 3DSecure so we will try to do that here.
    public void requestPayment(CallbackContext callbackContext) {
        HashMap<String, Object> message = new HashMap<>();
        message.put("status", "REQUEST_PAYMENT_ERROR");
        message.put("error", "[CONFIG]: Config is not set, init() must be called before using plugin");

        if (!StripePluginConfig.getInstance().validate()) {
            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
        }

        if (mPaymentSession == null) {
            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
            return;
        }

        PaymentSessionData data = mPaymentSession.getPaymentSessionData();
        final String selectedPaymentMethodId = data.getSelectedPaymentMethodId();

        if (data.isPaymentReadyToCharge() && data.getPaymentResult() == PaymentResultListener.INCOMPLETE && selectedPaymentMethodId != null) {
            CustomerSession.getInstance().retrieveCurrentCustomer(
                new CustomerSession.CustomerRetrievalListener() {
                    @Override
                    public void onCustomerRetrieved(@NonNull Customer customer) {
                        CustomerSource source = customer.getSourceById(selectedPaymentMethodId);
                        if (source == null) {
                            message.put("error", "Error: No valid payment source is available to complete payment");
                            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
                            return;
                        }

                        Source src = source.asSource();
                        if (src == null) {
                            message.put("error", "Error: No valid payment source is available to complete payment");
                            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
                            return;
                        }

                        String sourceType = src.getType();
                        if (Source.CARD.equals(sourceType)) {
                            // Before we complete, we need to check if this transaction requires 3DSecure
                            SourceCardData cardData = (SourceCardData) src.getSourceTypeModel();
                            if (SourceCardData.REQUIRED.equals(cardData.getThreeDSecureStatus())) {
                                // In this case, you would need to ask the user to verify the purchase.
                                createThreeDSecureSource(src.getId());
                                return;
                            }
                        }

                        // Either this is not a card, and it's Stripe's job to return the source;
                        // or it is a card, and 3DS is not required. In either case we can immediately
                        // return the Source for charging.
                        Log.i(LOG_TAG, "[StripePaymentsPlugin].requestPayment source retrieved:" + src.getId());
                        message.put("status", "PAYMENT_CREATED");
                        message.remove("error");
                        message.put("source", src.getId());
                        successCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                    }

                    @Override
                    public void onError(int httpCode, @Nullable String errorMessage, @Nullable StripeError stripeError) {
                        displayError(errorMessage);
                        message.put("error", errorMessage);
                        errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
                    }
                });
        } else {
            message.put("error", "Error: No valid payment source is available to complete payment");
            errorCallback(callbackContext, StripePluginUtils.mapToJSON(message));
        }
    }


    /**
     *
     *
     * Implementation methods
     *
     */

    private void setupCustomerSession(@Nullable  CreateCustomerSessionListener listener) {
        CustomerSession.endCustomerSession();
        // CustomerSession only needs to be initialized once per app.
        CustomerSession.initCustomerSession(
            new StripePluginEphemeralKeyProvider(
                new StripePluginEphemeralKeyProvider.ProgressListener() {
                    @Override
                    public void onStringResponse(@NonNull String string) {
                        if (string.startsWith("Error: ")) {
                            new AlertDialog.Builder(getApplicationContext())
                                    .setMessage(string)
                                    .show();
                            if (listener != null) {
                                listener.onKeyRetrieved(null);
                            }
                            return;
                        }
                        if (listener != null) {
                            listener.onKeyRetrieved(string);
                        }
                    }
                }));
    }

    private void setupPaymentSession() {
        mPaymentSession = new PaymentSession(getActivity());

        mPaymentSession.init(new PaymentSession.PaymentSessionListener() {
            @Override
            public void onCommunicatingStateChanged(boolean isCommunicating) { }

            @Override
            public void onError(int errorCode, @Nullable String errorMessage) {
                HashMap<String, Object> message = new HashMap<>();
                message.put("status", "PAYMENT_STATUS_ERROR");
                message.put("error", errorMessage);
                errorCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                displayError(errorMessage);
            }

            @Override
            public void onPaymentSessionDataChanged(@NonNull PaymentSessionData data) {
                HashMap<String, Object> message = new HashMap<>();
                message.put("status", "PAYMENT_STATUS_ERROR");

                final String selectedPaymentMethodId = data.getSelectedPaymentMethodId();

                if (selectedPaymentMethodId != null) {
                    CustomerSession.getInstance().retrieveCurrentCustomer(
                        new CustomerSession.CustomerRetrievalListener() {
                            @Override
                            public void onCustomerRetrieved(@NonNull Customer customer) {
                                // This is how you'd do it if you wanted to use the Customer's default source.
                                // However, we want to use the one they selected in the dialog.
                                // String sourceId = customer.getDefaultSource();
                                // if (sourceId == null) { return; }
                                CustomerSource source = customer.getSourceById(selectedPaymentMethodId);

                                if (source == null) {
                                    message.put("error", "Error: No valid payment source is available to complete payment");
                                    errorCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                                    return;
                                }

                                Source src = source.asSource();
                                if (src == null) {
                                    message.put("error", "Error: No valid payment source is available to complete payment");
                                    errorCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                                    return;
                                }

                                // Report if this transaction requires 3DSecure so that client has an opportunity
                                // to prompt the user to verify.
                                SourceCardData cardData = (SourceCardData) src.getSourceTypeModel();
                                boolean is3ds = SourceCardData.REQUIRED.equals(cardData.getThreeDSecureStatus());
                                String sourceId = src.getId();

                                message.put("status", "PAYMENT_STATUS_CHANGED");
                                message.put("isPaymentReady", data.isPaymentReadyToCharge());
                                message.put("isLoading", !data.isPaymentReadyToCharge());
                                message.put("label", StripePluginUtils.formatSourceDescription(src));
                                message.put("image", null); // Not supported on this platform... yet.
                                message.put("is3DSRequired", is3ds);
                                message.put("source", sourceId);

                                successCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                            }

                            @Override
                            public void onError(int httpCode, @Nullable String errorMessage, @Nullable StripeError stripeError) {
                                displayError(errorMessage);
                                message.put("error", errorMessage);
                                errorCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                            }
                        });
                } else {
                    message.put("status", "PAYMENT_STATUS_CHANGED");
                    message.put("isPaymentReady", false);
                    message.put("isLoading", true);
                    message.put("label", "");
                    message.put("image", null); // Not supported on this platform... yet.
                    successCallback(paymentStatusCallback, StripePluginUtils.mapToJSON(message), true);
                }
            }
        }, new PaymentSessionConfig.Builder()
                .setShippingInfoRequired(false)
                .setShippingMethodsRequired(false)
                .build()
        );
    }

    /**
     * Create the 3DS Source as a separate call to the API. This is what is needed
     * to verify the third-party approval. The only information from the Card source
     * that is used is the ID field.
     *
     * @param sourceId the {@link Source#mId} from the {@link Card}-created {@link Source}.
     */
    void createThreeDSecureSource(String sourceId) {
        // This represents a request for a 3DS purchase.
        final SourceParams threeDParams = SourceParams.createThreeDSecureParams(
                StripePaymentConfig.getInstance().price,
                StripePaymentConfig.getInstance().currency,
                RETURN_SCHEMA + RETURN_HOST_SYNC,
                sourceId);

        Observable<Source> threeDSecureObservable = Observable.fromCallable(
                new Callable<Source>() {
                    @Override
                    public Source call() throws Exception {
                        return stripeInstance.createSourceSynchronous(
                                threeDParams,
                                PaymentConfiguration.getInstance().getPublishableKey());
                    }
                });

        mCompositeSubscription.add(threeDSecureObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        // Because we've made the mapping above, we're now subscribing
                        // to the result of creating a 3DS Source
                        new Action1<Source>() {
                            @Override
                            public void call(Source source) {
                                // Once a 3DS Source is created, that is used
                                // to initiate the third-party verification
                                mRedirectSource = source;
                                cordova.setActivityResultCallback(StripePaymentsPlugin.this);
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(source.getRedirect().getUrl()));
                                getActivity().startActivity(browserIntent);
                            }
                        },
                        new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                displayError(throwable.getMessage());
                            }
                        }
                ));
    }

    private void displayError(String errorMessage) {
        AlertDialog alertDialog = new AlertDialog.Builder(getApplicationContext()).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage(errorMessage);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    private Context getContext() {
        return this.cordova.getContext();
    }

    private Activity getActivity() {
        return this.cordova.getActivity();
    }

    private Context getApplicationContext() {
        return this.getActivity().getApplicationContext();
        // Other useful lines
        // cordova.startActivityForResult(this, intent, REQUEST_SOMETHING);
        // this.cordova.getThreadPool().execute(() -> { });
    }

    private PluginResult successCallback(CallbackContext context, JSONObject message) {
        return successCallback(context, message, false);
    }

    private PluginResult successCallback(CallbackContext context, JSONObject message, boolean keepCallback) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, message);
        result.setKeepCallback(keepCallback);
        context.sendPluginResult(result);
        return result;
    }

    private PluginResult errorCallback(CallbackContext context, JSONObject message) {
        return errorCallback(context, message, false);
    }

    private PluginResult errorCallback(CallbackContext context, JSONObject message, boolean keepCallback) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, message);
        result.setKeepCallback(keepCallback);
        context.sendPluginResult(result);
        return result;
    }

    private interface CreateCustomerSessionListener {
        void onKeyRetrieved(@Nullable String key);
    }

}
