package io.flutter.plugins.camerapreview

typealias TorchStateCallback = (state: Int) -> Unit
typealias ZoomScaleStateCallback = (zoomScale: Double) -> Unit
typealias CameraStartedCallback = (width: Double, height: Double, torchState: Int, numberOfCameras: Int, cameraDirection: Int?) -> Unit
