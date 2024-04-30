package com.example.cameraxdemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
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
import com.example.cameraxdemo.databinding.ActivityFaceMeshCheckBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.PointF3D
import com.google.mlkit.vision.common.Triangle
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor


/**
 * 人脸网格检测
 * implementation 'com.google.mlkit:face-mesh-detection:16.0.0-beta1'
 *
 * 输入图片:
 * 1.图像的拍摄应在设备摄像头约 2 米内
 * 2.人脸应朝向摄像头，至少一半的脸部可见。
 * 配置人脸网格检测器:
 *  FaceMeshDetectorOptions.setUseCase
 *  BOUNDING_BOX_ONLY:仅为检测到的人脸网格提供边界框。
 *  FACE_MESH（默认选项):提供边界框和额外的人脸网格信息（468 个 3D 点和三角形信息）
 *
 *
 * 异常:JNI DETECTED ERROR IN APPLICATION: field operation on NULL object: 0x0
 */
class FaceMeshCheckActivity : AppCompatActivity() {

    private lateinit var bind: ActivityFaceMeshCheckBinding
    private lateinit var cameraExecutor: Executor
    private lateinit var detector: FaceMeshDetector//人脸网格检测器

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象

    private lateinit var paintTxt:Paint//绘制脸部特征点的文字画笔
    private lateinit var paint:Paint//绘制脸部特征点的文字画笔
    private lateinit var positionPaint:Paint
    private lateinit var boxPaint:Paint


    private var isShow=false //是否正在显示检测结果

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityFaceMeshCheckBinding.inflate(layoutInflater)
        setContentView(bind.root)

       val  options = FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build()
        detector = FaceMeshDetection.getClient(options)

        cameraExecutor = ContextCompat.getMainExecutor(this@FaceMeshCheckActivity)
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
                .permission(
                    Permission.READ_MEDIA_IMAGES
                    ,Permission.READ_MEDIA_VIDEO
                    ,Permission.READ_MEDIA_AUDIO
                )
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
            isShow = false
            bind.layoutImg.visibility = View.GONE
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
        paint.setColor(Color.RED)
        paint.style = Paint.Style.STROKE//不填充
        paint.strokeWidth = 2f.dp2pxF(this)//线的宽度
        //文字画笔
        paintTxt = Paint()
        paintTxt.setColor(Color.WHITE)
        paintTxt.isAntiAlias = true
        paintTxt.textSize=8f.dp2pxF(this)

        val selectedColor = Color.WHITE
        positionPaint = Paint()
        positionPaint.color = selectedColor

