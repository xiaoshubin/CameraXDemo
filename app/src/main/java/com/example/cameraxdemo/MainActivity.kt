package com.example.cameraxdemo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * CameraX
 * 官方文档：https://developer.android.google.cn/media/camera/camerax?hl=zh-cn
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
