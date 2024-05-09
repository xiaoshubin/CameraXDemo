package com.example.cameraxdemo

import android.content.Context
import android.net.Uri
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
fun Float.dp2pxF(context: Context):Float{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, context.resources.displayMetrics)
}
fun Int.dp2px(context: Context):Int{
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics).toInt()
}

/**
 * 从Uri获取路径
 * @param context Context
 * @param uri Uri
 * @return String
 */
fun getPathFromUri(context: Context,uri: Uri):String{
    val filePathcolumn = arrayOf(android.provider.MediaStore.Images.Media.DATA)
    val cursor = context.contentResolver.query(uri, filePathcolumn, null, null, null)
    cursor?.moveToFirst()
    val columnIndex =cursor?.getColumnIndex(filePathcolumn[0])?:0
    val imagePath =cursor?.getString(columnIndex)
    cursor?.close()
    return imagePath?:""
}