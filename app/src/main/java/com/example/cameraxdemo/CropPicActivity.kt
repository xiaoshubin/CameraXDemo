package com.example.cameraxdemo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.example.cameraxdemo.databinding.ActivityCropPicBinding
import com.google.common.util.concurrent.ListenableFuture
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
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
 * 用户示例
 * https://gitcode.com/sdwwld/CameraX/overview?utm_source=artical_gitcode
 *
 * 默认情况下，剪裁矩形是完整的缓冲区矩形，您可通过 ViewPort 和 UseCaseGroup 对其进行自定义。
 *
 * 1.聚焦
 * 2.裁剪
 * 3.保存
 */
class CropPicActivity : AppCompatActivity() {
    private val tag = "TakePicActivity"

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象
    private var imageCapture: ImageCapture? =null//图片捕获
    private lateinit var cameraProvider:ProcessCameraProvider//检查摄像头可用列表后,获取摄像头提供者
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK//镜头,默认后置

    private lateinit var bind: ActivityCropPicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bind = ActivityCropPicBinding.inflate(layoutInflater)
        setContentView(bind.root)

        //摄像头权限获取
        XXPermissions.with(this).permission(Permission.CAMERA,Permission.MANAGE_EXTERNAL_STORAGE).request { _, allGranted ->
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
        bind.btnTakePic.setOnClickListener {
            savePic()
        }
        //摄像头前后切换
        bind.btnLens.setOnClickListener {
            if (camera==null)return@setOnClickListener
            lensFacing = if (lensFacing==CameraSelector.LENS_FACING_BACK)CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            bindPreview()
        }

    }

