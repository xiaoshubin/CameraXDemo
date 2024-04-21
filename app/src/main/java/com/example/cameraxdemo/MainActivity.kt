package com.example.cameraxdemo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.cameraxdemo.databinding.ActivityMainBinding

/**
 * CameraX
 * 官方文档：https://developer.android.google.cn/media/camera/camerax?hl=zh-cn
 * 机器学习
 * https://developers.google.cn/ml-kit?hl=zh-cn
 * 图像识别:
 * 基于现有的API您可以很轻松的实现文字识别、条码识别、图像标签、人脸检测、对象检测等功能
 * https://gitee.com/jenly1314/MLKit
 * 使用机器学习套件检测人脸 (Android)
 * https://developers.google.cn/ml-kit/vision/face-detection/android?hl=cs
 *
 * 图片裁剪:
 * 拍照裁剪:https://gitcode.com/sdwwld/CameraX/overview?utm_source=artical_gitcode
 */
class MainActivity : AppCompatActivity() {
    private lateinit var  bind:ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)
        //拍照
        bind.btnTakePic.setOnClickListener {
            startActivity(Intent(this,TakePicActivity::class.java))
        }
        //录像
        bind.btnTakeVideo.setOnClickListener {
            startActivity(Intent(this,VideoRecordActivity::class.java))
        }
        //数据分析
        bind.btnPicAnaliys.setOnClickListener {
            startActivity(Intent(this,ImageAnalysisActivity::class.java))
        }
        //剪裁矩形
        bind.btnPicCrop.setOnClickListener {
            startActivity(Intent(this,CropPicActivity::class.java))
        }

    }
}
