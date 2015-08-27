package org.bottiger.podcast.webservices.datastore.gpodder;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.util.SparseArray;

import org.bottiger.podcast.BuildConfig;
import org.bottiger.podcast.provider.ISubscription;
import org.bottiger.podcast.utils.OPMLImportExport;
import org.bottiger.podcast.webservices.datastore.gpodder.datatypes.GSubscription;
import org.bottiger.podcast.webservices.datastore.gpodder.datatypes.SubscriptionChanges;
import org.bottiger.podcast.webservices.datastore.gpodder.datatypes.UpdatedUrls;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.OkClient;
import retrofit.client.Response;

/**
 * Created by Arvid on 8/23/2015.
 */
public class GPodderAPI {

    private static final String TAG = "GPodderAPI";

    private IGPodderAPI api;
    private String mUsername;

    private boolean mAuthenticated = false;

    public GPodderAPI(@NonNull String argUsername, @NonNull String argPassword) {
        this(argUsername, argPassword, null);
    }

    public GPodderAPI(@NonNull String argUsername, @NonNull String argPassword, @Nullable Callback argCallback) {

        mUsername = argUsername;

        ApiRequestInterceptor requestInterceptor = new ApiRequestInterceptor(argUsername, argPassword);

        RestAdapter.LogLevel logLevel = BuildConfig.DEBUG ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.BASIC;

        api = new RestAdapter.Builder()
                .setRequestInterceptor(requestInterceptor)
                .setEndpoint(IGPodderAPI.baseUrl)
                .setClient(new OkClient()) // The default client didn't handle well responses like 401
                .setLogLevel(logLevel)
                .setLog(new RestAdapter.Log() {
                    @Override
                    public void log(String message) {
                        Log.d("retrofit", message);
                    }
                })
                .build()
                .create(IGPodderAPI.class);

        authenticate(api, argCallback);
    }

    public void uploadSubscriptions(final LongSparseArray<ISubscription> argSubscriptions) {

        if (mAuthenticated) {
            uploadSubscriptionsInternal(argSubscriptions);
            return;
        }

        authenticate(api, new Callback() {
            @Override
            public void success(Object o, Response response) {
                uploadSubscriptionsInternal(argSubscriptions);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(TAG, error.toString());
            }
        });
    }

    private void uploadSubscriptionsInternal(final LongSparseArray<ISubscription> argSubscriptions) {

        //String test = "feeds.twit.tv/brickhouse.xml\nleoville.tv/podcasts/twit.xml";
        //String opml = OPMLImportExport.toOPML(argSubscriptions);

        List<String> subscriptionList = new LinkedList<>();
        long key;
        ISubscription feed;

        for (int i = 0; i < argSubscriptions.size(); i++) {
            key = argSubscriptions.keyAt(i);
            feed = argSubscriptions.get(key);
            subscriptionList.add(feed.getURLString());
        }

        api.uploadDeviceSubscriptions(subscriptionList, mUsername, GPodderUtils.getDeviceID(), new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                Log.d(TAG, response.toString());
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(TAG, error.toString());
            }
        });
    }

    public void getDeviceSubscriptions() {

        if (mAuthenticated) {
            getDeviceSubscriptions();
            return;
        }

        api.getDeviceSubscriptions(mUsername, GPodderUtils.getDeviceID(), new Callback<List<GSubscription>>() {
            @Override
            public void success(List<GSubscription> gSubscriptions, Response response) {
                Log.d(TAG, response.toString());
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(TAG, error.toString());
            }
        });
    }

    public void getAllSubscriptions() {

        if (mAuthenticated) {
            getAllSubscriptions();
            return;
        }

        api.getSubscriptions(mUsername, new Callback<List<GSubscription>>() {
            @Override
            public void success(List<GSubscription> gSubscriptions, Response response) {
                Log.d(TAG, response.toString());
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(TAG, error.toString());
            }
        });
    }

    public void getDeviceSubscriptionsChanges(long argSince) {

        if (mAuthenticated) {
            getDeviceSubscriptionsChanges(argSince);
            return;
        }

        api.getDeviceSubscriptionsChanges(mUsername, GPodderUtils.getDeviceID(), argSince, new Callback<SubscriptionChanges>() {
            @Override
            public void success(SubscriptionChanges s, Response response) {
                Log.d(TAG, response.toString());
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(TAG, error.toString());
            }
        });
    }

    private void uploadSubscriptionChangesInternal(LongSparseArray<ISubscription> argAdded,
                                                   LongSparseArray<ISubscription> argRemoved) {

        SubscriptionChanges changes = new SubscriptionChanges();

        ISubscription subscription;

        for (int i = 0; i < argAdded.size(); i++)
        {
            subscription = argAdded.valueAt(i);
            changes.add.add(subscription.getURLString());
        }

        for (int i = 0; i < argRemoved.size(); i++)
        {
            subscription = argRemoved.valueAt(i);
            changes.remove.add(subscription.getURLString());
        }

        api.uploadDeviceSubscriptionsChanges(changes, mUsername, GPodderUtils.getDeviceID(), new Callback<String>() {
            @Override
            public void success(String s, Response response) {
                Log.d(TAG, response.toString());
                //UpdatedUrls updatedUrls = new Gson
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d(TAG, error.toString());
            }
        });
    }

    private void authenticate(@NonNull final IGPodderAPI apiService, @Nullable Callback argCallback) {

        // Fetch data from: http://gpodder.net/clientconfig.json

        AuthenticationCallback callback = new AuthenticationCallback(argCallback);
        apiService.login(mUsername, callback);
    }

    private class AuthenticationCallback extends CallbackWrapper {

        public AuthenticationCallback(Callback argCallback) {
            super(argCallback);
        }

        @Override
        public void success(Object o, Response response) {
            mAuthenticated = true;

            int numHeaders = response.getHeaders().size();

            for (int i = 0; i < numHeaders; i++) {
                Header header = response.getHeaders().get(i);
                if (header.getName().equals("Set-Cookie") && header.getValue().startsWith("sessionid")) {
                    ApiRequestInterceptor.cookie = header.getValue().split(";")[0];
                }
            }
            super.success(o, response);
        }

        @Override
        public void failure(RetrofitError error) {
            mAuthenticated = false;
            Response response = error.getResponse();

            switch (response.getStatus()) {
                case 401: {
                    // 401 Unauthorized
                    break;
                }
                case 400: {
                    // If the client provides a cookie, but for a different username than the one given
                    break;
                }
            }

            Log.d(TAG, error.toString());
            super.failure(error);
        }

    }

    private class CallbackWrapper implements Callback {

        private Callback mCallback;

        public CallbackWrapper(@Nullable Callback argCallback) {
            mCallback = argCallback;
        }

        @Override
        public void success(Object o, Response response) {
            if (mCallback == null)
                return;

            mCallback.success(o, response);
        }

        @Override
        public void failure(RetrofitError error) {
            if (mCallback == null)
                return;

            mCallback.failure(error);
        }
    }
}
