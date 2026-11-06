// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;

import android.content.Context;
import android.util.TypedValue;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/* JADX INFO: loaded from: classes.dex */
public class GridAutofitLayoutManager extends GridLayoutManager {
    private int mColumnWidth;
    private boolean mColumnWidthChanged;

    public GridAutofitLayoutManager(Context context, int i) {
        super(context, 1);
        this.mColumnWidthChanged = true;
        setColumnWidth(checkedColumnWidth(context, i));
    }

    public GridAutofitLayoutManager(Context context, int i, int i2, boolean z) {
        super(context, 1, i2, z);
        this.mColumnWidthChanged = true;
        setColumnWidth(checkedColumnWidth(context, i));
    }

    private int checkedColumnWidth(Context context, int i) {
        return i <= 0 ? (int) TypedValue.applyDimension(1, 48.0f, context.getResources().getDisplayMetrics()) : i;
    }

    public void setColumnWidth(int i) {
        if (i <= 0 || i == this.mColumnWidth) {
            return;
        }
        this.mColumnWidth = i;
        this.mColumnWidthChanged = true;
    }

    @Override // androidx.recyclerview.widget.GridLayoutManager, androidx.recyclerview.widget.LinearLayoutManager, androidx.recyclerview.widget.RecyclerView.LayoutManager
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        int paddingTop;
        int width = getWidth();
        int height = getHeight();
        if (this.mColumnWidthChanged && this.mColumnWidth > 0 && width > 0 && height > 0) {
            if (getOrientation() == 1) {
                paddingTop = (width - getPaddingRight()) - getPaddingLeft();
            } else {
                paddingTop = (height - getPaddingTop()) - getPaddingBottom();
            }
            setSpanCount(Math.max(1, paddingTop / this.mColumnWidth));
            this.mColumnWidthChanged = false;
        }
        super.onLayoutChildren(recycler, state);
    }
}
