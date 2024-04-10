package com.example.cameraxdemo

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
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
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


 主要功能：
 1.图像预览
 2.图像保存
 3.闪光灯开关
 4.前置后置摄像头切换

权限配置：
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.FLASHLIGHT"/>
<uses-feature android:name="android.hardware.camera"/>

 注意:
 1.要使用闪光灯功能一定要操作enableTorch,CameraControl.enableTorch(boolean) 可以启用或停用手电筒（手电筒应用）。
 启用手电筒后，无论闪光灯模式设置如何，手电筒在拍照和拍视频时都会保持开启状态。仅当手电筒被停用时，ImageCapture 中的 flashMode 才会起作用。
 文档是如此说的,但实际情况是,小米Mix2s,不管enableTorch是否开启,ImageCapture 中的 flashMode 都不起作用,所以只好使用enableTorch开关灯
 */
class ImageAnalysisActivity : AppCompatActivity() {
    private val TAG = "TakePicActivity"

    private lateinit var previewView:PreviewView//预览视图
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象
    private var imageCapture: ImageCapture? =null//图片捕获
    private var ivPreview: ImageFilterView? =null//图片预览
    private var flashModeState = FLASH_MODE_OFF//闪光灯状态
    private lateinit var cameraProvider:ProcessCameraProvider//检查摄像头可用列表后,获取摄像头提供者
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK//镜头,默认后置

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_pic)
        ivPreview = findViewById(R.id.iv_preview)
        previewView = findViewById(R.id.preview_view)
        val btnFlash = findViewById<ToggleButton>(R.id.btn_flash)
        val btnLens = findViewById<ToggleButton>(R.id.btn_lens)
        val btnTakePic = findViewById<ImageFilterView>(R.id.btn_take_pic)
        //摄像头权限获取
        XXPermissions.with(this).permission(Permission.CAMERA).request { _, allGranted ->
            if (allGranted) {
                //1.获取CameraProvider
                cameraProviderFuture = ProcessCameraProvider.getInstance(this@ImageAnalysisActivity)
                //2.检查 CameraProvider 可用性,请求 CameraProvider 后，请验证它能否在视图创建后成功初始化
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    //3.绑定视图
                    bindPreview()
                }, ContextCompat.getMainExecutor(this@ImageAnalysisActivity))

            } else {
                Toast.makeText(this@ImageAnalysisActivity, "请开启相机权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        //点击保存图片
        btnTakePic.setOnClickListener {
            savePic()
        }
        //开光闪光灯
        btnFlash.setOnClickListener {
            if (camera==null)return@setOnClickListener
            flashModeState = if (flashModeState==FLASH_MODE_OFF)FLASH_MODE_ON else FLASH_MODE_OFF
            camera?.cameraControl?.enableTorch( flashModeState==FLASH_MODE_ON)
        }
        //摄像头前后切换
        btnLens.setOnClickListener {
            if (camera==null)return@setOnClickListener
            lensFacing = if (lensFacing==CameraSelector.LENS_FACING_BACK)CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            bindPreview()
        }




    }

    /**
     * 保存照片
     */
    private fun savePic() {
        val outFile = File(externalCacheDir?.path,"${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        imageCapture?.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this@ImageAnalysisActivity),
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
     * 视图与当前页面生命周期绑定
     * 选择相机并绑定生命周期和用例,创建并确认 CameraProvider 后，请执行以下操作
     * 1.创建 Preview。
     * 2.指定所需的相机 LensFacing 选项。
     * 3.将所选相机和任意用例绑定到生命周期。
     * 4.将 Preview 连接到 PreviewView。
     */
    private fun bindPreview() {
        cameraProvider.unbindAll()
        val preview : Preview = Preview.Builder().build()
        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        //图片捕获，拍照使用
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageCapture,preview)
    }
}