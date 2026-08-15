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

        String process = currentProcessName();
        boolean mainProcess = process == null || getPackageName().equals(process);

        if (!mainProcess) {
            // WebActivity's ":web" process. From Android 9 a WebView in a
            // secondary process must claim its own data directory before first
            // use, or creating it throws. And this process must never touch
            // the Store: SharedPreferences is not multi-process safe, and a
            // second writer means whole-file last-writer-wins data loss.
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                try {
                    android.webkit.WebView.setDataDirectorySuffix("web");
                } catch (Throwable ignored) { }
            }
            return;
        }

        Store.get().load(this);
        Store.get().seedDemoOnce();
    }

    private String currentProcessName() {
        if (android.os.Build.VERSION.SDK_INT >= 28) return getProcessName();
        // Pre-P (Fire OS 5/6 sticks): the kernel-reported command line is the
        // process name.
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.FileReader("/proc/self/cmdline"))) {
            StringBuilder sb = new StringBuilder(64);
            int c;
            while ((c = r.read()) > 0) sb.append((char) c);
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
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
