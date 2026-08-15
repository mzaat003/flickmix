package com.flickmix.firetv;

import org.json.JSONException;
import org.json.JSONObject;

/** One playable entry inside a source. */
public class Title {

    public static final int CAT_MOVIE = 0;
    public static final int CAT_TV = 1;
    public static final int CAT_ANIME = 2;
    public static final int CAT_OTHER = 3;

    public String id;
    public String sourceId;
    public String title;
    public String year = "";
    public String runtime = "";
    public String rating = "";
    public String description = "";
    public String posterUrl = "";
    public String backdropUrl = "";
    /** Direct media URL: .mp4 / .mkv / .m3u8 / .mpd, or a local file:// path. */
    public String streamUrl = "";
    public int category = CAT_MOVIE;

    public Title(String id, String sourceId, String title, String streamUrl) {
        this.id = id;
        this.sourceId = sourceId;
        this.title = title;
        this.streamUrl = streamUrl;
    }

    public String metaLine() {
        StringBuilder sb = new StringBuilder();
        if (!year.isEmpty()) sb.append(year);
        if (!runtime.isEmpty()) {
            if (sb.length() > 0) sb.append("  \u2022  ");
            sb.append(runtime);
        }
        return sb.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("sourceId", sourceId);
        o.put("title", title);
        o.put("year", year);
        o.put("runtime", runtime);
        o.put("rating", rating);
        o.put("description", description);
        o.put("posterUrl", posterUrl);
        o.put("backdropUrl", backdropUrl);
        o.put("streamUrl", streamUrl);
        o.put("category", category);
        return o;
    }

    public static Title fromJson(JSONObject o) {
        Title t = new Title(
                o.optString("id"),
                o.optString("sourceId"),
                o.optString("title", "Untitled"),
                o.optString("streamUrl"));
        t.year = o.optString("year", "");
        t.runtime = o.optString("runtime", "");
        t.rating = o.optString("rating", "");
        t.description = o.optString("description", "");
        t.posterUrl = o.optString("posterUrl", "");
        t.backdropUrl = o.optString("backdropUrl", "");
        t.category = o.optInt("category", CAT_MOVIE);
        return t;
    }
}
