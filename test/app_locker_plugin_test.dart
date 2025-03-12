import 'package:flutter_test/flutter_test.dart';
import 'package:app_locker_plugin/app_locker_plugin.dart';
import 'package:app_locker_plugin/app_locker_plugin_platform_interface.dart';
import 'package:app_locker_plugin/app_locker_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockAppLockerPluginPlatform
    with MockPlatformInterfaceMixin
    implements AppLockerPluginPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final AppLockerPluginPlatform initialPlatform = AppLockerPluginPlatform.instance;

  test('$MethodChannelAppLockerPlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelAppLockerPlugin>());
  });

  test('getPlatformVersion', () async {
    AppLockerPlugin appLockerPlugin = AppLockerPlugin();
    MockAppLockerPluginPlatform fakePlatform = MockAppLockerPluginPlatform();
    AppLockerPluginPlatform.instance = fakePlatform;

    expect(await appLockerPlugin.getPlatformVersion(), '42');
  });
}
