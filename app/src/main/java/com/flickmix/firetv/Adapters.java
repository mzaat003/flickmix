package com.flickmix.firetv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** All the small RecyclerView adapters for the main screen. */
public class Adapters {

    public interface OnPick<T> { void pick(T item); }

    // ------------------------------------------------------------------
    // Left nav rail
    // ------------------------------------------------------------------

    public static class NavItem {
        public final int id;
        public final String label;
        public final int icon;
        NavItem(int id, String label, int icon) {
            this.id = id; this.label = label; this.icon = icon;
        }
    }

    public static final int NAV_HOME = 0;
    public static final int NAV_MOVIES = 1;
    public static final int NAV_TV = 2;
    public static final int NAV_ANIME = 3;
    public static final int NAV_LIST = 4;
    public static final int NAV_CONTINUE = 5;
    public static final int NAV_SEARCH = 6;
    public static final int NAV_SETTINGS = 7;

    public static List<NavItem> defaultNav(Context c) {
        List<NavItem> out = new ArrayList<>();
        out.add(new NavItem(NAV_HOME, c.getString(R.string.nav_home), R.drawable.ic_home));
        out.add(new NavItem(NAV_MOVIES, c.getString(R.string.nav_movies), R.drawable.ic_movies));
        out.add(new NavItem(NAV_TV, c.getString(R.string.nav_tv), R.drawable.ic_tv));
        out.add(new NavItem(NAV_ANIME, c.getString(R.string.nav_anime), R.drawable.ic_anime));
        out.add(new NavItem(NAV_LIST, c.getString(R.string.nav_list), R.drawable.ic_list));
        out.add(new NavItem(NAV_CONTINUE, c.getString(R.string.nav_continue), R.drawable.ic_continue));
        out.add(new NavItem(NAV_SEARCH, c.getString(R.string.nav_search), R.drawable.ic_search));
        out.add(new NavItem(NAV_SETTINGS, c.getString(R.string.nav_settings), R.drawable.ic_settings));
        return out;
    }

    public static class NavAdapter extends RecyclerView.Adapter<NavAdapter.VH> {
        private final List<NavItem> items;
        private final OnPick<NavItem> onPick;
        private int activeId = NAV_HOME;

        public NavAdapter(List<NavItem> items, OnPick<NavItem> onPick) {
            this.items = items;
            this.onPick = onPick;
        }

        public void setActive(int id) {
            activeId = id;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_nav, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            NavItem it = items.get(position);
            h.label.setText(it.label);
            h.icon.setImageResource(it.icon);
            h.itemView.setActivated(it.id == activeId);
            // Focused state paints the pill green, so flip the text to black.
            h.itemView.setOnFocusChangeListener((v, has) -> {
                h.label.setTextColor(has ? 0xFF000000 : 0xFFFFFFFF);
                h.icon.setColorFilter(has ? 0xFF000000 : 0xFFFFFFFF);
            });
            h.itemView.setOnClickListener(v -> onPick.pick(it));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView label; ImageView icon;
            VH(View v) {
                super(v);
                label = v.findViewById(R.id.navLabel);
                icon = v.findViewById(R.id.navIcon);
            }
        }
    }

    // ------------------------------------------------------------------
    // Source pills (top bar) and source list (rail)
    // ------------------------------------------------------------------

    public static class SourceTabAdapter extends RecyclerView.Adapter<SourceTabAdapter.VH> {
        private final List<Source> items;
        private final OnPick<Source> onPick;
        private final Runnable onAdd;
        private String activeId;

        public SourceTabAdapter(List<Source> items, String activeId,
                                OnPick<Source> onPick, Runnable onAdd) {
            this.items = items;
            this.activeId = activeId;
            this.onPick = onPick;
            this.onAdd = onAdd;
        }

        public void setActive(String id) {
            activeId = id;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_source_tab, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            if (position < items.size()) {
                Source s = items.get(position);
                h.index.setText(String.valueOf(position + 1));
                h.index.setVisibility(View.VISIBLE);
                h.label.setText(s.name.toUpperCase());
                h.itemView.setActivated(s.id.equals(activeId));
                h.itemView.setOnClickListener(v -> onPick.pick(s));
            } else {
                h.index.setVisibility(View.GONE);
                h.label.setText("+");
                h.itemView.setActivated(false);
                h.itemView.setOnClickListener(v -> onAdd.run());
            }
        }

        @Override public int getItemCount() {
            return items.size() + (Store.get().canAddSource() ? 1 : 0);
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView index, label;
            VH(View v) {
                super(v);
                index = v.findViewById(R.id.tabIndex);
                label = v.findViewById(R.id.tabLabel);
            }
        }
    }

