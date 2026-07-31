package com.alastorkaneki.discordwidget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;

public final class RainbowButton extends AppCompatButton {
    private RainbowOutlineDrawable outline;

    public RainbowButton(Context context) {
        super(context);
        initialize(context);
    }

    public RainbowButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public RainbowButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        outline = new RainbowOutlineDrawable(context, 16f, 2.25f);
        setStateListAnimator(null);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        outline.setBounds(0, 0, width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
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