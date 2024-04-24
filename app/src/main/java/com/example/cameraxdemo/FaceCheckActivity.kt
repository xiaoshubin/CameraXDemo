package com.example.cameraxdemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.annotation.Nullable
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.FileProvider
import com.example.cameraxdemo.databinding.ActivityFaceCheckBinding
import com.google.android.gms.tasks.Task
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


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
 */
class FaceCheckActivity : AppCompatActivity() {

    private lateinit var bind:ActivityFaceCheckBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var options:FaceDetectorOptions//人脸检测配置
    private lateinit var detector: FaceDetector//人脸检测器

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityFaceCheckBinding.inflate(layoutInflater)
        setContentView(bind.root)
        options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        detector = FaceDetection.getClient(options)

        cameraExecutor = Executors.newSingleThreadExecutor()//创建单个线程池 ，线程池中只有一个线程
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
    }
    private fun faceCheck(image:InputImage){
         detector.process(image)
            .addOnSuccessListener { faces ->
                getFaceData(faces)
            }
            .addOnFailureListener { e ->
                Log.e(TAG,"获取人脸失败:${e.message}")
            }
    }

    private fun getFaceData(faces:List<Face>){
        for (face in faces) {
            val bounds = face.boundingBox
            val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
            val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

            // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
            // nose available):
            val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
            leftEar?.let {
                val leftEarPos = leftEar.position
            }

            // If contour detection was enabled:
            val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
            val upperLipBottomContour = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points

            // If classification was enabled:
            if (face.smilingProbability != null) {
                val smileProb = face.smilingProbability
            }
            if (face.rightEyeOpenProbability != null) {
                val rightEyeOpenProb = face.rightEyeOpenProbability
            }

            // If face tracking was enabled:
            if (face.trackingId != null) {
                val id = face.trackingId
            }
        }
    }

    /**
     * 开启相机扫描
     */
    @ExperimentalGetImage
    private fun startCamera() {
        var cameraController = LifecycleCameraController(baseContext)
        val previewView: PreviewView = bind.viewFinder
        cameraController.setImageAnalysisAnalyzer(cameraExecutor) {imageProxy->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                faceCheck(inputImage)
            }

        }

        cameraController.bindToLifecycle(this)
        previewView.controller = cameraController
    }
    private fun selectPic(){
        val intent =  Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setType("image/*")
        startActivityForResult(intent, FaceCheckActivity.PICK_IMAGE_REQUEST)
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
        if (requestCode == FaceCheckActivity.PICK_IMAGE_REQUEST &&resultcode == RESULT_OK &&data!=null){
            val uri = data.data
            if (uri!=null){
                val realPath = getPathFromUri(uri)
                val uriForFile = FileProvider.getUriForFile(this,"$packageName.fileprovider", File(realPath))
                bind.ivPhoto.setImageURI(uriForFile)
                //识别图片二维码
                val inputImage = InputImage.fromFilePath(this@FaceCheckActivity,uriForFile)
                faceCheck(inputImage)

            }
        }
    }
    companion object {
        private const val TAG = "CameraX-MLKit"
        private const val PICK_IMAGE_REQUEST = 10
    }

}

