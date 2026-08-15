package com.flickmix.firetv;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Add, rename, reorder, enable/disable, reload and delete the 10 source slots. */
public class SourcesActivity extends AppCompatActivity {

    private RecyclerView list;
    private TextView slotCount;

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_sources);

        list = findViewById(R.id.sourceList);
        slotCount = findViewById(R.id.slotCount);
        list.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.addBtn).setOnClickListener(v -> promptAddSource());
        findViewById(R.id.addTitleBtn).setOnClickListener(v -> promptAddTitle());
        findViewById(R.id.refreshBtn).setOnClickListener(v -> promptReload());

        refresh();
    }

    private void refresh() {
        List<Source> sources = Store.get().sources();
        slotCount.setText(sources.size() + " of " + Store.MAX_SOURCES + " slots used");
        list.setAdapter(new SourceRowAdapter(sources));
    }

    // ------------------------------------------------------------------

    private void promptAddSource() {
        if (!Store.get().canAddSource()) {
            Toast.makeText(this, "All " + Store.MAX_SOURCES + " slots are in use. "
                    + "Remove one first.", Toast.LENGTH_LONG).show();
            return;
        }

        final String[] types = {
                "Direct  \u2014  add individual video URLs by hand",
                "Playlist  \u2014  load an M3U/M3U8 you own",
                "Web  \u2014  open a site in the TV browser"
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Source type")
                .setItems(types, (d, which) -> collectSourceDetails(which))
                .show();
    }

    private void collectSourceDetails(final int type) {
        final EditText nameField = field("Name shown on the tab", InputType.TYPE_CLASS_TEXT);
        final EditText urlField = field(
                type == Source.TYPE_M3U ? "https://\u2026/playlist.m3u"
                        : type == Source.TYPE_WEB ? "https://\u2026"
                        : "(not needed for Direct)",
                InputType.TYPE_TEXT_VARIATION_URI);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 8);
        wrap.addView(nameField);
        if (type != Source.TYPE_DIRECT) wrap.addView(urlField);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("New source")
                .setView(wrap)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nameField.getText().toString().trim();
                    String url = urlField.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Give the source a name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (type != Source.TYPE_DIRECT && !url.startsWith("http")) {
                        Toast.makeText(this, "Enter a full http(s) URL.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Source s = Store.get().addSource(name, type, url);
                    refresh();
                    if (s != null && type == Source.TYPE_M3U) loadPlaylist(s);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptAddTitle() {
        final List<Source> sources = Store.get().sources();
        if (sources.isEmpty()) {
            Toast.makeText(this, "Add a source first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) names[i] = sources.get(i).name;

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Add title to which source?")
                .setItems(names, (d, which) -> collectTitleDetails(sources.get(which)))
                .show();
    }

    private void collectTitleDetails(final Source source) {
        final EditText nameField = field("Title", InputType.TYPE_CLASS_TEXT);
        final EditText urlField = field("Direct media URL (.mp4 / .m3u8 / .mpd)",
                InputType.TYPE_TEXT_VARIATION_URI);
        final EditText posterField = field("Poster image URL (optional)",
                InputType.TYPE_TEXT_VARIATION_URI);
        final EditText yearField = field("Year (optional)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(48, 24, 48, 8);
        wrap.addView(nameField);
        wrap.addView(urlField);
        wrap.addView(posterField);
        wrap.addView(yearField);

        final String[] cats = {"Movie", "TV Show", "Anime", "Other"};

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("New title in \u201C" + source.name + "\u201D")
                .setView(wrap)
                .setPositiveButton("Next", (d, w) -> {
                    final String name = nameField.getText().toString().trim();
                    final String url = urlField.getText().toString().trim();
                    if (name.isEmpty() || url.isEmpty()) {
                        Toast.makeText(this, "Title and URL are both required.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Category")
                            .setItems(cats, (d2, cat) -> {
                                Title t = Store.get().addTitle(source.id, name, url);
                                t.posterUrl = posterField.getText().toString().trim();
                                t.year = yearField.getText().toString().trim();
                                t.category = cat;
                                Store.get().updateSource(source);
                                Toast.makeText(this, "Added \u201C" + name + "\u201D",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptReload() {
        final List<Source> sources = Store.get().sources();
        final java.util.List<Source> playlists = new java.util.ArrayList<>();
        for (Source s : sources) if (s.type == Source.TYPE_M3U) playlists.add(s);

        if (playlists.isEmpty()) {
            Toast.makeText(this, "No playlist sources configured.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Reload which playlist?")
                .setItems(names, (d, which) -> loadPlaylist(playlists.get(which)))
                .show();
    }

    private void loadPlaylist(final Source source) {
        Toast.makeText(this, "Loading " + source.name + "\u2026", Toast.LENGTH_SHORT).show();
        M3uParser.load(source.id, source.url, new M3uParser.Callback() {
            @Override public void onLoaded(List<Title> titles) {
                Store.get().clearTitlesForSource(source.id);
                Store.get().addTitles(titles);
                Toast.makeText(SourcesActivity.this,
                        "Loaded " + titles.size() + " entries from " + source.name,
                        Toast.LENGTH_LONG).show();
                refresh();
            }

            @Override public void onError(String message) {
                new AlertDialog.Builder(SourcesActivity.this,
                        android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Could not load playlist")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0xFF6E6E6E);
        return e;
    }

    // ------------------------------------------------------------------

    private class SourceRowAdapter extends RecyclerView.Adapter<SourceRowAdapter.VH> {
        private final List<Source> items;

        SourceRowAdapter(List<Source> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_source_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final Source s = items.get(position);
            final int count = Store.get().titlesForSource(s.id).size();

            h.index.setText(String.valueOf(position + 1));
            h.name.setText(s.name);
            h.detail.setText(s.typeLabel()
                    + (s.url.isEmpty() ? "" : "  \u2022  " + s.url)
                    + "  \u2022  " + count + " titles");
            h.state.setText(s.enabled ? "ON" : "OFF");
            h.state.setTextColor(s.enabled ? 0xFF9BE300 : 0xFF6E6E6E);

            h.itemView.setOnClickListener(v -> showRowMenu(s, position));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView index, name, detail, state;
            VH(View v) {
                super(v);
                index = v.findViewById(R.id.rowIndex);
                name = v.findViewById(R.id.rowName);
                detail = v.findViewById(R.id.rowDetail);
                state = v.findViewById(R.id.rowState);
            }
        }
    }

    private void showRowMenu(final Source s, final int position) {
        final String[] actions = {
                s.enabled ? "Disable" : "Enable",
                "Rename",
                "Change URL",
                "Move up",
                "Move down",
                s.type == Source.TYPE_M3U ? "Reload playlist" : "Open in browser",
                "Delete slot"
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(s.name)
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0:
                            s.enabled = !s.enabled;
                            Store.get().updateSource(s);
                            refresh();
                            break;
                        case 1:
                            promptEdit("Rename source", s.name, val -> {
                                s.name = val;
                                Store.get().updateSource(s);
                                refresh();
                            });
                            break;
                        case 2:
                            promptEdit("Source URL", s.url, val -> {
                                s.url = val;
                                Store.get().updateSource(s);
                                refresh();
                            });
                            break;
                        case 3:
                            Store.get().moveSource(position, position - 1);
                            refresh();
                            break;
                        case 4:
                            Store.get().moveSource(position, position + 1);
                            refresh();
                            break;
                        case 5:
                            if (s.type == Source.TYPE_M3U) {
                                loadPlaylist(s);
                            } else if (!s.url.isEmpty()) {
                                android.content.Intent i =
                                        new android.content.Intent(this, WebActivity.class);
                                i.putExtra(WebActivity.EXTRA_URL, s.url);
                                i.putExtra(WebActivity.EXTRA_NAME, s.name);
                                startActivity(i);
                            } else {
                                Toast.makeText(this, "No URL set for this slot.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case 6:
                            new AlertDialog.Builder(this,
                                    android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                    .setTitle("Delete slot")
                                    .setMessage("Remove \u201C" + s.name + "\u201D and its "
                                            + Store.get().titlesForSource(s.id).size()
                                            + " titles?")
                                    .setPositiveButton("Delete", (d2, w2) -> {
                                        Store.get().removeSource(s.id);
                                        refresh();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            break;
                    }
                })
                .show();
    }

    private interface OnValue { void accept(String value); }

    private void promptEdit(String heading, String current, final OnValue cb) {
        final EditText e = field(heading, InputType.TYPE_CLASS_TEXT);
        e.setText(current);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(48, 24, 48, 8);
        wrap.addView(e);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(heading)
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> cb.accept(e.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
