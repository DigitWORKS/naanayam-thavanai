import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Tokens and the signed-in user, kept in the platform keystore and cached in memory
/// so the HTTP interceptor can read them synchronously.
class TokenStore {
  TokenStore([FlutterSecureStorage? storage]) : _storage = storage ?? const FlutterSecureStorage();

  static const _kAccess = 'access_token';
  static const _kRefresh = 'refresh_token';
  static const _kUser = 'user';

  final FlutterSecureStorage _storage;

  String? accessToken;
  String? refreshToken;
  Map<String, dynamic>? userJson;

  bool get hasSession => refreshToken != null && userJson != null;

  Future<void> load() async {
    accessToken = await _storage.read(key: _kAccess);
    refreshToken = await _storage.read(key: _kRefresh);
    final user = await _storage.read(key: _kUser);
    userJson = user == null ? null : jsonDecode(user) as Map<String, dynamic>;
  }

  Future<void> save({
    required String accessToken,
    required String refreshToken,
    required Map<String, dynamic> userJson,
  }) async {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.userJson = userJson;
    await _storage.write(key: _kAccess, value: accessToken);
    await _storage.write(key: _kRefresh, value: refreshToken);
    await _storage.write(key: _kUser, value: jsonEncode(userJson));
  }

  Future<void> clear() async {
    accessToken = null;
    refreshToken = null;
    userJson = null;
    await _storage.deleteAll();
  }
}
