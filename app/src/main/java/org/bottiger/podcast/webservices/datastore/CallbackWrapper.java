package org.bottiger.podcast.webservices.datastore;

import android.support.annotation.Nullable;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by Arvid on 8/27/2015.
 */
public class CallbackWrapper<T> implements Callback<T>, IWebservice.ICallback {

    private Callback<T> mCallback;
    private IWebservice.ICallback mICallback;

    public CallbackWrapper(@Nullable IWebservice.ICallback argICallback, @Nullable Callback<T> argCallback) {
        mCallback = argCallback;
        mICallback = argICallback;
    }

    @Override
    public void onResponse(Call call, Response response) {
        if (mCallback != null)
            mCallback.onResponse(call, response);

        if (mICallback != null)
            mICallback.onResponse(call, response);
    }

    @Override
    public void onFailure(Call call, Throwable error) {
        if (mCallback != null)
            mCallback.onFailure(call, error);

        if (mICallback != null)
            mICallback.onFailure(call, error);
    }
}
