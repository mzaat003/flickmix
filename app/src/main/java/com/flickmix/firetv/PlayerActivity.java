package com.flickmix.firetv;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player. Design follows the approved reference with one deliberate
 * omission: there is no X-Ray panel.
 *
 * Menus are built from the tracks the stream actually declares. If a stream
 * carries one audio track and no subtitles, the AUDIO menu shows one entry and
 * the SUBTITLES menu says so. Nothing here fakes a capability the media does
 * not have.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE_ID = "titleId";

    private static final long SKIP_MS = 10_000L;
    private static final long HIDE_DELAY_MS = 4_000L;

    private PlayerView playerView;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;

    private View overlay;
    private ProgressBar buffering;
    private SeekBar seekBar;
    private TextView playBtn, timeCurrent, timeTotal, playerTitle, playerMeta, sourceLabel, favBtn;

    private Title title;
    private boolean scrubbing = false;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            updateProgress();
            ui.postDelayed(this, 500L);
        }
    };

    private final Runnable hideOverlay = () -> setOverlayVisible(false);

    private final Runnable commitSeek = new Runnable() {
        @Override public void run() {
            if (player != null && player.getDuration() > 0) {
                player.seekTo(player.getDuration() * seekBar.getProgress() / 1000L);
            }
            scrubbing = false;
        }
    };

    // ------------------------------------------------------------------

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_player);

        // Hand memory to video: drop cached artwork before the first frame.
        App.get().releaseForPlayback();

        playerView = findViewById(R.id.playerView);
        overlay = findViewById(R.id.overlay);
        buffering = findViewById(R.id.buffering);
        seekBar = findViewById(R.id.seekBar);
        playBtn = findViewById(R.id.playBtn);
        timeCurrent = findViewById(R.id.timeCurrent);
        timeTotal = findViewById(R.id.timeTotal);
        playerTitle = findViewById(R.id.playerTitle);
        playerMeta = findViewById(R.id.playerMeta);
        sourceLabel = findViewById(R.id.sourceLabel);

        String titleId = getIntent().getStringExtra(EXTRA_TITLE_ID);
        title = Store.get().titleById(titleId);
        if (title == null) {
            Toast.makeText(this, "That title is no longer available.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        playerTitle.setText(title.title);
        playerMeta.setText(title.metaLine());
        Source src = Store.get().sourceById(title.sourceId);
        sourceLabel.setText(src != null ? src.name : "");

        favBtn = findViewById(R.id.favBtn);
        refreshFav();
        favBtn.setOnClickListener(v -> {
            Store.get().toggleFavourite(title.id);
            refreshFav();
            showOverlay();
        });

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        playBtn.setOnClickListener(v -> togglePlay());
        findViewById(R.id.rewindBtn).setOnClickListener(v -> skip(-SKIP_MS));
        findViewById(R.id.forwardBtn).setOnClickListener(v -> skip(SKIP_MS));
        findViewById(R.id.subsBtn).setOnClickListener(v -> showTrackMenu(C.TRACK_TYPE_TEXT, "Subtitles"));
        findViewById(R.id.audioBtn).setOnClickListener(v -> showTrackMenu(C.TRACK_TYPE_AUDIO, "Audio"));
        findViewById(R.id.qualityBtn).setOnClickListener(v -> showQualityMenu());
        findViewById(R.id.speedBtn).setOnClickListener(v -> showSpeedMenu());
        findViewById(R.id.serverBtn).setOnClickListener(v -> showSourceMenu());

        // D-pad LEFT/RIGHT on a focused SeekBar changes progress with
        // fromUser=true but never fires the touch-tracking callbacks -- and the
        // Fire remote has no touch at all. So the seek is committed from
        // onProgressChanged with a short debounce; touch release commits
        // immediately.
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) return;
                scrubbing = true;
                if (player != null && player.getDuration() > 0) {
                    timeCurrent.setText(fmt(player.getDuration() * p / 1000L));
                }
                ui.removeCallbacks(commitSeek);
                ui.postDelayed(commitSeek, 600L);
                showOverlay();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                scrubbing = true;
                ui.removeCallbacks(commitSeek);
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                ui.removeCallbacks(commitSeek);
                commitSeek.run();
            }
        });

        initPlayer();
    }

    // ------------------------------------------------------------------

    private void initPlayer() {
        trackSelector = new DefaultTrackSelector(this);
        // Auto quality: let ABR pick, but never above the panel's own 1080p.
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setMaxVideoSize(1920, 1080)
                .setForceHighestSupportedBitrate(false));

        // Conservative buffering: a 3rd-gen Fire Stick has little RAM to spare,
        // and an over-eager buffer is what causes the OOM stutter people blame
        // on "lag".
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        15_000,   // min buffer
                        50_000,   // max buffer
                        2_000,    // buffer before playback starts
                        5_000)    // buffer before playback resumes after a rebuffer
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBackBuffer(10_000, true)
                .build();

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);

        player = new ExoPlayer.Builder(this, renderers)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();

        playerView.setPlayer(player);
        playerView.setKeepScreenOn(true);

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                buffering.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) {
                    timeTotal.setText(fmt(player.getDuration()));
                }
                if (state == Player.STATE_ENDED) {
                    Store.get().saveResume(title.id, 0, player.getDuration());
                    playNextOrFinish();
                }
            }

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                playBtn.setText(isPlaying ? "PAUSE" : "PLAY");
            }

            @Override public void onPlayerError(PlaybackException error) {
                showError(error);
            }

            @Override public void onTracksChanged(Tracks tracks) {
                // Nothing to do -- menus read live from the player when opened.
            }
        });

        String url = title.streamUrl;
        if (url == null || url.trim().isEmpty()) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("No stream URL")
                    .setMessage("This title has no playable URL attached. Open Sources and add "
                            + "the direct media link for it.")
                    .setPositiveButton("OK", (d, w) -> finish())
                    .setOnDismissListener(d -> finish())
                    .show();
            return;
        }

        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();

        long resume = Store.get().resumePosition(title.id);
        if (resume > 0) {
            player.seekTo(resume);
            Toast.makeText(this, "Resuming from " + fmt(resume), Toast.LENGTH_SHORT).show();
        }
        player.setPlayWhenReady(true);

        ui.post(progressTick);
        scheduleHide();
    }

    /**
     * Up Next: when a title plays to the end, continue with the next entry in
     * the same source (reference-art behaviour). BACK still exits at any time;
     * the last entry falls through to the detail page as before.
     */
    private void playNextOrFinish() {
        Title next = Store.get().nextTitle(title.id);
        if (next == null || player == null || !Store.get().autoplayNext()) {
            finish();
            return;
        }
        title = next;
        playerTitle.setText(title.title);
        playerMeta.setText(title.metaLine());
        Source s = Store.get().sourceById(title.sourceId);
        sourceLabel.setText(s != null ? s.name : "");
        refreshFav();
        Toast.makeText(this, "Up Next:  " + title.title, Toast.LENGTH_LONG).show();
        player.setMediaItem(MediaItem.fromUri(title.streamUrl));
        player.prepare();
        player.setPlayWhenReady(true);
        showOverlay();
    }

    private void refreshFav() {
        boolean fav = Store.get().isFavourite(title.id);
        favBtn.setText(fav ? "♥" : "♡");
        favBtn.setTextColor(fav ? 0xFF7CFC00 : 0xFFFFFFFF);
    }

    private void showError(PlaybackException error) {
        String detail;
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                detail = "The stream could not be reached. Check the network or the URL.";
                break;
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                detail = "The server refused the request. The link may have expired.";
                break;
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
                detail = "This Fire Stick cannot decode that format.";
                break;
            case PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR:
                detail = "This stream is DRM-protected. Play it in its own licensed app.";
                break;
            default:
                detail = error.getErrorCodeName();
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Playback stopped")
                .setMessage(detail)
                .setPositiveButton("Retry", (d, w) -> {
                    player.prepare();
                    player.setPlayWhenReady(true);
                })
                .setNegativeButton("Back", (d, w) -> finish())
                .show();
    }

    // ------------------------------------------------------------------
    // Menus, built from live track info
    // ------------------------------------------------------------------

    private void showTrackMenu(int trackType, String heading) {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();

        final List<String> labels = new ArrayList<>();
        final List<TrackGroup> groups = new ArrayList<>();
        final List<Integer> indices = new ArrayList<>();

        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != trackType) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                Format f = g.getTrackFormat(i);
                labels.add(describe(f, trackType));
                groups.add(g.getMediaTrackGroup());
                indices.add(i);
            }
        }

        if (labels.isEmpty()) {
            Toast.makeText(this,
                    "This stream provides no " + heading.toLowerCase() + " tracks.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Subtitles get an explicit Off; audio always has to play something.
        final boolean allowOff = trackType == C.TRACK_TYPE_TEXT;
        if (allowOff) labels.add(0, "Off");

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(heading)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (allowOff && which == 0) {
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                                .clearOverridesOfType(trackType)
                                .setTrackTypeDisabled(trackType, true));
                        return;
                    }
                    int idx = allowOff ? which - 1 : which;
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setTrackTypeDisabled(trackType, false)
                            .clearOverridesOfType(trackType)
                            .addOverride(new TrackSelectionOverride(
                                    groups.get(idx), indices.get(idx))));
                })
                .setOnDismissListener(d -> scheduleHide())
                .show();
    }

    private String describe(Format f, int trackType) {
        StringBuilder sb = new StringBuilder();
        if (f.label != null) {
            sb.append(f.label);
        } else if (f.language != null) {
            sb.append(new Locale(f.language).getDisplayLanguage());
        } else {
            sb.append("Track");
        }
        if (trackType == C.TRACK_TYPE_AUDIO && f.channelCount > 0) {
            sb.append(f.channelCount >= 6 ? "  [5.1]" : "  [Stereo]");
        }
        if (trackType == C.TRACK_TYPE_TEXT && f.language != null
                && f.language.toLowerCase().startsWith("ar")) {
            sb.append("   \u0639\u0631\u0628\u064A");
        }
        return sb.toString();
    }

    private void showQualityMenu() {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();

        final List<String> labels = new ArrayList<>();
        final List<TrackGroup> groups = new ArrayList<>();
        final List<Integer> indices = new ArrayList<>();

        labels.add("Auto  \u2014  adapts to your connection");

        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                Format f = g.getTrackFormat(i);
                if (f.height <= 0) continue;
                String rate = f.bitrate > 0
                        ? String.format(Locale.US, "  \u2014  %.1f Mbps", f.bitrate / 1_000_000f)
                        : "";
                labels.add(f.height + "p" + rate);
                groups.add(g.getMediaTrackGroup());
                indices.add(i);
            }
        }

        if (labels.size() == 1) {
            Toast.makeText(this,
                    "This stream offers a single rendition, so there is nothing to switch between.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Quality")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    // Position is preserved: overriding the track selection does not
                    // reset the timeline or the surface.
                    if (which == 0) {
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .setMaxVideoSize(1920, 1080));
                    } else {
                        int idx = which - 1;
                        trackSelector.setParameters(trackSelector.buildUponParameters()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .addOverride(new TrackSelectionOverride(
                                        groups.get(idx), indices.get(idx))));
                    }
                })
                .setOnDismissListener(d -> scheduleHide())
                .show();
    }

    private void showSpeedMenu() {
        final float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        final String[] labels = {"0.75x", "Normal", "1.25x", "1.5x", "2x"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Playback speed")
                .setItems(labels, (d, which) ->
                        player.setPlaybackParameters(new PlaybackParameters(speeds[which])))
                .setOnDismissListener(d -> scheduleHide())
                .show();
    }

    /**
     * Switching between alternate copies of the same title that the user has
     * added themselves -- e.g. a local file and a remote copy on their server.
     * Playback position carries over.
     */
    private void showSourceMenu() {
        final List<Title> alts = new ArrayList<>();
        for (Title t : Store.get().titles()) {
            if (t.title.equalsIgnoreCase(title.title) && !t.id.equals(title.id)) alts.add(t);
        }

        final boolean auto = Store.get().autoplayNext();
        final List<String> labels = new ArrayList<>();
        for (int i = 0; i < alts.size(); i++) {
            Source s = Store.get().sourceById(alts.get(i).sourceId);
            labels.add("Source " + (i + 2) + "  \u2014  " + (s != null ? s.name : "Unknown"));
        }
        labels.add("Autoplay next:  " + (auto ? "ON" : "OFF"));

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Video source")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == labels.size() - 1) {
                        Store.get().setAutoplayNext(!auto);
                        Toast.makeText(this, "Autoplay next "
                                + (!auto ? "on" : "off"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long pos = player.getCurrentPosition();
                    title = alts.get(which);
                    player.setMediaItem(MediaItem.fromUri(title.streamUrl));
                    player.prepare();
                    player.seekTo(pos);
                    player.setPlayWhenReady(true);
                    Source s = Store.get().sourceById(title.sourceId);
                    sourceLabel.setText(s != null ? s.name : "");
                    refreshFav();
                })
                .setOnDismissListener(d -> scheduleHide())
                .show();
    }

    // ------------------------------------------------------------------
    // Transport + overlay
    // ------------------------------------------------------------------

    private void togglePlay() {
        if (player == null) return;
        player.setPlayWhenReady(!player.getPlayWhenReady());
        showOverlay();
    }

    private void skip(long deltaMs) {
        if (player == null) return;
        long target = player.getCurrentPosition() + deltaMs;
        long dur = player.getDuration();
        if (target < 0) target = 0;
        if (dur > 0 && target > dur) target = dur;
        player.seekTo(target);
        showOverlay();
    }

    private void updateProgress() {
        if (player == null || scrubbing) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        timeCurrent.setText(fmt(pos));
        if (dur > 0) {
            timeTotal.setText(fmt(dur));
            seekBar.setProgress((int) (pos * 1000L / dur));
        }
    }

    private void setOverlayVisible(boolean visible) {
        overlay.animate().alpha(visible ? 1f : 0f).setDuration(180)
                .withStartAction(() -> { if (visible) overlay.setVisibility(View.VISIBLE); })
                .withEndAction(() -> { if (!visible) overlay.setVisibility(View.INVISIBLE); })
                .start();
    }

    private void showOverlay() {
        setOverlayVisible(true);
        scheduleHide();
    }

    private void scheduleHide() {
        ui.removeCallbacks(hideOverlay);
        ui.postDelayed(hideOverlay, HIDE_DELAY_MS);
    }

    // ------------------------------------------------------------------
    // Fire remote
    // ------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean overlayHidden = overlay.getVisibility() != View.VISIBLE || overlay.getAlpha() < 0.5f;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // With controls hidden, OK is play/pause. With them shown, let
                // focus handle it so buttons stay clickable.
                if (overlayHidden || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    togglePlay();
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (overlayHidden || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                    skip(-SKIP_MS);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (overlayHidden || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    skip(SKIP_MS);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (overlayHidden) {
                    showOverlay();
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (player != null) player.setPlayWhenReady(true);
                return true;

            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (player != null) player.setPlayWhenReady(false);
                return true;

            case KeyEvent.KEYCODE_MENU:
                showQualityMenu();
                return true;
        }

        showOverlay();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Back from the player always returns to the detail page it came from.
        // It never dumps the user out to the Fire TV home screen.
        super.onBackPressed();
    }

    // ------------------------------------------------------------------
    // Lifecycle: release everything, every time
    // ------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        // onStop released the player to hand memory back while the app was in
        // the background; coming back needs a fresh one or the screen is dead.
        if (player == null && title != null) {
            initPlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            saveResume();
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }

    private void saveResume() {
        if (player == null || title == null) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        // This is a live mid-session save (HOME press, screensaver, source
        // switch), not proof the user finished the film -- so keep the exact
        // position. Only a position within the last 5s counts as done here;
        // the real "watched to the end" cleanup happens on STATE_ENDED.
        if (dur > 0 && pos >= dur - 5_000L) {
            Store.get().saveResume(title.id, 0, dur);
        } else {
            Store.get().savePosition(title.id, pos, dur);
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        saveResume();
        ui.removeCallbacks(progressTick);
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    // ------------------------------------------------------------------

    private static String fmt(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000L;
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }
}
