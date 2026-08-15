package com.flickmix.firetv;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView navList, navSourceList, sourceTabs, shelfList;
    private View emptyState;
    private TextView clock;

    private Adapters.NavAdapter navAdapter;
    private Adapters.SourceTabAdapter tabAdapter;

    private int activeNav = Adapters.NAV_HOME;
    @Nullable private String activeSourceId;
    private String searchQuery = "";

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()));
            clockHandler.postDelayed(this, 30_000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        navList = findViewById(R.id.navList);
        navSourceList = findViewById(R.id.navSourceList);
        sourceTabs = findViewById(R.id.sourceTabs);
        shelfList = findViewById(R.id.shelfList);
        emptyState = findViewById(R.id.emptyState);
        clock = findViewById(R.id.clock);

        navList.setLayoutManager(new LinearLayoutManager(this));
        navSourceList.setLayoutManager(new LinearLayoutManager(this));
        sourceTabs.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        shelfList.setLayoutManager(new LinearLayoutManager(this));

        navAdapter = new Adapters.NavAdapter(Adapters.defaultNav(this), item -> onNav(item.id));
        navList.setAdapter(navAdapter);

        findViewById(R.id.addSourceBtn).setOnClickListener(v -> openSources());
        findViewById(R.id.emptyCta).setOnClickListener(v -> openSources());

        clockTick.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.get().load(this);
        rebuild();
    }

    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------

    private void onNav(int id) {
        if (id == Adapters.NAV_SETTINGS) {
            openSources();
            return;
        }
        if (id == Adapters.NAV_SEARCH) {
            promptSearch();
            return;
        }
        activeNav = id;
        navAdapter.setActive(id);
        rebuild();
    }

    private void openSources() {
        startActivity(new Intent(this, SourcesActivity.class));
    }

    private void promptSearch() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Title contains\u2026");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF6E6E6E);
        input.setText(searchQuery);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(48, 24, 48, 8);
        wrap.addView(input);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Search FlickMix")
                .setView(wrap)
                .setPositiveButton("Search", (d, w) -> {
                    searchQuery = input.getText().toString().trim();
                    activeNav = Adapters.NAV_SEARCH;
                    navAdapter.setActive(Adapters.NAV_SEARCH);
                    rebuild();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------

    private void rebuild() {
        List<Source> enabled = Store.get().enabledSources();

        if (activeSourceId != null && Store.get().sourceById(activeSourceId) == null) {
            activeSourceId = null;
        }
        if (activeSourceId == null && !enabled.isEmpty()) {
            activeSourceId = enabled.get(0).id;
        }

        tabAdapter = new Adapters.SourceTabAdapter(enabled, activeSourceId,
                s -> {
                    if (s.type == Source.TYPE_WEB) {
                        openWeb(s);
                    } else {
                        activeSourceId = s.id;
                        tabAdapter.setActive(s.id);
                        rebuild();
                    }
                },
                this::openSources);
        sourceTabs.setAdapter(tabAdapter);

        navSourceList.setAdapter(new Adapters.SourceRailAdapter(enabled, s -> {
            if (s.type == Source.TYPE_WEB) {
                openWeb(s);
            } else {
                activeSourceId = s.id;
                rebuild();
            }
        }));

        List<Adapters.Shelf> shelves = buildShelves();

        boolean empty = Store.get().sources().isEmpty()
                || (shelves.isEmpty() && activeNav != Adapters.NAV_SEARCH);

        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        shelfList.setVisibility(empty ? View.GONE : View.VISIBLE);

        shelfList.setAdapter(new Adapters.ShelfAdapter(shelves, this::openDetail));
    }

    private List<Adapters.Shelf> buildShelves() {
        Store st = Store.get();
        List<Adapters.Shelf> out = new ArrayList<>();

        switch (activeNav) {
            case Adapters.NAV_SEARCH: {
                List<Title> hits = st.search(searchQuery);
                out.add(new Adapters.Shelf(
                        searchQuery.isEmpty() ? "Search" : "Results for \u201C" + searchQuery + "\u201D",
                        hits));
                break;
            }
            case Adapters.NAV_LIST:
                addIfAny(out, "My List", st.favourites());
                break;
            case Adapters.NAV_CONTINUE:
                addIfAny(out, "Continue Watching", st.continueWatching());
                break;
            case Adapters.NAV_MOVIES:
                addIfAny(out, "Movies", st.titlesForSource(activeSourceId, Title.CAT_MOVIE));
                break;
            case Adapters.NAV_TV:
                addIfAny(out, "TV Shows", st.titlesForSource(activeSourceId, Title.CAT_TV));
                break;
            case Adapters.NAV_ANIME:
                addIfAny(out, "Anime", st.titlesForSource(activeSourceId, Title.CAT_ANIME));
                break;
            default: {
                addIfAny(out, "Continue Watching", st.continueWatching());
                addIfAny(out, "My List", st.favourites());

                List<Title> all = st.titlesForSource(activeSourceId);
                if (!all.isEmpty()) {
                    // "Trending" here just means most recently added -- FlickMix has no
                    // ranking service and should not pretend it does.
                    List<Title> recent = new ArrayList<>(
                            all.subList(Math.max(0, all.size() - 12), all.size()));
                    java.util.Collections.reverse(recent);
                    out.add(new Adapters.Shelf("Recently Added", recent));
                }
                addIfAny(out, "Movies", st.titlesForSource(activeSourceId, Title.CAT_MOVIE));
                addIfAny(out, "TV Shows", st.titlesForSource(activeSourceId, Title.CAT_TV));
                addIfAny(out, "Anime", st.titlesForSource(activeSourceId, Title.CAT_ANIME));
                break;
            }
        }
        return out;
    }

    private void addIfAny(List<Adapters.Shelf> out, String title, List<Title> items) {
        if (!items.isEmpty()) out.add(new Adapters.Shelf(title, items));
    }

    private void openDetail(Title t) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_TITLE_ID, t.id);
        startActivity(i);
    }

    private void openWeb(Source s) {
        Intent i = new Intent(this, WebActivity.class);
        i.putExtra(WebActivity.EXTRA_URL, s.url);
        i.putExtra(WebActivity.EXTRA_NAME, s.name);
        startActivity(i);
    }

    // ------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // MENU on the Fire remote jumps to source management from anywhere.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSources();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Back never traps: from a filtered view, return Home first.
        if (activeNav != Adapters.NAV_HOME) {
            onNav(Adapters.NAV_HOME);
            return;
        }
        super.onBackPressed();
    }
}
