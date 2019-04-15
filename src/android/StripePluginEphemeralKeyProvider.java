package com.rolamix.plugins.stripe;

import android.support.annotation.NonNull;
import android.support.annotation.Size;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.stripe.android.EphemeralKeyProvider;
import com.stripe.android.EphemeralKeyUpdateListener;

import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class StripePluginEphemeralKeyProvider implements EphemeralKeyProvider {

  @NonNull private final CompositeSubscription mCompositeSubscription;
  @NonNull private final StripeService mStripeService;
  @NonNull private final ProgressListener mProgressListener;

  public StripePluginEphemeralKeyProvider(@NonNull ProgressListener progressListener) {
    final Retrofit retrofit = RetrofitFactory.getInstance();
    mStripeService = retrofit.create(StripeService.class);
    mCompositeSubscription = new CompositeSubscription();
    mProgressListener = progressListener;
  }

  @Override
  public void createEphemeralKey(@NonNull @Size(min = 4) String apiVersion,
                                 @NonNull final EphemeralKeyUpdateListener keyUpdateListener) {
    final Map<String, String> apiParamMap = new HashMap<>();
    apiParamMap.put("api_version", apiVersion);

    final Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.putAll(StripePluginConfig.getInstance().extraHTTPHeaders);

    HttpUrl url = HttpUrl.get(StripePluginConfig.getInstance().ephemeralKeyUrl);

    mCompositeSubscription.add(
      mStripeService.createEphemeralKey(url, apiParamMap, headers)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<ResponseBody>() {
          @Override
          public void call(ResponseBody response) {
            try {
              String rawKey = response.string();
              keyUpdateListener.onKeyUpdate(rawKey);
              mProgressListener.onStringResponse(rawKey);
            } catch (IOException ignored) {
            }
          }
        }, new Action1<Throwable>() {
          @Override
          public void call(Throwable throwable) {
            mProgressListener.onStringResponse("Error: " + throwable.getMessage());
          }
        }));
  }

  public interface ProgressListener {
    void onStringResponse(@NonNull String string);
  }
}
