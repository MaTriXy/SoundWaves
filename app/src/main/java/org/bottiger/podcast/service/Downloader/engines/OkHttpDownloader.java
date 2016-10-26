package org.bottiger.podcast.service.Downloader.engines;

import android.Manifest;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.MimeTypeMap;

import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.utils.StrUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import okhttp3.OkUrlFactory;

/**
 * Created by apl on 17-09-2014.
 */
public class OkHttpDownloader extends DownloadEngineBase {

    private static final String TAG = "OkHttpDownloaderLog";

    private static final int BUFFER_SIZE = 4096;

    private final okhttp3.OkHttpClient mOkHttpClient = new okhttp3.OkHttpClient();
    private HttpURLConnection mConnection;
    private final SparseArray<Callback> mExternalCallback = new SparseArray<>();

    private volatile boolean mAborted = false;

    private Context mContext;
    private final URL mURL;

    private double mProgress = 0;

    @WorkerThread
    public OkHttpDownloader(@NonNull Context argContext, @NonNull IEpisode argEpisode) {
        super(argEpisode);
        mContext = argContext;
        mURL = argEpisode.getUrl();
    }

    @WorkerThread
    @Override
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void startDownload() throws SecurityException{
        mProgress = 0;
        try {

            Log.d(TAG, "startDownload");

            if (mURL == null || !StrUtils.isValidUrl(mURL.toString())) {
                Log.d(TAG, "no URL, return");
                onFailure(new MalformedURLException());
                return;
            }

            mConnection = new OkUrlFactory(mOkHttpClient).open(mURL);

            if (mEpisode instanceof FeedItem) {
                FeedItem episode = (FeedItem) mEpisode;

                String extension = MimeTypeMap.getFileExtensionFromUrl(mURL.toString());

                Log.d(TAG, "fileextension: " + extension);

                String filename = Integer.toString(episode.getEpisodeNumber()) + episode.getTitle().replace(' ', '_'); //Integer.toString(item.getEpisodeNumber()) + "_"
                episode.setFilename(filename + "." + extension); // .replaceAll("[^a-zA-Z0-9_-]", "") +

                double contentLength = mConnection.getContentLength();
                InputStream inputStream = mConnection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(episode.getAbsoluteTmpPath(mContext));

                mEpisode.setFilesize((long)contentLength);

                Log.d(TAG, "starting file transfer");

                int bytesRead = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (mAborted) {
                        Log.d(TAG, "Transfer abort");
                        onFailure(new InterruptedIOException());
                        return;
                    }

                    outputStream.write(buffer, 0, bytesRead);
                    mProgress += bytesRead / contentLength;
                    getEpisode().setProgress(mProgress*100);
                }

                Log.d(TAG, "filetransfer done");

                outputStream.close();
                inputStream.close();

                File tmpFIle = new File(episode.getAbsoluteTmpPath(mContext));
                File finalFIle = new File(episode.getAbsolutePath());

                Log.d(TAG, "pre move file");

                // If download was succesfull
                if (tmpFIle.exists() && tmpFIle.length() == contentLength) {
                    Log.d(TAG, "Renaming file");
                    tmpFIle.renameTo(finalFIle);
                    Log.d(TAG, "File renamed");
                    onSucces(finalFIle);
                    Log.d(TAG, "post onSucces");
                } else {
                    Log.d(TAG, "File already exists");
                    String msg = "Wrong file size. Expected: " + tmpFIle.length() + ", got: " + contentLength; // NoI18N
                    onFailure(new FileNotFoundException(msg));
                    Log.d(TAG, "post onfailure");
                }
            } else {
                Log.d(TAG, "Trying to download slim episode - abort");
            }

        } catch (IOException e){
            Log.d(TAG, "IOException: " + e.toString());
            onFailure(e);
            String[] keys = {"DownloadUrl"};
            String[] values = {mURL.toString()};
            VendorCrashReporter.handleException(e, keys, values);
        } finally{
            Log.d(TAG, "disconnecting");
            HttpURLConnection connection = mConnection;
            if (connection != null)
                connection.disconnect();
        }
    }

    @Override
    public float getProgress() {
        return (float)mProgress;
    }

    @Override
    public void addCallback(@NonNull Callback argCallback) {
        int newKey = mExternalCallback.size() == 0 ? 0 : mExternalCallback.keyAt(mExternalCallback.size()-1) + 1;
        mExternalCallback.append(newKey, argCallback);
    }

    @Override
    public void abort() {
        mAborted = true;
    }

    private void onSucces(File response)  throws IOException {
        Log.d("Download", "Download succeeded");

        for(int i = 0; i < mExternalCallback.size(); i++) {
            int key = mExternalCallback.keyAt(i);
            Callback callback = mExternalCallback.valueAt(key);
            callback.downloadCompleted(mEpisode);
        }
    }

    public void onFailure(IOException e) {
        Log.w("Download", "Download Failed: " + e.getMessage());

        for(int i = 0; i < mExternalCallback.size(); i++) {
            int key = mExternalCallback.keyAt(i);
            Callback callback = mExternalCallback.valueAt(key);
            callback.downloadInterrupted(mEpisode);
        }
    }
}
