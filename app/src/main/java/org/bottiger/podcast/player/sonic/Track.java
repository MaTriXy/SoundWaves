//Copyright 2012 James Falcon
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package org.bottiger.podcast.player.sonic;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import org.bottiger.podcast.player.sonic.service.IDeathCallback;
import org.bottiger.podcast.player.sonic.service.IOnBufferingUpdateListenerCallback;
import org.bottiger.podcast.player.sonic.service.IOnCompletionListenerCallback;
import org.bottiger.podcast.player.sonic.service.IOnErrorListenerCallback;
import org.bottiger.podcast.player.sonic.service.IOnInfoListenerCallback;
import org.bottiger.podcast.player.sonic.service.IOnPitchAdjustmentAvailableChangedListenerCallback;
import org.bottiger.podcast.player.sonic.service.IOnPreparedListenerCallback;
import org.bottiger.podcast.player.sonic.service.IOnSeekCompleteListenerCallback;
import org.bottiger.podcast.player.sonic.service.IOnSpeedAdjustmentAvailableChangedListenerCallback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

@TargetApi(16)
public class Track {
    private AudioTrack mTrack;
    private Sonic mSonic;
    private MediaExtractor mExtractor;
    private MediaCodec mCodec;
    private Thread mDecoderThread;
    private String mPath;
    private Uri mUri;
    private final ReentrantLock mLock;
    private final Object mDecoderLock;
    private boolean mContinue;
    private boolean mIsDecoding;
    private long mDuration;
    private float mCurrentSpeed;
    private float mCurrentPitch;
    private int mCurrentState;
    private final Context mContext;

    private final static int TRACK_NUM = 0;
    private final static String TAG_TRACK = "PrestissimoTrack";

    private final static int STATE_IDLE = 0;
    private final static int STATE_INITIALIZED = 1;
    private final static int STATE_PREPARING = 2;
    private final static int STATE_PREPARED = 3;
    private final static int STATE_STARTED = 4;
    private final static int STATE_PAUSED = 5;
    private final static int STATE_STOPPED = 6;
    private final static int STATE_PLAYBACK_COMPLETED = 7;
    private final static int STATE_END = 8;
    private final static int STATE_ERROR = 9;

    // Not available in API 16 :(
    private final static int MEDIA_ERROR_MALFORMED = 0xfffffc11;
    private final static int MEDIA_ERROR_IO = 0xfffffc14;

    // The aidl interface should automatically implement stubs for these, so
    // don't initialize or require null checks.
    protected IOnErrorListenerCallback errorCallback;
    protected IOnCompletionListenerCallback completionCallback;
    protected IOnBufferingUpdateListenerCallback bufferingUpdateCallback;
    protected IOnInfoListenerCallback infoCallback;
    protected IOnPitchAdjustmentAvailableChangedListenerCallback pitchAdjustmentAvailableChangedCallback;
    protected IOnPreparedListenerCallback preparedCallback;
    protected IOnSeekCompleteListenerCallback seekCompleteCallback;
    protected IOnSpeedAdjustmentAvailableChangedListenerCallback speedAdjustmentAvailableChangedCallback;

    // Don't know how to persist this other than pass it in and 'hold' it
    private final IDeathCallback mDeath;

    public Track(Context context, IDeathCallback cb) {
        mCurrentState = STATE_IDLE;
        mCurrentSpeed = (float) 1.0;
        mCurrentPitch = (float) 1.0;
        mContinue = false;
        mIsDecoding = false;
        mContext = context;
        mDeath = cb;
        mPath = null;
        mUri = null;
        mLock = new ReentrantLock();
        mDecoderLock = new Object();
    }

    // TODO: This probably isn't right...
    public float getCurrentPitchStepsAdjustment() {
        return mCurrentPitch;
    }

    public int getCurrentPosition() {
        switch (mCurrentState) {
            case STATE_ERROR:
                error();
                break;
            default:
                return (int) (mExtractor.getSampleTime() / 1000);
        }
        return 0;
    }

    public float getCurrentSpeed() {
        return mCurrentSpeed;
    }

    public int getDuration() {
        switch (mCurrentState) {
            case STATE_INITIALIZED:
            case STATE_IDLE:
            case STATE_ERROR:
                error();
                break;
            default:
                return (int) (mDuration / 1000);
        }
        return 0;
    }

