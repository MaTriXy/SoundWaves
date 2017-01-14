package org.bottiger.podcast.views.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TextView;

import org.bottiger.podcast.R;
import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.player.GenericMediaPlayerInterface;
import org.bottiger.podcast.playlist.Playlist;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.provider.ISubscription;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.service.PlayerService;
import org.bottiger.podcast.utils.PlaybackSpeed;
import org.bottiger.podcast.utils.rxbus.RxBusSimpleEvents;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by Arvid on 8/30/2015.
 */
public class DialogPlaybackSpeed extends DialogFragment {

    private static final String TAG = "DialogPlaybackSpeed";

    @IntDef({EPISODE, SUBSCRIPTION, GLOBAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Scope {}

    public static final int EPISODE = 0;
    public static final int SUBSCRIPTION = 1;
    public static final int GLOBAL = 2;

    private static final String scopeName = "Scope";

    private Activity mActivity;

    private Button mIncrementButton;
    private Button mDecrementButton;
    private TextView mCurrentSpeedView;

    private SwitchCompat mRemoveSilenceSwitch;
    private SwitchCompat mAutomaticGainSwitch;

    private RadioButton mRadioEpisode;
    private RadioButton mRadioSubscription;
    private RadioButton mRadioGlobal;

    private float mInitialPlaybackSpeed = PlaybackSpeed.UNDEFINED;
    private float mCurrentSpeed = mInitialPlaybackSpeed;

    private CompositeSubscription mRxSubscriptions = new CompositeSubscription();

    public static DialogPlaybackSpeed newInstance(@Scope int argScope) {
        DialogPlaybackSpeed frag = new DialogPlaybackSpeed();
        Bundle args = new Bundle();
        args.putInt(scopeName, argScope);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mActivity = getActivity();

        @Scope int scope = getArguments().getInt(scopeName, EPISODE);

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        // Get the layout inflater
        LayoutInflater inflater = mActivity.getLayoutInflater();

        View view = inflater.inflate(R.layout.dialog_playback_speed, null);

        // bind stuff
        mIncrementButton = (Button) view.findViewById(R.id.playback_increment_button);
        mDecrementButton = (Button) view.findViewById(R.id.playback_decrement_button);
        mCurrentSpeedView = (TextView) view.findViewById(R.id.playback_speed_value);

        mRemoveSilenceSwitch = (SwitchCompat) view.findViewById(R.id.remove_silence_switch);
        mAutomaticGainSwitch = (SwitchCompat) view.findViewById(R.id.automatic_gain_control_switch);

        mRadioEpisode = (RadioButton) view.findViewById(R.id.radio_playback_speed_episode);
        mRadioSubscription = (RadioButton) view.findViewById(R.id.radio_playback_speed_subscription);
        mRadioGlobal = (RadioButton) view.findViewById(R.id.radio_playback_speed_global);

        final PlayerService ps = PlayerService.getInstance();
        final Playlist playlist = SoundWaves.getAppContext(getActivity()).getPlaylist();
        final GenericMediaPlayerInterface player = SoundWaves.getAppContext(getActivity()).getPlayer();

        final IEpisode episode = playlist.first();

        if (episode != null) {
            ISubscription isubscription = episode.getSubscription(getActivity());

            scope = GLOBAL;

            if (isubscription instanceof Subscription) {
                if (((Subscription)isubscription).hasCustomPlaybackSpeed()) {
                    scope = SUBSCRIPTION;
                }
            }

            mInitialPlaybackSpeed = episode.getPlaybackSpeed(getActivity());
        }

        if (mInitialPlaybackSpeed == PlaybackSpeed.UNDEFINED) {
            mInitialPlaybackSpeed = player.getCurrentSpeedMultiplier();
            scope = GLOBAL;
        }

        setSpeed(player, mInitialPlaybackSpeed);

        mRxSubscriptions
                .add(SoundWaves.getRxBus().toObserverable()
                        .ofType(RxBusSimpleEvents.PlaybackEngineChanged.class)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<RxBusSimpleEvents.PlaybackEngineChanged>() {
                            @Override
                            public void call(RxBusSimpleEvents.PlaybackEngineChanged playbackEngineChanged) {
                                Log.d(TAG, "new playback: " + playbackEngineChanged.speed);
                                setSpeed(player, playbackEngineChanged.speed);
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                VendorCrashReporter.report("subscribeError" , throwable.toString());
                                Log.d(TAG, "error: " + throwable.toString());
                            }
                        }));

        setSpeed(player, mInitialPlaybackSpeed);
        checkRadioButton(scope);
        mRemoveSilenceSwitch.setChecked(player.doRemoveSilence());
        mAutomaticGainSwitch.setChecked(player.doAutomaticGainControl());

        mIncrementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float newPlaybackSpeed = mInitialPlaybackSpeed + PlaybackSpeed.sSpeedIncrements;
                Log.d(TAG, "increment playback speed to: " + newPlaybackSpeed);
                setSpeed(player, newPlaybackSpeed);
                //player.setPlaybackSpeed(newPlaybackSpeed);
            }
        });

        mDecrementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float newPlaybackSpeed = mInitialPlaybackSpeed - PlaybackSpeed.sSpeedIncrements;
                Log.d(TAG, "dencrement playback speed to: " + newPlaybackSpeed);
                setSpeed(player, newPlaybackSpeed);
                //player.setPlaybackSpeed(newPlaybackSpeed);
            }
        });

        mRemoveSilenceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "remove silence: " + isChecked);
                player.setRemoveSilence(isChecked);
            }
        });

        mAutomaticGainSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "automatic gain: " + isChecked);
                player.setAutomaticGainControl(isChecked);
            }
        });



        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                setSpeed(player, mInitialPlaybackSpeed);

                if (mRadioSubscription.isChecked() && ps != null) {
                    if (episode instanceof FeedItem) {
                        ISubscription subscription = episode.getSubscription(mActivity);
                        if (subscription instanceof Subscription) {
                            Subscription subscription1 = (Subscription)subscription;
                            int storedSpeed = Math.round(mInitialPlaybackSpeed * 10);
                            subscription1.setPlaybackSpeed(storedSpeed);
                        }
                    }
                }

                if (mRadioGlobal.isChecked()) {
                    /*
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
                    String key = getResources().getString(R.string.soundwaves_player_playback_speed_key);
                    int storedSpeed = Math.round(mInitialPlaybackSpeed * 10);
                    prefs.edit().putInt(key, storedSpeed).apply();
                    */
                    PlaybackSpeed.setGlobalPlaybackSpeed(mActivity, mInitialPlaybackSpeed);
                }
            }
        });

        return builder.create();
    }

    @Override
    public void onDestroyView() {
        mRxSubscriptions.unsubscribe();
        super.onDestroyView();
    }

    private void checkRadioButton(@Scope int argScope) {
        RadioButton activeButton = null;
        switch (argScope) {
            case EPISODE: {
                activeButton = mRadioEpisode;
                break;
            }
            case SUBSCRIPTION: {
                activeButton = mRadioSubscription;
                break;
            }
            case GLOBAL: {
                activeButton = mRadioGlobal;
                break;
            }
        }

        if (activeButton == null) {
            Log.wtf(TAG, "activeButton should never be null");
            return;
        }

        activeButton.setChecked(true);
    }

    private void setSpeed(@NonNull GenericMediaPlayerInterface argPlayer, float argNewSpeed) {
        if (Math.abs(mCurrentSpeed - argNewSpeed) < 0.01)
            return;

        if (argNewSpeed > PlaybackSpeed.sSpeedMaximum || argNewSpeed < PlaybackSpeed.sSpeedMinimum)
            return;

        argNewSpeed = Math.round(argNewSpeed*10)/10.0f;

        mInitialPlaybackSpeed = argNewSpeed;
        mCurrentSpeedView.setText(getSpeedText(mInitialPlaybackSpeed));

        mIncrementButton.setEnabled(argNewSpeed < PlaybackSpeed.sSpeedMaximum);
        mDecrementButton.setEnabled(argNewSpeed > PlaybackSpeed.sSpeedMinimum);

        mCurrentSpeed = argNewSpeed;
        argPlayer.setPlaybackSpeed(argNewSpeed);
    }

    private String getSpeedText(float argSpeed) {
        return getString(R.string.speed_multiplier, argSpeed);
    }

}
