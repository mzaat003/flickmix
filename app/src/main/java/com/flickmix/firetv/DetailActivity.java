package com.flickmix.firetv;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE_ID = "titleId";

    private Title title;
    private TextView listBtn;

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_detail);

        title = Store.get().titleById(getIntent().getStringExtra(EXTRA_TITLE_ID));
        if (title == null) {
            finish();
            return;
        }

        TextView t = findViewById(R.id.detailTitle);
        TextView meta = findViewById(R.id.detailMeta);
        TextView desc = findViewById(R.id.detailDesc);
        TextView url = findViewById(R.id.detailUrl);
        ImageView backdrop = findViewById(R.id.backdrop);
        TextView playBtn = findViewById(R.id.playBtn);
        listBtn = findViewById(R.id.listBtn);
        TextView removeBtn = findViewById(R.id.removeBtn);

        t.setText(title.title);

        // Chips row per the reference art; the plain meta line keeps the source.
        android.widget.LinearLayout chips = findViewById(R.id.detailChips);
        addChip(chips, title.year);
        addChip(chips, title.rating.isEmpty() ? "" : "\u2605 " + title.rating);
        addChip(chips, title.runtime);
        chips.setVisibility(chips.getChildCount() > 0
                ? android.view.View.VISIBLE : android.view.View.GONE);

        Source src = Store.get().sourceById(title.sourceId);
        meta.setText(src != null ? "Source:  " + src.name : "");

        desc.setText(title.description.isEmpty()
                ? "No description provided for this entry."
                : title.description);
        url.setText(title.streamUrl);

        String art = !title.backdropUrl.isEmpty() ? title.backdropUrl : title.posterUrl;
        if (!art.isEmpty()) {
            Glide.with(this).load(art).override(1280, 720).centerCrop().into(backdrop);
        }

        long resume = Store.get().resumePosition(title.id);
        playBtn.setText(resume > 0 ? "\u25B6  RESUME" : "\u25B6  PLAY");

        playBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_TITLE_ID, title.id);
            startActivity(i);
        });

        listBtn.setOnClickListener(v -> {
            Store.get().toggleFavourite(title.id);
            refreshListBtn();
        });

        removeBtn.setOnClickListener(v ->
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Remove title")
                        .setMessage("Remove \u201C" + title.title + "\u201D from FlickMix? "
                                + "This only removes your entry; nothing else is affected.")
                        .setPositiveButton("Remove", (d, w) -> {
                            Store.get().removeTitle(title.id);
                            Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        refreshListBtn();
        playBtn.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView playBtn = findViewById(R.id.playBtn);
        if (title != null && playBtn != null) {
            playBtn.setText(Store.get().resumePosition(title.id) > 0 ? "\u25B6  RESUME" : "\u25B6  PLAY");
        }
    }

    private void refreshListBtn() {
        boolean fav = Store.get().isFavourite(title.id);
        listBtn.setText(fav ? "\u2713  IN MY LIST" : "+  MY LIST");
    }

    private void addChip(android.widget.LinearLayout row, String text) {
        if (text == null || text.trim().isEmpty()) return;
        TextView chip = new TextView(this);
        chip.setText(text.trim());
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(13);
        chip.setBackgroundResource(R.drawable.pill_bg);
        chip.setPadding(28, 10, 28, 10);
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = 16;
        row.addView(chip, lp);
    }
}
