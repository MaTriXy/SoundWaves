package org.bottiger.podcast.flavors.Analytics;

import android.support.annotation.Nullable;

/**
 * Created by apl on 21-02-2015.
 */
public interface IAnalytics {

    public enum EVENT_TYPE { PLAY,
                             PAUSE,
                             PLAY_FROM_PLAYLIST,
                             PLAY_FROM_FEEDVIEW,
                             PLAY_FROM_DISCOVERY,
                             SUBSCRIBE_TO_FEED,
                             OPML_IMPORT,
                             OPML_EXPORT,
                             DATABASE_UPGRADE,
                             MEDIA_ROUTING,
                             REFRESH_DURATION,
                             INTRO_DURATION
    };

    boolean doShare();

    public void startTracking();
    public void stopTracking();

    public void activityPause();
    public void activityResume();

    public void trackEvent(EVENT_TYPE argEvent);
    public void trackEvent(EVENT_TYPE argEvent, @Nullable Integer argValue);
}
