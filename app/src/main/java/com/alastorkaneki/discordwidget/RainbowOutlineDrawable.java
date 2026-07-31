package com.alastorkaneki.discordwidget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SweepGradient;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

final class RainbowOutlineDrawable extends Drawable implements Animatable {
    private static final int[] COLORS = {
            Color.rgb(255, 45, 85),
            Color.rgb(255, 0, 170),
            Color.rgb(154, 71, 255),
            Color.rgb(66, 133, 244),
            Color.rgb(0, 229, 255),
            Color.rgb(0, 230, 118),
            Color.rgb(255, 214, 10),
            Color.rgb(255, 45, 85)
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
    private final float radius;
    private final float strokeWidth;
    private SweepGradient gradient;
    private float angle;

    RainbowOutlineDrawable(Context context, float radiusDp, float strokeDp) {
        float density = context.getResources().getDisplayMetrics().density;
        radius = radiusDp * density;
        strokeWidth = strokeDp * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        animator.setDuration(6500L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            angle = (float) valueAnimator.getAnimatedValue();
            invalidateSelf();
        });
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds.width() > 0 && bounds.height() > 0) {
            gradient = new SweepGradient(
                    bounds.exactCenterX(),
                    bounds.exactCenterY(),
                    COLORS,
                    null
            );
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0 || gradient == null) {
            return;
        }
        float centerX = bounds.exactCenterX();
        float centerY = bounds.exactCenterY();
        matrix.setRotate(angle, centerX, centerY);
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);
        float inset = strokeWidth / 2f;
        canvas.drawRoundRect(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset,
                radius,
                radius,
                paint
        );
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void start() {
        if (!animator.isStarted() && ValueAnimator.areAnimatorsEnabled()) {
            animator.start();
        }
    }

    @Override
    public void stop() {
        animator.cancel();
    }

    @Override
    public boolean isRunning() {
        return animator.isRunning();
    }
}