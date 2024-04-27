package com.example.cameraxdemo

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions

/**
 * @CLassName FaceMeshDetectorProcessor
 * @Author xiao
 * @Date 2024/4/27
 * @Description
 */
class FaceMeshDetectorProcessor(context: Context) :
    VisionProcessorBase<List<FaceMesh>>(context) {

    private val detector: FaceMeshDetector

    init {
        val optionsBuilder = FaceMeshDetectorOptions.Builder()
        if (PreferenceUtils.getFaceMeshUseCase(context) == FaceMeshDetectorOptions.BOUNDING_BOX_ONLY) {
            optionsBuilder.setUseCase(FaceMeshDetectorOptions.BOUNDING_BOX_ONLY)
        }
        detector = FaceMeshDetection.getClient(optionsBuilder.build())
    }

    override fun stop() {
        super.stop()
        detector.close()
    }

    override fun detectInImage(image: InputImage): Task<List<FaceMesh>> {
        return detector.process(image)
    }

    override fun onSuccess(faces: List<FaceMesh>, graphicOverlay: GraphicOverlay) {
        for (face in faces) {
            graphicOverlay.add(FaceMeshGraphic(graphicOverlay, face))
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Face detection failed $e")
    }

    companion object {
        private const val TAG = "SelfieFaceProcessor"
    }
}