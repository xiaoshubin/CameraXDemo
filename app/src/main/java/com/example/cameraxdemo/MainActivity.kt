package com.example.cameraxdemo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * CameraX
 * 官方文档：https://developer.android.google.cn/media/camera/camerax?hl=zh-cn
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn_take_pic).setOnClickListener {
            startActivity(Intent(this,TakePicActivity::class.java))
        }

    }
}
