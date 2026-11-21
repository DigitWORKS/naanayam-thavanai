import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

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
    await _signIn(tokens);
  }

  /// Self-registration: creates the business on the free trial and signs the owner in.
  Future<void> register(RegisterInput input) async {
    final dio = ref.read(apiClientProvider);
    final tokens = await ApiError.guard(() async {
      final res = await dio.post<Map<String, dynamic>>(
        '/auth/register',
        data: input.toJson(),
        options: Options(extra: {kNoAuth: true}),
      );
      return TokenResponse.fromJson(res.data!);
    });
    await _signIn(tokens);
  }

  /// Changes the signed-in user's PIN. The server signs out every other device and returns new tokens
  /// for this one; the user is unchanged, so the session (and open screens) carry on.
  Future<void> changePin(String currentPin, String newPin) async {
    final dio = ref.read(apiClientProvider);
    final tokens = await ApiError.guard(() async {
      final res = await dio.post<Map<String, dynamic>>(
        '/auth/change-pin',
        data: {'currentPin': currentPin, 'newPin': newPin},
      );
      return TokenResponse.fromJson(res.data!);
    });
    await ref.read(tokenStoreProvider).save(
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          userJson: tokens.userJson,
        );
  }

  /// Owner deletes the business account. The server closes it, signs everyone out and erases the data on
  /// the returned date; this phone signs out with a notice on the login screen.
  Future<void> deleteAccount(String pin) async {
    final dio = ref.read(apiClientProvider);
    final eraseOn = await ApiError.guard(() async {
      final res = await dio.post<Map<String, dynamic>>('/auth/delete-account', data: {'pin': pin});
      return DateFormat('d MMM yyyy').format(DateTime.parse(res.data!['eraseOn'] as String));
    });
    await ref.read(tokenStoreProvider).clear();
    state = SignedOut('Your business account is deleted. All its data will be erased on $eraseOn. '
        'To undo, contact DigitWORKS support before then.');
  }

  Future<void> _signIn(TokenResponse tokens) async {
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
