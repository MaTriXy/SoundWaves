package org.bottiger.podcast.model;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.util.ArrayMap;
import android.support.v7.util.SortedList;
import android.text.TextUtils;
import android.util.Log;

import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.cloud.EventLogger;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.model.events.EpisodeChanged;
import org.bottiger.podcast.model.events.ItemChanged;
import org.bottiger.podcast.model.events.SubscriptionChanged;
import org.bottiger.podcast.playlist.Playlist;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.provider.ISubscription;
import org.bottiger.podcast.provider.ItemColumns;
import org.bottiger.podcast.provider.PodcastOpenHelper;
import org.bottiger.podcast.provider.SlimImplementations.SlimSubscription;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.provider.SubscriptionColumns;
import org.bottiger.podcast.provider.SubscriptionLoader;
import org.bottiger.podcast.utils.StrUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Created by aplb on 11-10-2015.
 */
public class Library {

    private static final String TAG = "Library";
    private static final int BACKPREASURE_BUFFER_SIZE = 10000;

    @NonNull private Context mContext;
    @NonNull private LibraryPersistency mLibraryPersistency;

    private final ReentrantLock mLock = new ReentrantLock();

    @NonNull
    private final ArrayList<IEpisode> mEpisodes = new ArrayList<>();
    @NonNull
    private final ArrayMap<String, IEpisode> mEpisodesUrlLUT = new ArrayMap<>();
    @NonNull
    private final ArrayMap<Long, FeedItem> mEpisodesIdLUT = new ArrayMap<>();

    @NonNull
    public PublishSubject<Subscription> mSubscriptionsChangeObservable = PublishSubject.create();

