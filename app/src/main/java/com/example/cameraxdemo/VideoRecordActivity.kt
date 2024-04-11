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
捕获系统通常会录制视频流和音频流，对其进行压缩，对这两个流进行多路复用，然后将生成的流写入磁盘。
在 CameraX 中，用于视频捕获的解决方案是 VideoCapture 用例：

图例
1.使用 QualitySelector 创建 Recorder
2.使用其中一个 0utputOptions 配置 Recorder
3.如果需要，使用 withAudioEnabled() 启用音频
4.使用 VideoRecordEvent 监听器调用 start() 以开始录制
5.针对 Recording 使用 pause() / resume() / stop() 来控制录制操作.
6.在事件监听器内响应 VideoRecordEvents。
详细的 API列表位于源代码内的 current-txt 中

 */
class VideoRecordActivity : AppCompatActivity() {
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
        setContentView(R.layout.activity_video_record)
        ivPreview = findViewById(R.id.iv_preview)
        previewView = findViewById(R.id.preview_view)
        val btnFlash = findViewById<ToggleButton>(R.id.btn_flash)
        val btnLens = findViewById<ToggleButton>(R.id.btn_lens)
        val btnTakePic = findViewById<ImageFilterView>(R.id.btn_take_pic)
        //摄像头权限获取
        XXPermissions.with(this).permission(Permission.CAMERA).request { _, allGranted ->
            if (allGranted) {
                //1.获取CameraProvider
                cameraProviderFuture = ProcessCameraProvider.getInstance(this@VideoRecordActivity)
                //2.检查 CameraProvider 可用性,请求 CameraProvider 后，请验证它能否在视图创建后成功初始化
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    //3.绑定视图
                    bindPreview()
                }, ContextCompat.getMainExecutor(this@VideoRecordActivity))

            } else {
                Toast.makeText(this@VideoRecordActivity, "请开启相机权限", Toast.LENGTH_LONG).show()
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
        imageCapture?.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this@VideoRecordActivity),
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