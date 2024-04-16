package com.example.cameraxdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * @CLassName FocusView
 * @Author xiaoshubin
 * @Date 2024/4/15
 * @Description 注意此控件的父布局:FrameLayout
 */
class FocusView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var focusSize = 0 //焦点框的大小
    private var focusColor = 0 //焦点框的颜色
    private var focusTime = 0 //焦点框显示的时长
    private var focusStrokeSize = 0 //焦点框线条的尺寸
    private var cornerSize = 0 //焦点框圆角尺寸
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var mPaint: Paint? = null
    private var rect: RectF? = null

    init {
        init(context)
    }

    private fun init(context: Context) {
        handler = Handler()
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        rect = RectF()
        runnable = Runnable { hideFocusView() }
    }

    fun setParam(
        focusViewSize: Int, focusViewColor: Int, focusViewTime: Int,
        focusViewStrokeSize: Int, cornerViewSize: Int
    ) {
        if (focusViewSize == -1) {
            focusSize = 60f.dp2px(context)
        } else {
            focusSize = focusViewSize
        }
        if (focusViewColor == -1) {
            focusColor = Color.GREEN
        } else {
            focusColor = focusViewColor
        }
        focusTime = focusViewTime
        if (focusViewStrokeSize == -1) {
            focusStrokeSize = 2f.dp2px(context)
        } else {
            focusStrokeSize = focusViewStrokeSize
        }
        if (cornerViewSize == -1) {
            cornerSize = focusSize / 5
        } else {
            cornerSize = cornerViewSize
        }
        mPaint!!.style = Paint.Style.STROKE
        mPaint!!.strokeWidth = focusStrokeSize.toFloat()
        mPaint!!.setColor(focusColor)
        rect!!.top = 0f
        rect!!.left = rect!!.top
        rect!!.bottom = focusSize.toFloat()
        rect!!.right = rect!!.bottom
    }

    fun showFocusView(x: Int, y: Int) {
        visibility = VISIBLE
        val layoutParams = layoutParams as FrameLayout.LayoutParams
        layoutParams.leftMargin = x - focusSize / 2
        layoutParams.topMargin = y - focusSize / 2
        setLayoutParams(layoutParams)
        invalidate()
        handler!!.postDelayed(runnable!!, (focusTime * 1000).toLong())
    }

    fun hideFocusView() {
        visibility = GONE
        if (handler != null) {
            handler!!.removeCallbacks(runnable!!)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(focusSize, focusSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(rect!!, cornerSize.toFloat(), cornerSize.toFloat(), mPaint!!)
    }

    override fun onDetachedFromWindow() {
        if (handler != null) {
            handler!!.removeCallbacks(runnable!!)
        }
        super.onDetachedFromWindow()
    }
}
