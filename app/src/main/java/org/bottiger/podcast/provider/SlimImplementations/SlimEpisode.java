package org.bottiger.podcast.provider.SlimImplementations;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.bottiger.podcast.model.events.EpisodeChanged;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.provider.ISubscription;
import org.bottiger.podcast.provider.base.BaseEpisode;
import org.bottiger.podcast.utils.PlaybackSpeed;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

/**
 * Created by apl on 21-04-2015.
 */
public class SlimEpisode extends BaseEpisode implements Parcelable {

    private String mTitle;
    private URL mUrl;
    private String mDescription;
    private long mDuration = -1;
    private int mPriority;
    private String mArtworkUrl;
    private long mOffset;
    private long mFilesize = 0;

    // Find a better method
    @Deprecated
    public SlimEpisode() {
    }

    public SlimEpisode(@NonNull String argTitle,
                       @NonNull URL argUrl,
                       @NonNull String argDescription) {
        mTitle = argTitle;
        mUrl = argUrl;
        mDescription = argDescription;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    @Nullable
    public URL getUrl() {
        return mUrl;
    }

    public boolean isNew() {
        return false;
    }

    @Nullable
    @Override
    public String getArtwork(@NonNull Context argContext) {
        if (mArtworkUrl == null)
            return null;

        return mArtworkUrl;
    }

    @Override
    @NonNull
    public String getDescription() {
        if (mDescription == null)
            return "";

        return mDescription;
    }

    @Override
    public String getAuthor() {
        return ""; // FIXME
    }

    @Override
    public long getDuration() {
        return mDuration;
    }

    @Override
    public int getPriority() {
        return mPriority;
    }

    @Override
    public ISubscription getSubscription(@NonNull Context argContext) {
        return null;
    }

    @Override
    public long getOffset() {
        return mOffset;
    }

    @Override
    public Date getDateTime() {
        return null;
    }

    @Override
    public Date getCreatedAt() {
        return new Date();
    }

    @Override
    public long getFilesize() { return mFilesize; }

    @Override
    public boolean isMarkedAsListened() { return false; }

    @Override
    public boolean isVideo() {
        return false;
    }

    @Override
    public void setIsVideo(boolean argIsVideo) {

    }

    public float getPlaybackSpeed(@NonNull Context argContext) {
        return PlaybackSpeed.UNDEFINED;
    }

    @Override
    public void setIsParsing(boolean argIsParsing, boolean argDoNotify) {
    }

    @Override
    public void setTitle(@NonNull String argTitle) {
        mTitle = argTitle;
    }

    @Override
    public void setUrl(@NonNull URL argUrl) {
        mUrl = argUrl;
    }

    @Override
    public void setArtwork(@NonNull String argUrl) {
        mArtworkUrl = argUrl;
    }

    @Override
    public void setDescription(@NonNull String argDescription) {
        mDescription = argDescription;
    }

    @Override
    public boolean setDuration(long argDurationMs) {
        mDuration = argDurationMs;
        return true;
    }

    @Override
    public void setPriority(@Nullable IEpisode argPrecedingItem, @NonNull Context argContext) {
        int precedingPriority = 0;
        if (argPrecedingItem != null) {
            precedingPriority = argPrecedingItem.getPriority();
        }

        mPriority = precedingPriority +1;
    }

    @Override
    public void removePriority() {
        mPriority = -1;
    }

    @Override
    public boolean canDownload() {
        return false;
    }

    @Override
    public void setPriority(int argPriority) {
        mPriority = argPriority;
    }

    @Override
    public void setOffset(@Nullable ContentResolver contentResolver, long i) {
        mOffset = i;
    }

    @Override
    public String getURL() {
        return mUrl.toString();
    }

    @Nullable
    @Override
    public Uri getFileLocation(@Location int argLocation) {
        if (argLocation == REQUIRE_LOCAL || mUrl == null)
            return null;

        return Uri.parse(mUrl.toString());
    }

    @Override
    public boolean isDownloaded() {
        return false;
    }

    public int describeContents() {
        return 0;
    }

    public void setFilesize(long argFilesize) {
        mFilesize = argFilesize;
    }

    @Override
    public void setURL(String argUrl) {
        try {
            setUrl(new URL(argUrl));
        } catch (MalformedURLException e) {
            return;
        }
    }

    @Override
    public void setAuthor(String argAuthor) {

    }

    @Override
    public void setPubDate(Date argPubDate) {

    }

    @Override
    public void setPageLink(String argPageLink) {

    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mTitle);
        out.writeString(mUrl.toString());
        out.writeString(mDescription);
        out.writeLong(mFilesize);
        out.writeLong(mDuration);
    }

    public static final Parcelable.Creator<SlimEpisode> CREATOR
            = new Parcelable.Creator<SlimEpisode>() {
        public SlimEpisode createFromParcel(Parcel in) {
            return new SlimEpisode(in);
        }

        public SlimEpisode[] newArray(int size) {
            return new SlimEpisode[size];
        }
    };

    private SlimEpisode(Parcel in) {
        mTitle = in.readString();
        try {
            mUrl = new URL(in.readString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        mDescription = in.readString();
        mFilesize = in.readLong();
        mDuration = in.readLong();
    }

    @Override
    protected void notifyPropertyChanged(@EpisodeChanged.Action int argAction) {

    }
}
