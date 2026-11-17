import 'package:dio/dio.dart';

import 'config.dart';
import 'token_store.dart';

/// Request option: set `extra: {kNoAuth: true}` for public endpoints (login, refresh).
const kNoAuth = 'noAuth';
const _kRetried = 'retried';

Dio buildApiClient(TokenStore store, {required void Function() onSessionExpired}) {
  BaseOptions options() => BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 20),
        contentType: Headers.jsonContentType,
      );

  // Bare client for refresh and for replaying a request after refresh (no interceptors).
  final bare = Dio(options());
  final dio = Dio(options());
  dio.interceptors.add(_AuthInterceptor(store, bare, onSessionExpired));
  return dio;
}

/// Adds the bearer token; on 401 refreshes once and replays the request.
/// [QueuedInterceptor] handles errors one at a time, so parallel 401s trigger a single refresh.
class _AuthInterceptor extends QueuedInterceptor {
  _AuthInterceptor(this._store, this._bare, this._onSessionExpired);

  final TokenStore _store;
  final Dio _bare;
  final void Function() _onSessionExpired;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = _store.accessToken;
    if (options.extra[kNoAuth] != true && token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final req = err.requestOptions;
    final eligible = err.response?.statusCode == 401 && req.extra[kNoAuth] != true && req.extra[_kRetried] != true;
    if (!eligible) return handler.next(err);

    final sentWith = (req.headers['Authorization'] as String?)?.replaceFirst('Bearer ', '');
    // Another queued request may already have refreshed the token.
    final alreadyRefreshed = _store.accessToken != null && _store.accessToken != sentWith;
    if (!alreadyRefreshed) {
      final outcome = await _refresh();
      if (outcome == _Refresh.offline) return handler.next(err);
      if (outcome == _Refresh.rejected) {
        _onSessionExpired();
        return handler.next(err);
      }
    }

    req.headers['Authorization'] = 'Bearer ${_store.accessToken}';
    req.extra[_kRetried] = true;
    try {
      handler.resolve(await _bare.fetch(req));
    } on DioException catch (e) {
      handler.next(e);
    }
  }

  Future<_Refresh> _refresh() async {
    final refreshToken = _store.refreshToken;
    if (refreshToken == null) return _Refresh.rejected;
    try {
      final res = await _bare.post<Map<String, dynamic>>('/auth/refresh', data: {'refreshToken': refreshToken});
      final body = res.data!;
      await _store.save(
        accessToken: body['accessToken'] as String,
        refreshToken: body['refreshToken'] as String,
        userJson: body['user'] as Map<String, dynamic>,
      );
      return _Refresh.ok;
    } on DioException catch (e) {
      // Offline: keep the session; only a definite rejection signs the user out.
      return e.response == null ? _Refresh.offline : _Refresh.rejected;
    }
  }
}

enum _Refresh { ok, offline, rejected }
