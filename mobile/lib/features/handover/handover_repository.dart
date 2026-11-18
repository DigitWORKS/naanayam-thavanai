import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../core/providers.dart';

enum HandoverStatus { pending, confirmed, disputed }

/// Cash an agent handed to the owner for one day (API: HandoverResponse).
class Handover {
  const Handover({
    required this.id,
    required this.agentName,
    required this.handoverDate,
    required this.expectedAmount,
    required this.handedAmount,
    required this.difference,
    required this.status,
  });

  final int id;
  final String agentName;
  final DateTime handoverDate;
  final Money expectedAmount;
  final Money handedAmount;

  /// handed − expected: negative means short, positive means extra.
  final Money difference;
  final HandoverStatus status;

  bool get matches => difference.isZero;

  factory Handover.fromJson(Map<String, dynamic> j) => Handover(
        id: (j['id'] as num).toInt(),
        agentName: (j['agentName'] as String?) ?? 'Agent',
        handoverDate: DateTime.parse(j['handoverDate'] as String),
        expectedAmount: Money.fromJson(j['expectedAmount']),
        handedAmount: Money.fromJson(j['handedAmount']),
        difference: Money.fromJson(j['difference']),
        status: HandoverStatus.values.byName((j['status'] as String).toLowerCase()),
      );
}

/// Handovers waiting for the owner to confirm.
final pendingHandoversProvider = FutureProvider.autoDispose<List<Handover>>((ref) {
  final dio = ref.watch(apiClientProvider);
  return ApiError.guard(() async {
    final res = await dio.get<List<dynamic>>('/cash-handovers', queryParameters: {'status': 'PENDING'});
    return res.data!.map((e) => Handover.fromJson(e as Map<String, dynamic>)).toList();
  });
});

final handoverRepositoryProvider = Provider<HandoverRepository>((ref) => HandoverRepository(ref));

class HandoverRepository {
  HandoverRepository(this._ref);

  final Ref _ref;

  /// Owner decision: CONFIRMED or DISPUTED, with optional remarks.
  Future<void> decide(int handoverId, HandoverStatus decision, {String? remarks}) => ApiError.guard(() async {
        await _ref.read(apiClientProvider).post<Map<String, dynamic>>(
          '/cash-handovers/$handoverId/confirm',
          data: {
            'status': decision.name.toUpperCase(),
            if (remarks != null && remarks.trim().isNotEmpty) 'remarks': remarks.trim(),
          },
        );
      });
}
