import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/api_error.dart';
import '../../core/providers.dart';
import 'auth_models.dart';

sealed class AuthState {
  const AuthState();
}

class SignedOut extends AuthState {
  const SignedOut([this.notice]);

  /// Shown on the login screen, e.g. after the session expired.
  final String? notice;
}

class SignedIn extends AuthState {
  const SignedIn(this.user);

  final AppUser user;
}

final authControllerProvider = NotifierProvider<AuthController, AuthState>(AuthController.new);

class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    final store = ref.read(tokenStoreProvider);
    return store.hasSession ? SignedIn(AppUser.fromJson(store.userJson!)) : const SignedOut();
  }

  Future<void> login(String phone, String pin) async {
    final dio = ref.read(apiClientProvider);
    final tokens = await ApiError.guard(() async {
      final res = await dio.post<Map<String, dynamic>>(
        '/auth/login',
        data: {'phone': phone, 'pin': pin},
        options: Options(extra: {kNoAuth: true}),
      );
      return TokenResponse.fromJson(res.data!);
    });
    if (tokens.user.role == Role.superAdmin) {
      throw const ApiError(code: 'WEB_ONLY', message: 'Super Admin uses the web app.');
    }
    await ref.read(tokenStoreProvider).save(
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          userJson: tokens.userJson,
        );
    state = SignedIn(tokens.user);
  }

  Future<void> logout() async {
    await ref.read(tokenStoreProvider).clear();
    state = const SignedOut();
  }

  Future<void> sessionExpired() async {
    if (state is SignedOut) return;
    await ref.read(tokenStoreProvider).clear();
    state = const SignedOut('Your session has expired. Please log in again.');
  }
}
