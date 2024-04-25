package com.example.cameraxdemo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
/**
 * @CLassName FaceDrawable
 * @Author xiaoshubin
 * @Date 2024/4/24
 * @Description  人脸检测
 */
class FaceDrawable(qrCodeViewModel: FaceViewModel) : Drawable() {
    private val boundingRectPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.YELLOW
        strokeWidth = 5F
        alpha = 200
    }

    private val contentRectPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.YELLOW
        alpha = 255
    }

    private val contentTextPaint = Paint().apply {
        color = Color.DKGRAY
        alpha = 255
        textSize = 36F
    }

    private val qrCodeViewModel = qrCodeViewModel
    private val contentPadding = 25
    private var textWidth = contentTextPaint.measureText(qrCodeViewModel.content).toInt()

    override fun draw(canvas: Canvas) {
        qrCodeViewModel.boundingRects.forEach {boundingRect->
            canvas.drawRect(boundingRect, boundingRectPaint)
            canvas.drawRect(
                Rect(
                    boundingRect.left,
                    boundingRect.bottom + contentPadding/2,
                    boundingRect.left + textWidth + contentPadding*2,
                    boundingRect.bottom + contentTextPaint.textSize.toInt() + contentPadding),
                contentRectPaint
            )
        }


    }

    override fun setAlpha(alpha: Int) {
        boundingRectPaint.alpha = alpha
        contentRectPaint.alpha = alpha
        contentTextPaint.alpha = alpha
    }

    override fun setColorFilter(colorFiter: ColorFilter?) {
        boundingRectPaint.colorFilter = colorFilter
        contentRectPaint.colorFilter = colorFilter
        contentTextPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class FaceViewModel(rects: List<Rect>) {
    var boundingRects: List<Rect> = rects
    var content: String = ""

}