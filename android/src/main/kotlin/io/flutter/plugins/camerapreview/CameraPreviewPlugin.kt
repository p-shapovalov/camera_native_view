package io.flutter.plugins.camerapreview

import io.flutter.embedding.engine.plugins.FlutterPlugin

/**
 * Flutter plugin for camera preview.
 *
 * This plugin provides camera preview functionality using CameraX and SurfaceView
 * rendered below the Flutter layer.
 */
class CameraPreviewPlugin : FlutterPlugin {
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // Plugin initialization - the actual camera preview is handled by
        // CameraSurfacePreview which extends NativeView
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // Cleanup
    }
}