        boxPaint = Paint()
        boxPaint.color = selectedColor
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 2f


    }

    /**
     * 人脸检查
     * @param image InputImage 待检测图片
     * @param bitmap Bitmap? 显示图片
     */
    private fun faceCheck(image:InputImage,bitmap: Bitmap?=null){
         if (isShow)return
         if (image==null)return
         detector.process(image)
            .addOnSuccessListener { faceMeshs ->
                 loadFaceData(bitmap,faceMeshs)
            }
            .addOnFailureListener { e ->
//                Log.e(TAG,"获取人脸失败:${e.message}")
            }
    }

    /**
     * 加载人脸网格数据
     */
    private fun loadFaceData(bitmap:Bitmap?,faceMeshs:List<FaceMesh>){

        //绘制人脸框
        if (faceMeshs.isNotEmpty()){
             Log.i(TAG,"人脸网格检测 faces:${faceMeshs.size}")
            isShow = true
            bitmap?.let { drawRectOnBitmap(it, faceMeshs) }
            bind.layoutImg.visibility = View.VISIBLE
            bind.ivShow.setImageBitmap(bitmap)

        }
    }

    /**
     * 在图像上画出人脸矩形
     * @param bitmap Bitmap    拍摄或者选择的图片
     * @param faces List<Face> 多个人脸
     * 当您一次性获得所有面部的轮廓时，您会获得一个由 133 个点组成的数组
     */
    private fun drawRectOnBitmap(bitmap:Bitmap?,faceMeshs:List<FaceMesh>){
        if (bitmap==null)return
        try {
            val canvas = Canvas(bitmap)
            faceMeshs.forEach {faceMesh->
                val bounds = faceMesh.boundingBox//人脸方框
                //绘制人脸方框
                paint.setColor(Color.RED)
                canvas.drawRect(bounds, paint)
                //绘制人脸网格所有的3D点:468个
                val faceMeshpoints = faceMesh.allPoints
                drawPoints(canvas,faceMeshpoints)
                //绘制人脸网格三角
                val allTriangles =faceMesh.allTriangles
                drawTriangles(canvas,allTriangles)

            }
        } catch (e: Exception) {
            Log.e(TAG,"位图不可改变:${e.message}")
        }

    }

    /**
     * 绘制多个三角形
     * @param canvas Canvas
     * @param triangles List<Triangle<FaceMeshPoint>>
     */
    private fun drawTriangles(canvas: Canvas, triangles: List<Triangle<FaceMeshPoint>>) {
        if (triangles.isNullOrEmpty())return
        for (triangle in triangles){
            //3个连接点
            val points = triangle.allPoints.map { it.position }
            points.forEachIndexed {index,pointF3D->
                val x = pointF3D.x
                val y = pointF3D.y
                val point1 = triangle.allPoints[0].position
                val point2 = triangle.allPoints[1].position
                val point3 = triangle.allPoints[2].position
                drawLine(canvas, point1, point2)
                drawLine(canvas, point1, point3)
                drawLine(canvas, point2, point3)

            }

        }


    }

    private fun drawLine(canvas: Canvas, point1: PointF3D, point2: PointF3D) {

    }
    private fun drawPoints(canvas: Canvas, faceMeshPoints:List<FaceMeshPoint>?){
        if (faceMeshPoints.isNullOrEmpty())return
        val points = faceMeshPoints.map { it.position }
        points.forEachIndexed {index,pointF3D->
            val x = pointF3D.x
            val y = pointF3D.y
            //绘制线段
//            if (index<points.size-1){
//                val nextPointF3D = points[index+1]
//                val endX = nextPointF3D.x
//                val endY = nextPointF3D.y
//                canvas.drawLine(x,y,endX,endY,paint)
//            }
            //点用白色
            canvas.drawCircle(x,y,3f,paintTxt)
        }

    }

    private fun drawPoint(canvas: Canvas, faceLandmark: FaceLandmark?){
        if (faceLandmark==null)return
        val pointF = faceLandmark.position
        val x = pointF.x
        val y = pointF.y
        canvas.drawPoint(x,y,paint)
    }


    /**
     * 开启相机扫描
     */
    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this@FaceMeshCheckActivity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            //3.绑定视图
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this@FaceMeshCheckActivity))
    }

    /**
     * 视图与当前页面生命周期绑定
     * 选择相机并绑定生命周期和用例,创建并确认 CameraProvider 后，请执行以下操作
     * 1.创建 Preview。
     * 2.指定所需的相机 LensFacing 选项。
     * 3.将所选相机和任意用例绑定到生命周期。
     * 4.将 Preview 连接到 PreviewView。
     */
    @OptIn(ExperimentalGetImage::class)
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
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@FaceMeshCheckActivity)) { imageProxy ->
            val mediaImage = imageProxy.image ?: return@setAnalyzer
//            Log.i(TAG,"图片宽高:[${mediaImage.width}:${mediaImage.height}]")//[640:480]
//            Log.i(TAG,"preview宽高:[${bind.viewFinder.width}:${bind.viewFinder.height}]")//[1080:2084]

            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            //如果是拍摄后转换的inputImage,里面的inputImage.bitmapInternal无法获取,所以这里从imageProxy获取
            faceCheck(inputImage,getBitmapFromImageProxy(imageProxy))
            imageProxy.close()
        }
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector,imageAnalysis,preview)
    }

    /**
     * 从图片分析中获取bitmap
     * @param imageProxy ImageProxy
     * @return Bitmap
     */
    private fun getBitmapFromImageProxy(imageProxy: ImageProxy):Bitmap{
        val bitmapResource = imageProxy.toBitmap()
        val degrees = imageProxy.imageInfo.rotationDegrees.toFloat()
        val matrix =  Matrix()
        matrix.postRotate(degrees)
        val xlengWidth = bitmapResource.width
        val ylengHeight = bitmapResource.height
        return Bitmap.createBitmap(bitmapResource,0,0,xlengWidth,ylengHeight,matrix,true)
    }


    private fun selectPic(){
        val intent =  Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setType("image/*")
        startActivityForResult(intent, FaceMeshCheckActivity.PICK_IMAGE_REQUEST)
    }
    private fun getPathFromUri(uri: Uri):String{
        val filePathcolumn = arrayOf(android.provider.MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri, filePathcolumn, null, null, null)
        cursor?.moveToFirst()
        val columnIndex =cursor?.getColumnIndex(filePathcolumn[0])?:0
        val imagePath =cursor?.getString(columnIndex)
        cursor?.close()
        return imagePath?:""
    }
    override fun onActivityResult(requestCode: Int, resultcode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultcode, data)
        if (requestCode == FaceMeshCheckActivity.PICK_IMAGE_REQUEST &&resultcode == RESULT_OK &&data!=null){
            val uri = data.data
            if (uri!=null){
                val realPath = getPathFromUri(uri)
                val uriForFile = FileProvider.getUriForFile(this,"$packageName.fileprovider", File(realPath))
//                bind.ivPhoto.setImageURI(uriForFile)
                //识别图片二维码
                val inputImage = InputImage.fromFilePath(this@FaceMeshCheckActivity,uriForFile)
                faceCheck(inputImage,inputImage.bitmapInternal)
                //修改图片时间
//                setImageCreateTime(realPath,Date())

            }
        }
    }
    companion object {
        private const val TAG = "FaceCheckActivity"
        private const val PICK_IMAGE_REQUEST = 10
        private val paintTxt = Paint()

    }

    /**
     * 未获取存储权限将导致
     * java.io.IOException: Could'nt rename to /storage/emulated/0/DCIM/Camera/IMG_20240425_192131_1.jpg.tmp
     * @param imagePath String
     * @param date Date
     */
    private fun setImageCreateTime(imagePath:String,date:Date){
        val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
        val formattedDate: String = dateFormat.format(date)
        try {
            val exif = ExifInterface(imagePath)
            exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
            exif.saveAttributes()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}

