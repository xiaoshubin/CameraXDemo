package com.example.cameraxdemo

import android.R.attr
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FLASH_MODE_OFF
import androidx.camera.core.ImageCapture.FLASH_MODE_ON
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.util.concurrent.TimeUnit


/**
 * 图片裁剪
 * CameraX 用例的输出由两部分组成：缓冲区和转换信息。
 * 缓冲区是一个字节数组，而转换信息指明在向最终用户显示缓冲区之前应如何对其进行剪裁和旋转。
 * 转换的应用方式取决于缓冲区的格式。
 *
 * ImageCapture
 * 对于 ImageCapture 用例，剪裁矩形缓冲区会先应用，然后再保存到磁盘，并且旋转信息会保存在 Exif 数据中。应用无需执行任何其他操作。
 * 官方文档:
 * https://developer.android.google.cn/media/camera/camerax/configuration?hl=zh-cn
 *
 * 默认情况下，剪裁矩形是完整的缓冲区矩形，您可通过 ViewPort 和 UseCaseGroup 对其进行自定义。
 */
class CropPicActivity : AppCompatActivity() {
    private val TAG = "TakePicActivity"

    private lateinit var previewView:PreviewView//预览视图
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象
    private var imageCapture: ImageCapture? =null//图片捕获
    private var ivPreview: ImageFilterView? =null//图片预览
    private var flashModeState = FLASH_MODE_OFF//闪光灯状态
    private lateinit var cameraProvider:ProcessCameraProvider//检查摄像头可用列表后,获取摄像头提供者
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK//镜头,默认后置
    private lateinit var focusView:FocusView//聚焦框
    private var viewport: ViewPort?=null//ViewPort 用于指定最终用户可看到的缓冲区矩形

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_pic)
        ivPreview = findViewById(R.id.iv_preview)
        previewView = findViewById(R.id.preview_view)
        focusView = findViewById(R.id.focus_view)
        val btnFlash = findViewById<ToggleButton>(R.id.btn_flash)
        val btnLens = findViewById<ToggleButton>(R.id.btn_lens)
        val btnTakePic = findViewById<ImageFilterView>(R.id.btn_take_pic)
        viewport = previewView.viewPort
        //设置焦点参数
        focusView.setParam(60f.dp2px(this), Color.WHITE,2, 2f.dp2px(this), 12f.dp2px(this))

        //摄像头权限获取
        XXPermissions.with(this).permission(Permission.CAMERA).request { _, allGranted ->
            if (allGranted) {
                //1.获取CameraProvider
                cameraProviderFuture = ProcessCameraProvider.getInstance(this@CropPicActivity)
                //2.检查 CameraProvider 可用性,请求 CameraProvider 后，请验证它能否在视图创建后成功初始化
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    //3.绑定视图
                    bindPreview()
                }, ContextCompat.getMainExecutor(this@CropPicActivity))

            } else {
                Toast.makeText(this@CropPicActivity, "请开启相机权限", Toast.LENGTH_LONG).show()
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
        imageCapture?.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this@CropPicActivity),
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
        //构建 ImageAnalysis 用例
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//            .setTargetResolution(Size(1280, 720))//目标检测区域
            .build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@CropPicActivity), ImageAnalysis.Analyzer { imageProxy ->
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            // 可以得到的一些图像信息，参见 ImageProxy 类相关方法
            val rect = imageProxy.cropRect
            val format = imageProxy.format
            val width = imageProxy.width
            val height = imageProxy.height
            val planes = imageProxy.planes
            // insert your code here.
            val matrix = getCorrectionMatrix(imageProxy,previewView)

            Log.i(TAG,"图片宽高:[${width}*${height}]图片格式:${format}图片裁剪区域:[${rect.left},${rect.top},${rect.right},${rect.bottom}]图片平面大小:{${planes.size}}")
            imageProxy.close()
        })
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageCapture,imageAnalysis,preview)
    }

    /**
     * 转换坐标
     * 以下代码段会创建一个矩阵，将用于图片分析的坐标映射到 PreviewView 坐标
     */
    fun getCorrectionMatrix(imageProxy: ImageProxy, previewView: PreviewView) : Matrix {
        val cropRect = imageProxy.cropRect
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()

        // A float array of the source vertices (crop rect) in clockwise order.
        val source = floatArrayOf(
            cropRect.left.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.top.toFloat(),
            cropRect.right.toFloat(),
            cropRect.bottom.toFloat(),
            cropRect.left.toFloat(),
            cropRect.bottom.toFloat()
        )

        // A float array of the destination vertices in clockwise order.
        val previewWidth = previewView.width.toFloat()
        val previewHeight = previewView.height.toFloat()
        val destination = floatArrayOf(
            0f, 0f, previewWidth, 0f,
            previewWidth, previewHeight, 0f, previewHeight
        )

        // The destination vertexes need to be shifted based on rotation degrees. The
        // rotation degree represents the clockwise rotation needed to correct the image.

        // Each vertex is represented by 2 float numbers in the vertices array.
        val vertexSize = 2
        // The destination needs to be shifted 1 vertex for every 90° rotation.
        val shiftOffset = rotationDegrees / 90 * vertexSize;
        val tempArray = destination.clone()
        for (toIndex in source.indices) {
            val fromIndex = (toIndex + shiftOffset) % source.size
            destination[toIndex] = tempArray[fromIndex]
        }
        matrix.setPolyToPoly(source, 0, destination, 0, 4)
        return matrix
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event!!.action === MotionEvent.ACTION_DOWN) {
            autoFocus(event?.x?.toInt()?:0, event?.y?.toInt()?:0)
        }
        return super.onTouchEvent(event)
    }

    /**
     * 自动聚焦
     */
    private fun autoFocus(x: Int, y: Int) {
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(x.toFloat(), y.toFloat())
        val point = factory.createPoint(x.toFloat(), y.toFloat())
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()
        //触发自动对焦
        val future: ListenableFuture<FocusMeteringResult>? = camera?.cameraControl?.startFocusAndMetering(action)
        future?.addListener({
            try {
                val result = future.get()
                if (result.isFocusSuccessful) {
                    focusView.showFocusView(x, y)
                } else {
                    focusView.hideFocusView()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                focusView.hideFocusView()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}