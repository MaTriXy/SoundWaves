package org.bottiger.podcast.playlist.filters;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.bottiger.podcast.R;
import org.bottiger.podcast.provider.ItemColumns;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by apl on 29-04-2015.
 */
public class SubscriptionFilter implements IPlaylistFilter, SharedPreferences.OnSharedPreferenceChangeListener {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SHOW_ALL, SHOW_NONE, SHOW_SELECTED})
    public @interface Mode {}
    public static final int SHOW_ALL = 1;
    public static final int SHOW_NONE = 2;
    public static final int SHOW_SELECTED = 3;

    private final String mModeKey;
    private final String mValueKey;
    private final String mListenedKey;

    private static final String SEPARATOR = ",";

    private @Mode int mDefaultFilterType = SHOW_ALL;
    private @Mode int mFilterType = mDefaultFilterType;
    private boolean mShowListened;

    private final HashSet<Long> mSubscriptions = new HashSet<>();
    private ReentrantLock mLock = new ReentrantLock();

    public SubscriptionFilter(@NonNull Context argContext) {
        Context context = argContext.getApplicationContext();
        Resources resources = context.getResources();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        mModeKey = resources.getString(R.string.pref_playlist_subscriptions_key);
        mValueKey = resources.getString(R.string.pref_playlist_subscriptions_values_key);
        mListenedKey = resources.getString(R.string.pref_playlist_show_listened_key);
        boolean listenedDefault = resources.getBoolean(R.bool.pref_show_listened_default);

        mShowListened = sharedPreferences.getBoolean(mListenedKey, listenedDefault);
        int mode = sharedPreferences.getInt(mModeKey, mDefaultFilterType);
        switch (mode) {
            case SHOW_ALL:{
                mFilterType = SHOW_ALL;
                break;
            }
            case SHOW_NONE:{
                mFilterType = SHOW_NONE;
                break;
            }
            case SHOW_SELECTED: {
                mFilterType = SHOW_SELECTED;
                break;
            }
        }

        try {
            mLock.lock();
            onSharedPreferenceChanged(sharedPreferences, mValueKey);
            onSharedPreferenceChanged(sharedPreferences, mListenedKey);
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        } finally {
            mLock.unlock();
        }
    }

    public void add(Long argID) {
        try {
            mLock.lock();

            if (mSubscriptions.contains(argID)) {
                return;
            }

            mSubscriptions.add(argID);
        } finally {
            mLock.unlock();
        }
    }

    public boolean remove(Long argID) {
        try {
            mLock.lock();

            if (mSubscriptions.contains(argID)) {
                mSubscriptions.remove(argID);
                return true;
            }
            return false;
        } finally {
            mLock.unlock();
        }
    }

    public boolean isShown(Long argID) {
        try {
            mLock.lock();

            if (mFilterType == SHOW_ALL)
                return true;

            if (mFilterType == SHOW_NONE)
                return false;

            return mSubscriptions.contains(argID);
        } finally {
            mLock.unlock();
        }
    }

    public @Mode int getMode() {
        return mFilterType;
    }

    public void setMode(@Mode int argMode, Context argContext) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(argContext);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String value = toPreferenceValue(mSubscriptions);

        mFilterType = argMode;
        editor.putInt(mModeKey, argMode);
        editor.putString(mValueKey, value);

        editor.apply();
    }

    public boolean showListened() {
        return mShowListened;
    }

    public String toSQL() {

        String listened = (mShowListened) ? " 1 " : " " + ItemColumns.LISTENED + "<= 0 ";

        String sql = "";

        try {
            mLock.lock();

            if (mFilterType == SHOW_NONE) {
                return "(" + ItemColumns.TABLE_NAME + "." + ItemColumns.PRIORITY + " > 0)";
                //return " 0 "; // false for all subscriptions
            }

            if (mFilterType == SHOW_ALL) {
                return listened; // true for all subscriptions
            }

            if (!mSubscriptions.isEmpty()) {

                sql = " " + ItemColumns.SUBS_ID + " IN (";
                sql += TextUtils.join(",", mSubscriptions);
                sql += ")";
                sql += " AND " + listened;
            }
        } finally {
            mLock.unlock();
        }
        return sql;
    }

    public void clear() {
        mSubscriptions.clear();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mValueKey.equals(key)) {
            try {
                mLock.lock();
                String prefrenceValue = sharedPreferences.getString(key, "");
                mSubscriptions.clear();

                if (TextUtils.isEmpty(prefrenceValue)) {
                    clear();
                    return;
                }

                if (prefrenceValue.contains(SEPARATOR)) {
                    for (Long subscriptionId : parsePreference(prefrenceValue)) {
                        mSubscriptions.add(subscriptionId);
                    }
                    return;
                }

                Long intValue = Long.valueOf(prefrenceValue);

                // in case there is only one subscription in the list
                if (intValue > 0) {
                    mSubscriptions.add(intValue);
                    return;
                }
            } finally {
                mLock.unlock();
            }
        } else if (mListenedKey.equals(key)) {
            mShowListened = sharedPreferences.getBoolean(mListenedKey, mShowListened);
        }
    }

    private HashSet<Long> parsePreference(@Nullable String argPrefValue) {
        HashSet<Long> longs = new HashSet<>();
        String[] strings = TextUtils.split(argPrefValue, SEPARATOR);
        for (int i = 0; i < strings.length; i++) {
            Long value = Long.valueOf(strings[i]);
            longs.add(value);
        }
        return longs;
    }

    private String toPreferenceValue(@NonNull HashSet<Long> argLongs) {
        return TextUtils.join(SEPARATOR, mSubscriptions);
    }
}
