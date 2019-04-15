package com.rolamix.plugins.stripe;

import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Url;
import rx.Observable;

/**
 * The {@link retrofit2.Retrofit} interface that creates our API service.
 */
public interface StripeService {

  @FormUrlEncoded
  @POST()
  Observable<ResponseBody> createEphemeralKey(@Url() HttpUrl url, @FieldMap Map<String, String> apiVersionMap, @HeaderMap Map<String, String> headers);
}
