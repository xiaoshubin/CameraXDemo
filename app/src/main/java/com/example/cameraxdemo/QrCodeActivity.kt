package com.example.cameraxdemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.annotation.Nullable
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.cameraxdemo.databinding.ActivityQrCodeBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * 二维码扫描
 *
 * implementation 'com.google.mlkit:barcode-scanning:17.2.0'

 * 使用机器学习套件扫描条形码 (Android)
 * https://developers.google.cn/ml-kit/vision/barcode-scanning/android?hl=zh-cn
 *
 * 优势:
 * 1.速度快
 * 2.支持格式多
 * 缺点:
 * 1.汉字64个的二维码可以快速扫描,64以上的就很慢,100以上基本扫描不出来
 *
 *
 * 注意:
 * 1. 7.0以上需要以FileProvider的方式获取Uri
 * 2. 目前无法通过BarcodeScanning解析图片
 */
class QrCodeActivity : AppCompatActivity() {
    private lateinit var bind:ActivityQrCodeBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityQrCodeBinding.inflate(layoutInflater)
        setContentView(bind.root)
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

        var cameraController = LifecycleCameraController(baseContext)
        val previewView: PreviewView = bind.viewFinder

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
        cameraController.setImageAnalysisAnalyzer(cameraExecutor,
                MlKitAnalyzer(listOf(barcodeScanner), COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(this@QrCodeActivity)) { result: MlKitAnalyzer.Result? ->
                val barcodeResults = result?.getValue(barcodeScanner)
                //没有数据返回,继续扫描
                if ((barcodeResults == null) || (barcodeResults.size == 0) || (barcodeResults.first() == null)) {
                    previewView.overlay.clear()
                    previewView.setOnTouchListener { _, _ -> false } //no-op
                    return@MlKitAnalyzer
                }
               val str = barcodeResults[0].rawValue.toString()
                Log.i(TAG,"扫描出来的二维码数据:$str")
                Toast.makeText(this,"$str", Toast.LENGTH_SHORT).show()
                //在这里关闭线程池,关闭扫描,避免弹出多个扫描结果
                cameraExecutor.shutdown()
                barcodeScanner.close()
                finish()

//                val qrCodeViewModel = QrCodeViewModel(barcodeResults[0])
//                val qrCodeDrawable = QrCodeDrawable(qrCodeViewModel)
//                previewView.setOnTouchListener(qrCodeViewModel.qrCodeTouchCallback)
//                previewView.overlay.clear()
//                previewView.overlay.add(qrCodeDrawable)


            }
        )

        cameraController.bindToLifecycle(this)
        previewView.controller = cameraController
    }
    private fun selectPic(){
        val intent =  Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.setType("image/*")
        startActivityForResult(intent,PICK_IMAGE_REQUEST)
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
                val inputImage = InputImage.fromFilePath(this@QrCodeActivity,uriForFile)
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
        private const val TAG = "CameraX-MLKit"
        private const val PICK_IMAGE_REQUEST = 10
    }

}

