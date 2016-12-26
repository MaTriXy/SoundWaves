package org.bottiger.podcast.model.events;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by aplb on 13-10-2015.
 */
public class EpisodeChanged implements ItemChanged {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ADDED, REMOVED, CHANGED, PARSED, PLAYING_PROGRESS, DOWNLOADED, DOWNLOAD_PROGRESS, FILE_DELETED})
    public @interface Action {}
    public static final int ADDED = 1;
    public static final int REMOVED = 2;
    public static final int CHANGED = 3;
    public static final int PARSED = 4;
    public static final int PLAYING_PROGRESS = 5;
    public static final int DOWNLOADED = 6;
    public static final int DOWNLOAD_PROGRESS = 7;
    public static final int FILE_DELETED = 8;

    private long id;
    private String url;
    private @Action int action;

    public EpisodeChanged(long argId,
                          @NonNull String argUrl,
                          @Action int argAction) {
        id = argId;
        url = argUrl;
        action = argAction;
    }

    public long getId() {
        return id;
    }

    public @Action int getAction() {
        return action;
    }

    public String getUrl() {
        return url;
    }
}