    /**
     * 保存照片
     */
    @SuppressLint("RestrictedApi")
    private fun savePic() {
        //图片保存路径：当前应用下的缓存路径
        val outFile = File(externalCacheDir?.path,"${System.currentTimeMillis()}.jpg")
        val tempPath = outFile.path
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        imageCapture?.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this@CropPicActivity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(error: ImageCaptureException){
                    Log.e(tag,"图片保存异常：${error.message}")
                }
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val photoFile = outputFileResults.savedUri?.path?:""
                    Log.i(tag,"图片保存路径：$photoFile")
                    //裁剪图片为位图
                    val bitmap: Bitmap = cropPicToBitmap(this@CropPicActivity, photoFile)

                    bind.llPictureParent.visibility = View.VISIBLE
                    bind.rlResultPicture.visibility = View.VISIBLE
                    bind.btnLens.visibility = View.GONE
                    bind.imgPicture.setImageBitmap(bitmap)

                    bind.imgPictureCancel.setOnClickListener {
                        bind.llPictureParent.visibility = View.GONE
                        bind.rlResultPicture.visibility = View.GONE
                        bind.btnLens.visibility = View.VISIBLE
                        File(photoFile).delete()
                    }

                    bind.imgPictureSave.setOnClickListener {
                        Toast.makeText(this@CropPicActivity,"已保存到：$tempPath",Toast.LENGTH_LONG).show()
                        saveBitmap(bitmap,getPicturePath())
                        finish()
                    }

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
    @SuppressLint("RestrictedApi")
    private fun bindPreview() {
        cameraProvider.unbindAll()
        val preview : Preview = Preview.Builder().build()
        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        preview.setSurfaceProvider( bind.previewView.surfaceProvider)
        //图片捕获，拍照使用
        imageCapture = ImageCapture.Builder()
            .setTargetRotation( bind.previewView.display.rotation)//旋转角度
            .build()
        //构建 ImageAnalysis 用例
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
//            .setTargetResolution(Size(1280, 720))//目标检测区域
            .build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@CropPicActivity)) { imageProxy ->
            // 可以得到的一些图像信息，参见 ImageProxy 类相关方法
            val rect = imageProxy.cropRect
            val format = imageProxy.format
            val width = imageProxy.width
            val height = imageProxy.height
            val planes = imageProxy.planes//图形面板
            //Y通道长度是（width * height）
            val y = planes[0].buffer.remaining()
            val u = planes[1].buffer.remaining()
            val v = planes[2].buffer.remaining()
            Log.i(
                tag,
                "图片宽高:[${width}*${height}]图片格式:${format}图片裁剪区域:[${rect.left},${rect.top},${rect.right},${rect.bottom}]图片平面大小:{Y:$y U:$u V:$v}"
            )
            imageProxy.close()
        }
      val viewPort = bind.previewView.viewPort
        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalysis)
            .addUseCase(imageCapture!!)
            .setViewPort(viewPort!!)
            .build()
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, useCaseGroup)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN)
            autoFocus(event.x.toInt(), event.y.toInt())
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
                    bind.focusView.showFocusView(x, y)
                } else {
                    bind.focusView.hideFocusView()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                bind.focusView.hideFocusView()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 根据图片路径和摄像头是否前置
     * 设置图片旋转角度并返回对应矩阵（Matrix）
     * @param imgPath 图片路径
     * @param front 是否前置摄像头
     * @return Matrix 图像矩阵
     */
    private fun pictureDegree(imgPath: String, front: Boolean): Matrix {
        val matrix = Matrix()
        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(imgPath)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (exif == null) return matrix
        var degree = 0
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> degree = 90
            ExifInterface.ORIENTATION_ROTATE_180 -> degree = 180
            ExifInterface.ORIENTATION_ROTATE_270 -> degree = 270
        }
        matrix.postRotate(degree.toFloat())
        if (front) {
            matrix.postScale(-1f, 1f)
        }
        return matrix
    }

    /**
     * 根据设置的裁剪蒙版，裁剪图片
     * 56 ，406
     */
    private fun cropPicToBitmap(mContext:Context,tempPath: String) :Bitmap{
        val outLoca: IntArray = getViewLocal( bind.viewMask)
        val rect = Rect(outLoca[0], outLoca[1]-getStatusBarHeight(),  bind.viewMask.measuredWidth,  bind.viewMask.measuredHeight)
        //是否是前置摄像头
        val front = lensFacing==CameraSelector.LENS_FACING_FRONT
        val matrix = pictureDegree(tempPath, front)
        var clipBitmap = BitmapFactory.decodeFile(tempPath)
        clipBitmap = Bitmap.createBitmap(clipBitmap, 0, 0, clipBitmap.width, clipBitmap.height, matrix, true)
            val bitmapRatio = clipBitmap.height * 1.0 / clipBitmap.width //基本上都是16/9
            val width: Int = mContext.resources?.displayMetrics?.widthPixels?:0
            val height: Int = mContext.resources?.displayMetrics?.heightPixels?:0
            val screenRatio = height * 1.0 / width
            if (bitmapRatio > screenRatio) { //胖的手机
                val clipHeight = (clipBitmap.width * screenRatio).toInt()
                clipBitmap = Bitmap.createBitmap(clipBitmap,0, clipBitmap.height - clipHeight shr 1, clipBitmap.width, clipHeight, null, true)
                scalRect(rect, clipBitmap.width * 1.0 / width)
            } else { //瘦长的手机
                val marginTop = ((height - width * bitmapRatio) / 2).toInt()
                rect.top -= marginTop
                scalRect(rect, clipBitmap.width * 1.0 / width)
            }
            clipBitmap = Bitmap.createBitmap(
                clipBitmap,
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                null,
                true
            )
        return clipBitmap
    }

    /**
     * 获取电量栏高度
     */
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId)
        }
        return result
    }

    @SuppressLint("SimpleDateFormat")
    fun getPicturePath(): String {
        val cameraPath: String = if (checkSD()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + File.separator + "DCIM" + File.separator + "Camera"
            } else {
                Environment.getExternalStorageDirectory().path + File.separator + "DCIM" + File.separator + "Camera"
            }
        } else {
            filesDir.toString() + File.separator
        }
        val cameraFolder = File(cameraPath)
        if (!cameraFolder.exists()) {
            cameraFolder.mkdirs()
        }
        val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        return (cameraFolder.absolutePath + File.separator + "IMG_" + simpleDateFormat.format(Date())) + ".jpg"
    }

    /**
     * 检查SD卡
     * @return Boolean
     */
    private fun checkSD(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /**
     * 获取当前视图位置
     */
    private fun getViewLocal(view: View): IntArray {
        val outLocation = IntArray(2)
        view.getLocationInWindow(outLocation)
        return outLocation
    }

    /**
     * 缩放区域转换
     */
    private fun scalRect(rect: Rect, scale: Double) {
        rect.left = (rect.left * scale).toInt()
        rect.top = (rect.top * scale).toInt()
        rect.right = (rect.right * scale).toInt()
        rect.bottom = (rect.bottom * scale).toInt()
    }

    /**
     * 保存位图，jpg格式
     * @param bitmap Bitmap 位图
     * @param savePath String 路径
     * @return Boolean 是否保存成功
     */
    private fun saveBitmap(bitmap: Bitmap, savePath: String): Boolean {
        try {
            val file = File(savePath)
            val parent = file.parentFile
            if (parent != null) {
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            val fos = FileOutputStream(file)
            val b = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
            return b
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }
}