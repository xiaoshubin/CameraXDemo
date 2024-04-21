package com.example.cameraxdemo

import android.content.Context
import android.util.TypedValue

/**
 * @CLassName Ex
 * @Author xiaoshubin
 * @Date 2024/4/15
 * @Description
 */

fun Float.dp2px(context: Context):Int{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, context.resources.displayMetrics).toInt()
}
fun Int.dp2px(context: Context):Int{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics).toInt()
}