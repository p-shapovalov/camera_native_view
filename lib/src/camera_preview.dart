import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_native_view_android/flutter_native_view_android.dart';

/// Default key for identifying the camera preview native view.
const String kCameraPreviewViewKey = 'camera_preview';

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
    this.viewKey = kCameraPreviewViewKey,
    this.onViewReady,
    this.placeholderBuilder,
  });

  /// The unique key identifying the native view to control.
  ///
  /// This must match the key registered in the MainActivity.
  final String viewKey;

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
      viewKey: widget.viewKey,
      onViewReady: _onViewReady,
    );

    final showPlaceholder = !_isViewReady && widget.placeholderBuilder != null;

    if (!showPlaceholder) {
      return nativeViewWidget;
    }

    return Stack(
      children: [
        nativeViewWidget,
        Positioned.fill(child: widget.placeholderBuilder!(context)),
      ],
    );
  }
}

/// Internal widget that extends NativeViewWidget to manage the native view
/// lifecycle.
class _CameraPreviewNativeWidget extends NativeViewWidget {
  const _CameraPreviewNativeWidget({
    required this.viewKey,
    this.onViewReady,
  });

  @override
  final String viewKey;

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
