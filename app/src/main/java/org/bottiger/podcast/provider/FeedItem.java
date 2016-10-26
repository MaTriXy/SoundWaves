package org.bottiger.podcast.provider;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.model.Library;
import org.bottiger.podcast.model.events.DownloadProgress;
import org.bottiger.podcast.model.events.EpisodeChanged;
import org.bottiger.podcast.provider.base.BaseEpisode;
import org.bottiger.podcast.service.DownloadStatus;
import org.bottiger.podcast.utils.BitMaskUtils;
import org.bottiger.podcast.utils.PlaybackSpeed;
import org.bottiger.podcast.utils.SDCardManager;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.requery.android.database.sqlite.SQLiteDatabase;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

public class FeedItem extends BaseEpisode implements Comparable<FeedItem> {

	private static final String TAG = "FeedItem";

    // Se Subscription.java for details
    private static final int IS_VIDEO = 1;
	private static final int HAS_BEEN_DOWNLOADED_ONCE = (1 << 2);
	private static final int IS_FAVORITE = (1 << 3);

	/*
	 * Let's document these retared fields! They are totally impossible to guess
	 * the meaning of.
	 */

	/**
	 * Unique ID
	 */
	public long id;

	/**
	 * Unique ID of the file on the remote server
	 */
	@Deprecated
	public String remote_id;

	/**
	 * URL of the episode:
	 * http://podcast.dr.dk/P1/p1debat/2013/p1debat_1301171220.mp3
	 */
	private String url;

	/**
	 * Currently not persisted.
	 * A link to the show notes
	 */
	public String link_show_notes;

	/**
	 * Title of the episode
	 */
	private String title;

	/**
	 * Name of Publisher
	 */
	public String author;

	/**
	 * Date Published
	 */
    @Deprecated
	public String date;

    /**
     * Date Published
     */
    public long pub_date;

	/**
	 * Episode description in text
	 */
	public String content;

	/**
	 * Also an URL
	 */
	@Deprecated
	public String resource;

	/**
	 * Duration as String hh:mm:ss or mm:ss 02:23:34
	 */
	@Deprecated
	public String duration_string;

	/**
	 * Duration in milliseconds
	 */
	public long duration_ms;

	/**
	 * URL to relevant episode image
	 */
	public String image;

	/**
	 * Unique ID of the subscription the episode belongs to
	 */
	public long sub_id;

	/**
	 * Total size of the episode in bytes
	 */
	public long filesize;

	/**
	 * Size of the file on disk in bytes
	 */
	@Deprecated
	public long chunkFilesize;

	/**
	 * Filename of the episode on disk. sn209.mp3
	 */
	public String filename;

	/**
	 * Episode number.
	 */
	@Deprecated
	public int episodeNumber;

	/**
	 * Download reference ID as returned by
	 * http://developer.android.com/reference
	 * /android/app/DownloadManager.html#enqueue
	 * (android.app.DownloadManager.Request)
	 */
	@Deprecated
	private long downloadReferenceID;

	/**
	 * Flags for filtering downloaded items
	 */
	public Boolean isDownloaded;

	/**
	 * Last position during playback in ms Should match seekTo(int)
	 * http://developer
	 * .android.com/reference/android/media/NDKMediaPlayer.html#seekTo(int)
	 */
	public int offset;

	/**
	 * This was a deprecated status from before I forked the project.
	 * Now I will use it as a bitmask for keeping track of episode specific status related things,
	 * like "is this episode a video or not"
	 */
	public int status;

	@Deprecated
	public long failcount;
	// failcount is currently used for two purposes:
	// 1. counts the number of times we fail to download, and
	// when we exceed a predefined max, we pause the download.
	// 2. when an item is in the extended_player, failcount is used as
	// the order of the item in the list.

	/**
	 * Have to listened to this episode yet?
	 */
	public int listened;

