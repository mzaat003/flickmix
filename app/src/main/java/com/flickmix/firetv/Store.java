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

    private static Store sInstance;

    private SharedPreferences prefs;
    private final List<Source> sources = new ArrayList<>();
    private final List<Title> titles = new ArrayList<>();
    private final List<String> favourites = new ArrayList<>();
    /** titleId -> position in ms */
    private final Map<String, Long> resume = new HashMap<>();

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
        try {
            JSONArray a = new JSONArray(prefs.getString(K_SOURCES, "[]"));
            for (int i = 0; i < a.length(); i++) sources.add(Source.fromJson(a.getJSONObject(i)));

            JSONArray t = new JSONArray(prefs.getString(K_TITLES, "[]"));
            for (int i = 0; i < t.length(); i++) titles.add(Title.fromJson(t.getJSONObject(i)));

            JSONArray f = new JSONArray(prefs.getString(K_FAVS, "[]"));
            for (int i = 0; i < f.length(); i++) favourites.add(f.getString(i));

            JSONObject r = new JSONObject(prefs.getString(K_RESUME, "{}"));
            for (java.util.Iterator<String> it = r.keys(); it.hasNext(); ) {
                String k = it.next();
                resume.put(k, r.optLong(k, 0));
            }
        } catch (Exception ignored) { }
    }

    private void persist() {
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

            prefs.edit()
                    .putString(K_SOURCES, a.toString())
                    .putString(K_TITLES, t.toString())
                    .putString(K_FAVS, f.toString())
                    .putString(K_RESUME, r.toString())
                    .apply();
        } catch (Exception ignored) { }
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

    public List<Title> continueWatching() {
        List<Title> out = new ArrayList<>();
        for (String id : resume.keySet()) {
            Title t = titleById(id);
            if (t != null) out.add(t);
        }
        return out;
    }
}
