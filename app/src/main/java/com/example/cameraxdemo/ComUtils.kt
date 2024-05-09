package com.example.cameraxdemo

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * @CLassName BitmapUtils
 * @Author xiaoshubin
 * @Date 2024/5/9
 * @Description
 */
object ComUtils {
    /**
     * 从图片分析中获取bitmap
     * @param imageProxy ImageProxy
     * @return Bitmap
     */
    fun getBitmapByImageProxy(imageProxy: ImageProxy?): Bitmap?{
        val bitmapResource = imageProxy?.toBitmap()
        val degrees = imageProxy?.imageInfo?.rotationDegrees?.toFloat()?:0f
        val matrix =  Matrix()
        matrix.postRotate(degrees)
        val xlengWidth = bitmapResource?.width
        val ylengHeight = bitmapResource?.height
        return bitmapResource?.let { Bitmap.createBitmap(it,0,0,xlengWidth?:0,ylengHeight?:0,matrix,true) }
    }
}