package org.bottiger.podcast.views.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Challenge;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.framed.FrameReader;

import org.bottiger.podcast.R;
import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.model.Library;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.utils.AuthenticationUtils;
import org.bottiger.podcast.utils.JSonUtils;
import org.bottiger.podcast.utils.PreferenceHelper;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import rx.Observable;
import rx.Observer;
import rx.functions.Func2;

/**
 * Created by aplb on 31-12-2015.
 */
public class DialogFeedAuthentication extends DialogFragment {

    private static final String TAG = "DialogFeedAuth";
    private static final String sUrlKey = "urlkey";

    private Activity mActivity;

    OkHttpClient client = new OkHttpClient();

    private rx.Subscription _subscription = null;

    private Observable<CharSequence> _usernameChangeObservable;
    private Observable<CharSequence> _passwordChangeObservable;

    public static DialogFeedAuthentication newInstance(@NonNull String argUrl) {
        DialogFeedAuthentication frag = new DialogFeedAuthentication();
        Bundle args = new Bundle();
        args.putString(sUrlKey, argUrl);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mActivity = getActivity();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        final String prefKey = mActivity.getResources().getString(R.string.feed_authentication_data_key);
        final HashMap<String, List<String>> prefCredentials = JSonUtils.getComplexObject(prefKey, prefs);

        //final String url = "http://www.httpwatch.com/httpgallery/authentication/authenticatedimage/default.aspx?0.2779941682013324";
        final String url = getArguments().getString(sUrlKey, "");

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

        // Get the layout inflater
        LayoutInflater inflater = mActivity.getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_authenticate_feed, null);

        final Subscription subscription = SoundWaves.getLibraryInstance().getSubscription(url);

        // bind things
        final ContentLoadingProgressBar contentLoadingProgressBar = (ContentLoadingProgressBar) view.findViewById(R.id.test_credentials_loading);
        final TextView usernameTextView = (TextView) view.findViewById(R.id.authenticate_feed_username);
        final TextView passwordTextView = (TextView) view.findViewById(R.id.authenticate_feed_password);

        final Button testCredentials    = (Button) view.findViewById(R.id.test_credentials_button);
        final TextView textResult       = (TextView) view.findViewById(R.id.test_credentials_result);

        _usernameChangeObservable   = RxTextView.textChanges(usernameTextView);//.skip(1);
        _passwordChangeObservable   = RxTextView.textChanges(passwordTextView);//.skip(1);

        contentLoadingProgressBar.hide();

        testCredentials.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(url))
                    return;

                AuthenticationUtils.initCredentialTest(contentLoadingProgressBar, textResult);

                final String username = usernameTextView.getText().toString();
                final String password = passwordTextView.getText().toString();

                ResponseCallback responseCallback = new ResponseCallback() {
                    @Override
                    public void run() {
                        Handler mainHandler = new Handler(mActivity.getMainLooper());

                        Runnable myRunnable = new Runnable() {
                            @Override
                            public void run() {
                                AuthenticationUtils.setState(response.isSuccessful(), mActivity, contentLoadingProgressBar, textResult);
                            }
                        };
                        mainHandler.post(myRunnable);
                    }
                };

                FailureCallback failureCallback = new FailureCallback() {
                    @Override
                    public void run() {
                        Handler mainHandler = new Handler(mActivity.getMainLooper());

                        Runnable myRunnable = new Runnable() {
                            @Override
                            public void run() {
                                AuthenticationUtils.setState(false, mActivity, contentLoadingProgressBar, textResult);
                            }
                        };
                        mainHandler.post(myRunnable);
                    }
                };

                if (subscription != null)
                    testCredentials(client, subscription, username, password, responseCallback, failureCallback);
            }
        });

        _subscription = Observable.combineLatest(
                _usernameChangeObservable,
                _passwordChangeObservable,
                new Func2<CharSequence, CharSequence, Boolean>() {
                    @Override
                    public Boolean call(CharSequence newEmail,
                                        CharSequence newPassword) {

                        boolean credentialsValid = AuthenticationUtils.validateCredentials(newEmail.toString(), newPassword.toString());

                        return credentialsValid;

                    }
                })//
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onCompleted() {
                        Log.d(TAG, "completed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "there was an error");
                    }

                    @Override
                    public void onNext(Boolean formValid) {
                        AuthenticationUtils.disableButton(testCredentials, formValid);
                    }
                });

        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                final String username = usernameTextView.getText().toString();
                final String password = passwordTextView.getText().toString();

                LinkedList<String> credentials = new LinkedList<>();
                credentials.add(username);
                credentials.add(password);

                if (prefCredentials != null) {
                    prefCredentials.put(url, credentials);
                    JSonUtils.setComplexObject(prefKey, prefs, prefCredentials);
                }

                if (subscription != null)
                    testCredentials(client, subscription, username, password, null, null);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        return builder.create();
    }

    @Override
    public void onDestroyView() {
        if (_subscription != null && !_subscription.isUnsubscribed()) {
            _subscription.unsubscribe();
        }
        super.onDestroyView();
    }

    private static void testCredentials(@NonNull OkHttpClient argClient,
                                        final @NonNull Subscription argSubscription,
                                        @Nullable final String argUsername,
                                        final @Nullable String argPassword,
                                        final @Nullable ResponseCallback argResponseCallback,
                                        final @Nullable FailureCallback argFailureCallback) {
        argClient.setAuthenticator(new Authenticator() {
            @Override
            public Request authenticate(Proxy proxy, Response response) throws IOException {
                String credential = Credentials.basic(argUsername, argPassword);
                return response.request().newBuilder().header("Authorization", credential).build();
            }

            @Override
            public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
                return null;
            }
        });

        Request request = new Request.Builder()
                .url(argSubscription.getUrl())
                .build();

        argClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                if (argFailureCallback != null)
                    argFailureCallback.run(request);
                //AuthenticationUtils.setState(false, mActivity, contentLoadingProgressBar, textResult);
                argSubscription.setAuthenticationWorking(false);
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (argResponseCallback != null)
                    argResponseCallback.run(response);
                //AuthenticationUtils.setState(response.isSuccessful(), mActivity, contentLoadingProgressBar, textResult);
                argSubscription.setAuthenticationWorking(response.isSuccessful());
            }
        });
    }

    abstract class ResponseCallback implements Runnable {

        Response response;

        public void run(@NonNull Response argResponse) {
            response = argResponse;
            run();
        }
    }

    abstract class FailureCallback implements Runnable {

        Request request;

        public void run(@NonNull Request argRequest) {
            request = argRequest;
            run();
        }
    }
}
