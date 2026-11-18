import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/providers.dart';
import '../loans/loans_repository.dart';
import 'settings_models.dart';

final settingsProvider = FutureProvider.autoDispose<BusinessSettings>((ref) {
  final dio = ref.watch(apiClientProvider);
  return ApiError.guard(() async {
    final res = await dio.get<Map<String, dynamic>>('/settings');
    return BusinessSettings.fromJson(res.data!);
  });
});

final settingsRepositoryProvider = Provider<SettingsRepository>((ref) => SettingsRepository(ref));

class SettingsRepository {
  SettingsRepository(this._ref);

  final Ref _ref;

  /// Owner only. Refreshes the copies other screens read (loan rules for New loan / pre-closure).
  Future<BusinessSettings> save(BusinessSettings s) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).put<Map<String, dynamic>>('/settings', data: s.toJson());
        _ref.invalidate(loanRulesProvider);
        _ref.invalidate(settingsProvider);
        return BusinessSettings.fromJson(res.data!);
      });
}
