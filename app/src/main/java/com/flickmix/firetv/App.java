package com.flickmix.firetv;

import android.app.Application;
import android.content.ComponentCallbacks2;

import com.bumptech.glide.Glide;

/**
 * Fire Stick 3rd gen has very little headroom. Two rules live here:
 * 1. Trim the artwork cache aggressively when the system asks.
 * 2. Never hold web content or catalog bitmaps alive while video plays --
 *    PlayerActivity calls {@link #releaseForPlayback()} on start.
 */
public class App extends Application {

    private static App sInstance;

    public static App get() { return sInstance; }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Store.get().load(this);
    }

    /** Called when the player takes over the screen: give video the memory. */
    public void releaseForPlayback() {
        try {
            Glide.get(this).clearMemory();
        } catch (Throwable ignored) { }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        try {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                Glide.get(this).clearMemory();
            } else {
                Glide.get(this).trimMemory(level);
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        try { Glide.get(this).clearMemory(); } catch (Throwable ignored) { }
    }
}
