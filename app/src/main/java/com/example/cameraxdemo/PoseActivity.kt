package com.example.cameraxdemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.icu.lang.UCharacter.GraphemeClusterBreak.L
import android.net.Uri
import android.os.Bundle
import android.support.annotation.Nullable
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import com.example.cameraxdemo.ComUtils.getBitmapByImageProxy
import com.example.cameraxdemo.databinding.ActivityPoseBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.util.concurrent.Executor


/**
 * 姿势识别
 * 1. 创建 PoseDetector 实例
 * 检测模式:
 * STREAM_MODE（默认）视频流中检测姿势
 * SINGLE_IMAGE_MODE 静态图片上使用姿势检测或不需要跟踪时
 * 硬件配置: setPreferredHardwareConfigs
 * CPU：仅使用 CPU 运行检测器
 * CPU_GPU：使用 CPU 和 GPU 运行检测器
 */
class PoseActivity : AppCompatActivity() {
    private lateinit var bind:ActivityPoseBinding
    private lateinit var cameraExecutor: Executor
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象
    private lateinit var poseDetector : PoseDetector //姿势识别实例
    private var isShow=false //是否正在显示检测结果

    private lateinit var paint: Paint//绘制脸部特征点的文字画笔


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityPoseBinding.inflate(layoutInflater)
        setContentView(bind.root)

        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)
        cameraExecutor = ContextCompat.getMainExecutor(this@PoseActivity)
        XXPermissions.with(this)
            .permission(Permission.CAMERA)
            .request { _, all ->
                if (all){
                    startCamera()
                }else{
                    Toast.makeText(this,"未获取相机权限", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

        //从相册选择
        bind.ivPhoto.setOnClickListener {
            XXPermissions.with(this)
                .permission(Permission.READ_MEDIA_IMAGES)
                .request { _, all ->
                    if (all){
                        selectPic()
                    }else{
                        Toast.makeText(this,"未获取相册存储权限", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

        }
        bind.ivClose.setOnClickListener {
            isShow=false
            bind.layoutText.visibility = View.GONE
        }
        initPaint()
    }

    /**
     * 画笔初始化
     * paintTxt:文字画笔
     */
    private fun initPaint() {
        //脸部轮廓画笔
        paint = Paint()
        paint.setColor(Color.WHITE)
        paint.style = Paint.Style.STROKE//不填充
        paint.strokeWidth = 2f.dp2pxF(this)//线的宽度


    }

    /**
     * 开启相机扫描
     */
    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this@PoseActivity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            //3.绑定视图
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this@PoseActivity))
    }
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview : Preview = Preview.Builder().build()
        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(bind.viewFinder.surfaceProvider)
        //构建 ImageAnalysis 用例
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
//            .setTargetResolution(Size(1280, 720))//目标检测区域
            .build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@PoseActivity)) { imageProxy ->
            if (isShow)return@setAnalyzer
            //如果是拍摄后转换的inputImage,里面的inputImage.bitmapInternal无法获取,所以这里从imageProxy获取
            loadPose(imageProxy)

        }
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector,imageAnalysis,preview)
    }

    /**
     * 通过视频图像解析姿势数据
     * @param imageProxy ImageProxy
     */
    @OptIn(ExperimentalGetImage::class)
    private fun loadPose(imageProxy: ImageProxy) {
        if (imageProxy.image==null)return
        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        val task = poseDetector.process(inputImage)
            .addOnSuccessListener { result ->
                if (result!=null){
                    val allPoseLandmarks = result.getAllPoseLandmarks()
                    if (allPoseLandmarks.size==0)return@addOnSuccessListener
                    bind.layoutText.visibility = View.VISIBLE
                    Log.i(TAG,"allPoseLandmarks:${allPoseLandmarks.size}")
                    isShow = true
                    val bitmap = getBitmapByImageProxy(imageProxy)
                    bind.ivShow.setImageBitmap(bitmap)

                    drawPose(bitmap,allPoseLandmarks)

                }
            }
        task.addOnCompleteListener {
            imageProxy.close()
        }

    }

    /**
     * 绘制所有的点到图片中
     * @param allPoseLandmarks List<PoseLandmark>
     *  可能发生: java.lang.IllegalStateException: Immutable bitmap passed to Canvas constructor
     *  传递给Canvas构造函数的不可变位图
     */
    private fun drawPose(bitmap: Bitmap?, allPoseLandmarks: List<PoseLandmark>) {
        if (bitmap==null)return
        try {
            val canvas = Canvas(bitmap)
            val points = allPoseLandmarks.map { it.position }
            drawPoints(canvas,points)
        } catch (e: Exception) {
            Log.e(TAG,"图片上绘制点异常:${e.message}")
        }
    }
    private fun drawPoints(canvas: Canvas, points:List<PointF>?){
        if (points.isNullOrEmpty())return
        points.forEachIndexed {index,pointF->
            val x = pointF.x
            val y = pointF.y
            canvas.drawCircle(x,y,3f,paint)
        }

    }


    private fun selectPic(){
        val intent =  Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setType("image/*")
        startActivityForResult(intent,PICK_IMAGE_REQUEST)
    }


    /**
     * @param uri 图片选择后的地址
     */
    private fun loadPose(uri: Uri){
        val inputImage = InputImage.fromFilePath(this@PoseActivity,uri)
        poseDetector.process(inputImage)
            .addOnSuccessListener { result ->
                if (result!=null){
                    val allPoseLandmarks = result.getAllPoseLandmarks()
                    if (allPoseLandmarks.size==0)return@addOnSuccessListener
                    Log.i(TAG,"allPoseLandmarks:${allPoseLandmarks.size}")
                    bind.layoutText.visibility = View.VISIBLE
                    isShow = true
                    val bitmap = inputImage.bitmapInternal
                    bind.ivShow.setImageBitmap(bitmap)
                    drawPose(bitmap,allPoseLandmarks)
                }
            }


    }


    override fun onActivityResult(requestCode: Int, resultcode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultcode, data)
        if (requestCode == PICK_IMAGE_REQUEST&&resultcode == RESULT_OK &&data!=null){
            val uri = data.data
            if (uri!=null){
                val realPath = getPathFromUri(this@PoseActivity,uri)
                val uriForFile = FileProvider.getUriForFile(this,"$packageName.fileprovider", File(realPath))
                bind.ivPhoto.setImageURI(uriForFile)
                //识别图片二维码
                loadPose(uriForFile)

            }
        }
    }





    companion object {
        private const val TAG = "TextRecognitionActivity"
        private const val PICK_IMAGE_REQUEST = 10
    }

}

