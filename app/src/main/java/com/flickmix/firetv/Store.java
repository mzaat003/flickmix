package com.flickmix.firetv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything FlickMix knows lives here, in SharedPreferences as JSON.
 * No network calls, no third-party accounts, no analytics.
 */
public class Store {

    public static final int MAX_SOURCES = 10;

    private static final String PREFS = "flickmix";
    private static final String K_SOURCES = "sources";
    private static final String K_TITLES = "titles";
    private static final String K_FAVS = "favs";
    private static final String K_RESUME = "resume";
    private static final String K_DURATIONS = "durations";
    private static final String K_SEEDED = "demoSeeded";

    private static Store sInstance;

    private SharedPreferences prefs;
    /** Bumped on every mutation so screens can skip needless rebuilds. */
    private int modCount;
    private final List<Source> sources = new ArrayList<>();
    private final List<Title> titles = new ArrayList<>();
    private final List<String> favourites = new ArrayList<>();
    /** titleId -> position in ms */
    private final Map<String, Long> resume = new HashMap<>();
    /** titleId -> last known duration in ms, so resume bars show a real percent */
    private final Map<String, Long> durations = new HashMap<>();

    public static synchronized Store get() {
        if (sInstance == null) sInstance = new Store();
        return sInstance;
    }

    public void load(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sources.clear();
        titles.clear();
        favourites.clear();
        resume.clear();
        // Each section fails independently: one corrupt value must not wipe
        // the rest (a shared catch would drop titles/favourites/resume and the
        // next persist() would make the loss permanent).
        try {
            JSONArray a = new JSONArray(prefs.getString(K_SOURCES, "[]"));
            for (int i = 0; i < a.length(); i++) sources.add(Source.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        try {
            JSONArray t = new JSONArray(prefs.getString(K_TITLES, "[]"));
            for (int i = 0; i < t.length(); i++) titles.add(Title.fromJson(t.getJSONObject(i)));
        } catch (Exception ignored) { }
        try {
            JSONArray f = new JSONArray(prefs.getString(K_FAVS, "[]"));
            for (int i = 0; i < f.length(); i++) favourites.add(f.getString(i));
        } catch (Exception ignored) { }
        try {
            JSONObject r = new JSONObject(prefs.getString(K_RESUME, "{}"));
            for (java.util.Iterator<String> it = r.keys(); it.hasNext(); ) {
                String k = it.next();
                resume.put(k, r.optLong(k, 0));
            }
        } catch (Exception ignored) { }
        try {
            JSONObject d = new JSONObject(prefs.getString(K_DURATIONS, "{}"));
            for (java.util.Iterator<String> it = d.keys(); it.hasNext(); ) {
                String k = it.next();
                durations.put(k, d.optLong(k, 0));
            }
        } catch (Exception ignored) { }
        modCount++;
    }

    public int modCount() { return modCount; }

    // ---------- player preferences ----------

    private static final String K_AUTOPLAY = "autoplayNext";

    public boolean autoplayNext() {
        return prefs == null || prefs.getBoolean(K_AUTOPLAY, true);
    }

    public void setAutoplayNext(boolean on) {
        if (prefs != null) prefs.edit().putBoolean(K_AUTOPLAY, on).apply();
    }

    private void persist() {
        modCount++;
        if (prefs == null) return;
        try {
            JSONArray a = new JSONArray();
            for (Source s : sources) a.put(s.toJson());

            JSONArray t = new JSONArray();
            for (Title x : titles) t.put(x.toJson());

            JSONArray f = new JSONArray();
            for (String id : favourites) f.put(id);

            JSONObject r = new JSONObject();
            for (Map.Entry<String, Long> e : resume.entrySet()) r.put(e.getKey(), e.getValue());

            JSONObject d = new JSONObject();
            for (Map.Entry<String, Long> e : durations.entrySet()) d.put(e.getKey(), e.getValue());

            prefs.edit()
                    .putString(K_SOURCES, a.toString())
                    .putString(K_TITLES, t.toString())
                    .putString(K_FAVS, f.toString())
                    .putString(K_RESUME, r.toString())
                    .putString(K_DURATIONS, d.toString())
                    .apply();
        } catch (Exception ignored) { }
    }

    /**
     * First run only: preload one "Demo" source of openly licensed films
     * (Blender Foundation open movies) plus Apple's public HLS test stream, so
     * the app has playable content out of the box and every player menu --
     * quality, subtitles, audio -- can be exercised before any real source is
     * added. Deleting the Demo slot is permanent; it is never re-seeded.
     */
    // The original seed pointed Tears of Steel at an engineering test stream
    // that has bitrate/resolution/codec figures burned into the picture
    // itself. Installs seeded before the fix get the clean encode swapped in.
    private static final String OLD_TOS_URL =
            "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8";
    private static final String NEW_TOS_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4";

    public void seedDemoOnce() {
        if (prefs == null) return;
        if (prefs.getBoolean(K_SEEDED, false)) {
            repairDemoStreams();
            return;
        }
        prefs.edit().putBoolean(K_SEEDED, true).apply();
        if (!sources.isEmpty()) return;

        Source demo = new Source(UUID.randomUUID().toString(), "Demo", Source.TYPE_DIRECT, "");
        sources.add(demo);

        addDemo(demo.id, "Big Buck Bunny", "2008", "10 min",
                "Blender Foundation open movie. A giant rabbit takes gentle revenge "
                        + "on three bullying rodents.",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "https://storage.googleapis.com/gtv-videos-bucket/sample/images_480x270/BigBuckBunny.jpg");

        addDemo(demo.id, "Sintel", "2010", "15 min",
                "Blender Foundation open movie. A lone girl searches for the dragon "
                        + "she once rescued. Adaptive HLS stream with multiple qualities.",
                "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                "");

        addDemo(demo.id, "Tears of Steel", "2012", "12 min",
                "Blender Foundation open movie. Sci-fi short mixing live action and "
                        + "CGI.",
                NEW_TOS_URL,
                "https://storage.googleapis.com/gtv-videos-bucket/sample/images_480x270/TearsOfSteel.jpg");

        addDemo(demo.id, "Elephants Dream", "2006", "11 min",
                "The first Blender Foundation open movie: two characters explore a "
                        + "strange mechanical world.",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "https://storage.googleapis.com/gtv-videos-bucket/sample/images_480x270/ElephantsDream.jpg");

        addDemo(demo.id, "Player Test Stream", "", "",
                "Apple's public HLS example stream. Declares several video "
                        + "qualities, alternate audio and closed captions, so the "
                        + "Quality, Audio and Subtitles menus are all testable here. "
                        + "The numbers stamped on the picture are part of Apple's "
                        + "test video itself, not the player.",
                "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                "");

        persist();
    }

    private void repairDemoStreams() {
        boolean changed = false;
        for (Title t : titles) {
            if (OLD_TOS_URL.equals(t.streamUrl)) {
                t.streamUrl = NEW_TOS_URL;
                t.description = "Blender Foundation open movie. Sci-fi short "
                        + "mixing live action and CGI.";
                changed = true;
            }
        }
        if (changed) persist();
    }

    private void addDemo(String sourceId, String name, String year, String runtime,
                         String description, String streamUrl, String posterUrl) {
        Title t = new Title(UUID.randomUUID().toString(), sourceId, name, streamUrl);
        t.year = year;
        t.runtime = runtime;
        t.description = description;
        t.posterUrl = posterUrl;
        t.category = Title.CAT_MOVIE;
        titles.add(t);
    }

    // ---------- sources ----------

    public List<Source> sources() { return sources; }

    public List<Source> enabledSources() {
        List<Source> out = new ArrayList<>();
        for (Source s : sources) if (s.enabled) out.add(s);
        return out;
    }

    public Source sourceById(String id) {
        for (Source s : sources) if (s.id.equals(id)) return s;
        return null;
    }

    public boolean canAddSource() { return sources.size() < MAX_SOURCES; }

    public Source addSource(String name, int type, String url) {
        if (!canAddSource()) return null;
        Source s = new Source(UUID.randomUUID().toString(), name, type, url);
        sources.add(s);
        persist();
        return s;
    }

    public void updateSource(Source s) { persist(); }

    public void removeSource(String id) {
        for (int i = sources.size() - 1; i >= 0; i--) {
            if (sources.get(i).id.equals(id)) sources.remove(i);
        }
        for (int i = titles.size() - 1; i >= 0; i--) {
            if (id.equals(titles.get(i).sourceId)) titles.remove(i);
        }
        persist();
    }

    public void moveSource(int from, int to) {
        if (from < 0 || to < 0 || from >= sources.size() || to >= sources.size()) return;
        Collections.swap(sources, from, to);
        persist();
    }

    // ---------- titles ----------

    public List<Title> titles() { return titles; }

    public Title titleById(String id) {
        for (Title t : titles) if (t.id.equals(id)) return t;
        return null;
    }

    public List<Title> titlesForSource(String sourceId) {
        List<Title> out = new ArrayList<>();
        for (Title t : titles) if (sourceId == null || sourceId.equals(t.sourceId)) out.add(t);
        return out;
    }

    public List<Title> titlesForSource(String sourceId, int category) {
        List<Title> out = new ArrayList<>();
        for (Title t : titles) {
            if ((sourceId == null || sourceId.equals(t.sourceId)) && t.category == category) out.add(t);
        }
        return out;
    }

    public Title addTitle(String sourceId, String name, String streamUrl) {
        Title t = new Title(UUID.randomUUID().toString(), sourceId, name, streamUrl);
        titles.add(t);
        persist();
        return t;
    }

    public void addTitles(List<Title> batch) {
        titles.addAll(batch);
        persist();
    }

    public void removeTitle(String id) {
        for (int i = titles.size() - 1; i >= 0; i--) {
            if (titles.get(i).id.equals(id)) titles.remove(i);
        }
        favourites.remove(id);
        resume.remove(id);
        persist();
    }

    public void clearTitlesForSource(String sourceId) {
        for (int i = titles.size() - 1; i >= 0; i--) {
            if (sourceId.equals(titles.get(i).sourceId)) titles.remove(i);
        }
        persist();
    }

    public List<Title> search(String query) {
        List<Title> out = new ArrayList<>();
        if (TextUtils.isEmpty(query)) return out;
        String q = query.toLowerCase();
        for (Title t : titles) if (t.title.toLowerCase().contains(q)) out.add(t);
        return out;
    }

    // ---------- my list ----------

    public boolean isFavourite(String titleId) { return favourites.contains(titleId); }

    public void toggleFavourite(String titleId) {
        if (favourites.contains(titleId)) favourites.remove(titleId);
        else favourites.add(0, titleId);
        persist();
    }

    public List<Title> favourites() {
        List<Title> out = new ArrayList<>();
        for (String id : favourites) {
            Title t = titleById(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    // ---------- continue watching ----------

    public long resumePosition(String titleId) {
        Long v = resume.get(titleId);
        return v == null ? 0L : v;
    }

    /** Store position; anything under 30s or within 90s of the end is treated as "done". */
    public void saveResume(String titleId, long positionMs, long durationMs) {
        if (positionMs < 30_000L || (durationMs > 0 && positionMs > durationMs - 90_000L)) {
            resume.remove(titleId);
        } else {
            resume.put(titleId, positionMs);
        }
        persist();
    }

    /**
     * Store the exact position with no "done" heuristic. Used for lifecycle
     * saves (HOME press, screensaver), where the user has not finished
     * anything -- they were interrupted.
     */
    public void savePosition(String titleId, long positionMs, long durationMs) {
        if (positionMs < 5_000L) return;   // nothing meaningful to resume
        resume.put(titleId, positionMs);
        if (durationMs > 0) durations.put(titleId, durationMs);
        persist();
    }

    /** Percent watched for the poster progress bar; clamped so it stays visible. */
    public int resumePercent(String titleId) {
        Long p = resume.get(titleId);
        if (p == null || p <= 0) return 0;
        Long d = durations.get(titleId);
        if (d == null || d <= 0) return 35;   // unknown duration: neutral marker
        return (int) Math.max(2, Math.min(98, p * 100 / d));
    }

    /**
     * The entry after this one within the same source, for Up Next autoplay.
     * Returns null when the title is last (or gone), never wraps around.
     */
    public Title nextTitle(String titleId) {
        Title current = titleById(titleId);
        if (current == null) return null;
        boolean seen = false;
        for (Title t : titles) {
            if (t.id.equals(titleId)) { seen = true; continue; }
            if (seen && current.sourceId.equals(t.sourceId)
                    && t.streamUrl != null && !t.streamUrl.trim().isEmpty()) {
                return t;
            }
        }
        return null;
    }

    public List<Title> continueWatching() {
        List<Title> out = new ArrayList<>();
        for (String id : resume.keySet()) {
            Title t = titleById(id);
            if (t != null) out.add(t);
        }
        return out;
    }
}
