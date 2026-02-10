package io.flutter.plugins.camerapreview

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.View
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.plugins.nativeview.NativeView
import java.io.File
import java.util.concurrent.Executors

/**
 * A native view implementation that renders camera preview using a SurfaceView
 * below the transparent Flutter layer.
 *
 * This class handles only the camera preview without barcode scanning.
 * For barcode scanning functionality, extend this class and override
 * [createImageAnalysis] to add image analysis.
 */
open class CameraSurfacePreview : NativeView() {

    companion object {
        private const val TAG = "CameraSurfacePreview"

        private var currentInstance: CameraSurfacePreview? = null

        fun configureCameraProcessProvider() {
            try {
                val config = CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig()).apply {
                    setMinimumLoggingLevel(Log.ERROR)
                }
                ProcessCameraProvider.configureInstance(config.build())
            } catch (_: IllegalStateException) {
                // The ProcessCameraProvider was already configured.
            }
        }

        /**
         * Toggle the torch on the current instance.
         */
        fun toggleTorchStatic() {
            currentInstance?.toggleTorch()
        }

        /**
         * Enable the torch on the current instance.
         */
        fun enableTorchStatic() {
            currentInstance?.enableTorch()
        }

        /**
         * Disable the torch on the current instance.
         */
        fun disableTorchStatic() {
            currentInstance?.disableTorch()
        }

        /**
         * Set the zoom scale on the current instance.
         */
        fun setScaleStatic(scale: Double) {
            currentInstance?.setScale(scale)
        }

        /**
         * Set the zoom ratio on the current instance.
         */
        fun setZoomRatioStatic(zoomRatio: Double) {
            currentInstance?.setZoomRatio(zoomRatio)
        }

        /**
         * Reset the zoom scale on the current instance.
         */
        fun resetScaleStatic() {
            currentInstance?.resetScale()
        }