    public boolean isPlaying() {
        switch (mCurrentState) {
            case STATE_ERROR:
                error();
                break;
            default:
                return mCurrentState == STATE_STARTED;
        }
        return false;
    }

    public void pause() {
        switch (mCurrentState) {
            case STATE_STARTED:
            case STATE_PAUSED:
                mTrack.pause();
                mCurrentState = STATE_PAUSED;
                Log.d(TAG_TRACK, "State changed to STATE_PAUSED");
                break;
            default:
                error();
        }
    }

    public void prepare() {
        switch (mCurrentState) {
            case STATE_INITIALIZED:
            case STATE_STOPPED:
                try {
                    initStream();
                } catch (IOException e) {
                    Log.e(TAG_TRACK, "Failed setting data source!", e);
                    error();
                    return;
                }
                mCurrentState = STATE_PREPARED;
                Log.d(TAG_TRACK, "State changed to STATE_PREPARED");
                try {
                    preparedCallback.onPrepared();
                } catch (RemoteException e) {
                    // Binder should handle our death
                    Log.e(TAG_TRACK,
                            "RemoteException calling onPrepared after prepare", e);
                }
                break;
            default:
                error();
        }
    }

    public void prepareAsync() {
        switch (mCurrentState) {
            case STATE_INITIALIZED:
            case STATE_STOPPED:
                mCurrentState = STATE_PREPARING;
                Log.d(TAG_TRACK, "State changed to STATE_PREPARING");

                Thread t = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            initStream();
                        } catch (IOException e) {
                            Log.e(TAG_TRACK, "Failed setting data source!", e);
                            error();
                            return;
                        }
                        if (mCurrentState != STATE_ERROR) {
                            mCurrentState = STATE_PREPARED;
                            Log.d(TAG_TRACK, "State changed to STATE_PREPARED");
                        }
                         final IOnPreparedListenerCallback preparedListenerCallback08 = preparedCallback;
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        preparedListenerCallback08.onPrepared();
                                    } catch (RemoteException e) {
                                    // Binder should handle our death
                                    Log.e(TAG_TRACK,
                                            "RemoteException trying to call onPrepared after prepareAsync",
                                            e);
                                    }
                                }
                            });

                    }
                });
                t.setDaemon(true);
                t.start();

                break;
            default:
                error();
        }
    }

    public void stop() {
        switch (mCurrentState) {
            case STATE_PREPARED:
            case STATE_STARTED:
            case STATE_STOPPED:
            case STATE_PAUSED:
            case STATE_PLAYBACK_COMPLETED:
                mCurrentState = STATE_STOPPED;
                Log.d(TAG_TRACK, "State changed to STATE_STOPPED");
                mContinue = false;
                mTrack.pause();
                mTrack.flush();
                break;
            default:
                error();
        }
    }

    public void start() {
        switch (mCurrentState) {
            case STATE_PREPARED:
            case STATE_PLAYBACK_COMPLETED:
                mCurrentState = STATE_STARTED;
                Log.d(SoundService.TAG_API, "State changed to STATE_STARTED");
                mContinue = true;
                mTrack.play();
                decode();
            case STATE_STARTED:
                break;
            case STATE_PAUSED:
                mCurrentState = STATE_STARTED;
                Log.d(SoundService.TAG_API, "State changed to STATE_STARTED");
                synchronized (mDecoderLock) {
                    mDecoderLock.notify();
                }
                mTrack.play();
                break;
            default:
                mCurrentState = STATE_ERROR;
                Log.d(SoundService.TAG_API, "State changed to STATE_ERROR in start");
                if (mTrack != null) {
                    error();
                } else {
                    Log.d("start",
                            "Attempting to start while in idle after construction.  Not allowed by no callbacks called");
                }
        }
    }

    public void release() {
        reset();
        errorCallback = null;
        completionCallback = null;
        bufferingUpdateCallback = null;
        infoCallback = null;
        pitchAdjustmentAvailableChangedCallback = null;
        preparedCallback = null;
        seekCompleteCallback = null;
        speedAdjustmentAvailableChangedCallback = null;
        mCurrentState = STATE_END;
    }

    public void reset() {
        mLock.lock();
        mContinue = false;
        try {
            if (mDecoderThread != null
                    && mCurrentState != STATE_PLAYBACK_COMPLETED) {
                while (mIsDecoding) {
                    synchronized (mDecoderLock) {
                        mDecoderLock.notify();
                        mDecoderLock.wait();
                    }
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG_TRACK,
                    "Interrupted in reset while waiting for decoder thread to stop.",
                    e);
        }
        if (mCodec != null) {
            mCodec.release();
            mCodec = null;
        }
        if (mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }
        if (mTrack != null) {
            mTrack.release();
            mTrack = null;
        }
        mCurrentState = STATE_IDLE;
        Log.d(TAG_TRACK, "State changed to STATE_IDLE");
        mLock.unlock();
    }

    public void seekTo(final int msec) {
        switch (mCurrentState) {
            case STATE_PREPARED:
            case STATE_STARTED:
            case STATE_PAUSED:
            case STATE_PLAYBACK_COMPLETED:
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mLock.lock();
                        if (mTrack == null) {
                            return;
                        }
                        mTrack.flush();
                        mExtractor.seekTo(((long) msec * 1000),
                                MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        try {
                            seekCompleteCallback.onSeekComplete();
                        } catch (RemoteException e) {
                            // Binder should handle our death
                            Log.e(TAG_TRACK,
                                    "Received RemoteException trying to call onSeekComplete in seekTo",
                                    e);
                        }
                        mLock.unlock();
                    }
                });
                t.setDaemon(true);
                t.start();
                break;
            default:
                error();
        }
    }

    public void setDataSourceString(String path) {
        switch (mCurrentState) {
            case STATE_IDLE:
                mPath = path;
                mCurrentState = STATE_INITIALIZED;
                Log.d(TAG_TRACK, "Moving state to STATE_INITIALIZED");
                break;
            default:
                error();
        }
    }

    public void setDataSourceUri(Uri uri) {
        switch (mCurrentState) {
            case STATE_IDLE:
                mUri = uri;
                mCurrentState = STATE_INITIALIZED;
                Log.d(TAG_TRACK, "Moving state to STATE_INITIALIZED");
                break;
            default:
                error();
        }
    }

    public void setPlaybackPitch(float f) {
        mCurrentPitch = f;
    }

    public void setPlaybackSpeed(float f) {
        mCurrentSpeed = f;
    }

    public void setVolume(float left, float right) {
        // Pass call directly to AudioTrack if available.
        if (null != mTrack) {
            mTrack.setStereoVolume(left, right);
        }
    }

    public void error() {
        error(0);
    }

    public void error(int extra) {
        Log.e(TAG_TRACK, "Moved to error state!");
        mCurrentState = STATE_ERROR;
        try {
            boolean handled = errorCallback.onError(
                    MediaPlayer.MEDIA_ERROR_UNKNOWN, extra);
            if (!handled) {
                completionCallback.onCompletion();
            }
        } catch (RemoteException e) {
            // Binder should handle our death
            Log.e(TAG_TRACK,
                    "Received RemoteException when trying to call onCompletion in error state",
                    e);
        }
    }

    private int findFormatFromChannels(int numChannels) {
        switch (numChannels) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            default:
                return -1; // Error
        }
    }

    public void initStream() throws IOException {
        mLock.lock();
        mExtractor = new MediaExtractor();
        if (mPath != null) {
            mExtractor.setDataSource(mPath);
        } else if (mUri != null) {
            mExtractor.setDataSource(mContext, mUri, null);
        } else {
            throw new IOException();
        }

        final MediaFormat oFormat = mExtractor.getTrackFormat(TRACK_NUM);
        int sampleRate = oFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = oFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        final String mime = oFormat.getString(MediaFormat.KEY_MIME);
        mDuration = oFormat.getLong(MediaFormat.KEY_DURATION);

        Log.v(TAG_TRACK, "Sample rate: " + sampleRate);
        Log.v(TAG_TRACK, "Mime type: " + mime);

        initDevice(sampleRate, channelCount);
        mExtractor.selectTrack(TRACK_NUM);
        mCodec = MediaCodec.createDecoderByType(mime);
        mCodec.configure(oFormat, null, null, 0);
        mLock.unlock();
    }

    private void initDevice(int sampleRate, int numChannels) {
        mLock.lock();
        final int format = findFormatFromChannels(numChannels);
        final int minSize = AudioTrack.getMinBufferSize(sampleRate, format,
                AudioFormat.ENCODING_PCM_16BIT);
        mTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, format,
                AudioFormat.ENCODING_PCM_16BIT, minSize * 4,
                AudioTrack.MODE_STREAM);
        mSonic = new Sonic(sampleRate, numChannels);
        mLock.unlock();
    }

    public void decode() {
        mDecoderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mIsDecoding = true;
                mCodec.start();

                ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = mCodec.getOutputBuffers();

                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;

                while (!sawInputEOS && !sawOutputEOS && mContinue) {
                    if (mCurrentState == STATE_PAUSED) {
                        System.out.println("Decoder changed to PAUSED");
                        try {
                            synchronized (mDecoderLock) {
                                mDecoderLock.wait();
                                System.out.println("Done with wait");
                            }
                        } catch (InterruptedException e) {
                            // Purposely not doing anything here
                        }
                        continue;
                    }

                    if (null != mSonic) {
                        mSonic.setSpeed(mCurrentSpeed);
                        mSonic.setPitch(mCurrentPitch);
                    }

                    int inputBufIndex = mCodec.dequeueInputBuffer(200);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = inputBuffers[inputBufIndex];
                        int sampleSize = mExtractor.readSampleData(dstBuf, 0);
                        long presentationTimeUs = 0;
                        if (sampleSize < 0) {
                            sawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = mExtractor.getSampleTime();
                        }
                        mCodec.queueInputBuffer(
                                inputBufIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                        : 0);
                        if (!sawInputEOS) {
                            mExtractor.advance();
                        }
                    }

                    final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    byte[] modifiedSamples = new byte[info.size];

                    int res;
                    do {
                        res = mCodec.dequeueOutputBuffer(info, 200);
                        if (res >= 0) {
                            int outputBufIndex = res;
                            ByteBuffer buf = outputBuffers[outputBufIndex];
                            final byte[] chunk = new byte[info.size];
                            outputBuffers[res].get(chunk);
                            outputBuffers[res].clear();

                            if (chunk.length > 0) {
                                mSonic.putBytes(chunk, chunk.length);
                            } else {
                                mSonic.flush();
                            }
                            int available = mSonic.availableBytes();
                            if (available > 0) {
                                if (modifiedSamples.length < available) {
                                    modifiedSamples = new byte[available];
                                }
                                mSonic.receiveBytes(modifiedSamples, available);
                                mTrack.write(modifiedSamples, 0, available);
                            }

                            mCodec.releaseOutputBuffer(outputBufIndex, false);

                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true;
                            }
                        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            outputBuffers = mCodec.getOutputBuffers();
                            Log.d("PCM", "Output buffers changed");
                        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            mTrack.stop();
                            mLock.lock();
                            mTrack.release();
                            final MediaFormat oformat = mCodec
                                    .getOutputFormat();
                            Log.d("PCM", "Output format has changed to"
                                    + oformat);
                            initDevice(
                                    oformat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                    oformat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                            outputBuffers = mCodec.getOutputBuffers();
                            mTrack.play();
                            mLock.unlock();
                        }
                    } while (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                            || res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
                }
                Log.d(TAG_TRACK,
                        "Decoding loop exited. Stopping codec and track");
                Log.d(TAG_TRACK, "Duration: " + (int) (mDuration / 1000));
                Log.d(TAG_TRACK,
                        "Current position: "
                                + (int) (mExtractor.getSampleTime() / 1000));
                mCodec.stop();
                mTrack.stop();
                Log.d(TAG_TRACK, "Stopped codec and track");
                Log.d(TAG_TRACK,
                        "Current position: "
                                + (int) (mExtractor.getSampleTime() / 1000));
                mIsDecoding = false;
                if (mContinue && (sawInputEOS || sawOutputEOS)) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                completionCallback.onCompletion();
                            } catch (RemoteException e) {
                                // Binder should handle our death
                                Log.e(TAG_TRACK,
                                        "RemoteException trying to call onCompletion after decoding",
                                        e);
                            }
                        }
                    });
                    t.setDaemon(true);
                    t.start();
                } else {
                    Log.d(TAG_TRACK,
                            "Loop ended before saw input eos or output eos");
                    Log.d(TAG_TRACK, "sawInputEOS: " + sawInputEOS);
                    Log.d(TAG_TRACK, "sawOutputEOS: " + sawOutputEOS);
                }
                synchronized (mDecoderLock) {
                    mDecoderLock.notifyAll();
                }
            }
        });
        mDecoderThread.setDaemon(true);
        mDecoderThread.start();
    }
}

