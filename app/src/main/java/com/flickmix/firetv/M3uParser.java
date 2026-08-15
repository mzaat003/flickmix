package com.flickmix.firetv;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads a standard M3U / M3U8 playlist that the user supplies -- their own
 * media server export, a paid IPTV subscription they hold, a personal NAS
 * index, or any list they are entitled to play.
 *
 * This is a plain text-format parser. It does not log in, solve challenges,
 * strip protection, or pull media out of a web page.
 */
public class M3uParser {

    public interface Callback {
        void onLoaded(List<Title> titles);
        void onError(String message);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * Hard bounds for a 1 GB-RAM device. A text playlist line is never
     * kilobytes long -- hitting MAX_LINE means someone pointed a Playlist slot
     * at a binary file (an .mp4, a .ts stream), and reading on would OOM the
     * app. MAX_TITLES keeps a 100k-entry IPTV export from freezing the UI and
     * pinning megabytes of JSON in memory forever.
     */
    private static final int MAX_LINE = 8_192;
    private static final int MAX_TITLES = 5_000;
    private static final int MAX_LINES = 200_000;

    public static void load(final String sourceId, final String playlistUrl, final Callback cb) {
        POOL.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(playlistUrl);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "FlickMix/1.0 (Fire TV)");
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code != 200) {
                    fail(cb, "Playlist returned HTTP " + code);
                    return;
                }

                List<Title> out = new ArrayList<>();
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                String pendingName = null;
                String pendingLogo = null;
                String pendingGroup = null;
                int lines = 0;

                while ((line = readBoundedLine(r)) != null) {
                    if (++lines > MAX_LINES || out.size() >= MAX_TITLES) break;
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith("#EXTINF")) {
                        pendingLogo = attr(line, "tvg-logo");
                        pendingGroup = attr(line, "group-title");
                        int comma = line.lastIndexOf(',');
                        pendingName = comma >= 0 && comma < line.length() - 1
                                ? line.substring(comma + 1).trim()
                                : "Untitled";
                    } else if (!line.startsWith("#")) {
                        String name = pendingName != null ? pendingName : "Untitled";
                        Title t = new Title(UUID.randomUUID().toString(), sourceId, name, line);
                        if (pendingLogo != null) t.posterUrl = pendingLogo;
                        t.category = categorise(pendingGroup);
                        if (pendingGroup != null) t.description = pendingGroup;
                        out.add(t);
                        pendingName = null;
                        pendingLogo = null;
                        pendingGroup = null;
                    }
                }
                r.close();

                if (out.isEmpty()) {
                    fail(cb, "No entries found. Is this a valid M3U playlist?");
                } else {
                    final List<Title> result = out;
                    MAIN.post(() -> cb.onLoaded(result));
                }
            } catch (Exception e) {
                fail(cb, e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void fail(Callback cb, String msg) {
        MAIN.post(() -> cb.onError(msg));
    }

    /**
     * readLine() with a ceiling. BufferedReader.readLine() on a giant binary
     * with no newlines builds one enormous String and OOMs the app before any
     * length check could run; this reads char by char and aborts early.
     */
    private static String readBoundedLine(BufferedReader r) throws java.io.IOException {
        StringBuilder sb = new StringBuilder(96);
        int c;
        while ((c = r.read()) != -1) {
            if (c == '\n') return sb.toString();
            if (c == '\r') continue;
            if (sb.length() >= MAX_LINE) {
                throw new java.io.IOException(
                        "This is not a text playlist (line longer than " + MAX_LINE + " chars)");
            }
            sb.append((char) c);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String attr(String line, String key) {
        String needle = key + "=\"";
        int i = line.indexOf(needle);
        if (i < 0) return null;
        int start = i + needle.length();
        int end = line.indexOf('"', start);
        if (end < 0) return null;
        String v = line.substring(start, end).trim();
        return v.isEmpty() ? null : v;
    }

    private static int categorise(String group) {
        if (group == null) return Title.CAT_OTHER;
        String g = group.toLowerCase();
        if (g.contains("anime")) return Title.CAT_ANIME;
        if (g.contains("series") || g.contains("tv") || g.contains("show")) return Title.CAT_TV;
        if (g.contains("movie") || g.contains("film") || g.contains("vod")) return Title.CAT_MOVIE;
        return Title.CAT_OTHER;
    }
}
