package io.flutter.plugins.camerapreview.example

import io.flutter.plugins.nativeview.NativeViewFlutterActivity
import io.flutter.plugins.camerapreview.CameraSurfacePreview

class MainActivity : NativeViewFlutterActivity() {
    override fun onRegisterNativeViews() {
        registerNativeViewFactory("camera_preview") {
            CameraSurfacePreview()
        }
    }
}
