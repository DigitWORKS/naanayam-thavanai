import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/auth/auth_controller.dart';
import 'api_client.dart';
import 'token_store.dart';

/// Loaded in main() before the first frame and injected with overrideWithValue.
final tokenStoreProvider = Provider<TokenStore>((ref) => throw UnimplementedError('override in main()'));

final apiClientProvider = Provider<Dio>((ref) {
  final store = ref.watch(tokenStoreProvider);
  return buildApiClient(
    store,
    onSessionExpired: () => ref.read(authControllerProvider.notifier).sessionExpired(),
  );
});
