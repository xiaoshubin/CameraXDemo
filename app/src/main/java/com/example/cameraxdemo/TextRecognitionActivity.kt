package com.example.cameraxdemo

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import com.example.cameraxdemo.databinding.ActivityTextRecognitionBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * 文字识别
 * 1. 创建 TextRecognizer 实例
 * 2. 准备输入图片
 * 3. 处理图片
 * 4. 从识别出的文本块中提取文本
 *
 * 注意:
 * 1.识别中文错误率还是挺高
 */
class TextRecognitionActivity : AppCompatActivity() {
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
        cameraExecutor = ContextCompat.getMainExecutor(this@TextRecognitionActivity)
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
            isShow=false
            bind.layoutText.visibility = View.GONE
        }

    }

    /**
     * 开启相机扫描
     * 支持以下格式：
     * 代码 128 (FORMAT_CODE_128)
     * 代码 39 (FORMAT_CODE_39)
     * 代码 93 (FORMAT_CODE_93)
     * 科达巴 (FORMAT_CODABAR)
     * EAN-13（FORMAT_EAN_13）
     * EAN-8（FORMAT_EAN_8）
     * ITF（FORMAT_ITF）
     * UPC-A (FORMAT_UPC_A)
     * UPC-E（FORMAT_UPC_E）
     * 二维码 (FORMAT_QR_CODE)
     * PDF417 (FORMAT_PDF417)
     * 阿兹特克语 (FORMAT_AZTEC)
     * 数据矩阵 (FORMAT_DATA_MATRIX)
     */
    private fun startCamera() {

        cameraProviderFuture = ProcessCameraProvider.getInstance(this@TextRecognitionActivity)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            //3.绑定视图
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this@TextRecognitionActivity))
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
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this@TextRecognitionActivity)) { imageProxy ->
            if (isShow)return@setAnalyzer
            val mediaImage = imageProxy.image ?: return@setAnalyzer
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            //如果是拍摄后转换的inputImage,里面的inputImage.bitmapInternal无法获取,所以这里从imageProxy获取
            loadText(inputImage)
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
    private fun loadText(image:InputImage){
        if (image==null)return
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (result!=null){
                    Log.i(TAG,"result:${result.text}")
                    isShow = true
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
                val realPath = getPathFromUri(uri)
                val uriForFile = FileProvider.getUriForFile(this,"$packageName.fileprovider", File(realPath))

                bind.ivPhoto.setImageURI(uriForFile)
                //识别图片二维码
                val inputImage = InputImage.fromFilePath(this@TextRecognitionActivity,uriForFile)
                scanBarcodes(inputImage)

            }
        }
    }

    private fun scanBarcodes(image: InputImage) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if ((barcodes == null) || (barcodes.size == 0) || (barcodes.first() == null)) {
                    Log.i(TAG,"未解析到任何数据")
                    return@addOnSuccessListener
                }
                val str = barcodes[0].rawValue.toString()
                Log.i(TAG,"扫描出来的二维码数据:$str")
            }
            .addOnFailureListener {
                Log.i(TAG,"解析失败")
            }
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
    companion object {
        private const val TAG = "TextRecognitionActivity"
        private const val PICK_IMAGE_REQUEST = 10
    }

}

