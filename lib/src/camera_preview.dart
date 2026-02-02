import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_native_view_android/flutter_native_view_android.dart';

/// Key for identifying the camera preview native view.
const String _kCameraPreviewViewKey = 'camera_preview';

/// Controller for interacting with the camera preview.
///
/// Use this class to control torch and zoom functionality.
class CameraPreviewController {
  CameraPreviewController();

  static const _channel = MethodChannel('io.flutter.plugins.camerapreview/control');

  /// Enables the torch (flashlight).
  Future<void> enableTorch() async {
    await _channel.invokeMethod('enableTorch');
  }

  /// Disables the torch (flashlight).
  Future<void> disableTorch() async {
    await _channel.invokeMethod('disableTorch');
  }

  /// Toggles the torch state.
  Future<void> toggleTorch() async {
    await _channel.invokeMethod('toggleTorch');
  }

  /// Sets the zoom level using linear scale (0.0 to 1.0).
  ///
  /// [zoom] must be between 0.0 and 1.0, where:
  /// - 0.0 represents minimum zoom (widest angle)
  /// - 1.0 represents maximum zoom
  Future<void> setZoom(double zoom) async {
    await _channel.invokeMethod('setZoom', {'zoom': zoom});
  }

  /// Sets the zoom level using zoom ratio.
  ///
  /// [zoomRatio] is the actual zoom multiplier (e.g., 2.0 for 2x zoom).
  Future<void> setZoomRatio(double zoomRatio) async {
    await _channel.invokeMethod('setZoomRatio', {'zoomRatio': zoomRatio});
  }

  /// Resets the zoom to the default level (1x).
  Future<void> resetZoom() async {
    await _channel.invokeMethod('resetZoom');
  }
}

/// A widget that displays a camera preview using a native SurfaceView
/// rendered below the Flutter layer.
///
/// This widget uses CameraX on Android for camera management and renders
/// the preview using a SurfaceView for optimal performance.
///
/// **Important:** This widget only works on Android and requires the app's
/// MainActivity to extend `NativeViewFlutterActivity` and register the
/// camera preview native view factory.
///
/// Example usage in MainActivity.kt:
/// ```kotlin
/// class MainActivity : NativeViewFlutterActivity() {
///     override fun onRegisterNativeViews() {
///         registerNativeViewFactory("camera_preview") {
///             CameraSurfacePreview()
///         }
///     }
/// }
/// ```
class CameraPreview extends StatefulWidget {
  /// Creates a camera preview widget.
  const CameraPreview({
    super.key,
    this.onViewReady,
    this.placeholderBuilder,
  });

  /// Called when the native view has been created and is ready.
  final VoidCallback? onViewReady;

  /// Builder for a placeholder widget shown while the native view is loading.
  final WidgetBuilder? placeholderBuilder;

  @override
  State<CameraPreview> createState() => _CameraPreviewState();
}

class _CameraPreviewState extends State<CameraPreview> {
  bool _isViewReady = false;

  void _onViewReady() {
    if (mounted && !_isViewReady) {
      setState(() {
        _isViewReady = true;
      });
      widget.onViewReady?.call();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return const Center(
        child: Text('Camera preview is only supported on Android'),
      );
    }

    final Widget nativeViewWidget = _CameraPreviewNativeWidget(
      onViewReady: _onViewReady,
    );

    final showPlaceholder = !_isViewReady && widget.placeholderBuilder != null;

    return Stack(
      children: [
        nativeViewWidget,
        if (showPlaceholder) Positioned.fill(child: widget.placeholderBuilder!(context)),
      ],
    );
  }
}

/// Internal widget that extends NativeViewWidget to manage the native view
/// lifecycle.
class _CameraPreviewNativeWidget extends NativeViewWidget {
  const _CameraPreviewNativeWidget({
    this.onViewReady,
  });

  @override
  String get viewKey => _kCameraPreviewViewKey;

  final VoidCallback? onViewReady;

  @override
  void onViewShown() {
    onViewReady?.call();
  }

  @override
  void onViewHidden() {
    // Camera is managed by native view lifecycle
  }
}
