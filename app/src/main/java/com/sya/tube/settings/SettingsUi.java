package com.sya.tube.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import com.thekirankumar.youtubeauto2.R;

/** The pieces the settings pages are built from, grouped rows on rounded cards in the phone app's dark style */
public final class SettingsUi {
    private final Context context;
    private final int onSurface, muted, accent, surface, surfaceHigh;

    public SettingsUi(Context context) {
        this.context = context;
        onSurface = context.getColor(R.color.tube_on_surface);
        muted = context.getColor(R.color.tube_muted);
        accent = context.getColor(R.color.tube_accent);
        surface = context.getColor(R.color.tube_surface);
        surfaceHigh = context.getColor(R.color.tube_surface_high);
    }

    public int dp(float value) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()); }

    public LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    /** A section heading and the card its rows go on */
    public LinearLayout section(LinearLayout page, String heading) {
        TextView label = text(heading, 13, muted, true);
        label.setPadding(dp(20), dp(22), dp(20), dp(8));
        page.addView(label);
        LinearLayout card = column();
        card.setBackground(rounded(surface, dp(18)));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, -2);
        place.setMargins(dp(12), 0, dp(12), 0);
        page.addView(card, place);
        return card;
    }

    public TextView note(LinearLayout page, String value) {
        TextView note = text(value, 13, muted, false);
        note.setPadding(dp(20), dp(10), dp(20), 0);
        note.setLineSpacing(dp(2), 1);
        page.addView(note);
        return note;
    }

    private void divide(LinearLayout card) {
        if (card.getChildCount() == 0) return;
        View line = new View(context);
        line.setBackgroundColor(Color.argb(28, 255, 255, 255));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.7f)));
        place.setMarginStart(dp(18));
        card.addView(line, place);
    }

    private LinearLayout rowBase(LinearLayout card, String title, String summary, TextView[] summaryOut) {
        divide(card);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        row.setPadding(dp(18), dp(12), dp(14), dp(12));
        LinearLayout labels = column();
        labels.addView(text(title, 16, onSurface, false));
        TextView sub = text(summary == null ? "" : summary, 13, muted, false);
        sub.setPadding(0, dp(2), 0, 0);
        sub.setVisibility(summary == null || summary.isEmpty() ? View.GONE : View.VISIBLE);
        labels.addView(sub);
        if (summaryOut != null) summaryOut[0] = sub;
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        return row;
    }

    public SwitchCompat toggle(LinearLayout card, String title, String summary, String key, boolean fallback, Runnable changed) {
        LinearLayout row = rowBase(card, title, summary, null);
        SwitchCompat toggle = new SwitchCompat(context);
        SharedPreferences prefs = TubePrefs.get(context);
        toggle.setChecked(prefs.getBoolean(key, fallback));
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, Color.rgb(190, 190, 190)}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{accent, Color.rgb(80, 80, 80)}));
        toggle.setOnCheckedChangeListener((button, on) -> { prefs.edit().putBoolean(key, on).apply(); if (changed != null) changed.run(); });
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
        place.setMarginStart(dp(12));
        row.addView(toggle, place);
        row.setBackground(ripple(Color.TRANSPARENT, 0));
        row.setOnClickListener(v -> toggle.toggle());
        return toggle;
    }

    /** A row that opens something, the summary can be changed later through the returned view */
    public TextView link(LinearLayout card, String title, String summary, View.OnClickListener click) {
        TextView[] sub = new TextView[1];
        LinearLayout row = rowBase(card, title, summary, sub);
        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.tube_ic_chevron);
        chevron.setImageTintList(ColorStateList.valueOf(muted));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(22)));
        row.setBackground(ripple(Color.TRANSPARENT, 0));
        row.setOnClickListener(click);
        return sub[0];
    }

    public TextView info(LinearLayout card, String title, String summary) {
        TextView[] sub = new TextView[1];
        rowBase(card, title, summary, sub);
        return sub[0];
    }

    /** A row with a small button at its end */
    public TextView action(LinearLayout card, String title, String summary, String label, boolean filled, View.OnClickListener click) {
        TextView[] sub = new TextView[1];
        LinearLayout row = rowBase(card, title, summary, sub);
        TextView button = pill(label, filled);
        button.setOnClickListener(click);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, dp(36));
        place.setMarginStart(dp(12));
        row.addView(button, place);
        return sub[0];
    }

    /** A titled row of chips, one value per chip, the chosen one is white */
    public void choice(LinearLayout card, String title, String summary, String[] labels, String[] values, String current, java.util.function.Consumer<String> chosen) {
        divide(card);
        LinearLayout block = column();
        block.setPadding(dp(18), dp(14), dp(14), dp(14));
        block.addView(text(title, 16, onSurface, false));
        if (summary != null && !summary.isEmpty()) { TextView sub = text(summary, 13, muted, false); sub.setPadding(0, dp(2), 0, 0); block.addView(sub); }
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(context);
        chips.setBackground(rounded(surfaceHigh, dp(20)));
        chips.setPadding(dp(3), dp(3), dp(3), dp(3));
        TextView[] all = new TextView[values.length];
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            TextView chip = text(labels[i], 14, onSurface, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setMinWidth(dp(56));
            all[i] = chip;
            chip.setOnClickListener(v -> { for (int j = 0; j < all.length; j++) mark(all[j], values[j].equals(value)); chosen.accept(value); });
            mark(chip, value.equals(current));
            chips.addView(chip, new LinearLayout.LayoutParams(-2, dp(34)));
        }
        scroll.addView(chips);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-2, -2);
        place.topMargin = dp(10);
        block.addView(scroll, place);
        card.addView(block);
    }

    private void mark(TextView chip, boolean on) {
        chip.setBackground(ripple(on ? onSurface : Color.TRANSPARENT, dp(17)));
        chip.setTextColor(on ? Color.BLACK : onSurface);
    }

    public EditText field(LinearLayout card, String label, String hint, int type) {
        divide(card);
        LinearLayout block = column();
        block.setPadding(dp(18), dp(12), dp(18), dp(12));
        block.addView(text(label, 13, muted, true));
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setHintTextColor(Color.rgb(110, 110, 110));
        edit.setTextColor(onSurface);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        edit.setSingleLine(true);
        edit.setInputType(type);
        edit.setBackground(rounded(surfaceHigh, dp(12)));
        edit.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, -2);
        place.topMargin = dp(6);
        block.addView(edit, place);
        card.addView(block);
        return edit;
    }

    public static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    public static final int URL = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
    public static final int PASSWORD = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;

    /** A full width button under a card */
    public TextView button(LinearLayout page, String label, boolean filled, View.OnClickListener click) {
        TextView button = pill(label, filled);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setOnClickListener(click);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(-1, dp(48));
        place.setMargins(dp(12), dp(12), dp(12), 0);
        page.addView(button, place);
        return button;
    }

    public TextView pill(String label, boolean filled) {
        TextView pill = text(label, 14, filled ? Color.BLACK : onSurface, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), 0, dp(16), 0);
        pill.setBackground(ripple(filled ? onSurface : surfaceHigh, dp(24)));
        return pill;
    }

    public TextView text(String value, int sp, int color, boolean strong) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        if (strong) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }

    public Drawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    public Drawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(50, 255, 255, 255)), color == Color.TRANSPARENT ? null : rounded(color, radius), rounded(Color.WHITE, Math.max(radius, 1)));
    }
}
