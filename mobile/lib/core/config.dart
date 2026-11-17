import 'dart:io' show Platform;

/// Build-time configuration.
///
/// Override the API with `--dart-define=API_BASE_URL=http://192.168.1.10:8080/v1`
/// (needed on a physical phone: use the Mac's LAN IP).
class AppConfig {
  AppConfig._();

  static const _apiFromEnv = String.fromEnvironment('API_BASE_URL');

  static String get apiBaseUrl {
    if (_apiFromEnv.isNotEmpty) return _apiFromEnv;
    // Android emulator reaches the host machine on 10.0.2.2; iOS simulator shares localhost.
    return Platform.isAndroid ? 'http://10.0.2.2:8080/v1' : 'http://localhost:8080/v1';
  }
}
