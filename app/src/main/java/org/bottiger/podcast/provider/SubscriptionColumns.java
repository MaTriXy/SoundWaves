package org.bottiger.podcast.provider;

import android.content.ContentValues;
import android.database.SQLException;
import android.net.Uri;
import android.provider.BaseColumns;

public class SubscriptionColumns implements BaseColumns {

	public static final Uri URI = Uri.parse("content://"
			+ PodcastProvider.AUTHORITY + "/subscriptions");

	public static final String TABLE_NAME = "subscriptions";

	public static final String URL = "url";

	public static final String LINK = "link";

	public static final String TITLE = "title";

	public static final String DESCRIPTION = "description";
	
	public static final String IMAGE_URL = "image";

	public static final String LAST_UPDATED = "last_updated";

	public static final String LAST_ITEM_UPDATED = "last_item_updated";

	public static final String FAIL_COUNT = "fail";

	public static final String STATUS = "status";

	public static final String COMMENT = "comment";
	public static final String RATING = "rating";
	public static final String USERNAME = "user";
	public static final String PASSWORD = "pwd";
	public static final String SUBSCRIBED_AT = "server_id";
	public static final String REMOTE_ID = "sync";
	public static final String AUTO_DOWNLOAD = "auto_download";	
	public static final String PLAYLIST_POSITION = "playlist_id";		

    public static final String PRIMARY_COLOR = "primary_color";
    public static final String PRIMARY_TINT_COLOR = "primary_tint_color";
    public static final String SECONDARY_COLOR = "secondary_color";

	public static final String NEW_EPISODES = "new_episodes";
	public static final String EPISODE_COUNT = "episodes_count";

	public static final String SETTINGS = "settings";

	public static final String DEFAULT_SORT_ORDER = _ID + " ASC";
	public static final String sql_create_table = "CREATE TABLE " 
		+ TABLE_NAME + " (" 
		+ _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " 
		+ URL + " VARCHAR(1024) NOT NULL UNIQUE, "
		+ LINK + " VARCHAR(256), " 
		+ TITLE	+ " VARCHAR(128), " 
		+ DESCRIPTION + " TEXT, "
		+ LAST_UPDATED + " INTEGER, " 
		+ LAST_ITEM_UPDATED + " INTEGER, "
		+ FAIL_COUNT + " INTEGER, " 
		+ STATUS + " INTEGER, " 
		+ COMMENT + " TEXT, " 
		+ RATING + " INTEGER DEFAULT 0, "
		+ USERNAME	+ " VARCHAR(32) , " 
		+ PASSWORD + " VARCHAR(32) , " 
		+ SUBSCRIBED_AT + " INTEGER , "
		+ REMOTE_ID + " VARCHAR(128), "
		+ AUTO_DOWNLOAD + " INTEGER , "
		+ PLAYLIST_POSITION + " INTEGER , " 	
		+ IMAGE_URL + " VARCHAR(1024), "
        + PRIMARY_COLOR + " INTEGER DEFAULT 0 , "
        + PRIMARY_TINT_COLOR + " INTEGER DEFAULT 0 , "
		+ SECONDARY_COLOR + " INTEGER DEFAULT 0 , "
        + SETTINGS + " INTEGER DEFAULT -1 , "
		+ NEW_EPISODES + " INTEGER DEFAULT 0 , "
		+ EPISODE_COUNT + " INTEGER DEFAULT 0 "
		+ ");";

	public static final String sql_index_subs_url = "CREATE UNIQUE INDEX IDX_"
			+ TABLE_NAME + "_" + URL + " ON " + TABLE_NAME + " (" + URL + ");";

	public static final String sql_index_last_update = "CREATE INDEX IDX_"
			+ TABLE_NAME + "_" + LAST_UPDATED + " ON " + TABLE_NAME + " ("
			+ LAST_UPDATED + ");";
	
	public static ContentValues checkValues(ContentValues values, Uri uri) {
		if (!values.containsKey(URL)) {
			throw new SQLException("Fail to insert row because URL is needed "
					+ uri);
		}

		if (!values.containsKey(LINK)) {
			values.put(LINK, "");
		}

		if (!values.containsKey(TITLE)) {
			values.put(TITLE, "unknow");
		}

		if (!values.containsKey(DESCRIPTION)) {
			values.put(DESCRIPTION, "");
		}
		
		if (!values.containsKey(IMAGE_URL)) {
			values.put(IMAGE_URL, "");
		}

		if (!values.containsKey(LAST_UPDATED)) {
			values.put(LAST_UPDATED, 0);
		}

		if (!values.containsKey(LAST_ITEM_UPDATED)) {
			values.put(LAST_ITEM_UPDATED, 0);
		}

		if (!values.containsKey(FAIL_COUNT)) {
			values.put(FAIL_COUNT, 0);
		}
		
		if (!values.containsKey(AUTO_DOWNLOAD)) {
			values.put(AUTO_DOWNLOAD, 0);
		}		
		if (!values.containsKey(PLAYLIST_POSITION)) {
			values.put(PLAYLIST_POSITION, -1);
		}

        if (!values.containsKey(PRIMARY_COLOR)) {
            values.put(PRIMARY_COLOR, -1);
        }
        if (!values.containsKey(PRIMARY_TINT_COLOR)) {
            values.put(PRIMARY_TINT_COLOR, -1);
        }
        if (!values.containsKey(SECONDARY_COLOR)) {
            values.put(SECONDARY_COLOR, -1);
        }
		if (!values.containsKey(SETTINGS)) {
			values.put(SETTINGS, -1);
		}

		if (!values.containsKey(NEW_EPISODES)) {
			values.put(NEW_EPISODES, 0);
		}
		if (!values.containsKey(EPISODE_COUNT)) {
			values.put(EPISODE_COUNT, 0);
		}

		return values;
	}
}
