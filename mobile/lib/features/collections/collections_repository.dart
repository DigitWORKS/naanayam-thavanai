import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/providers.dart';
import 'due_item.dart';

/// Today's due list, optionally for one area (null = all of the caller's areas).
final dueListProvider = FutureProvider.autoDispose.family<List<DueItem>, int?>((ref, areaId) {
  final dio = ref.watch(apiClientProvider);
  return ApiError.guard(() async {
    final res = await dio.get<List<dynamic>>(
      '/collections/due',
      queryParameters: {if (areaId != null) 'areaId': areaId},
    );
    return res.data!.map((e) => DueItem.fromJson(e as Map<String, dynamic>)).toList();
  });
});
