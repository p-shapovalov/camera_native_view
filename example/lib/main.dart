import 'package:flutter/material.dart';
import 'package:camerapreview/camerapreview.dart';
import 'package:flutter_native_view_android/flutter_native_view_android.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return NativeViewOverlayApp(
      enabled: true,
      child: MaterialApp(
        title: 'Camera Preview Example',
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
          useMaterial3: true,
        ),
        home: const CameraPage(),
      ),
    );
  }
}

class CameraPage extends StatefulWidget {
  const CameraPage({super.key});

  @override
  State<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  final _controller = CameraPreviewController();
  bool _isTorchOn = false;
  double _zoomLevel = 0.0;
  bool _isReady = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: Stack(
          children: [
            // Camera Preview
            Positioned.fill(
              child: NativeViewOverlayBody(
                enabled: true,
                child: CameraPreview(
                  onViewReady: () {
                    setState(() => _isReady = true);
                  },
                  placeholderBuilder: (context) => const Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CircularProgressIndicator(color: Colors.white),
                        SizedBox(height: 16),
                        Text(
                          'Starting camera...',
                          style: TextStyle(color: Colors.white),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),

            // Controls overlay
            if (_isReady)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.bottomCenter,
                      end: Alignment.topCenter,
                      colors: [
                        Colors.black.withValues(alpha: 0.7),
                        Colors.transparent,
                      ],
                    ),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Zoom slider
                      Row(
                        children: [
                          const Icon(Icons.zoom_out, color: Colors.white),
                          Expanded(
                            child: Slider(
                              value: _zoomLevel,
                              onChanged: (value) async {
                                setState(() => _zoomLevel = value);
                                await _controller.setZoom(value);
                              },
                            ),
                          ),
                          const Icon(Icons.zoom_in, color: Colors.white),
                        ],
                      ),
                      Text(
                        'Zoom: ${(_zoomLevel * 100).toInt()}%',
                        style: const TextStyle(color: Colors.white),
                      ),
                      const SizedBox(height: 16),

                      // Control buttons
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          // Torch toggle
                          _ControlButton(
                            icon: _isTorchOn ? Icons.flash_on : Icons.flash_off,
                            label: _isTorchOn ? 'Torch On' : 'Torch Off',
                            onPressed: () async {
                              await _controller.toggleTorch();
                              setState(() => _isTorchOn = !_isTorchOn);
                            },
                          ),

                          // Reset zoom
                          _ControlButton(
                            icon: Icons.refresh,
                            label: 'Reset Zoom',
                            onPressed: () async {
                              await _controller.resetZoom();
                              setState(() => _zoomLevel = 0.0);
                            },
                          ),

                          // 2x zoom preset
                          _ControlButton(
                            icon: Icons.filter_2,
                            label: '2x Zoom',
                            onPressed: () async {
                              await _controller.setZoomRatio(2.0);
                              setState(() => _zoomLevel = 0.5);
                            },
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _ControlButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onPressed;

  const _ControlButton({
    required this.icon,
    required this.label,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton.filled(
          onPressed: onPressed,
          icon: Icon(icon),
          style: IconButton.styleFrom(
            backgroundColor: Colors.white24,
            foregroundColor: Colors.white,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(color: Colors.white, fontSize: 12),
        ),
      ],
    );
  }
}
