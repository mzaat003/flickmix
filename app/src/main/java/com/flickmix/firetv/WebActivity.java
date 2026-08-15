package com.flickmix.firetv;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * A plain, D-pad-drivable web view for source slots of TYPE_WEB.
 *
 * It renders the page as the site publishes it: scripts run, the site's own
 * layout and its own ads are intact, and nothing is stripped, injected, or
 * lifted out. It is a viewport, not a harvester -- FlickMix does not read a
 * site's markup to pull media into the catalog.
 *
 * It also runs in its own process (see the manifest) and is destroyed on exit,
 * so it can never sit resident eating Fire Stick memory behind the player.
 */
public class WebActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";

    private WebView web;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_web);

        web = findViewById(R.id.webView);
        progress = findViewById(R.id.webProgress);
        TextView titleView = findViewById(R.id.webTitle);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        String url = getIntent().getStringExtra(EXTRA_URL);
        titleView.setText(name == null ? "Browser" : name);

        findViewById(R.id.webBack).setOnClickListener(v -> finish());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Match mobile Chrome's compatibility posture: an https page may still
        // pull its video over http, which a strict WebView silently drops.
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Identify honestly. Do not impersonate another browser to slip past
        // a site's device or bot checks.
        s.setUserAgentString(s.getUserAgentString() + " FlickMix/1.0");

        web.setBackgroundColor(0xFF000000);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return false; // follow links normally
            }

            // A page that fails to load must say so; a silent black screen
            // reads as a broken app. (Legacy signature: it is the one that
            // fires only for the main page, and it works on every API level
            // this app supports.)
            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView v, int code, String desc, String failingUrl) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(WebActivity.this,
                        "That site did not load: " + desc, Toast.LENGTH_LONG).show();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? ProgressBar.GONE : ProgressBar.VISIBLE);
            }
        });

        if (url != null && !url.isEmpty()) {
            web.loadUrl(url);
        }
        web.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // The Fire remote has no mouse. Up/Down scroll the page; the WebView's
        // own spatial navigation handles link focus.
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                web.pageUp(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                web.pageDown(false);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Back walks the page history first, then leaves. It never traps.
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.onPause();
            web.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.resumeTimers();
            web.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        // Full teardown: no lingering WebView, no residual cache in RAM.
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.clearHistory();
            web.clearCache(true);
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.removeAllViews();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
