package com.example.cameraxdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * 点击页面显示聚焦点
 * @CLassName FocusView
 * @Author xiaoshubin
 * @Date 2024/4/15
 * @Description 注意此控件的父布局:FrameLayout
 */
class FocusView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var focusSize = 0 //焦点框的大小
    private var cornerSize = 0 //焦点框圆角尺寸
    private var handler: Handler? = null
    private var runnable: Runnable
    private var mPaint: Paint
    private var rect: RectF

    init {
        focusSize = 60f.dp2px(context)
        val focusColor = Color.GREEN
        val focusStrokeSize = 2f.dp2px(context)
        cornerSize = focusSize / 5

        handler = Handler(Looper.myLooper()!!)
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        rect = RectF()

        mPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = focusStrokeSize.toFloat()
            color = focusColor
        }
        rect.apply {
            top = 0f
            left=0f
            bottom = focusSize.toFloat()
            right = focusSize.toFloat()
        }
        runnable = Runnable { hideFocusView() }
    }

    fun showFocusView(x: Int, y: Int) {
        visibility = VISIBLE
        val layoutParams = layoutParams as FrameLayout.LayoutParams
        layoutParams.leftMargin = x - focusSize / 2
        layoutParams.topMargin = y - focusSize / 2
        setLayoutParams(layoutParams)
        invalidate()
        handler!!.postDelayed(runnable, 3000L)
    }

    fun hideFocusView() {
        visibility = GONE
        if (handler != null) {
            handler!!.removeCallbacks(runnable)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(focusSize, focusSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(rect, cornerSize.toFloat(), cornerSize.toFloat(), mPaint)
    }

    override fun onDetachedFromWindow() {
        if (handler != null) {
            handler!!.removeCallbacks(runnable)
        }
        super.onDetachedFromWindow()
    }
}