    @NonNull
    private final SortedList<Subscription> mActiveSubscriptions;
    @NonNull
    private final ArrayMap<String, Subscription> mSubscriptionUrlLUT = new ArrayMap<>();
    @NonNull
    private final ArrayMap<Long, Subscription> mSubscriptionIdLUT = new ArrayMap<>();
    @NonNull
    private SortedList.Callback<Subscription> mSubscriptionsListCallback = new SortedList.Callback<Subscription>() {

        /**
         *
         * @param o1
         * @param o2
         * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
         */
        @Override
        public int compare(Subscription o1, Subscription o2) {
            return compareSubscriptions(o1, o2);
        }

        @Override
        public void onInserted(int position, int count) {
            Subscription subscription = mActiveSubscriptions.get(position);
            notifySubscriptionChanged(subscription.getId(), SubscriptionChanged.ADDED, "SortedListInsert");
        }

        @Override
        public void onRemoved(int position, int count) {
            if (mActiveSubscriptions.size() >= position) {
                VendorCrashReporter.report("Library.onRemoved", "IndexOutOfBound");
                return;
            }

            long subscriptionId = mActiveSubscriptions.get(position).getId();
            notifySubscriptionChanged(subscriptionId, SubscriptionChanged.REMOVED, "SortedListRemoved");
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {

        }

        @Override
        public void onChanged(int position, int count) {
            notifySubscriptionChanged(mActiveSubscriptions.get(position).getId(), SubscriptionChanged.CHANGED, "SortedListChanged");
        }

        @Override
        public boolean areContentsTheSame(Subscription oldItem, Subscription newItem) {
            return areItemsTheSame(oldItem, newItem);
        }

        @Override
        public boolean areItemsTheSame(Subscription item1, Subscription item2) {
            if (item1 == null && item2 == null)
                return true;

            if (item1 == null)
                return false;

            return item1.equals(item2);
        }
    };

    public Library(@NonNull Context argContext) {
        mContext = argContext.getApplicationContext();

        mLibraryPersistency = new LibraryPersistency(argContext);

        mActiveSubscriptions = new SortedList<>(Subscription.class, mSubscriptionsListCallback);

        loadSubscriptions();

        SoundWaves.getRxBus()
                .toObserverable()
                .onBackpressureBuffer(BACKPREASURE_BUFFER_SIZE)
                .ofType(ItemChanged.class)
                .subscribe(new Action1<ItemChanged>() {
                    @Override
                    public void call(ItemChanged itemChangedEvent) {
                        if (itemChangedEvent instanceof EpisodeChanged) {
                            handleChangedEvent((EpisodeChanged) itemChangedEvent);
                            return;
                        }

                        if (itemChangedEvent instanceof SubscriptionChanged) {
                            handleChangedEvent((SubscriptionChanged) itemChangedEvent);
                            return;
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        VendorCrashReporter.handleException(throwable);
                        Log.wtf(TAG, "Missing back pressure. Should not happen anymore :(");
                    }
                });

    }

    public void handleChangedEvent(SubscriptionChanged argSubscriptionChanged) {
        Subscription subscription = getSubscription(argSubscriptionChanged.getId());

        if (subscription == null || TextUtils.isEmpty(subscription.getURLString())) {
            Log.wtf("Subscription empty!", "id: " + argSubscriptionChanged.getId());
            return;
        }

        try {
            mLock.lock();
            if (!subscription.IsSubscribed() &&
                mActiveSubscriptions.indexOf(subscription) != SortedList.INVALID_POSITION) {
                    Log.e("Unsubscribing", "from: " + subscription.title + ", tag:" + argSubscriptionChanged.getTag());
                    mActiveSubscriptions.remove(subscription);
                    mSubscriptionsChangeObservable.onNext(subscription);
            }
        } finally {
                mLock.unlock();
        }

        switch (argSubscriptionChanged.getAction()) {
            case SubscriptionChanged.CHANGED: {
                updateSubscription(subscription);
                break;
            }
            case SubscriptionChanged.ADDED:
            case SubscriptionChanged.REMOVED:
            case SubscriptionChanged.SUBSCRIBED:
        }
    }

    public void handleChangedEvent(EpisodeChanged argEpisodeChanged) {
        @EpisodeChanged.Action int action = argEpisodeChanged.getAction();
        if (action == EpisodeChanged.PARSED) {
            if (mEpisodesUrlLUT.containsKey(argEpisodeChanged.getUrl()))
                return;
        }

        if (action == EpisodeChanged.CHANGED ||
            action == EpisodeChanged.ADDED ||
            action == EpisodeChanged.REMOVED) {
            IEpisode episode = getEpisode(argEpisodeChanged.getId());
            updateEpisode(episode);
        }
    }

    /**
     *
     * @param argSubscription
     * @return
     */
    public boolean addEpisodes(@NonNull Subscription argSubscription) {
        mLock.lock();

        LinkedList<IEpisode> episodes = argSubscription.getEpisodes().getFilteredList();
        LinkedList<String> keys = new LinkedList<>();
        LinkedList<FeedItem> unpersistedEpisodes = new LinkedList<>();

        for (int i = 0; i < episodes.size(); i++) {
            keys.add(i, getKey(episodes.get(i)));
        }

        try {
            if (mEpisodesUrlLUT.containsAll(keys)) {
                return false;
            }

            for (int i = 0; i < episodes.size(); i++) {
                FeedItem episode = (FeedItem) episodes.get(i);
                if (!mEpisodesUrlLUT.containsKey(keys.get(i))) {
                    mEpisodes.add(episode);
                    mEpisodesUrlLUT.put(keys.get(i), episode);

                    if (!episode.isPersisted()) {
                        unpersistedEpisodes.add(episode);
                    }
                }
            }

            //long start = System.currentTimeMillis();
            mLibraryPersistency.insert(mContext, unpersistedEpisodes);
            //long end = System.currentTimeMillis();
            //Log.d(TAG, "insert time: " + (end-start) + " ms (#" + unpersistedEpisodes.size() + ")");

        } finally {
            mLock.unlock();
        }

        return true;
    }

    /*
        Return true if the episode was added
     */
    public boolean addEpisode(@Nullable IEpisode argEpisode) {
        return addEpisodeInternal(argEpisode, false);
    }

    /**
     * Adds an episode to the Library. If the Episode already exists in the Library it will be updated.
     *
     * @param argEpisode The Episode
     * @param argSilent Do not senda notification about the event
     * @return True if the episode was added. False it was already there.
     */
    private boolean addEpisodeInternal(@Nullable IEpisode argEpisode, boolean argSilent) {

        if (argEpisode == null)
            return false;

        boolean isFeedItem = argEpisode instanceof FeedItem;
        FeedItem item = isFeedItem ? (FeedItem)argEpisode : null;

        mLock.lock();
        try {
            // If the item is a feedItem it should belong to a subscription.
            // If it does not belong to a subscription yet (i.e. we are parsing the subscription, maybe for the first time)
            // We do not add it to the library yet.
            if (isFeedItem && item.sub_id < 0)
                return false;

            Subscription subscription = null;
            if (item != null) {
                subscription = item.getSubscription(mContext);

                if (subscription != null) {
                    subscription.addEpisode(item, true);
                }
            }

            if (mEpisodesUrlLUT.containsKey(argEpisode.getURL())) {
                // FIXME we should update the content of the model episode
                return false;
            }

            mEpisodes.add(argEpisode);
            mEpisodesUrlLUT.put(argEpisode.getURL(), argEpisode);

            if (isFeedItem) {
                boolean updatedEpisode = false;

                //long start = System.currentTimeMillis();
                if (!item.isPersisted()) {
                    updateEpisode(item);
                    updatedEpisode = true;
                }
                //long end = System.currentTimeMillis();
                //Log.d(TAG, "insert time: " + (end-start) + " ms");

                mEpisodesIdLUT.put(item.getId(), item);

                if (subscription != null && updatedEpisode) {
                    IEpisode episode = subscription.getEpisodes().getNewest();
                    if (episode != null) {
                        subscription.setLastUpdated(episode.getCreatedAt().getTime());
                    }
                }
            }

        } finally {
            mLock.unlock();
        }

        return true;
    }

    @WorkerThread
    public void addSubscription(@Nullable Subscription argSubscription) {
        addSubscriptionInternal(argSubscription, false);
    }

    @WorkerThread
    private void addSubscriptionInternal(@Nullable Subscription argSubscription, boolean argSilent) {
        mLock.lock();
        try {
            if (argSubscription == null)
                return;

            if (mSubscriptionIdLUT.containsKey(argSubscription.getId()))
                return;

            mSubscriptionUrlLUT.put(argSubscription.getUrl(), argSubscription);
            mSubscriptionIdLUT.put(argSubscription.getId(), argSubscription);

            if (mActiveSubscriptions.indexOf(argSubscription) == SortedList.INVALID_POSITION &&
                    argSubscription.IsSubscribed()) {
                mActiveSubscriptions.add(argSubscription);
            }

            if (!argSilent)
                mSubscriptionsChangeObservable.onNext(argSubscription);

        } finally {
            mLock.unlock();
        }
    }

    public void removeSubscription(@Nullable Subscription argSubscription) {
        mLock.lock();
        try {
            if (argSubscription == null)
                return;

            VendorCrashReporter.report("remove", argSubscription.getUrl());

            mSubscriptionIdLUT.remove(argSubscription.getId());
            mSubscriptionUrlLUT.remove(argSubscription.getUrl());
            mActiveSubscriptions.remove(argSubscription);
        } finally {
            mLock.unlock();
        }

        mSubscriptionsChangeObservable.onNext(argSubscription);
    }

    private void clearSubscriptions() {
        mLock.lock();
        try {
            mActiveSubscriptions.clear();
            mSubscriptionUrlLUT.clear();
            mSubscriptionIdLUT.clear();
        } finally {
            mLock.unlock();
        }
    }

    public static Subscription getByCursor(Cursor cursor, Subscription argSubscription) {
        if (argSubscription == null) {
            argSubscription = new Subscription();
        }

        argSubscription = SubscriptionLoader.fetchFromCursor(argSubscription, cursor);
        return argSubscription;
    }

    public SortedList<Subscription> getSubscriptions() {
        return mActiveSubscriptions;
    }

    @Nullable
    public Subscription getSubscription(@NonNull String argUrl) {
        return mSubscriptionUrlLUT.get(argUrl);
    }

    @Nullable
    public Subscription getSubscription(@NonNull Long argId) {
        return mSubscriptionIdLUT.get(argId);
    }

    @NonNull
    public ArrayList<IEpisode> getEpisodes() {
        return mEpisodes;
    }

    @Nullable
    public IEpisode getEpisode(@NonNull String argUrl) {
        return mEpisodesUrlLUT.get(argUrl);
    }

    @Nullable
    public FeedItem getEpisode(long argId) {
        return mEpisodesIdLUT.get(argId);
    }

    public SortedList<IEpisode> newEpisodeSortedList(SortedList.Callback<IEpisode> argCallback) {
        SortedList<IEpisode> sortedList = new SortedList<>(IEpisode.class, argCallback);
        sortedList.addAll(mEpisodes);
        return sortedList;
    }

    public boolean containsSubscription(@Nullable ISubscription argSubscription) {
        if (argSubscription == null)
            return false;

        ISubscription subscription = mSubscriptionUrlLUT.get(argSubscription.getURLString());
        if (subscription == null)
            return false;

        return subscription.IsSubscribed();
    }

    public boolean containsEpisode(@Nullable IEpisode argEpisode) {
        if (argEpisode == null)
            return false;

        return mEpisodes.contains(argEpisode);
    }

    private void invalidate() {
        return;
    }

    /**
     * Return a timestamp which can be used to determine if an episode is new.
     * @return
     */
    public static long episodeNewThreshold() {
        int newThresholdInDays = 6;

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_YEAR,-newThresholdInDays);
        Date threshold= cal.getTime();
        return threshold.getTime();
    }

    /**
     * Get all the subscriptions from the database.
     *
     * @return
     */
    private String getAllSubscriptions() {
        // SELECT *, (SELECT count(item._id) FROM item WHERE item.subs_id == subscriptions._id
        // AND item.pub_date>1445210057385) AS new_episodes FROM subscriptions

        long thresholdTimestamp = episodeNewThreshold();

        StringBuilder builder = new StringBuilder(200);
        builder.append("SELECT ");
        builder.append("*, ");
        builder.append("(SELECT count(");
        builder.append(ItemColumns.TABLE_NAME + "." + ItemColumns._ID);
        builder.append(") ");
        builder.append("FROM " + ItemColumns.TABLE_NAME + " ");
        builder.append("WHERE ");
        builder.append(ItemColumns.TABLE_NAME + "." + ItemColumns.SUBS_ID + " == ");
        builder.append(SubscriptionColumns.TABLE_NAME + "." + SubscriptionColumns._ID);
        builder.append(" AND ");
        builder.append(ItemColumns.TABLE_NAME + "." + ItemColumns.PUB_DATE + ">" + thresholdTimestamp + ") ");
        builder.append("AS " + SubscriptionColumns.NEW_EPISODES + " ");
        builder.append("FROM " + SubscriptionColumns.TABLE_NAME + " ");
        //builder.append("WHERE " + SubscriptionColumns.STATUS + "==" + Subscription.STATUS_SUBSCRIBED);

        return builder.toString();
    }

    private String getAllEpisodes(@NonNull Subscription argSubscription) {
        return "SELECT * FROM " + ItemColumns.TABLE_NAME + " WHERE " + ItemColumns.SUBS_ID + "==" + argSubscription.getId();
    }

    private String getPlaylistEpisodes(@NonNull Playlist argPlaylist) {
        return "SELECT * FROM " + ItemColumns.TABLE_NAME + " WHERE " +
                argPlaylist.getWhere() + " ORDER BY " +
                argPlaylist.getOrder();
    }

    public void loadPlaylistSync(@NonNull final Playlist argPlaylist) {
        String query = getPlaylistEpisodes(argPlaylist);
        loadPlaylistInternal(query, argPlaylist);
    }

    public void loadPlaylist(@NonNull final Playlist argPlaylist) {
        String query = getPlaylistEpisodes(argPlaylist);

        Observable.just(query)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Action1<String>() {
            @Override
            public void call(String query) {
                loadPlaylistInternal(query, argPlaylist);
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                VendorCrashReporter.report("subscribeError" , throwable.toString());
                Log.d(TAG, "error: " + throwable.toString());
            }
        });
    }

