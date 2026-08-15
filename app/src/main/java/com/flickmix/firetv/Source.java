package com.flickmix.firetv;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One configurable source slot. FlickMix supports up to {@link Store#MAX_SOURCES}
 * of these; the user names them, orders them, enables/disables them and points
 * them at whatever they have the right to play.
 *
 * FlickMix ships with none preconfigured on purpose. It is a shell around
 * whatever you supply -- it does not scrape, log into, or extract media from
 * third-party sites, and it does not defeat DRM or access controls.
 */
public class Source {

    /** A hand-entered list of individual playable items (direct file / HLS / DASH URLs). */
    public static final int TYPE_DIRECT = 0;
    /** An M3U / M3U8 playlist URL that expands into many items. */
    public static final int TYPE_M3U = 1;
    /** A web page, opened on demand in the built-in D-pad browser. */
    public static final int TYPE_WEB = 2;

    public String id;
    public String name;
    public int type;
    /** Playlist URL for TYPE_M3U, home page for TYPE_WEB, unused for TYPE_DIRECT. */
    public String url;
    public boolean enabled = true;

    public Source(String id, String name, int type, String url) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.url = url == null ? "" : url;
    }

    public String typeLabel() {
        switch (type) {
            case TYPE_M3U: return "Playlist";
            case TYPE_WEB: return "Web";
            default: return "Direct";
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("type", type);
        o.put("url", url);
        o.put("enabled", enabled);
        return o;
    }

    public static Source fromJson(JSONObject o) {
        Source s = new Source(
                o.optString("id"),
                o.optString("name", "Source"),
                o.optInt("type", TYPE_DIRECT),
                o.optString("url"));
        s.enabled = o.optBoolean("enabled", true);
        return s;
    }
}
