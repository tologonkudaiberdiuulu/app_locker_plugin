import 'package:flutter/services.dart';
import 'app_locker_plugin_platform_interface.dart';

class AppLockerPlugin {
  static const MethodChannel _channel = MethodChannel('app_locker_plugin');

  Future<String?> getPlatformVersion() {
    return AppLockerPluginPlatform.instance.getPlatformVersion();
  }

  /// Starts the app locking service with the specified list of apps to lock.
  static Future<String> startService(List<String> appList) async {
    final String result = await _channel.invokeMethod('startService', {
      'appList': appList,
    });
    return result;
  }

  /// Stops the app locking service.
  static Future<String> stopService() async {
    final String result = await _channel.invokeMethod('stopService');
    return result;
  }

  /// Checks if overlay permission is granted.
  static Future<bool> checkOverlayPermission() async {
    final bool result = await _channel.invokeMethod('checkOverlayPermission');
    return result;
  }

  /// Requests overlay permission.
  static Future<bool> askOverlayPermission() async {
    final bool result = await _channel.invokeMethod('askOverlayPermission');
    return result;
  }

  /// Checks if usage stats permission is granted.
  static Future<bool> checkUsageStatsPermission() async {
    final bool result = await _channel.invokeMethod(
      'checkUsageStatsPermission',
    );
    return result;
  }

  /// Requests usage stats permission.
  static Future<bool> askUsageStatsPermission() async {
    final bool result = await _channel.invokeMethod('askUsageStatsPermission');
    return result;
  }

  /// Shows the overlay UI.
  static Future<bool> showOverlay() async {
    final bool result = await _channel.invokeMethod('showOverlay');
    return result;
  }

  /// Hides the overlay UI.
  static Future<bool> hideOverlay() async {
    final bool result = await _channel.invokeMethod('hideOverlay');
    return result;
  }

  /// Sets up a listener for method calls from native.
  static void setMethodCallHandler(
    Future<dynamic> Function(MethodCall call)? handler,
  ) {
    _channel.setMethodCallHandler(handler);
  }
}