    private void loadPlaylistInternal(@NonNull String query, @NonNull  Playlist argPlaylist) {

        if (argPlaylist.isLoaded())
            return;

        Cursor cursor = null;

        mLock.lock();
        try {

            int counter = 0;
            cursor = PodcastOpenHelper.runQuery(Library.this.mContext, query);
            FeedItem episode;

            while (cursor.moveToNext()) {
                episode = LibraryPersistency.fetchEpisodeFromCursor(cursor, null);
                addEpisode(episode);
                //argPlaylist.setItem(counter, episode);
                counter++;
            }

            // Populate the playlist from the library
            argPlaylist.populatePlaylist();

            argPlaylist.setIsLoaded(true);
        } finally {
            if (cursor != null)
                cursor.close();
            mLock.unlock();
        }
    }

    @MainThread
    private void loadSubscriptions() {
        String query = getAllSubscriptions();

        Observable.just(query)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .subscribe(new Action1<String>() {
            @Override
            public void call(String query) {
                loadSubscriptionsInternal(query);
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (throwable instanceof SQLiteCantOpenDatabaseException) {
                    VendorCrashReporter.report("subscribeError2", throwable.toString());
                } else {
                    VendorCrashReporter.report("subscribeError", throwable.toString());
                }
                Log.d(TAG, "error: " + throwable.toString());
            }
        });
    }

