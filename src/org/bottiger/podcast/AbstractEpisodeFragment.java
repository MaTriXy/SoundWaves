package org.bottiger.podcast;


import java.util.HashMap;

import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.ItemColumns;
import org.bottiger.podcast.provider.PodcastOpenHelper;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.service.PodcastDownloadManager;
import org.bottiger.podcast.utils.SlidingMenuBuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

public abstract class AbstractEpisodeFragment extends PodcastBaseFragment {

	protected static HashMap<Integer, Integer> mIconMap;

	protected long pref_order;
	protected long pref_where;
	protected long pref_select;

	protected ListView actualListView = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.episode_list, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_download_all: {
			Cursor cursor = createCursor(getWhere(), getOrder());
			cursor.moveToFirst();
			while (cursor.isAfterLast() == false) {
				FeedItem feedItem = FeedItem.getByCursor(cursor);
				if (!feedItem.isDownloaded())
					PodcastDownloadManager.addItemToQueue(feedItem);

				cursor.moveToNext();
			}
			PodcastDownloadManager.startDownload(getActivity());
			return true;
		}
		case R.id.menu_clear_playlist: {
			resetPlaylist(getActivity());
			refreshView();
			//FeedItem.clearCache();
			//startInit(10, ItemColumns.URI, ItemColumns.ALL_COLUMNS, getWhere(), getOrder());
			
			//mAdapter.changeCursor(getCursor());
			//mAdapter.notifyDataSetChanged();
		}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	public String getWhere() {
		//String where = "1";
		Boolean showListened = sharedPreferences.getBoolean(SlidingMenuBuilder.showListenedKey, SlidingMenuBuilder.showListenedVal);
		String where = (showListened) ? "1" : ItemColumns.LISTENED + "== 0";
		return where;
	}

	public void getPref() {
		SharedPreferences pref = getActivity().getSharedPreferences(
				SettingsActivity.HAPI_PREFS_FILE_NAME, Context.MODE_PRIVATE);
		pref_order = pref.getLong("pref_order", 2);
		pref_where = pref.getLong("pref_where", 0);
		pref_select = pref.getLong("pref_select", 0);
	}

	@Deprecated
	protected Cursor createCursor(String condition, String order) {
		// return new CursorLoader(getActivity(), ItemColumns.URI, PROJECTION,
		// condition, null, getOrder()).loadInBackground();
		return new CursorLoader(getActivity(), ItemColumns.URI,
				ItemColumns.ALL_COLUMNS, condition, null, order)
				.loadInBackground();
	}

	public String getOrder() {
		return getOrder("DESC");
	}

	public String getOrder(String inputOrder) {
		assert inputOrder != null;

		String playingFirst = "";
		if (mPlayerServiceBinder != null && mPlayerServiceBinder.getCurrentItem() != null) {
			playingFirst = "case " + ItemColumns._ID + " when " + mPlayerServiceBinder.getCurrentItem().getId() + " then 1 else 2 end, ";
		}
		String prioritiesSecond = "case " + ItemColumns.PRIORITY + " when 0 then 2 else 1 end, " + ItemColumns.PRIORITY + ", ";
		String order = playingFirst + prioritiesSecond + ItemColumns.DATE + " " + inputOrder + " LIMIT 20"; // before:
		return order;
	}
	
	protected void enablePullToRefresh() {
		enablePullToRefresh(null);
	}
	
	protected void enablePullToRefresh(final Subscription subscription) {

		final PullToRefreshListView pullToRefreshView = (PullToRefreshListView) fragmentView
				.findViewById(R.id.episode_list);

		OnRefreshListener<ListView> pullToRefreshListener = new OnRefreshListener<ListView>() {

			@Override
			public void onRefresh(PullToRefreshBase<ListView> refreshView) {
				PodcastDownloadManager.start_update(getActivity(), pullToRefreshView, subscription);
			}
		};

		actualListView = pullToRefreshView.getRefreshableView();
		pullToRefreshView.getLoadingLayoutProxy().setRefreshingLabel(
				"Refreshing feeds");
		pullToRefreshView.setOnRefreshListener(pullToRefreshListener);
	}
	
	protected static void resetPlaylist(Context context) {
		// Update the database
		PodcastOpenHelper helper = new PodcastOpenHelper(context);
		SQLiteDatabase db = helper.getWritableDatabase();
		String action = "UPDATE " + ItemColumns.TABLE_NAME + " SET ";
		String value = ItemColumns.PRIORITY + "=0 ";
		String where = "WHERE " + ItemColumns.PRIORITY + "<> 0";
		String sql = action + value + where;
		db.execSQL(sql);
	}

}