	/**
	 * Priority in the playlist.
	 * * Default is 0.
	 * * Higher priority value is further down in the playlist.
	 * * The Priority 1 will always be the current playing episode.
	 * * The current playing episode doesn't need to have the priority 1. It could be a higher number or 0.
	 * * The priority 1 is assigned to episodes which are played "on demand".
	 * * Priorities above 1 are episode which are queued manually
	 * * If an episode is being played on demand it will replace other episodes with the priority one.
	 * * Other episodes, with priorities above 1, will just be pushed down the playlist.
	 */
	public int priority;

	/**
	 * Filesize as reported by the RSS feed
	 */
	public long length;

	/**
	 * The time the record in the database was updated the last time. measured
	 * in: System.currentTimeMillis()
	 */
	public long lastUpdate;

	/**
	 * The URI of the podcast episode
	 */
	@Deprecated
	public String uri;

	/**
	 * Title of the parent subscription
	 */
	public String sub_title;

	/**
	 * The time the episode was created locally
	 */
	public long created_at;

	static String[] DATE_FORMATS = {
			"EEE, dd MMM yyyy HH:mm:ss Z",
			"EEE, d MMM yy HH:mm z",
			"EEE, d MMM yyyy HH:mm:ss z",
			"EEE, d MMM yyyy HH:mm z",
			"d MMM yy HH:mm z",
			"d MMM yy HH:mm:ss z",
			"d MMM yyyy HH:mm z",
			"d MMM yyyy HH:mm:ss z",
			"yyyy-MM-dd HH:mm",
			"yyyy-MM-dd HH:mm:ss",
			"EEE,dd MMM yyyy HH:mm:ss Z"
	};

	public static String default_formatZ = "yyyy-MM-dd HH:mm:ss Z";
	public static String default_format = "yyyy-MM-dd HH:mm:ss";
    private static final SimpleDateFormat sFormat = new SimpleDateFormat(default_format, Locale.US);
	public static SimpleDateFormat sFormatZ = new SimpleDateFormat(default_formatZ, Locale.US);

	// Observables
	public PublishSubject<DownloadProgress> _downloadProgressChangeObservable = PublishSubject.create();

	private Date mDate = null;

	private boolean mIsParsing = false;

	public FeedItem() {
		reset();
	}

	public FeedItem(boolean isParsing) {
		mIsParsing = isParsing;
		reset();
	}

	public void reset() {
		url = null;
		title = null;
		author = null;
		date = null;
		content = null;
		resource = null;
		duration_string = null;
		filename = null;
		uri = null;
		image = null;
		remote_id = null;

		id = -1;
		offset = -1;
		status = -1;
		failcount = -1;
		length = -1;
		lastUpdate = -1;
		listened = -1;
		priority = 0;
		filesize = -1;
		chunkFilesize = -1;
		downloadReferenceID = -1;
		episodeNumber = -1;
		isDownloaded = null;
		duration_ms = -1;

		created_at = -1;
		sub_title = null;
		sub_id = -1;
        pub_date = -1;

	}

	public void setOffset(ContentResolver contentResolver, long i) {
		offset = (int) i;
        if (contentResolver != null) {
            lastUpdate = -1;
			//update(contentResolver);
        }

		notifyPropertyChanged();
	}

	/**
	 * Update the FeedItem in the database
	 */
	@Deprecated
	private void update(@NonNull Context argContext) {
		SoundWaves.getAppContext(argContext).getLibraryInstance().updateEpisode(this);
	}

	/**
	 * @return the PublishingDate as default_format = "yyyy-MM-dd HH:mm:ss Z"
	 */
	public String getDate() {
        if (pub_date > 0) {
            return sFormat.format(new Date(pub_date));
        }

		return this.date;
	}

	public long getSubscriptionId() {
		return this.sub_id;
	}

