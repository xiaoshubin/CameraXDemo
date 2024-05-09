package com.example.cameraxdemo

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import com.example.cameraxdemo.ComUtils.getBitmapByImageProxy
import com.example.cameraxdemo.databinding.ActivityTextRecognitionBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.util.concurrent.Executor


/**
 * 姿势识别
 */
class PoseActivity : AppCompatActivity() {
    private lateinit var bind:ActivityTextRecognitionBinding
    private lateinit var cameraExecutor: Executor
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>//摄像头可用列表
    private var camera: Camera? =null//预览成功后的相机对象
    private lateinit var recognizer: TextRecognizer//预览成功后的相机对象
    private var isShow=false //是否正在显示检测结果
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityTextRecognitionBinding.inflate(layoutInflater)
        setContentView(bind.root)
        recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
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
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@PoseActivity)) { imageProxy ->
            if (isShow)return@setAnalyzer
            val mediaImage = imageProxy.image ?: return@setAnalyzer
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            //如果是拍摄后转换的inputImage,里面的inputImage.bitmapInternal无法获取,所以这里从imageProxy获取
            loadText(inputImage,getBitmapByImageProxy(imageProxy))
            imageProxy.close()
        }
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector,imageAnalysis,preview)
    }
    private fun selectPic(){
        val intent =  Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setType("image/*")
        startActivityForResult(intent,PICK_IMAGE_REQUEST)
    }


    /**
     * 人脸检查
     * @param image InputImage 待检测图片
     * @param bitmap Bitmap? 显示图片
     * @param result :Text
     */
    private fun loadText(image: InputImage, bitmapFromImageProxy: Bitmap?){
        if (image==null)return
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (result!=null){
                    Log.i(TAG,"result:${result.text}")
                    isShow = true
                    bind.ivShow.setImageBitmap(bitmapFromImageProxy)
                    bind.layoutText.visibility = View.VISIBLE
                    bind.tvShow.text = result.text
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
                val inputImage = InputImage.fromFilePath(this@PoseActivity,uriForFile)
                loadText(inputImage,inputImage.bitmapInternal)

            }
        }
    }





    companion object {
        private const val TAG = "TextRecognitionActivity"
        private const val PICK_IMAGE_REQUEST = 10
    }

}

