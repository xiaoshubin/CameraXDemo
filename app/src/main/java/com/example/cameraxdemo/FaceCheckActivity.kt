package com.example.cameraxdemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
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
import com.example.cameraxdemo.databinding.ActivityFaceCheckBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.util.concurrent.Executor


/**
 * 人脸检测
 * implementation 'com.google.mlkit:face-detection:16.1.6'
 * 1. 配置人脸检测器
 *    在对图片应用人脸检测之前，如果要更改人脸检测器的任何默认设置，请使用 FaceDetectorOptions 对象指定这些设置。
 *
 * setPerformanceMode	PERFORMANCE_MODE_FAST（默认）| PERFORMANCE_MODE_ACCURATE
 * 在检测人脸时更注重速度还是准确性。
 *
 * setLandmarkMode	LANDMARK_MODE_NONE（默认）| LANDMARK_MODE_ALL
 * 是否尝试识别面部“特征点”：眼睛、耳朵、鼻子、脸颊、嘴巴等。
 *
 * setContourMode	CONTOUR_MODE_NONE（默认）| CONTOUR_MODE_ALL
 * 是否检测面部特征的轮廓。仅检测图片中最突出的人脸的轮廓。
 *
 * setClassificationMode	CLASSIFICATION_MODE_NONE（默认）| CLASSIFICATION_MODE_ALL
 * 是否将人脸分为不同类别（例如“微笑”和“睁眼”）。
 *
 * setMinFaceSize	float（默认值：0.1f）
 * 设置所需的最小脸部大小，表示为头部宽度与图片宽度的比率。
 *
 * enableTracking	false（默认）| true
 * 是否为人脸分配 ID，以用于跨图片跟踪人脸。
 * 请注意，启用轮廓检测后，仅会检测一张人脸，因此人脸跟踪不会生成有用的结果。因此，为了加快检测速度，请勿同时启用轮廓检测和人脸跟踪。
 *
 * 2. 准备输入图片
 * 3. 获取 FaceDetector 的一个实例
 * 4. 处理图片
 * 5. 获取有关检测到的人脸的信息
 *
 * 注意:
 * 1.对于人脸识别，您使用的图片尺寸应至少为 480x360 像素
 * 2.要在图片中检测的每张人脸应至少为 100x100 像素
 * 3.如果您想检测人脸的轮廓,每张人脸应至少为 200x200 像素
 * 4.启用轮廓检测后，仅会检测一张人脸，因此人脸跟踪不会生成有用的结果。
 * 因此，为了加快检测速度，请勿同时启用轮廓检测和人脸跟踪。
 *
 * 遗留项:
 * 1.人脸检测未实时更新到预览视图上面
 */
class FaceCheckActivity : AppCompatActivity() {

    private lateinit var bind:ActivityFaceCheckBinding
    private lateinit var cameraExecutor: Executor
    private lateinit var options:FaceDetectorOptions//人脸检测配置
    private lateinit var detector: FaceDetector//人脸检测器

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象

    private lateinit var paintTxt:Paint//绘制脸部特征点的文字画笔
    private lateinit var paint:Paint//绘制脸部特征点的文字画笔

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityFaceCheckBinding.inflate(layoutInflater)
        setContentView(bind.root)
        options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        detector = FaceDetection.getClient(options)

