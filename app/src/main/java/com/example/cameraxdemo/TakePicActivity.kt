package com.example.cameraxdemo

import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FLASH_MODE_OFF
import androidx.camera.core.ImageCapture.FLASH_MODE_ON
import androidx.camera.core.ImageCapture.FlashMode
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File

/**
使用 PreviewView
    1.（可选）配置 CameraXConfig.Provider。
    2.将 PreviewView 添加到布局。
    3.请求 ProcessCameraProvider。
    4.在创建 View 时，请检查 ProcessCameraProvider。
    5.选择相机并绑定生命周期和用例。
选择相机并绑定生命周期和用例,创建并确认 CameraProvider 后，请执行以下操作
    1.创建 Preview。
    2.指定所需的相机 LensFacing 选项。
    3.将所选相机和任意用例绑定到生命周期。
    4.将 Preview 连接到 PreviewView。
拍照
ImageCapture的takePicture方法

 主要功能：
 1.图像预览
 2.图像保存
 3.闪光灯开关
权限配置：
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.FLASHLIGHT"/>
 */
class TakePicActivity : AppCompatActivity() {
    private val TAG = "TakePicActivity"

    private lateinit var previewView:PreviewView
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private var camera: Camera? =null//预览成功后的相机对象
    private var imageCapture: ImageCapture? =null//图片捕获
    private var ivPreview: ImageFilterView? =null//图片预览
    private var flashMode=false//闪光灯状态

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_pic)
        ivPreview = findViewById(R.id.iv_preview)
        previewView = findViewById(R.id.preview_view)
        XXPermissions.with(this).permission(Permission.CAMERA).request { _, allGranted ->
            if (allGranted) {

                //1.获取CameraProvider
                cameraProviderFuture = ProcessCameraProvider.getInstance(this@TakePicActivity)
                //2.检查 CameraProvider 可用性,请求 CameraProvider 后，请验证它能否在视图创建后成功初始化
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    //3.绑定视图
                    bindPreview(cameraProvider)
                }, ContextCompat.getMainExecutor(this@TakePicActivity))

            } else {
                Toast.makeText(this@TakePicActivity, "请开启相机权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        //点击保存图片
        findViewById<ImageFilterView>(R.id.btn_take_pic).setOnClickListener {
            savePic()
        }
        val btnFlash = findViewById<ToggleButton>(R.id.btn_flash)
        btnFlash.setOnClickListener {
            if (imageCapture==null)return@setOnClickListener
            val isOpen = btnFlash.isChecked
            imageCapture?.flashMode = if (isOpen)FLASH_MODE_ON else FLASH_MODE_OFF
        }





    }

    /**
     * 保存照片
     */
    private fun savePic() {
        val outFile = File(externalCacheDir?.path,"${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        imageCapture?.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this@TakePicActivity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(error: ImageCaptureException){
                    Log.e(TAG,"图片保存异常：${error.message}")
                }
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // insert your code here.
                    Log.i(TAG,"图片保存路径：${outputFileResults.savedUri?.path}")
                    ivPreview?.visibility = View.VISIBLE
                    ivPreview?.setImageURI(outputFileResults.savedUri)

                }
            })
    }

    /**
     * 绑定视图
     * 选择相机并绑定生命周期和用例,创建并确认 CameraProvider 后，请执行以下操作
     * 1.创建 Preview。
     * 2.指定所需的相机 LensFacing 选项。
     * 3.将所选相机和任意用例绑定到生命周期。
     * 4.将 Preview 连接到 PreviewView。
     */
    private fun bindPreview(cameraProvider : ProcessCameraProvider) {
        val preview : Preview = Preview.Builder().build()
        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        //图片捕获，拍照使用
         imageCapture = ImageCapture.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageCapture,preview)
    }
}