	@Nullable
	public Date getDateTime() {
        if (pub_date > 0) {
            return new Date(pub_date);
        }

        if (mDate == null) {
            try {
                mDate = sFormat.parse(date);
            } catch (ParseException pe) {

				try {
					mDate = sFormatZ.parse(date);
				} catch (ParseException e) {
					//VendorCrashReporter.report("Datestring must be parsable", date);
					return null;
				}
            } catch (Exception e) {
				return null;
			}
        }
        return mDate;
    }

	@Override
	public String toString() {
		return "Feed: " + title;
	}

	/**
	 * Deletes the downloaded file and updates the data in the database
	 * 
	 * @param argContext
	 * @return True of the file was deleted succesfully
	 */
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
	public boolean delFile(@NonNull Context argContext) throws SecurityException {

		if (SDCardManager.getSDCardStatus()) {
			try {
				setDownloaded(false);
				update(argContext);
				File file = new File(getAbsolutePath());
				if (file.exists() && file.delete()) {
					// FIXME Investigate this
                    //DownloadProgressPublisher.deleteEpisode(this);
					notifyPropertyChanged(EpisodeChanged.CHANGED);
					return true;
				}
			} catch (Exception e) {
				Log.w(TAG, "del file failed : " + filename + "  " + e);
			}
		}

		return false;

	}

    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
	public void downloadSuccess(ContentResolver contentResolver) throws SecurityException {
		filesize = getCurrentFileSize();
		notifyPropertyChanged();
	}

	public void endDownload(ContentResolver context) {
		lastUpdate = System.currentTimeMillis();
		notifyPropertyChanged();
	}

    public boolean isPersisted() {
        return id > 0;
    }

    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
	public long getCurrentFileSize() throws SecurityException {
		String file = null;
		try {
			file = getAbsolutePath();
		} catch (IOException e) {
			return -1;
		}
		if (file != null)
			return new File(file).length();
		return -1;
	}