    public static class SourceRailAdapter extends RecyclerView.Adapter<SourceRailAdapter.VH> {
        private final List<Source> items;
        private final OnPick<Source> onPick;

        public SourceRailAdapter(List<Source> items, OnPick<Source> onPick) {
            this.items = items;
            this.onPick = onPick;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_nav, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Source s = items.get(position);
            h.label.setText((position + 1) + "  " + s.name);
            h.icon.setImageResource(R.drawable.ic_sources);
            h.icon.setColorFilter(s.enabled ? 0xFF9BE300 : 0xFF6E6E6E);
            h.itemView.setOnFocusChangeListener((v, has) -> {
                h.label.setTextColor(has ? 0xFF000000 : (s.enabled ? 0xFFFFFFFF : 0xFF6E6E6E));
                h.icon.setColorFilter(has ? 0xFF000000 : (s.enabled ? 0xFF9BE300 : 0xFF6E6E6E));
            });
            h.itemView.setOnClickListener(v -> onPick.pick(s));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView label; ImageView icon;
            VH(View v) {
                super(v);
                label = v.findViewById(R.id.navLabel);
                icon = v.findViewById(R.id.navIcon);
            }
        }
    }

    // ------------------------------------------------------------------
    // Poster row
    // ------------------------------------------------------------------

    public static class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.VH> {
        private final List<Title> items;
        private final OnPick<Title> onPick;

        public PosterAdapter(List<Title> items, OnPick<Title> onPick) {
            this.items = items;
            this.onPick = onPick;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_poster, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Title t = items.get(position);
            h.title.setText(t.title);
            h.meta.setText(t.metaLine());

            long resumeMs = Store.get().resumePosition(t.id);
            if (resumeMs > 0) {
                h.progress.setVisibility(View.VISIBLE);
                h.progress.setProgress(35);
            } else {
                h.progress.setVisibility(View.GONE);
            }

            if (t.posterUrl != null && !t.posterUrl.isEmpty()) {
                // Downsample to the view box: never decode a full-size bitmap on a Fire Stick.
                Glide.with(h.image.getContext())
                        .load(t.posterUrl)
                        .placeholder(R.drawable.poster_placeholder)
                        .error(R.drawable.poster_placeholder)
                        .override(300, 440)
                        .centerCrop()
                        .into(h.image);
            } else {
                h.image.setImageResource(R.drawable.poster_placeholder);
            }

            // Focus lift: subtle scale so the ring reads from ten feet away.
            h.itemView.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                        .setDuration(120).start();
                h.title.setTextColor(has ? 0xFF9BE300 : 0xFFFFFFFF);
            });
            h.itemView.setOnClickListener(v -> onPick.pick(t));
        }

        @Override public int getItemCount() { return items.size(); }

        @Override public void onViewRecycled(@NonNull VH h) {
            super.onViewRecycled(h);
            // Free the bitmap as soon as the card leaves the window.
            try { Glide.with(h.image.getContext()).clear(h.image); } catch (Throwable ignored) { }
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView image; TextView title, meta; ProgressBar progress;
            VH(View v) {
                super(v);
                image = v.findViewById(R.id.posterImage);
                title = v.findViewById(R.id.posterTitle);
                meta = v.findViewById(R.id.posterMeta);
                progress = v.findViewById(R.id.posterProgress);
            }
        }
    }

    // ------------------------------------------------------------------
    // Shelves (vertical list of horizontal rows)
    // ------------------------------------------------------------------

    public static class Shelf {
        public final String title;
        public final List<Title> items;
        public Shelf(String title, List<Title> items) {
            this.title = title;
            this.items = items;
        }
    }

    public static class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.VH> {
        private final List<Shelf> shelves;
        private final OnPick<Title> onPick;
        private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();

        public ShelfAdapter(List<Shelf> shelves, OnPick<Title> onPick) {
            this.shelves = shelves;
            this.onPick = onPick;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_shelf, parent, false);
            VH h = new VH(v);
            h.row.setLayoutManager(new LinearLayoutManager(
                    parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
            h.row.setRecycledViewPool(pool);
            h.row.setHasFixedSize(true);
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Shelf s = shelves.get(position);
            h.title.setText(s.title);
            h.count.setText(s.items.size() + " " + (s.items.size() == 1 ? "title" : "titles"));
            h.row.setAdapter(new PosterAdapter(s.items, onPick));
        }

        @Override public int getItemCount() { return shelves.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView title, count; RecyclerView row;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.shelfTitle);
                count = v.findViewById(R.id.shelfCount);
                row = v.findViewById(R.id.shelfRow);
            }
        }
    }
}
