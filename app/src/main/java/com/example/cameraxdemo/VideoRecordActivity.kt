package com.example.cameraxdemo

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.res.Configuration
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.lifecycle.withCreated
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
/**视频录制事件名称*/
fun VideoRecordEvent.getNameString() : String {
    return when (this) {
        is VideoRecordEvent.Status -> "Status"
        is VideoRecordEvent.Start -> "Started"
        is VideoRecordEvent.Finalize-> "Finalized"
        is VideoRecordEvent.Pause -> "Paused"
        is VideoRecordEvent.Resume -> "Resumed"
        else -> throw IllegalArgumentException("Unknown VideoRecordEvent: $this")
    }
}

/**根据分辨率设置宽高比*/
fun Quality.getAspectRatio(quality: Quality): Int {
    return when {
        arrayOf(Quality.UHD, Quality.FHD, Quality.HD).contains(quality)   -> AspectRatio.RATIO_16_9
        (quality ==  Quality.SD) -> AspectRatio.RATIO_4_3
        else -> throw UnsupportedOperationException()
    }
}
/** 帮助器函数，用于从质量枚举中检索长宽比字符串。*/
fun Quality.getAspectRatioString(quality: Quality, portraitMode:Boolean) :String {
    val hdQualities = arrayOf(Quality.UHD, Quality.FHD, Quality.HD)
    val ratio =
        when {
            hdQualities.contains(quality) -> Pair(16, 9)
            quality == Quality.SD         -> Pair(4, 3)
            else -> throw UnsupportedOperationException()
        }

    return if (portraitMode) "V,${ratio.second}:${ratio.first}"
    else "H,${ratio.first}:${ratio.second}"
}

/** 获取质量清晰度名称 */
fun Quality.getNameString() :String {
    return when (this) {
        Quality.UHD -> "4K高清_UHD(2160p)"
        Quality.FHD -> "1080高清_FHD(1080p)"
        Quality.HD -> "高清_HD(720p)"
        Quality.SD -> "标清_SD(480p)"
        else -> throw IllegalArgumentException("清晰度 $this 不支持")
    }
}


/** 视频录制分辨率适配器 */
class GenericListAdapter(
    private val dataset: List<String>,
    private val onBind: (view: View, data: String, position: Int) -> Unit
) : RecyclerView.Adapter<GenericListAdapter.GenericListViewHolder>() {
    class GenericListViewHolder(val view: View) : RecyclerView.ViewHolder(view)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = GenericListViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.video_quality_item, parent, false))
    override fun onBindViewHolder(holder: GenericListViewHolder, position: Int) {
        if (position < 0 || position > dataset.size) return
        onBind(holder.view, dataset[position], position)
    }
    override fun getItemCount() = dataset.size
}

/**
 * 官方文档:https://developer.android.google.cn/media/camera/camerax/video-capture?hl=zh-cn
 *
 * 捕获系统通常会录制视频流和音频流，对其进行压缩，对这两个流进行多路复用，然后将生成的流写入磁盘。
 * 在 CameraX 中，用于视频捕获的解决方案是 VideoCapture 用例：
 * 1.使用 QualitySelector 创建 Recorder
 * 2.使用其中一个 outputOptions 配置 Recorder
 * 3.如果需要，使用 withAudioEnabled() 启用音频
 * 4.使用 VideoRecordEvent 监听器调用 start() 以开始录制
 * 5.针对 Recording 使用 pause() / resume() / stop() 来控制录制操作.
 * 6.在事件监听器内响应 VideoRecordEvents。
 * 详细的 API列表位于源代码内的 current-txt 中
 *
 * 主要功能:
 * 1.视频录制保存
 * 2.是否录入音频（在开始录制的时候设置）
 * 3.录入大小和时间显示
 * 3.录制文件大小限制
 * 流程：
 * VideoCapture->Recorder->PendingRecording（启用音频）->Recording
 *
 * 所需权限：
 * <uses-permission android:name="android.permission.CAMERA"/>
 * <uses-permission android:name="android.permission.RECORD_AUDIO"/>
 *
 */
class VideoRecordActivity : AppCompatActivity() {
    //CameraX 视频捕获包括几个高级架构组件
    private lateinit var videoCapture: VideoCapture<Recorder> //视频录制
    private lateinit var recordingState:VideoRecordEvent //视频录制事件状态
    //操作按钮
    private lateinit var previewView:PreviewView        //预览视图
    private lateinit var captureButton:ImageButton      //录制按钮
    private lateinit var stopButton:ImageButton         //暂停按钮
    private lateinit var cameraButton:ImageButton       //相机切换
    private lateinit var audioSelection: CheckBox       //是否录入音频
    private lateinit var qualitySelection: RecyclerView //分辨率选择
    private lateinit var captureStatus: TextView        //录制描述

