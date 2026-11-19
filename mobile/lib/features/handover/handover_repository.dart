import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

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
    this.remarks,
    this.ownerRemarks,
  });

  final int id;
  final String agentName;
  final DateTime handoverDate;
  final Money expectedAmount;
  final Money handedAmount;

  /// handed − expected: negative means short, positive means extra.
  final Money difference;
  final HandoverStatus status;
  /// The agent's note from submit.
  final String? remarks;

  /// The owner's note from confirm / dispute.
  final String? ownerRemarks;

  bool get matches => difference.isZero;

  factory Handover.fromJson(Map<String, dynamic> j) => Handover(
        id: (j['id'] as num).toInt(),
        agentName: (j['agentName'] as String?) ?? 'Agent',
        handoverDate: DateTime.parse(j['handoverDate'] as String),
        expectedAmount: Money.fromJson(j['expectedAmount']),
        handedAmount: Money.fromJson(j['handedAmount']),
        difference: Money.fromJson(j['difference']),
        status: HandoverStatus.values.byName((j['status'] as String).toLowerCase()),
        remarks: j['remarks'] as String?,
        ownerRemarks: j['ownerRemarks'] as String?,
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

/// Agent: today's cash / UPI totals and whether today was already handed over.
final handoverPreviewProvider = FutureProvider.autoDispose<HandoverPreview>((ref) {
  final dio = ref.watch(apiClientProvider);
  return ApiError.guard(() async {
    final res = await dio.get<Map<String, dynamic>>('/cash-handovers/preview');
    return HandoverPreview.fromJson(res.data!);
  });
});

/// Agent: own handovers of the last two weeks (the server limits agents to their own).
final myRecentHandoversProvider = FutureProvider.autoDispose<List<Handover>>((ref) {
  final dio = ref.watch(apiClientProvider);
  final from = DateFormat('yyyy-MM-dd').format(DateTime.now().subtract(const Duration(days: 14)));
  return ApiError.guard(() async {
    final res = await dio.get<List<dynamic>>('/cash-handovers', queryParameters: {'from': from});
    return res.data!.map((e) => Handover.fromJson(e as Map<String, dynamic>)).toList();
  });
});

final handoverRepositoryProvider = Provider<HandoverRepository>((ref) => HandoverRepository(ref));

class HandoverRepository {
  HandoverRepository(this._ref);

  final Ref _ref;

  /// Agent hands over a day's cash. The server works out the expected amount itself.
  Future<Handover> submit({required DateTime date, required Money handed, String? remarks}) =>
      ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).post<Map<String, dynamic>>('/cash-handovers', data: {
          'handoverDate': DateFormat('yyyy-MM-dd').format(date),
          'handedAmount': handed.toApi(),
          if (remarks != null && remarks.trim().isNotEmpty) 'remarks': remarks.trim(),
        });
        return Handover.fromJson(res.data!);
      });

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

/// GET /cash-handovers/preview.
class HandoverPreview {
  const HandoverPreview({
    required this.date,
    required this.cashCollected,
    required this.cashPayments,
    this.lastCashAt,
    required this.upiCollected,
    this.existing,
  });

  final DateTime date;

  /// Cash payments minus cash reversals: what the owner expects to receive.
  final Money cashCollected;
  final int cashPayments;
  final DateTime? lastCashAt;
  final Money upiCollected;

  /// Set when this day was already handed over.
  final Handover? existing;

  factory HandoverPreview.fromJson(Map<String, dynamic> j) => HandoverPreview(
        date: DateTime.parse(j['date'] as String),
        cashCollected: Money.fromJson(j['cashCollected']),
        cashPayments: ((j['cashPayments'] as num?) ?? 0).toInt(),
        // LocalDateTime in business time (IST) without an offset.
        lastCashAt: j['lastCashAt'] == null ? null : DateTime.parse(j['lastCashAt'] as String),
        upiCollected: Money.fromJson(j['upiCollected']),
        existing: j['existing'] == null ? null : Handover.fromJson(j['existing'] as Map<String, dynamic>),
      );
}
