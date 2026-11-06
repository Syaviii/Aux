// Modified for Aux; CarStream-derived. See NOTICE.
package com.thekirankumar.youtubeauto2.utils;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;

/* JADX INFO: loaded from: classes.dex */
public class TopAlignedImageView extends androidx.appcompat.widget.AppCompatImageView {
    private boolean mHasFrame;
    private Matrix mMatrix;

    public TopAlignedImageView(Context context) {
        this(context, null, 0);
    }

    public TopAlignedImageView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public TopAlignedImageView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.mHasFrame = false;
        this.mMatrix = new Matrix();
    }

    @Override // android.widget.ImageView
    protected boolean setFrame(int i, int i2, int i3, int i4) {
        boolean frame = super.setFrame(i, i2, i3, i4);
        if (frame) {
            this.mHasFrame = true;
            setupScaleMatrix(i3 - i, i4 - i2);
        }
        return frame;
    }

    private void setupScaleMatrix(int i, int i2) {
        Drawable drawable;
        if (this.mHasFrame && (drawable = getDrawable()) != null) {
            Matrix matrix = this.mMatrix;
            int intrinsicWidth = drawable.getIntrinsicWidth();
            float f = i;
            float fMax = Math.max(i2 / drawable.getIntrinsicHeight(), f / intrinsicWidth);
            matrix.setTranslate((-intrinsicWidth) / 2.0f, 0.0f);
            matrix.postScale(fMax, fMax, 0.0f, 0.0f);
            matrix.postTranslate(f / 2.0f, 0.0f);
            setImageMatrix(matrix);
        }
    }

    @Override // android.widget.ImageView
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        setupScaleMatrix(getWidth(), getHeight());
    }

    @Override // android.widget.ImageView
    public void setImageResource(int i) {
        super.setImageResource(i);
        setupScaleMatrix(getWidth(), getHeight());
    }

    @Override // android.widget.ImageView
    public void setImageURI(Uri uri) {
        super.setImageURI(uri);
        setupScaleMatrix(getWidth(), getHeight());
    }
}