    private var currentRecording: Recording? = null // Recording会执行实际录制操作。您必须使用 PendingRecording 来创建 Recording
    private val cameraCapabilities = mutableListOf<CameraCapability>()//相机集合：包含相机和其支持的分辨率
    data class CameraCapability(val camSelector: CameraSelector, val qualities:List<Quality>)//包含相机和其支持的分辨率
    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(this) }//懒加载主线程
    private val captureLiveStatus = MutableLiveData<String>()//录制状态文字绑定
    private var enumerationDeferred: Deferred<Unit>? = null  // 一个表示异步操作的挂起函数的未来结果的抽象

    private var cameraIndex = 0
    private var qualityIndex = DEFAULT_QUALITY_IDX
    private var audioEnabled = false

    enum class UiState {
        IDLE,       // Not recording, all UI controls are active.
        RECORDING,  // Camera is recording, only display Pause/Resume & Stop button.
        FINALIZED,  // Recording just completes, disable all RECORDING UI controls.
        RECOVERY    // For future use.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_record)
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.capture_button)
        stopButton = findViewById(R.id.stop_button)
        cameraButton = findViewById(R.id.camera_button)
        audioSelection = findViewById(R.id.audio_selection)
        qualitySelection = findViewById(R.id.quality_selection)
        captureStatus = findViewById(R.id.capture_status)
        //摄像头权限获取
        XXPermissions.with(this).permission(Permission.CAMERA,Permission.RECORD_AUDIO).request { _, allGranted ->
            if (allGranted) {
                initCameraUI()
            } else {
                Toast.makeText(this@VideoRecordActivity, "请开启相机权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    }
    /**
     * 配置和创建录制对象
     * 应用可以通过 Recorder 创建录制对象来执行视频和音频捕获操作。应用通过执行以下操作来创建录制对象：
     * 1.使用 prepareRecording() 配置 OutputOptions。
     * 2.（可选）启用录音功能。
     * 3.使用 start() 注册 VideoRecordEvent 监听器，并开始捕获视频。
     *
     * 支持以下类型的 OutputOptions：
     * 1.FileDescriptorOutputOptions，用于捕获到 FileDescriptor 中。
     * 2.FileOutputOptions，用于捕获到 File 中。
     * 3.MediaStoreOutputOptions，用于捕获到 MediaStore 中。
     *
     * 无论使用哪种 OutputOptions 类型，您都能通过 setFileSizeLimit() 来设置文件大小上限,单位：字节。
     *
     * 应用可以进一步配置录制对象，例如：
     *
     * 1.使用 withAudioEnabled() 启用音频。
     * 2.使用 start(Executor, Consumer<VideoRecordEvent>) 注册监听器，以接收视频录制事件。
     * 3.通过 PendingRecording.asPersistentRecording()，允许在录音附加到的 VideoCapture 重新绑定到另一个相机时连续录制。
     */
    @SuppressLint("MissingPermission")
    private fun startRecording() {

        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = "CameraX-recording-" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            this.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .setFileSizeLimit(1024*1024*5)//5m
            .build()

        // configure Recorder and Start recording to the mediaStoreOutput.
        currentRecording = videoCapture.output
            .prepareRecording(this, mediaStoreOutput)
            .apply { if (audioEnabled) withAudioEnabled() }
            .start(mainThreadExecutor, captureListener)

        Log.i(TAG, "Recording started")
    }
    /**
     * 页面初始化
     * -初始化但禁用除“质量”选择之外的所有UI控件。
     * -设置“质量选择回收器”视图。
     * -将用例绑定到生命周期摄影机，启用UI控件。
     */
    private fun initCameraUI() {
        initializeUI()
        this.lifecycleScope.launch {
            if (enumerationDeferred != null) {
                enumerationDeferred!!.await()
                enumerationDeferred = null
            }
            initializeQualitySectionsUI()

            bindCaptureUsecase()
        }
    }
    private fun initializeUI() {
        cameraButton.apply {
            setOnClickListener {
                cameraIndex = (cameraIndex + 1) % cameraCapabilities.size
                // camera device change is in effect instantly:
                //   - reset quality selection
                //   - restart preview
                qualityIndex = DEFAULT_QUALITY_IDX
                initializeQualitySectionsUI()
                enableUI(false)
                this@VideoRecordActivity.lifecycleScope.launch {
                    bindCaptureUsecase()
                }
            }
            isEnabled = false
        }

        // audioEnabled by default is disabled.
        audioSelection.isChecked = audioEnabled
        audioSelection.setOnClickListener {
            audioEnabled = audioSelection.isChecked
        }

        // React to user touching the capture button
        captureButton.apply {
            setOnClickListener {
                if (!this@VideoRecordActivity::recordingState.isInitialized ||
                    recordingState is VideoRecordEvent.Finalize)
                {
                    enableUI(false)  // Our eventListener will turn on the Recording UI.
                    startRecording()
                } else {
                    when (recordingState) {
                        is VideoRecordEvent.Start -> {
                            currentRecording?.pause()
                            stopButton.visibility = View.VISIBLE
                        }
                        is VideoRecordEvent.Pause -> currentRecording?.resume()
                        is VideoRecordEvent.Resume -> currentRecording?.pause()
                        else -> throw IllegalStateException("recordingState in unknown state")
                    }
                }
            }
            isEnabled = false
        }

        stopButton.apply {
            setOnClickListener {
                // stopping: hide it after getting a click before we go to viewing fragment
                stopButton.visibility = View.INVISIBLE
                if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
                    return@setOnClickListener
                }

                val recording = currentRecording
                if (recording != null) {
                    recording.stop()
                    currentRecording = null
                }
                captureButton.setImageResource(R.drawable.ic_start)
            }
            // ensure the stop button is initialized disabled & invisible
            visibility = View.INVISIBLE
            isEnabled = false
        }

        captureLiveStatus.observe(this) {
            captureStatus.apply {
                post { text = it }
            }
        }
        captureLiveStatus.value = "idle"
    }

    /**
     * 绑定视频捕获用例
     */
    private suspend fun bindCaptureUsecase() {

        val cameraProvider = ProcessCameraProvider.getInstance(this).await()

        val cameraSelector = getCameraSelector(cameraIndex)

        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val quality = cameraCapabilities[cameraIndex].qualities[qualityIndex]
        val qualitySelector = QualitySelector.from(quality)

        previewView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val orientation = this@VideoRecordActivity.resources.configuration.orientation
            dimensionRatio = quality.getAspectRatioString(quality,
                (orientation == Configuration.ORIENTATION_PORTRAIT))
        }

        val preview = Preview.Builder()
            .setTargetAspectRatio(quality.getAspectRatio(quality))
            .build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        // build a recorder, which can:
        //   - record video/audio to MediaStore(only shown here), File, ParcelFileDescriptor
        //   - be used create recording(s) (the recording performs recording)
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                videoCapture,
                preview
            )
        } catch (exc: Exception) {
            // 在主线程，在UI上重置控制控件
            Log.e(TAG, "用例绑定失败", exc)
            resetUIandState("绑定生命周期失败: $exc")
        }
        enableUI(true)
    }

    /**
     * Retrieve the asked camera's type(lens facing type). In this sample, only 2 types:
     *   idx is even number:  CameraSelector.LENS_FACING_BACK
     *          odd number:   CameraSelector.LENS_FACING_FRONT
     */
    private fun getCameraSelector(idx: Int) : CameraSelector {
        if (cameraCapabilities.size == 0) {
            Log.i(TAG, "Error: This device does not have any camera, bailing out")
            this.finish()
        }
        return (cameraCapabilities[idx % cameraCapabilities.size].camSelector)
    }

    private fun enableUI(enable: Boolean) {
        arrayOf(cameraButton,captureButton, stopButton, audioSelection, qualitySelection).forEach {
            it.isEnabled = enable
        }
    }

    /**
     * 重置UI状态（重开）
     * 如果绑定失败，让我们再次更改以重试。在未来的情况下
     * 我们可能会失败，用户会收到状态通知
     */
    private fun resetUIandState(reason: String) {
        enableUI(true)
        showUI(UiState.IDLE, reason)
        cameraIndex = 0
        qualityIndex = DEFAULT_QUALITY_IDX
        audioEnabled = false
        audioSelection.isChecked = audioEnabled
        initializeQualitySectionsUI()
    }
    /**
     *  initializeQualitySectionsUI():
     *    Populate a RecyclerView to display camera capabilities:
     *       - one front facing
     *       - one back facing
     *    User selection is saved to qualityIndex, will be used
     *    in the bindCaptureUsecase().
     */
    private fun initializeQualitySectionsUI() {
        val selectorStrings = cameraCapabilities[cameraIndex].qualities.map {
            it.getNameString()
        }
        // create the adapter to Quality selection RecyclerView
        qualitySelection.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = GenericListAdapter(selectorStrings) { holderView, qcString, position ->

                holderView.apply {
                    findViewById<TextView>(R.id.qualityTextView)?.text = qcString
                    // select the default quality selector
                    isSelected = (position == qualityIndex)
                }

                holderView.setOnClickListener { view ->
                    if (qualityIndex == position) return@setOnClickListener

                    qualitySelection.let {
                        // deselect the previous selection on UI.
                        it.findViewHolderForAdapterPosition(qualityIndex)
                            ?.itemView
                            ?.isSelected = false
                    }
                    // turn on the new selection on UI.
                    view.isSelected = true
                    qualityIndex = position

                    // rebind the use cases to put the new QualitySelection in action.
                    enableUI(false)
                    this@VideoRecordActivity.lifecycleScope.launch {
                        bindCaptureUsecase()
                    }
                }
            }
            isEnabled = false
        }
    }



    private val captureListener = Consumer<VideoRecordEvent> { event ->
        if (event !is VideoRecordEvent.Status) recordingState = event
        updateUI(event)
        if (event is VideoRecordEvent.Finalize) {
            lifecycleScope.launch {
                 event.outputResults.outputUri
            }
        }

    }
    private fun updateUI(event: VideoRecordEvent) {
        val state = if (event is VideoRecordEvent.Status) recordingState.getNameString()  else event.getNameString()
        when (event) {
            is VideoRecordEvent.Status -> {

            }

            is VideoRecordEvent.Start -> {
                showUI(UiState.RECORDING, event.getNameString())
            }

            is VideoRecordEvent.Finalize -> {
                showUI(UiState.FINALIZED, event.getNameString())
            }

            is VideoRecordEvent.Pause -> {
                captureButton.setImageResource(R.drawable.ic_resume)
            }

            is VideoRecordEvent.Resume -> {
                captureButton.setImageResource(R.drawable.ic_pause)
            }
        }
        val stats = event.recordingStats
        val size = stats.numBytesRecorded / 1000
        val time = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
        var text = "${state}: 已录制 ${size}KB,  ${time}s"
        if(event is VideoRecordEvent.Finalize){
            text = "${text}\n文件已保存至: ${event.outputResults.outputUri}"
        }

        captureLiveStatus.value = text
        Log.i(TAG, "recording event: $text")
    }

    private fun showUI(state: UiState, status:String = "idle") {

            when(state) {
                UiState.IDLE -> {
                    captureButton.setImageResource(R.drawable.ic_start)
                    stopButton.visibility = View.INVISIBLE

                    cameraButton.visibility= View.VISIBLE
                    audioSelection.visibility = View.VISIBLE
                    qualitySelection.visibility=View.VISIBLE
                }
                UiState.RECORDING -> {
                    cameraButton.visibility = View.INVISIBLE
                    audioSelection.visibility = View.INVISIBLE
                    qualitySelection.visibility = View.INVISIBLE

                    captureButton.setImageResource(R.drawable.ic_pause)
                    captureButton.isEnabled = true
                    stopButton.visibility = View.VISIBLE
                    stopButton.isEnabled = true
                }
                //录制完成
                UiState.FINALIZED -> {
                    captureButton.setImageResource(R.drawable.ic_start)
                    stopButton.visibility = View.INVISIBLE

                    cameraButton.visibility= View.VISIBLE
                    audioSelection.visibility = View.VISIBLE
                    qualitySelection.visibility=View.VISIBLE
                }
                else -> {
                    val errorMsg = "Error: showUI($state) is not supported"
                    Log.e(TAG, errorMsg)
                    return
                }
            }
            captureStatus.text = status

    }




    companion object{
        // default Quality selection if no input from UI
        const val DEFAULT_QUALITY_IDX = 0
        val TAG:String = VideoRecordActivity::class.java.simpleName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
    init {
        enumerationDeferred = lifecycleScope.async {
            whenCreated {
                val provider = ProcessCameraProvider.getInstance(this@VideoRecordActivity).await()

                provider.unbindAll()
                for (camSelector in arrayOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraSelector.DEFAULT_FRONT_CAMERA
                )) {
                    try {
                        // just get the camera.cameraInfo to query capabilities
                        // we are not binding anything here.
                        if (provider.hasCamera(camSelector)) {
                            val camera = provider.bindToLifecycle(this@VideoRecordActivity, camSelector)
                            QualitySelector.getSupportedQualities(camera.cameraInfo)
                                .filter { quality ->
                                    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD).contains(quality)
                                }.also {
                                    cameraCapabilities.add(CameraCapability(camSelector, it))
                                }
                        }
                    } catch (exc: java.lang.Exception) {
                        Log.e(TAG, "Camera Face $camSelector is not supported")
                    }
                }
            }
        }
    }

}


