package com.alastorkaneki.discordwidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public final class RainbowLinearLayout extends LinearLayout {
    private RainbowOutlineDrawable outline;

    public RainbowLinearLayout(Context context) {
        super(context);
        initialize(context);
    }

    public RainbowLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public RainbowLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        outline = new RainbowOutlineDrawable(context, 18f, 2f);
        outline.setCallback(this);
        setWillNotDraw(false);
    }

    @Override
    protected boolean verifyDrawable(Drawable drawable) {
        return drawable == outline || super.verifyDrawable(drawable);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        outline.setBounds(0, 0, width, height);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        outline.draw(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        outline.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        outline.stop();
        super.onDetachedFromWindow();
    }
}