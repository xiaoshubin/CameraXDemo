package com.example.cameraxdemo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat.JPEG
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
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
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.internal.utils.ImageUtil
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.cameraxdemo.databinding.ActivityCropPicBinding
import com.google.common.util.concurrent.ListenableFuture
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.ref.SoftReference
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
 * 1.对焦
 * 2.裁剪
 */
class CropPicActivity : AppCompatActivity() {
    private val TAG = "TakePicActivity"

    private lateinit var previewView:PreviewView//预览视图
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象
    private var imageCapture: ImageCapture? =null//图片捕获
    private lateinit var cameraProvider:ProcessCameraProvider//检查摄像头可用列表后,获取摄像头提供者
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK//镜头,默认后置
    private lateinit var focusView:FocusView//聚焦框
    private var viewport: ViewPort?=null//ViewPort 用于指定最终用户可看到的缓冲区矩形
    private lateinit var viewMask: View //裁剪区域

    private lateinit var bind: ActivityCropPicBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bind = ActivityCropPicBinding.inflate(layoutInflater)
        setContentView(bind.root)
        previewView = findViewById(R.id.preview_view)
        focusView = findViewById(R.id.focus_view)
        viewMask = findViewById(R.id.view_mask)
        val btnLens = findViewById<ToggleButton>(R.id.btn_lens)
        val btnTakePic = findViewById<ImageFilterView>(R.id.btn_take_pic)
        viewport = previewView.viewPort

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
        btnTakePic.setOnClickListener {
            savePic(getPicturePath())
        }
        //摄像头前后切换
        btnLens.setOnClickListener {
            if (camera==null)return@setOnClickListener
            lensFacing = if (lensFacing==CameraSelector.LENS_FACING_BACK)CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            bindPreview()
        }
        bind.imgPictureCancel.setOnClickListener {
            bind.llPictureParent.visibility = View.GONE
            bind.rlResultPicture.visibility = View.GONE
        }
        bind.imgPictureSave.setOnClickListener {

        }




    }

    /**
     * 保存照片
     */
    @SuppressLint("RestrictedApi")
    private fun savePic(tempPath:String) {
//        val outFile = File(externalCacheDir?.path,"${System.currentTimeMillis()}.jpg")
        val outFile = File(tempPath)
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
//        imageCapture?.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this@CropPicActivity),
//            object : ImageCapture.OnImageSavedCallback {
//                override fun onError(error: ImageCaptureException){
//                    Log.e(TAG,"图片保存异常：${error.message}")
//                }
//                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//                    val photoFile = outputFileResults.savedUri?.path?:""
//                    Log.i(TAG,"图片保存路径：${outputFileResults.savedUri?.path}")
//                    val front =lensFacing== CameraSelector.LENS_FACING_FRONT
//                    val bitmap: Bitmap = bitmapClip(this@CropPicActivity, photoFile, front)
//
//                    saveCropPic(tempPath)
//
//                }
//            })
        //https://blog.csdn.net/ZHOU452840622/article/details/121919812
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            imageCapture?.takePicture(mainExecutor, object :ImageCapture.OnImageCapturedCallback(){
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    val shouldCropImage = ImageUtil.shouldCropImage(image)//是否需要裁剪
                    val imageFormat = image.format//图片格式
                    if (shouldCropImage&&imageFormat == JPEG){
                        val jpegByte = ImageUtil.jpegImageToJpegByteArray(image, image.cropRect, imageCapture!!.jpegQuality)
                        val bitmap = byteToBitmap(jpegByte)
                        val rotationDegrees = image.imageInfo.rotationDegrees
                        val rotate = rotate(bitmap,rotationDegrees, (bitmap?.width?:0) / 2f, (bitmap?.height?:0) / 2f)
                        bind.llPictureParent.visibility = View.VISIBLE
                        bind.rlResultPicture.visibility = View.VISIBLE
                        bind.imgPicture.setImageBitmap(rotate)
                    }

                }
            })
        }

    }

    /**
     * 旋转图片
     *
     * @param src     源图片
     * @param degrees 旋转角度
     * @param px      旋转点横坐标
     * @param py      旋转点纵坐标
     * @return 旋转后的图片
     */
    fun rotate(src: Bitmap?, degrees: Int, px: Float, py: Float): Bitmap? {
        return rotate(src, degrees, px, py, false)
    }

    /**
     * 旋转图片
     *
     * @param src     源图片
     * @param degrees 旋转角度
     * @param px      旋转点横坐标
     * @param py      旋转点纵坐标
     * @param recycle 是否回收
     * @return 旋转后的图片
     */
    fun rotate(src: Bitmap?, degrees: Int, px: Float, py: Float, recycle: Boolean): Bitmap? {
        if (isEmptyBitmap(src)) return null
        if (degrees == 0) return src
        val matrix = Matrix()
        matrix.setRotate(degrees.toFloat(), px, py)
        val ret = Bitmap.createBitmap(src!!, 0, 0, src.width, src.height, matrix, true)
        if (recycle && !src.isRecycled) src.recycle()
        return ret
    }
    private fun isEmptyBitmap(src: Bitmap?): Boolean {
        return src == null || src.width == 0 || src.height == 0
    }
    fun byteToBitmap(imgByte: ByteArray?): Bitmap? {
        var imgByte = imgByte
        var input: InputStream? = null
        var bitmap: Bitmap? = null
        val options = BitmapFactory.Options()
        options.inSampleSize = 8
        input = ByteArrayInputStream(imgByte)
        val softRef: SoftReference<*> = SoftReference<Any?>(
            BitmapFactory.decodeStream(
                input, null, options
            )
        )
        bitmap = softRef.get() as Bitmap?
        if (imgByte != null) {
            imgByte = null
        }
        try {
            if (input != null) {
                input.close()
            }
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        return bitmap
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
        preview.setSurfaceProvider(previewView.surfaceProvider)
        //图片捕获，拍照使用
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(previewView.display.rotation)//旋转角度
            .build()
        imageCapture?.setViewPortCropRect(Rect(0,0,100,100))
        //构建 ImageAnalysis 用例
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//            .setTargetResolution(Size(1280, 720))//目标检测区域
            .build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@CropPicActivity), ImageAnalysis.Analyzer { imageProxy ->
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            // 可以得到的一些图像信息，参见 ImageProxy 类相关方法

//            imageProxy.setCropRect(Rect(0,0,100,100))
            // insert your code here.
//            val matrix = getCorrectionMatrix(imageProxy,previewView)
            val rect = imageProxy.cropRect
            val format = imageProxy.format
            val width = imageProxy.width
            val height = imageProxy.height
            val planes = imageProxy.planes


            Log.i(TAG,"图片宽高:[${width}*${height}]图片格式:${format}图片裁剪区域:[${rect.left},${rect.top},${rect.right},${rect.bottom}]图片平面大小:{${planes.size}}")
            imageProxy.close()
        })

        val width = 16
        val height = 9
