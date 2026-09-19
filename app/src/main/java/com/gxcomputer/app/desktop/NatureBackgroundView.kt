package com.gxcomputer.app.desktop

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Kilit ekranı / masaüstü arka planı: gerçek bir video dosyası bu ortamda temin
 * edilemediği için (ne internet erişimi var, ne de telifsiz bir kaynak), Apple
 * tarzı ekran koruyuculardan ilham alan, TAMAMEN ÖZGÜN, hareketli bir doğa sahnesi
 * kod ile (Canvas) çiziliyor: gökyüzü + tepeler + yavaşça kayan bulutlar + gün
 * içindeki saate göre değişen ışık. Video dosyasıyla değiştirmek istenirse
 * VideoView/ExoPlayer ile bu View'ın yerine kolayca geçilebilir.
 */
class NatureBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillFarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resources.getColor(com.gxcomputer.app.R.color.gx_hill_far, null)
    }
    private val hillNearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resources.getColor(com.gxcomputer.app.R.color.gx_hill_near, null)
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
    }

    private var cloudOffset = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 60_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            cloudOffset = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // Gökyüzü gradyanı
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            resources.getColor(com.gxcomputer.app.R.color.gx_sky_top, null),
            resources.getColor(com.gxcomputer.app.R.color.gx_sky_bottom, null),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // Güneş / ay parıltısı
        val sunCx = w * 0.78f
        val sunCy = h * 0.22f
        sunPaint.shader = RadialGradient(
            sunCx, sunCy, w * 0.28f,
            intArrayOf(0x55FFE9A8, 0x00FFE9A8),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunCx, sunCy, w * 0.28f, sunPaint)
        sunPaint.shader = null
        sunPaint.color = resources.getColor(com.gxcomputer.app.R.color.gx_sun, null)
        canvas.drawCircle(sunCx, sunCy, w * 0.07f, sunPaint)

        // Yavaşça kayan bulutlar (sonsuz döngü)
        drawCloudRow(canvas, w, h * 0.18f, w * 0.30f, cloudOffset)
        drawCloudRow(canvas, w, h * 0.30f, w * 0.55f, cloudOffset * 0.6f)

        // Uzak tepeler
        val farHills = Path().apply {
            moveTo(0f, h * 0.62f)
            cubicTo(w * 0.25f, h * 0.50f, w * 0.45f, h * 0.66f, w * 0.7f, h * 0.55f)
            cubicTo(w * 0.85f, h * 0.50f, w * 0.95f, h * 0.58f, w, h * 0.54f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(farHills, hillFarPaint)

        // Yakın tepeler
        val nearHills = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.20f, h * 0.68f, w * 0.40f, h * 0.85f, w * 0.65f, h * 0.74f)
            cubicTo(w * 0.82f, h * 0.68f, w * 0.92f, h * 0.80f, w, h * 0.72f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(nearHills, hillNearPaint)
    }

    private fun drawCloudRow(canvas: Canvas, w: Float, y: Float, spacing: Float, offsetPct: Float) {
        var x = -spacing + (w + spacing * 2) * offsetPct
        while (x < w + spacing) {
            drawCloud(canvas, x, y)
            x += spacing
        }
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float) {
        val r = width * 0.045f
        canvas.drawCircle(cx, cy, r, cloudPaint)
        canvas.drawCircle(cx + r * 0.9f, cy + r * 0.2f, r * 0.75f, cloudPaint)
        canvas.drawCircle(cx - r * 0.9f, cy + r * 0.2f, r * 0.75f, cloudPaint)
    }
}
