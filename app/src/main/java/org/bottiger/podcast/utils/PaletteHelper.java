package org.bottiger.podcast.utils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.util.LruCache;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import org.bottiger.podcast.listeners.PaletteListener;

/**
 * Created by apl on 10-11-2014.
 */
public class PaletteHelper {

    private static final int CACHE_SIZE = 40;
    private static final int PALETTE_SIZE = 24; /* 24 is default size. You can decrease this value to speed up palette generation */

    private static LruCache<String, Palette> mPaletteCache = new LruCache<>(CACHE_SIZE);

    public static void generate(@NonNull final String argUrl, @NonNull final Activity argActivity, @Nullable final PaletteListener ... argCallbacks) {

        if (!StrUtils.isValidUrl(argUrl))
            return;

        Palette palette = mPaletteCache.get(argUrl);

        if (palette != null && argCallbacks != null) {
            for (PaletteListener callback : argCallbacks)
                callback.onPaletteFound(palette);
            return;
        }

        Glide.with(argActivity)
                .load(argUrl)
                .asBitmap()
                .into(new SimpleTarget<Bitmap>(200, 200) {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {

                        Palette.from(resource).generate(new Palette.PaletteAsyncListener() {
                            @Override
                            public void onGenerated(final Palette palette) {

                                if (palette != null) {
                                    mPaletteCache.put(argUrl, palette);
                                }

                                if (argCallbacks != null) {
                                    for (final PaletteListener listener : argCallbacks) {
                                    argActivity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                        listener.onPaletteFound(palette);
                                        }
                                        });

                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                    }
                });
    }
}
