package org.bottiger.podcast;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.bottiger.podcast.playlist.Playlist;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.ItemColumns;
import org.bottiger.podcast.service.PlayerService;

public abstract class AbstractEpisodeFragment extends PodcastBaseFragment {

	private static final String TAG = "AbstractEpisodeFragment";

	private SharedPreferences prefs;

	String showListenedKey = "showListened";
	Boolean showListenedVal = true;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.episode_list, menu);

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_clear_playlist: {

				Playlist playlist = SoundWaves.getAppContext(getContext()).getPlaylist();
				playlist.resetPlaylist(null);

				int size = playlist.defaultSize();

				if (!playlist.isEmpty()) {
					playlist.populatePlaylist(size, true);
					if (mAdapter != null) {
						mAdapter.notifyDataSetChanged();
					}
				}
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Deprecated
	public String getWhere() {
		Boolean showListened = sharedPreferences.getBoolean(showListenedKey,
				showListenedVal);
		String where = (showListened) ? "1" : ItemColumns.LISTENED + "== 0";
		return where;
	}

	@Deprecated
	public String getOrder() {
		return getOrder("DESC", 100);
	}

	@Deprecated
	public static String getOrder(String inputOrder, Integer amount) {
		assert inputOrder != null;

		PlayerService playerService = PlayerService.getInstance();

		String playingFirst = "";
		if (playerService != null && playerService.getCurrentItem() != null && playerService.getCurrentItem() instanceof FeedItem) {
			playingFirst = "case " + ItemColumns._ID + " when "
            + ((FeedItem)playerService.getCurrentItem()).getId()
					+ " then 1 else 2 end, ";
		}
		String prioritiesSecond = "case " + ItemColumns.PRIORITY
				+ " when 0 then 2 else 1 end, " + ItemColumns.PRIORITY + ", ";
		String order = playingFirst + prioritiesSecond + ItemColumns.DATE + " "
				+ inputOrder + " LIMIT " + amount; // before:
		return order;
	}

}