        /**
         * Take a picture and save it to a temporary file.
         */
        fun takePictureStatic(
            onSuccess: (String) -> Unit,
            onError: (String) -> Unit
        ) {
            currentInstance?.takePicture(onSuccess, onError)
                ?: onError("Camera not initialized")
        }

    }

    // Internal variables
    public var cameraProvider: ProcessCameraProvider? = null
    public var camera: Camera? = null
    public var cameraSelector: CameraSelector? = null
    public var preview: Preview? = null
    public var imageCapture: ImageCapture? = null
    public var surfaceView: AspectRatioSurfaceView? = null
    public var displayListener: DisplayManager.DisplayListener? = null
    public var analysisExecutor = Executors.newSingleThreadExecutor()
    public var isPaused = false

    // Configuration
    public var cameraPosition: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    public var torchEnabled: Boolean = false
    public var cameraResolutionWanted: Size? = null
    public var initialZoom: Double? = null

    // Callbacks
    var torchStateCallback: TorchStateCallback? = null
    var zoomScaleStateCallback: ZoomScaleStateCallback? = null
    var cameraStartedCallback: CameraStartedCallback? = null
    var cameraStartErrorCallback: ((exception: Exception) -> Unit)? = null

    init {
        configureCameraProcessProvider()
        currentInstance = this
    }

    override fun onCreateView(): View {

        surfaceView = AspectRatioSurfaceView(context!!).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Log.d(TAG, "Surface created - starting camera")
                    startCamera()
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    Log.d(TAG, "Surface changed: ${width}x${height}")
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d(TAG, "Surface destroyed")
                    stopCamera(force = true)
                }
            })
        }

        return surfaceView!!
    }

    override fun onShow() {
        super.onShow()
        Log.d(TAG, "onShow")
    }

    override fun onHide() {
        super.onHide()
        Log.d(TAG, "onHide")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDispose() {
        super.onDispose()
        Log.d(TAG, "onDispose")
        currentInstance = null
        releaseCamera(isDisposing = true)
    }

    /**
     * Configure the camera parameters before starting.
     */
    open fun configure(
        cameraPosition: CameraSelector,
        torch: Boolean,
        cameraResolutionWanted: Size?,
        initialZoom: Double?,
    ) {
        this.cameraPosition = cameraPosition
        this.torchEnabled = torch
        this.cameraResolutionWanted = cameraResolutionWanted
        this.initialZoom = initialZoom
    }

    /**
     * Create an optional ImageAnalysis use case for subclasses.
     * Override this method to add image analysis (e.g., barcode scanning).
     *
     * @param displayRotation The current display rotation
     * @param cameraResolution The camera resolution
     * @return An ImageAnalysis use case, or null if not needed
     */
    public open fun createImageAnalysis(
        displayRotation: Int,
        cameraResolution: Size
    ): ImageAnalysis? {
        return null
    }

    /**
     * Called when camera has started successfully.
     * Override this method to perform additional setup after camera starts.
     */
    @ExperimentalLensFacing
    public open fun onCameraStarted(
        camera: Camera,
        analysisResolution: Size?,
        numberOfCameras: Int
    ) {
        val cameraDirection = getCameraLensFacing(camera)
        val sensorRotationDegrees = camera.cameraInfo.sensorRotationDegrees
        val portrait = sensorRotationDegrees % 180 == 0

        val width = analysisResolution?.width?.toDouble() ?: 0.0
        val height = analysisResolution?.height?.toDouble() ?: 0.0

        var currentTorchState: Int = -1
        camera.cameraInfo.let {
            if (it.hasFlashUnit()) {
                currentTorchState = it.torchState.value ?: -1
            }
        }

        cameraStartedCallback?.invoke(
            if (portrait) width else height,
            if (portrait) height else width,
            currentTorchState,
            numberOfCameras,
            cameraDirection
        )
    }

    @ExperimentalLensFacing
    public fun getCameraLensFacing(camera: Camera?): Int? {
        return when (camera?.cameraInfo?.lensFacing) {
            CameraSelector.LENS_FACING_BACK -> 1
            CameraSelector.LENS_FACING_FRONT -> 0
            CameraSelector.LENS_FACING_EXTERNAL -> 2
            CameraSelector.LENS_FACING_UNKNOWN -> null
            else -> null
        }
    }

    /**
     * Start the camera preview.
     */
    @ExperimentalLensFacing
    open fun startCamera() {
        val activity = context as? android.app.Activity ?: run {
            cameraStartErrorCallback?.invoke(CameraError())
            return
        }

        if (camera?.cameraInfo != null && preview != null && !isPaused) {
            cameraStartErrorCallback?.invoke(AlreadyStarted())
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        val mainExecutor = ContextCompat.getMainExecutor(activity)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val numberOfCameras = cameraProvider?.availableCameraInfos?.size ?: 0

            if (cameraProvider == null) {
                cameraStartErrorCallback?.invoke(CameraError())
                return@addListener
            }

            cameraProvider?.unbindAll()

            // Get the display rotation for proper orientation
            val displayRotation = activity.windowManager.defaultDisplay.rotation

            // Create Preview.SurfaceProvider for SurfaceView
            val surfaceProvider = Preview.SurfaceProvider { request ->
                val surface = surfaceView?.holder?.surface
                if (surface != null && surface.isValid) {
                    val previewSize = request.resolution
                    Log.d(TAG, "Preview resolution: ${previewSize.width}x${previewSize.height}")

                    // Set preview size for aspect ratio calculation
                    surfaceView?.setPreviewSize(previewSize)

                    request.provideSurface(surface, Executors.newSingleThreadExecutor()) { result ->
                        when (result.resultCode) {
                            androidx.camera.core.SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY -> {
                                Log.d(TAG, "Surface used successfully")
                            }
                            else -> {
                                Log.w(TAG, "Surface result: ${result.resultCode}")
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Surface not available or invalid, cannot provide to camera")
                }
            }

            // Build the preview with target rotation
            val cameraResolution = cameraResolutionWanted ?: Size(1920, 1080)
            val previewBuilder = Preview.Builder()
                .setTargetRotation(displayRotation)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                cameraResolution,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
            preview = previewBuilder.build().apply { setSurfaceProvider(surfaceProvider) }

            // Build image capture use case
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(displayRotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                cameraResolution,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .build()

            // Create optional image analysis (for subclasses like barcode scanner)
            val analysis = createImageAnalysis(displayRotation, cameraResolution)

            // Register display listener for resolution changes
            val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            if (displayListener == null) {
                displayListener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) {}
                    override fun onDisplayRemoved(displayId: Int) {}
                    override fun onDisplayChanged(displayId: Int) {
                        // Handle display changes if needed
                    }
                }
                displayManager.registerDisplayListener(displayListener, null)
            }

            // Check if surface is still valid before binding
            val surface = surfaceView?.holder?.surface
            if (surface == null || !surface.isValid) {
                Log.w(TAG, "Surface destroyed before camera could bind, aborting")
                return@addListener
            }

            try {
                // Bind use cases to lifecycle
                camera = if (analysis != null) {
                    cameraProvider?.bindToLifecycle(
                        activity as LifecycleOwner,
                        cameraPosition,
                        preview,
                        imageCapture,
                        analysis
                    )
                } else {
                    cameraProvider?.bindToLifecycle(
                        activity as LifecycleOwner,
                        cameraPosition,
                        preview,
                        imageCapture
                    )
                }
                cameraSelector = cameraPosition
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera: ${e.message}")
                cameraStartErrorCallback?.invoke(NoCamera())
                return@addListener
            }

            camera?.let { cam ->
                // Register torch listener
                cam.cameraInfo.torchState.observe(activity as LifecycleOwner) { state ->
                    torchStateCallback?.invoke(state)
                }

                // Register zoom scale listener
                cam.cameraInfo.zoomState.observe(activity) { state ->
                    zoomScaleStateCallback?.invoke(state.linearZoom.toDouble())
                }

                // Enable torch if provided
                if (cam.cameraInfo.hasFlashUnit()) {
                    cam.cameraControl.enableTorch(torchEnabled)
                }

                if (initialZoom != null) {
                    try {
                        if (initialZoom!! in 0.0..1.0) {
                            cam.cameraControl.setLinearZoom(initialZoom!!.toFloat())
                        } else {
                            cam.cameraControl.setZoomRatio(initialZoom!!.toFloat())
                        }
                    } catch (e: Exception) {
                        cameraStartErrorCallback?.invoke(ZoomNotInRange())
                        return@addListener
                    }
                }

                // Notify subclasses that camera has started
                val analysisResolution = analysis?.resolutionInfo?.resolution
                onCameraStarted(cam, analysisResolution, numberOfCameras)
            }
        }, mainExecutor)
    }

    /**
     * Pause the camera preview.
     */
    fun pauseCamera(force: Boolean = false) {
        if (!force) {
            if (isPaused) {
                throw AlreadyPaused()
            } else if (isStopped()) {
                throw AlreadyStopped()
            }
        }

        cameraProvider?.unbindAll()
        isPaused = true
    }

    /**
     * Stop the camera preview.
     */
    fun stopCamera(force: Boolean = false) {
        if (!force) {
            if (!isPaused && isStopped()) {
                throw AlreadyStopped()
            }
        }

        releaseCamera()
    }

    public open fun releaseCamera(isDisposing: Boolean = false) {
        val activity = context as? android.app.Activity

        if (displayListener != null && activity != null) {
            val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            displayManager.unregisterDisplayListener(displayListener)
            displayListener = null
        }

        val owner = activity as? LifecycleOwner

        camera?.cameraInfo?.let {
            owner?.let { lifecycleOwner ->
                it.torchState.removeObservers(lifecycleOwner)
                it.zoomState.removeObservers(lifecycleOwner)
                it.cameraState.removeObservers(lifecycleOwner)
            }
        }

        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        isPaused = false

        analysisExecutor.shutdown()
        if (!isDisposing) {
            // Only recreate executor if not disposing (camera might be restarted)
            analysisExecutor = Executors.newSingleThreadExecutor()
        }
    }

    protected fun isStopped() = camera == null && preview == null

    /**
     * Toggle the torch.
     */
    fun toggleTorch() {
        camera?.let {
            if (!it.cameraInfo.hasFlashUnit()) {
                return@let
            }

            when (it.cameraInfo.torchState.value) {
                TorchState.OFF -> it.cameraControl.enableTorch(true)
                TorchState.ON -> it.cameraControl.enableTorch(false)
            }
        }
    }

    /**
     * Enable the torch.
     */
    fun enableTorch() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                it.cameraControl.enableTorch(true)
            }
        }
    }

    /**
     * Disable the torch.
     */
    fun disableTorch() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                it.cameraControl.enableTorch(false)
            }
        }
    }

    /**
     * Set the zoom scale (0.0 to 1.0).
     */
    fun setScale(scale: Double) {
        if (scale > 1.0 || scale < 0) throw ZoomNotInRange()
        if (camera == null) throw ZoomWhenStopped()
        camera?.cameraControl?.setLinearZoom(scale.toFloat())
    }

    /**
     * Set the zoom ratio.
     */
    fun setZoomRatio(zoomRatio: Double) {
        if (camera == null) throw ZoomWhenStopped()
        camera?.cameraControl?.setZoomRatio(zoomRatio.toFloat())
    }

    /**
     * Reset the zoom scale.
     */
    fun resetScale() {
        if (camera == null) throw ZoomWhenStopped()
        camera?.cameraControl?.setZoomRatio(1f)
    }

    /**
     * Set the focus point (x and y should be between 0.0 and 1.0).
     */
    fun setFocus(x: Float, y: Float) {
        val cam = camera ?: throw ZoomWhenStopped()

        if (x !in 0f..1f || y !in 0f..1f) {
            throw IllegalArgumentException("Focus coordinates must be between 0.0 and 1.0")
        }

        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val afPoint: MeteringPoint = factory.createPoint(x, y)

        val action = FocusMeteringAction.Builder(afPoint, FocusMeteringAction.FLAG_AF)
            .build()

        cam.cameraControl.startFocusAndMetering(action)
    }

    /**
     * Take a picture and save it to a temporary file.
     *
     * @param onSuccess Callback invoked with the saved file path on success
     * @param onError Callback invoked with error message on failure
     */
    fun takePicture(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("ImageCapture not initialized. Camera may not be started.")
            return
        }

        val activity = context as? android.app.Activity
        if (activity == null) {
            onError("Activity context not available")
            return
        }

        val outputFile = File.createTempFile(
            "CAP_${System.currentTimeMillis()}",
            ".jpg",
            activity.cacheDir
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedPath = outputFileResults.savedUri?.path ?: outputFile.absolutePath
                    Log.d(TAG, "Image saved to: $savedPath")
                    onSuccess(savedPath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                    onError(exception.message ?: "Unknown error during image capture")
                }
            }
        )
    }
}
