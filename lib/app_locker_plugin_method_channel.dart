import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'app_locker_plugin_platform_interface.dart';

/// An implementation of [AppLockerPluginPlatform] that uses method channels.
class MethodChannelAppLockerPlugin extends AppLockerPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('app_locker_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