    @WorkerThread
    private void loadSubscriptionsInternal(@NonNull String argQuery) {
        Cursor cursor = null;
        clearSubscriptions();
        Subscription subscription = null;
        try {
            cursor = PodcastOpenHelper.runQuery(Library.this.mContext, argQuery);

            while (cursor.moveToNext()) {
                subscription = getByCursor(cursor, null);

                if (!TextUtils.isEmpty(subscription.getUrl())) {
                    addSubscriptionInternal(subscription, true);
                } else {
                    subscription = null;
                }
            }
        } finally {
            if (subscription != null)
                mSubscriptionsChangeObservable.onNext(subscription);
            if(cursor != null)
                cursor.close();

        }
    }

    public void loadEpisodes(@NonNull final Subscription argSubscription) {
        if (argSubscription.IsLoaded())
            return;

        String query = getAllEpisodes(argSubscription);
        Observable.just(query)
                .observeOn(Schedulers.io())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String query) {
                        loadEpisodesSync(argSubscription, query);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        VendorCrashReporter.report("subscribeError" , throwable.toString());
                        Log.d(TAG, "error: " + throwable.toString());
                    }
                });
    }

    @WorkerThread
    public synchronized void loadEpisodesSync(@NonNull final Subscription argSubscription, @Nullable String argQuery) {
        if (argSubscription.IsLoaded())
            return;

        Cursor cursor = null;

        long start = 0;
        int counter = 0;
        try {

            if (argQuery == null)
                argQuery = getAllEpisodes(argSubscription);

            //argQuery = "update " + SubscriptionColumns.TABLE_NAME + " set " + SubscriptionColumns.STATUS + "=" + Subscription.STATUS_SUBSCRIBED;

            cursor = PodcastOpenHelper.runQuery(Library.this.mContext, argQuery);

            start = System.currentTimeMillis();

            FeedItem[] emptyItems = new FeedItem[1];
            int count = cursor.getCount();
            if (count > 0) {
                emptyItems = new FeedItem[cursor.getCount()];

                for (int i = 0; i < emptyItems.length; i++) {
                    emptyItems[i] = new FeedItem();
                }
            }

            argSubscription.setIsRefreshing(true);
            while (cursor.moveToNext()) {
                FeedItem item = LibraryPersistency.fetchEpisodeFromCursor(cursor, emptyItems[counter]);
                addEpisode(item);
                counter++;
            }

            argSubscription.setIsRefreshing(false);
            argSubscription.setIsLoaded(true);
        } finally {
            if (cursor != null)
                cursor.close();
            long end = System.currentTimeMillis();
            Log.d("loadAllEpisodes", "1: " + (end - start) + " ms");
        }
    }

    public boolean IsSubscribed(String argUrl) {
        if (!StrUtils.isValidUrl(argUrl))
            return false;

        Subscription subscription = mSubscriptionUrlLUT.get(argUrl);

        return subscription != null && subscription.IsSubscribed();
    }

    public void unsubscribe(String argUrl, String argTag) {
        if (!StrUtils.isValidUrl(argUrl))
            return;

        Subscription subscription = mSubscriptionUrlLUT.get(argUrl);

        if (subscription == null)
            return;

        subscription.unsubscribe(argTag);
        updateSubscription(subscription);
        removeSubscription(subscription);

        EventLogger.postEvent(mContext, EventLogger.UNSUBSCRIBE_PODCAST, null, argUrl, null);
    }

    @Deprecated
    public void subscribe(String argUrl) {
        if (!StrUtils.isValidUrl(argUrl))
            return;

        try {
            SlimSubscription slimSubscription = new SlimSubscription("", new URL(argUrl), "");
            subscribe(slimSubscription);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(@NonNull ISubscription argSubscription) {

        Observable
                .just(argSubscription)
                .map(new Func1<ISubscription, Subscription>() {
                    @Override
                    public Subscription call(ISubscription argSubscription) {

                        Subscription subscription;

                        mLock.lock();
                        try {
                            String key = getKey(argSubscription);
                            subscription = mSubscriptionUrlLUT.containsKey(key) ?
                                    mSubscriptionUrlLUT.get(key) :
                                    new Subscription(argSubscription);

                            subscription.subscribe("Subscribe:from:Library.subscribe");
                            updateSubscription(subscription);

                            mSubscriptionUrlLUT.put(key, subscription);
                            mSubscriptionIdLUT.put(subscription.getId(), subscription);
                            mActiveSubscriptions.add(subscription);

                        } finally {
                            mLock.unlock();
                        }

                        EventLogger.postEvent(mContext, EventLogger.SUBSCRIBE_PODCAST, null, argSubscription.getURLString(), null);
                        mSubscriptionsChangeObservable.onNext(subscription);

                        SoundWaves.getAppContext(mContext).getRefreshManager().refresh(subscription, null);

                        return subscription;
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Subscription>() {

                    Subscription mSubscription;

                    @Override
                    public void onCompleted() {
                        //SoundWaves.sAnalytics.trackEvent(IAnalytics.EVENT_TYPE.SUBSCRIBE_TO_FEED);
                        //notifySubscriptionChanged(mSubscription.getId(), SubscriptionChanged.ADDED);
                        addSubscription(mSubscription);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e("OnError", e.getMessage());
                        VendorCrashReporter.report("OnError", e.getStackTrace().toString());
                    }

                    @Override
                    public void onNext(Subscription subscription) {
                        mSubscription = subscription;
                    }
                });
    }

    public void updateSubscription(Subscription argSubscription) {
        mLibraryPersistency.persist(argSubscription);

        // Update new episodes
        SortedList<IEpisode> episodes = argSubscription.getEpisodes();
        for (int i = 0; i < episodes.size(); i++) {
            IEpisode episode = episodes.get(i);
            if (!containsEpisode(episode)) {
                updateEpisode(episode);
            }
        }
    }

    public @LibraryPersistency.PersistencyResult
    int updateEpisode(@NonNull IEpisode argEpisode) {
        if (argEpisode instanceof FeedItem) {
            return updateEpisode((FeedItem) argEpisode);
        }

        return LibraryPersistency.ERROR;
    }

    private @LibraryPersistency.PersistencyResult
    int updateEpisode(@NonNull FeedItem argEpisode) {
        addEpisode(argEpisode);
        return mLibraryPersistency.persist(argEpisode);
    }

    private int compareSubscriptions(ISubscription s1, ISubscription s2) {
        return s1.getTitle().compareTo(s2.getTitle());
    }

    private void notifySubscriptionChanged(final long argId, @SubscriptionChanged.Action final int argAction, @Nullable String argTag) {
        if (TextUtils.isEmpty(argTag))
            argTag = "NoTag";

        SoundWaves.getRxBus().send(new SubscriptionChanged(argId, argAction, argTag));
    }

    private static String getKey(@NonNull ISubscription argSubscription) {
        return argSubscription.getURLString();
    }

    private static String getKey(@NonNull IEpisode argEpisode) {
        return argEpisode.getURL();
    }
}
