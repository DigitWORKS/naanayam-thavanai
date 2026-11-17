import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/providers.dart';
import 'core/token_store.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/login_screen.dart';
import 'features/collections/today_screen.dart';
import 'theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final tokens = TokenStore();
  await tokens.load();
  runApp(ProviderScope(
    // No automatic retries: screens show the error with a "Try again" button instead.
    retry: (retryCount, error) => null,
    overrides: [tokenStoreProvider.overrideWithValue(tokens)],
    child: const NaanayamApp(),
  ));
}

class NaanayamApp extends ConsumerWidget {
  const NaanayamApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authControllerProvider);
    return MaterialApp(
      // New key on sign-in/out rebuilds the navigator, dropping any screens pushed in the old session.
      key: ValueKey(switch (auth) { SignedIn(:final user) => 'in-${user.id}', SignedOut() => 'out' }),
      title: 'Naanayam Thavanai',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      home: switch (auth) {
        SignedOut(:final notice) => LoginScreen(key: const ValueKey('login'), notice: notice),
        // Owners get the same list until the dashboard screen is built.
        SignedIn(:final user) => TodayScreen(key: ValueKey('today-${user.id}'), user: user),
      },
    );
  }
}