//        val viewPort =  ViewPort.Builder(Rational(width, height), previewView.display.rotation).build()
        val viewPort =  previewView.viewPort
        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalysis)
            .addUseCase(imageCapture!!)
            .setViewPort(viewPort!!)
            .build()
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, useCaseGroup)
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

    /**
     * 图片裁剪
     * @param mContext Context?
     * @param imgPath String?
     * @param front Boolean 是否是前置摄像头
     * @return Bitmap
     */
    fun bitmapClip(mContext: Context?, imgPath: String, front: Boolean): Bitmap {
        var bitmap = BitmapFactory.decodeFile(imgPath)
        Log.d(
            "wld__________bitmap",
            "width:" + bitmap.getWidth() + "--->height:" + bitmap.getHeight()
        )
        val matrix: Matrix = pictureDegree(imgPath, front)
        val bitmapRatio = bitmap.getHeight() * 1.0 / bitmap.getWidth() //基本上都是16/9
        val width: Int = mContext?.resources?.displayMetrics?.widthPixels?:0
        val height: Int = mContext?.resources?.displayMetrics?.heightPixels?:0
        val screenRatio = height * 1.0 / width //屏幕的宽高比
        bitmap = if (bitmapRatio > screenRatio) { //胖的手机
            val clipHeight = (bitmap.getWidth() * screenRatio).toInt()
            Bitmap.createBitmap(
                bitmap,
                0,
                bitmap.getHeight() - clipHeight shr 1,
                bitmap.getWidth(),
                clipHeight,
                matrix,
                true
            )
        } else { //瘦长的手机
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true)
        }
        return bitmap
    }
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
     * 保存裁剪区域视图
     */
    private fun saveCropPic(tempPath: String) {
        val outLoca: IntArray = getViewLocal(viewMask)
        var rect = Rect(outLoca[0], outLoca[1], viewMask.measuredWidth, viewMask.measuredHeight)
        val picPath = getPicturePath()
        saveBitmap(this, tempPath,picPath,rect, lensFacing==CameraSelector.LENS_FACING_FRONT)
        //删除临时文件
        File(tempPath).delete()

//        val intent = Intent()
//        intent.putExtra("picture_path_key", getPicturePath())
//        setResult(RESULT_OK, intent)
//        finish()
    }

    fun getPicturePath(): String {
//        String cameraPath = Environment.getExternalStorageDirectory().getPath() + File.separator + "DCIM" + File.separator + "Camera";
        var cameraPath: String? = null
        cameraPath = if (checkSD()) {
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
    private fun getPictureTempPath(): String {
        return externalCacheDir?.path + System.currentTimeMillis() + ".jpg"
    }

    /**
     * 检查SD卡
     * @return Boolean
     */
    fun checkSD(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    fun getViewLocal(view: View): IntArray {
        val outLocation = IntArray(2)
        view.getLocationInWindow(outLocation)
        return outLocation
    }

    /**
     * 保存视图为图片
     * @param mContext Context?
     * @param originPath String?
     * @param savePath String?
     * @param rect Rect?
     * @param front Boolean
     * @return Boolean
     */
    fun saveBitmap(mContext: Context?, originPath: String?, savePath: String, rect: Rect?, front: Boolean): Boolean {
        val matrix = pictureDegree(originPath!!, front)
        var clipBitmap = BitmapFactory.decodeFile(originPath)
        clipBitmap = Bitmap.createBitmap(clipBitmap, 0, 0, clipBitmap.getWidth(), clipBitmap.getHeight(), matrix, true)
        if (rect != null) {
            val bitmapRatio = clipBitmap.getHeight() * 1.0 / clipBitmap.getWidth() //基本上都是16/9
            val width: Int = mContext?.resources?.displayMetrics?.widthPixels?:0
            val height: Int = mContext?.resources?.displayMetrics?.heightPixels?:0
            val screenRatio = height * 1.0 / width
            if (bitmapRatio > screenRatio) { //胖的手机
                val clipHeight = (clipBitmap.getWidth() * screenRatio).toInt()
                clipBitmap = Bitmap.createBitmap(clipBitmap,0, clipBitmap.getHeight() - clipHeight shr 1, clipBitmap.getWidth(), clipHeight, null, true)
                scalRect(rect, clipBitmap.getWidth() * 1.0 / width)
            } else { //瘦长的手机
                val marginTop = ((height - width * bitmapRatio) / 2).toInt()
                rect.top -= marginTop
                scalRect(rect, clipBitmap.getWidth() * 1.0 / width)
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
        }
        return saveBitmap(clipBitmap, savePath)
    }
    private fun scalRect(rect: Rect, scale: Double) {
        rect.left = (rect.left * scale).toInt()
        rect.top = (rect.top * scale).toInt()
        rect.right = (rect.right * scale).toInt()
        rect.bottom = (rect.bottom * scale).toInt()
    }
    private fun saveBitmap(bitmap: Bitmap, savePath: String): Boolean {
        try {
            val file = File(savePath)
            val parent = file.getParentFile()
            if (!parent.exists()) {
                parent.mkdirs()
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