        cameraExecutor = ContextCompat.getMainExecutor(this@FaceCheckActivity)
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
        bind.ivClose.setOnClickListener { bind.layoutImg.visibility = View.GONE }

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

    }

    /**
     * 人脸检查
     * @param image InputImage 待检测图片
     * @param bitmap Bitmap? 显示图片
     */
    private fun faceCheck(image:InputImage,bitmap: Bitmap?=null){
         detector.process(image)
            .addOnSuccessListener { faces ->
                 loadFaceData(bitmap,faces)
            }
            .addOnFailureListener { e ->
//                Log.e(TAG,"获取人脸失败:${e.message}")
            }
    }

    /**
     *
     * @param bitmap Bitmap?
     * @param faces List<Face>
     */
    private fun loadFaceData(bitmap:Bitmap?,faces:List<Face>){
        Log.i(TAG,"人脸检测 faces:${faces.size}")
        val bounds = faces.map { it.boundingBox }
        //绘制人脸框
        if (faces.isNotEmpty()){
//            val faceViewModel = FaceViewModel(bounds)
//            val faceDrawable = FaceDrawable(faceViewModel)
//            bind.viewFinder.overlay.clear()
//            bind.viewFinder.overlay.add(faceDrawable)

            bitmap?.let { drawRectOnBitmap(it, faces) }
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
    private fun drawRectOnBitmap(bitmap:Bitmap?,faces:List<Face>){
        if (bitmap==null)return
        try {
            val canvas = Canvas(bitmap)
            faces.forEach {face->
                val rect = face.boundingBox//人脸方框
                //绘制人脸方框
                paint.setColor(Color.RED)
                canvas.drawRect(rect, paint)
                //绘制脸部特征点
    //            drawFacePoint(face,canvas)
                //绘制脸部外形
                drawFaceContour(face,canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG,"位图不可改变:${e.message}")
        }

    }

    /**
     * 绘制脸部外形
     * 要设置setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)才有这些面部信息
     * @param face Face
     * @param canvas Canvas
     * 当您一次性获得所有面部的轮廓时，您会获得一个由 133 个点组成的数组
     * 椭圆形脸	36 点	        上唇（上部）	11 点
     * 左眉毛（上侧）	5 点	上唇（底部）	9 点
     * 左眉毛（下侧）	5 点	下唇（上部）	9 点
     * 右眉毛（上侧）	5 点	下唇（底部）	9 点
     * 右眉毛（下侧）	5 点	鼻梁	2 分
     * 左眼	16 点	            鼻部下方	3 分
     * 右眼	16 点
     * 左脸颊（中心）	1 个端点
     * 右脸颊（中心）	1 分
     */
    private fun drawFaceContour(face:Face,canvas: Canvas){
        val facePoints = face.getContour(FaceContour.FACE)?.points//面部轮廓

        val leftEye = face.getContour(FaceContour.LEFT_EYE)?.points//左眼
        val rightEye = face.getContour(FaceContour.RIGHT_EYE)?.points//右眼

        val noseBridge = face.getContour(FaceContour.NOSE_BRIDGE)?.points//鼻粱
        val noseBottom = face.getContour(FaceContour.NOSE_BOTTOM)?.points//鼻底

        val leftEyebrowTop = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points//左眉上部
        val leftEyebrowBottom = face.getContour(FaceContour.LEFT_EYEBROW_BOTTOM)?.points//左眉下部
        val rightEyebrowTop = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points//右眉上部
        val rightEyebrowBottom = face.getContour(FaceContour.RIGHT_EYEBROW_BOTTOM)?.points//右眉下部

        val upperLipTop = face.getContour(FaceContour.UPPER_LIP_TOP)?.points//上嘴唇上部
        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points//上嘴唇下部
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points//下嘴唇上部
        val lowerLipBottom = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points//下嘴唇下部

        //绘制面部轮廓
        paint.setColor(Color.parseColor("#3366CC"))
        drawPoints(canvas,facePoints)
        //绘制眼睛
        paint.setColor(Color.parseColor("#3B3EAC"))
        drawPoints(canvas,leftEye)
        paint.setColor(Color.parseColor("#0099C6"))
        drawPoints(canvas,rightEye)
        //绘制鼻子
        paint.setColor(Color.parseColor("#994499"))
        drawPoints(canvas,noseBridge)
        paint.setColor(Color.parseColor("#22AA99"))
        drawPoints(canvas,noseBottom)
        //绘制眉毛
        paint.setColor(Color.parseColor("#DC3912"))
        drawPoints(canvas,leftEyebrowTop)
        paint.setColor(Color.parseColor("#FF9900"))
        drawPoints(canvas,leftEyebrowBottom)
        paint.setColor(Color.parseColor("#109618"))
        drawPoints(canvas,rightEyebrowTop)
        paint.setColor(Color.parseColor("#990099"))
        drawPoints(canvas,rightEyebrowBottom)
        //绘制嘴唇
        paint.setColor(Color.parseColor("#DD4477"))
        drawPoints(canvas,upperLipTop)
        paint.setColor(Color.parseColor("#66AA00"))
        drawPoints(canvas,upperLipBottom)
        paint.setColor(Color.parseColor("#B82E2E"))
        drawPoints(canvas,lowerLipTop)
        paint.setColor(Color.parseColor("#316395"))
        drawPoints(canvas,lowerLipBottom)
    }
    private fun drawPoints(canvas: Canvas, points:List<PointF>?){
        if (points.isNullOrEmpty())return

        points.forEachIndexed {index,pointF->
            val x = pointF.x
            val y = pointF.y
            //绘制线段
            if (index<points.size-1){
                val nextPointF = points[index+1]
                val endX = nextPointF.x
                val endY = nextPointF.y
                canvas.drawLine(x,y,endX,endY,paint)
            }
            //文字和点用白色
            canvas.drawText(index.toString(),x,y,paintTxt)
            canvas.drawCircle(x,y,3f,paintTxt)
        }

    }

    /**
     * 绘制脸部特征点
     * @param canvas Canvas
     * @param paint Paint
     */
    private fun drawFacePoint(face:Face,canvas: Canvas){
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)//鼻子
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)//左眼
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)//右眼
        val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)//左耳
        val rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR)//右耳
        val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)//嘴巴左侧
        val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)//嘴巴右侧
        val bottomMouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)//嘴巴下部
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)//左脸颊
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)//右脸颊
        //绘制鼻子
        drawPoint(canvas,nose)
        //绘制眼睛
        drawPoint(canvas,leftEye)
        drawPoint(canvas,rightEye)
        //绘制耳朵
        drawPoint(canvas,leftEar)
        drawPoint(canvas,rightEar)
        //绘制嘴巴
        drawPoint(canvas,leftMouth)
        drawPoint(canvas,rightMouth)
        drawPoint(canvas,bottomMouth)
        //绘制脸颊
        drawPoint(canvas,leftCheek)
        drawPoint(canvas,rightCheek)
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
        cameraProviderFuture = ProcessCameraProvider.getInstance(this@FaceCheckActivity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            //3.绑定视图
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this@FaceCheckActivity))
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
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@FaceCheckActivity)) { imageProxy ->
            val mediaImage = imageProxy.image ?: return@setAnalyzer
//            Log.i(TAG,"图片宽高:[${mediaImage.width}:${mediaImage.height}]")//[640:480]
//            Log.i(TAG,"preview宽高:[${bind.viewFinder.width}:${bind.viewFinder.height}]")//[1080:2084]

            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            //如果是拍摄后转换的inputImage,里面的inputImage.bitmapInternal无法获取,所以这里从imageProxy获取
            faceCheck(inputImage,ComUtils.getBitmapByImageProxy(imageProxy))
            imageProxy.close()
        }
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector,imageAnalysis,preview)
    }


    private fun selectPic(){
        val intent =  Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setType("image/*")
        startActivityForResult(intent, FaceCheckActivity.PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultcode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultcode, data)
        if (requestCode == FaceCheckActivity.PICK_IMAGE_REQUEST &&resultcode == RESULT_OK &&data!=null){
            val uri = data.data
            if (uri!=null){
                val realPath = getPathFromUri(this@FaceCheckActivity,uri)
                val uriForFile = FileProvider.getUriForFile(this,"$packageName.fileprovider", File(realPath))
//                bind.ivPhoto.setImageURI(uriForFile)
                //识别图片二维码
                val inputImage = InputImage.fromFilePath(this@FaceCheckActivity,uriForFile)
                faceCheck(inputImage,inputImage.bitmapInternal)

            }
        }
    }
    companion object {
        private const val TAG = "FaceCheckActivity"
        private const val PICK_IMAGE_REQUEST = 10
        private val paintTxt = Paint()

    }

}