	public String getFilename() {
		if (filename == null || filename.equals("")) {
			MessageDigest m;
			try {
				m = MessageDigest.getInstance("MD5");
				m.reset();
				m.update(this.getURL().getBytes());
				byte[] digest = m.digest();
				BigInteger bigInt = new BigInteger(1, digest);
				return bigInt.toString(16);
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return filename;
	}

	/**
	 * @param filename
	 *            the filename to set
	 */
	public void setFilename(String filename) {
		// Remove non ascii characters
		//this.filename = filename.replaceAll("[^\\x00-\\x7F]", "");
        this.filename = filename.replaceAll("[^(\\x41-\\x5A|\\x61-\\x7A|\\x2D|\\x2E|\\x30-\\x39|\\x5F)]", ""); // only a-z A-Z 0-9 .-_ http://www.asciitable.com/
		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	@RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
	public String getAbsolutePath() throws IOException, SecurityException {
		return SDCardManager.pathFromFilename(this);
	}

	public String getAbsoluteTmpPath(@NonNull Context argContext) throws IOException {
		return SDCardManager.pathTmpFromFilename(argContext, this);
	}

	@Nullable
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
	public Uri getFileLocation(@Location int argLocation) throws SecurityException {
		boolean isDownloaded = isDownloaded();

		if (!isDownloaded && argLocation == REQUIRE_LOCAL)
			return null;

		Uri uri;

		if (argLocation == REQUIRE_REMOTE) {
			uri = Uri.parse(getURL());
			return uri;
		}

		if (isDownloaded) {
			try {
				//Uri.Builder builder = new Uri.Builder();
				//builder.scheme("file");
				//builder.path("/" + getAbsolutePath());
				// uri = builder.build();

				File file = new File(getAbsolutePath());
				uri = Uri.fromFile(file);

				//uri = Uri.parse(getAbsolutePath());

				if (argLocation == PREFER_LOCAL)
					return uri;
			} catch (IOException ioe) {
				Log.w(TAG, "Failed to get local file path. " + ioe.toString()); // NoI18N
			}
		}

		return Uri.parse(getURL());
	}

    // in ms
	public long setPosition(long pos) {
		return setPosition(pos, false);
	}

	// in ms
	private static long lastPositionUpdate = System.currentTimeMillis();
	public long setPosition(long pos, boolean forceWrite) {

		if (offset == pos) {
			return this.offset;
		}

		long now = System.currentTimeMillis();

		// more than a second ago
		if (forceWrite || (offset == -1 && pos > offset) || (now - lastPositionUpdate > 1000)) {
			this.offset = (int) pos;
			notifyPropertyChanged(EpisodeChanged.PLAYING_PROGRESS);
			lastPositionUpdate = now;
		}

		return this.offset;
	}

	@Override
	public int compareTo(FeedItem another) {
		return another.date.compareTo(date);
		/*
		 * if (this.update > another.update) return 1; else if (this.update <
		 * another.update) return -1;
		 * 
		 * return 0;
		 */
	}
	
	public void setURL(String argUrl) {
		if (url != null && url.equals(argUrl))
			return;

		url = argUrl;
		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	@NonNull
	public String getURL() {
		String itemURL = "";
		if (url != null && url.length() > 1)
			itemURL = url;

		return itemURL;
	}

	public Date getCreatedAt() {
		return new Date(created_at);
	}

	public long getFilesize() {
		return filesize;
	}

	public void setFilesize(long argFilesize) {
		if (filesize  == argFilesize)
			return;

		filesize = argFilesize;
		notifyPropertyChanged(EpisodeChanged.PLAYING_PROGRESS);
	}

	/**
	 * 
	 * @return whether the item is downloaded to the phone
	 */
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
	public boolean isDownloaded() throws SecurityException {
        try {
            if (this.isDownloaded!=null)
                return this.isDownloaded;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

		File f = null;
		try {
			f = new File(getAbsolutePath());
		} catch (IOException e) {
			return false;
		}

		if (f == null)
            return false;

		if (f.exists())
			return true;

		isDownloaded = false;
		return isDownloaded;
	}

	/**
	 * @return true of the podcast has been listened too
	 */
	public boolean isMarkedAsListened() {
		return this.listened == 1;
	}

	public boolean isNew() {
		return pub_date >= Library.episodeNewThreshold();
	}

	/**
	 * @param argIsDownloaded the isDownloaded to set
	 */
	public void setDownloaded(boolean argIsDownloaded) {
		if (isDownloaded == argIsDownloaded && !hasBeenDownloadedOnce())
			return;

		isDownloaded = argIsDownloaded;
		setHasBeenDownloadedOnce(true);
		notifyPropertyChanged(EpisodeChanged.DOWNLOADED);
	}

	/**
	 * Mark the episode as listened
	 */
	public void markAsListened() {
		markAsListened(1);
	}
	
	/**
	 * If the episode has been listened to
	 */
	public boolean isListened() {
		return this.listened == 1;
	}

	/**
	 * @params 1 for listened. 0 for unlistened. -1 for undefined
	 */
	public void markAsListened(int hasBeenListened) {
		if (listened == hasBeenListened)
			return;

		listened = hasBeenListened;
		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	public int getListenedValue() {
		return listened;
	}

	/**
     * The duration of the mp3 (or whatever) in milliseconds.
	 * @return the duration in ms. -1 if unknown
	 */
	public long getDuration() {
        return duration_ms;
	}

    /**
     *
     * @param argDurationMs
     * @return true if the item was updated with a proper value
     */
    public boolean setDuration(long argDurationMs) {
        return setDuration(argDurationMs, true);
    }

    public boolean setDuration(long argDurationMs, boolean argOverride) {
		if (duration_ms == argDurationMs)
			return false;

        if (!argOverride) {
            if (duration_ms > 0)
                return false;
        }

        duration_ms = argDurationMs;
		notifyPropertyChanged(EpisodeChanged.CHANGED);

        return true;
    }

	/**
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		return result;
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null) {
			return false;
		}

		boolean isInstanceof = obj instanceof FeedItem;
		if (!isInstanceof) {
			return false;
		}

		FeedItem other = (FeedItem) obj;

		if (id > 0 && id == other.id) {
			return true;
		}

		return url != null && url.equals(other.url);

	}

	@Nullable
    public Subscription getSubscription(@NonNull Context argContext) {
		return SoundWaves.getAppContext(argContext).getLibraryInstance().getSubscription(sub_id);
    }

    @Override
    public long getOffset() {
        return offset;
    }

    public void removeFromPlaylist(@NonNull ContentResolver argContentResolver) {
        setPriority(0);
    }

	@Nullable
	public String getArtwork(@NonNull Context argContext) {
		String imageURL;

		if (!TextUtils.isEmpty(image))
			return image;

		Subscription subscription = getSubscription(argContext);

		if (subscription == null)
			return null;

		imageURL = subscription.imageURL;

		if (TextUtils.isEmpty(imageURL))
			return null;

		return imageURL;
	}

	public void setId(long argId) {
		if (id > 0 && argId != id) {
			VendorCrashReporter.report("duplicate id", "Can not assign a new id"); // NoI18N
			throw new IllegalStateException("Can not assign a new id"); // NoI18N
		}

		id = argId;
	}

	public long getId() {
		return id;
	}

	/**
	 * Put the current FeedItem after the argument in the playlist. If the given
	 * FeedItem is null the current FeedItem becomes the first item in the
	 * playlist
	 */
	public void setPriority(IEpisode precedingItem, Context context) {
		priority = precedingItem == null ? 1 : precedingItem.getPriority() + 1;
		increateHigherPriorities(precedingItem, context);
		notifyPropertyChanged();
	}

	public void trackEnded(ContentResolver contentResolver) {
		priority = 0;
		markAsListened();
	}

	@Override
	public void setPriority(int argPriority) {
		if (priority == argPriority)
			return;

		priority = argPriority;
		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	@Override
	public void removePriority() {
		priority = -1;
		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	@Override
	public boolean canDownload() {
		return true;
	}

	/**
	 * Increase the priority of all items in the playlist after the current item
	 * 
	 * @param precedingItem
	 * @param context
	 */
	private void increateHigherPriorities(IEpisode precedingItem,
			Context context) {
		String currentTime = String.valueOf(System.currentTimeMillis());
		String updateLastUpdate = ", " + ItemColumns.LAST_UPDATE + "="
				+ currentTime + " ";

		PodcastOpenHelper helper = PodcastOpenHelper.getInstance(context);//new PodcastOpenHelper(context);
		SQLiteDatabase db = helper.getWritableDatabase();
		String action = "UPDATE " + ItemColumns.TABLE_NAME + " SET ";
		String value = ItemColumns.PRIORITY + "=" + ItemColumns.PRIORITY + "+1"
				+ updateLastUpdate;
		String where = "WHERE " + ItemColumns.PRIORITY + ">=" + this.priority
				+ " AND " + ItemColumns.PRIORITY + "<> 0 AND "
				+ ItemColumns._ID + "<> " + this.id;
		String sql = action + value + where;

		String actionCurrent = "UPDATE " + ItemColumns.TABLE_NAME + " SET ";
		String valueCurrent = ItemColumns.PRIORITY + "=" + this.priority
				+ updateLastUpdate;
		String whereCurrent = "WHERE " + ItemColumns._ID + "==" + this.id;
		String sqlCurrent = actionCurrent + valueCurrent + whereCurrent;

		db.beginTransaction();
		try {
			db.execSQL(sql);
			db.execSQL(sqlCurrent);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	/**
	 * 
	 */
	public int getPriority() {
		return this.priority;
	}

	/**
	 * @return the episodeNumber
	 */
	public int getEpisodeNumber() {
		return episodeNumber;
	}

	/**
	 * @return the title
	 */
	public String getTitle() {
        return title != null ? title : "";
	}

    @Override
    public URL getUrl() {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null; // should never happen
    }

    @Override
	@NonNull
    public String getDescription() {
		if (content == null)
			return "";

        return content;
    }

	/**
	 * @param argTitle the title to set
	 */
	public void setTitle(String argTitle) {
		if (title != null && title.equals(argTitle))
			return;

		title = argTitle;
		notifyPropertyChanged();
	}

    @Override
    public void setUrl(@NonNull URL argUrl) {
		if(url != null && url.equals(argUrl.toString()))
			return;

		url = argUrl.toString();
		notifyPropertyChanged();
    }

    @Override
    public void setArtwork(@NonNull String argUrl) {
		if (image != null && image.equals(argUrl))
			return;

		image = argUrl;
		notifyPropertyChanged(EpisodeChanged.CHANGED);
    }

    public String getAuthor() {
		return author;
	}

	public float getPlaybackSpeed(@NonNull Context argContext) {
		if (getSubscription(argContext) == null)
			return PlaybackSpeed.UNDEFINED;

		return getSubscription(argContext).getPlaybackSpeed();
	}

	public void setAuthor(String argAuthor) {
		if (author != null && author.equals(argAuthor))
			return;

		author = argAuthor;
		notifyPropertyChanged();
	}

	/**
	 * Get the time this item was last updated
	 * 
	 * @return
	 */
	public long getLastUpdate() {
		return this.lastUpdate;
	}

	public long lastModificationDate() {
		return this.lastUpdate;
	}

	public void queue(Context context) {

		String sqlQueue = "update " + ItemColumns.TABLE_NAME + " set "
				+ ItemColumns.PRIORITY + " = (select max("
				+ ItemColumns.PRIORITY + ") from " + ItemColumns.TABLE_NAME
				+ ")+1 where " + ItemColumns._ID + " =" + this.id;

		DatabaseHelper dbHelper = new DatabaseHelper();
		dbHelper.executeSQL(context, sqlQueue);
	}

	public void setFeed(@NonNull Subscription argSubscription) {
		if (sub_id == argSubscription.getId())
			return;

		this.sub_id = argSubscription.getId();
		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	public void setPubDate(@Nullable Date argPubDate) {
        if (argPubDate == null)
            return;

        long pubDate = argPubDate.getTime();

        if (pubDate == pub_date)
            return;

        pub_date = pubDate;

        /*
        try {
            this.date = sFormat.format(parseRFC822Date);
        } catch (NullPointerException npe) {
            //Log.e("Date parsing error:" , "Could not parse: " + parseRFC822Date.toString());
            this.date = sFormat.format(new Date());
        }
        */
        notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	public void setDescription(@Nullable String argHTMLDescription) {
		if (argHTMLDescription == null) {
			return;
		}

		this.content = Html.fromHtml(argHTMLDescription).toString().trim();

		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	public void setPageLink(String argLink) {
		if (link_show_notes != null && link_show_notes.equals(argLink))
			return;

		link_show_notes = argLink;
		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	public int getStatus() {
		return status;
	}

    public boolean isVideo() {
        if (!BitMaskUtils.IsBitmaskInitialized(status))
            return false;

        return IsSettingEnabled(IS_VIDEO);
    }

	public boolean hasBeenDownloadedOnce() {
		if (!BitMaskUtils.IsBitmaskInitialized(status))
			return false;

		return IsSettingEnabled(HAS_BEEN_DOWNLOADED_ONCE);
	}

	public boolean isFavorite() {
		if (!BitMaskUtils.IsBitmaskInitialized(status))
			return false;

		return IsSettingEnabled(IS_FAVORITE);
	}

	private void setHasBeenDownloadedOnce(boolean argHasBeenDownloaded) {
		status = initStatus();
		status |= HAS_BEEN_DOWNLOADED_ONCE;

		if (argHasBeenDownloaded)
			status |= HAS_BEEN_DOWNLOADED_ONCE;
		else
			status &= ~HAS_BEEN_DOWNLOADED_ONCE;

		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

    public void setIsVideo(boolean argIsVideo) {
        status = initStatus();
        status |= IS_VIDEO;

        if (argIsVideo)
            status |= IS_VIDEO;
        else
            status &= ~IS_VIDEO;

		notifyPropertyChanged(EpisodeChanged.CHANGED);
    }

	public void setIsFavorite(boolean argIsFavorite) {
		status = initStatus();

		if (argIsFavorite)
			status |= IS_FAVORITE;
		else
			status &= ~IS_FAVORITE;

		notifyPropertyChanged(EpisodeChanged.CHANGED);
	}

	private int initStatus() {
		if (status < 0) {
			return 0;
		} else {
			return status;
		}
	}

    private boolean IsSettingEnabled(int setting) {
        return BitMaskUtils.IsBitSet(status, setting);
    }

	/**
	 * This can be used to turn no notifications of changes on the episode without
	 * emitting an event.
	 *
	 * @param argIsParsing
	 * @param doNotify
     */
	public void setIsParsing(boolean argIsParsing, boolean doNotify) {
		mIsParsing = argIsParsing;

		if (!mIsParsing && doNotify)
			notifyPropertyChanged(EpisodeChanged.PARSED);
	}

	public boolean IsParsing() {
		return mIsParsing;
	}

	public void downloadAborted() {
		progressChanged = new DownloadProgress(this, DownloadStatus.ERROR, 0);
		_downloadProgressChangeObservable.onNext(progressChanged);
	}

	DownloadProgress progressChanged;
	long lastUpdate2 = System.currentTimeMillis();

	protected void notifyPropertyChanged(@EpisodeChanged.Action int argAction) {
        if (mIsParsing)
            return;

		if (argAction != EpisodeChanged.PLAYING_PROGRESS) {
			EpisodeChanged ec = new EpisodeChanged(getId(), getURL(), argAction);
			SoundWaves.getRxBus().send(ec);
		}

		if (argAction == EpisodeChanged.DOWNLOADED) {
			boolean isDownloaded = false;
            try {
                isDownloaded = isDownloaded();
            } catch (SecurityException se) {
                VendorCrashReporter.report("IsDownloaded", "SecurityException");
            }
			DownloadStatus downloadStatus = isDownloaded ? DownloadStatus.DONE : DownloadStatus.DELETED;
			int progress = isDownloaded ? 100 : 0;
			progressChanged = new DownloadProgress(this, downloadStatus, progress);
			Log.d(TAG, "Notify progress changed: FeedItemHash: " + hashCode() + " progress: DONE");
			_downloadProgressChangeObservable.onNext(progressChanged);
		}

		// !isDownloaded() &&
		if (argAction == EpisodeChanged.DOWNLOAD_PROGRESS && (System.currentTimeMillis()-lastUpdate2)>160) {
			lastUpdate2 = System.currentTimeMillis();
			progressChanged = new DownloadProgress(this, DownloadStatus.DOWNLOADING, (int)getProgress());
			Log.d(TAG, "Notify progress changed: FeedItemHash: " + hashCode() + " progress: " + (int)getProgress());
			_downloadProgressChangeObservable.onNext(progressChanged);
		}
	}

	@Deprecated
	private void notifyPropertyChanged() {
		if (!mIsParsing) {
			SoundWaves.getRxBus().send(new EpisodeChanged(getId(), getURL(), EpisodeChanged.CHANGED));
		}
	}

	private Observable<DownloadProgress> _getDownloadProgressObservable(DownloadProgress argDownloadProgress) {
		return Observable.just(argDownloadProgress).map(new Func1<DownloadProgress, DownloadProgress>() {
			@Override
			public DownloadProgress call(DownloadProgress aBoolean) {
				DownloadProgress progressChanged = new DownloadProgress(FeedItem.this, DownloadStatus.DOWNLOADING, (int)getProgress());
				return progressChanged;
			}
		});
	}
}
