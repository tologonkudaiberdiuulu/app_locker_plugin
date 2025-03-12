import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'app_locker_plugin_method_channel.dart';

abstract class AppLockerPluginPlatform extends PlatformInterface {
  /// Constructs a AppLockerPluginPlatform.
  AppLockerPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static AppLockerPluginPlatform _instance = MethodChannelAppLockerPlugin();

  /// The default instance of [AppLockerPluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelAppLockerPlugin].
  static AppLockerPluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AppLockerPluginPlatform] when
  /// they register themselves.
  static set instance(AppLockerPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
