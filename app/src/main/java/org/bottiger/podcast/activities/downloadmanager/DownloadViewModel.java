package org.bottiger.podcast.activities.downloadmanager;

import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.content.res.Resources;
import android.databinding.BindingAdapter;
import android.databinding.ObservableField;
import android.databinding.ObservableInt;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;

import org.bottiger.podcast.R;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.model.events.DownloadProgress;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.service.DownloadStatus;
import org.bottiger.podcast.utils.ImageLoaderUtils;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by aplb on 05-10-2015.
 */
public class DownloadViewModel {

    private static final String TAG = DownloadViewModel.class.getSimpleName();

    private static final int MAX_PROGRESS = 100;

    public final ObservableField<String> subtitle = new ObservableField<>();
    public final ObservableInt progress = new ObservableInt();

    private Context mContext;
    private DownloadManagerAdapter mAdapter;
    private FeedItem mEpisode;
    private int mPosition;

    private Subscription mRxSubscription = null;

    public DownloadViewModel(@NonNull Context argContext,
                             @NonNull DownloadManagerAdapter argAdapter,
                             @NonNull final FeedItem argEpisode,
                             int argPosition) {
        mContext = argContext;
        mAdapter = argAdapter;
        mEpisode = argEpisode;
        mPosition = argPosition;
        updateProgress(0); //updateProgress(isFirst() ? 60 : 0);
    }

    public void subscribe() {
        mRxSubscription = getEpisode()._downloadProgressChangeObservable
                .onBackpressureDrop()
                .ofType(DownloadProgress.class)
                .filter(new Func1<DownloadProgress, Boolean>() {
                    @Override
                    public Boolean call(DownloadProgress downloadProgress) {
                        return getEpisode().equals(downloadProgress.getEpisode());
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<DownloadProgress>() {
                    @Override
                    public void call(DownloadProgress downloadProgress) {
                        Log.v(TAG, "Recieved downloadProgress event. Progress: " + downloadProgress.getProgress());

                        if (downloadProgress.getStatus() == DownloadStatus.DOWNLOADING) {
                            updateProgress(downloadProgress.getProgress());
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        VendorCrashReporter.report("subscribeError" , throwable.toString());
                        Log.d(TAG, "error: " + throwable.toString());
                    }
                });
    }

    public void unsubscribe() {
        if (mRxSubscription != null && !mRxSubscription.isUnsubscribed()) {
            mRxSubscription.unsubscribe();
        }
    }

    @NonNull
    public FeedItem getEpisode() {
        return mEpisode;
    }

    public String getTitle() {
        return mEpisode.getTitle();
    }

    public String getImageUrl() {
        return mEpisode.getArtwork(mContext);
    }

    @BindingAdapter("app:imageUrl")
    public static void loadImage(ImageView argImageView, String argImageUrl) {
        if (!TextUtils.isEmpty(argImageUrl)) {
            Context context = argImageView.getContext();
            RequestOptions options = ImageLoaderUtils.getRequestOptions(context);
            options.centerCrop();
            options.placeholder(R.drawable.generic_podcast);

            RequestBuilder<Bitmap> builder = ImageLoaderUtils.getGlide(context, argImageUrl);
            builder.apply(options);
            builder.into(argImageView);

        }
    }

    private String makeSubtitle() {

        double currentFilesize = 0;

        if (progress.get() != 0) {
            currentFilesize = (double) mEpisode.getFilesize() * progress.get() / 100.0;
        }

        Resources res = mContext.getResources();
        String currentFilesizeFormatted = "";

        long filesize = mEpisode.getFilesize();


        if (filesize < 0) {
            return mContext.getResources().getString(R.string.unknown_filesize);
        }

        String totalFilesizeFormatted = Formatter.formatFileSize(mContext, filesize);

        if (currentFilesize > 0) {
            currentFilesizeFormatted = Formatter.formatFileSize(mContext, (long)currentFilesize);
        } else {
            // http://stackoverflow.com/questions/13493011/getquantitystring-returns-wrong-string-with-0-value
            return totalFilesizeFormatted;
        }

        return res.getString(R.string.download_progress,
                currentFilesizeFormatted,
                totalFilesizeFormatted);
    }

    public int getMaxProgress() {
        return MAX_PROGRESS;
    }

    private boolean isFirst() {
        return mPosition == 0;
    }

    public void updateProgress(int argProgress) {
        progress.set(argProgress);
        subtitle.set(makeSubtitle());
    }

    public void onClickRemove(View view) {
        mAdapter.removed(mPosition);
    }

}
