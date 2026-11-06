package com.sya.tube.car;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.thekirankumar.youtubeauto2.R;

/** One look for the panels, notices and buttons Tube builds in code on the car screen, matching the toolbar and the phone */
public final class CarStyle {
    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    public static GradientDrawable rounded(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    /** Solid shapes only, a translucent panel over the video would have to be blended again on every frame */
    public static Drawable card(Context context) {
        return rounded(context.getColor(R.color.tube_surface), dp(context, 22));
    }

    /** Rotary focus gets a ring and a selected chip turns white, so both read at a glance while driving */
    private static Drawable states(Context context, int normal, int selected, float radius) {
        int ring = dp(context, 3);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(Color.rgb(58, 58, 58), radius));
        GradientDrawable focused = rounded(normal, radius);
        focused.setStroke(ring, Color.WHITE);
        GradientDrawable focusedSelected = rounded(selected, radius);
        focusedSelected.setStroke(ring, context.getColor(R.color.tube_accent));
        states.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_activated}, focusedSelected);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_activated}, rounded(selected, radius));
        states.addState(new int[]{}, rounded(normal, radius));
        return states;
    }

    private static ColorStateList textColors(Context context) {
        return new ColorStateList(new int[][]{{android.R.attr.state_activated}, {}}, new int[]{Color.BLACK, context.getColor(R.color.tube_on_surface)});
    }

    /** A pill button, a primary one is white with dark text and the rest sit on the raised surface */
    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(textColors(context));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 56));
        button.setMinimumHeight(dp(context, 56));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        button.setStateListAnimator(null);
        int surface = context.getColor(R.color.tube_surface_high);
        button.setBackground(states(context, surface, context.getColor(R.color.tube_on_surface), dp(context, 28)));
        button.setActivated(primary);
        return button;
    }

    /** One of a set of choices, selected marks the current one */
    public static Button chip(Context context, String label, boolean selected) {
        Button chip = button(context, label, false);
        chip.setBackground(states(context, Color.TRANSPARENT, context.getColor(R.color.tube_on_surface), dp(context, 24)));
        chip.setActivated(selected);
        chip.setMinHeight(dp(context, 48));
        chip.setMinimumHeight(dp(context, 48));
        return chip;
    }

    /** The rounded track a row of chips sits in */
    public static LinearLayout segment(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rounded(context.getColor(R.color.tube_surface_high), dp(context, 28)));
        int pad = dp(context, 4);
        row.setPadding(pad, pad, pad, pad);
        return row;
    }

    public static TextView text(Context context, String value, int sp, boolean strong) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(context.getColor(strong ? R.color.tube_on_surface : R.color.tube_muted));
        if (strong) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private CarStyle() {}
}
