package io.flutter.plugins.camerapreview

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Flutter plugin for camera preview.
 *
 * This plugin provides camera preview functionality using CameraX and SurfaceView
 * rendered below the Flutter layer.
 */
class CameraPreviewPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler {
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    private var methodChannel: MethodChannel? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = binding
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        val binaryMessenger = flutterPluginBinding!!.binaryMessenger

        methodChannel = MethodChannel(binaryMessenger, "io.flutter.plugins.camerapreview/control")
        methodChannel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromActivity() {
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "enableTorch" -> {
                CameraSurfacePreview.enableTorchStatic()
                result.success(null)
            }
            "disableTorch" -> {
                CameraSurfacePreview.disableTorchStatic()
                result.success(null)
            }
            "toggleTorch" -> {
                CameraSurfacePreview.toggleTorchStatic()
                result.success(null)
            }
            "setZoom" -> {
                val zoom = call.argument<Double>("zoom") ?: 0.0
                CameraSurfacePreview.setScaleStatic(zoom)
                result.success(null)
            }
            "setZoomRatio" -> {
                val zoomRatio = call.argument<Double>("zoomRatio") ?: 1.0
                CameraSurfacePreview.setZoomRatioStatic(zoomRatio)
                result.success(null)
            }
            "resetZoom" -> {
                CameraSurfacePreview.resetScaleStatic()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }
}
