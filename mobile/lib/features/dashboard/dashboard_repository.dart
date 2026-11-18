import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../core/providers.dart';

/// GET /reports/dashboard (owner only).
class DashboardSummary {
  const DashboardSummary({
    required this.businessName,
    this.plan,
    this.planExpiry,
    required this.todayTarget,
    required this.todayCollected,
    required this.todayCash,
    required this.todayUpi,
    required this.totalOutstanding,
    required this.overdueAmount,
    required this.overdueLoans,
    required this.activeLoans,
  });

  final String businessName;

  /// TRIAL, MONTHLY or YEARLY.
  final String? plan;
  final DateTime? planExpiry;
  final Money todayTarget;
  final Money todayCollected;
  final Money todayCash;
  final Money todayUpi;
  final Money totalOutstanding;
  final Money overdueAmount;
  final int overdueLoans;
  final int activeLoans;

  factory DashboardSummary.fromJson(Map<String, dynamic> j) => DashboardSummary(
        businessName: (j['businessName'] as String?) ?? '',
        plan: j['plan'] as String?,
        planExpiry: j['planExpiry'] == null ? null : DateTime.parse(j['planExpiry'] as String),
        todayTarget: Money.fromJson(j['todayTarget']),
        todayCollected: Money.fromJson(j['todayCollected']),
        todayCash: Money.fromJson(j['todayCash']),
        todayUpi: Money.fromJson(j['todayUpi']),
        totalOutstanding: Money.fromJson(j['totalOutstanding']),
        overdueAmount: Money.fromJson(j['overdueAmount']),
        overdueLoans: ((j['overdueLoans'] as num?) ?? 0).toInt(),
        activeLoans: ((j['activeLoans'] as num?) ?? 0).toInt(),
      );
}

/// One row of GET /reports/collections?groupBy=AREA for today.
class AreaProgress {
  const AreaProgress({required this.area, required this.target, required this.collected});

  final String area;
  final Money target;
  final Money collected;

  /// 0–100, or null when nothing was due in the area today.
  int? get percent => target.isPositive ? (collected.paise * 100 ~/ target.paise) : null;

  factory AreaProgress.fromJson(Map<String, dynamic> j) => AreaProgress(
        area: (j['key'] as String?) ?? '—',
        target: Money.fromJson(j['target']),
        collected: Money.fromJson(j['collected']),
      );
}

final dashboardProvider = FutureProvider.autoDispose<DashboardSummary>((ref) {
  final dio = ref.watch(apiClientProvider);
  return ApiError.guard(() async {
    final res = await dio.get<Map<String, dynamic>>('/reports/dashboard');
    return DashboardSummary.fromJson(res.data!);
  });
});

final areaProgressProvider = FutureProvider.autoDispose<List<AreaProgress>>((ref) {
  final dio = ref.watch(apiClientProvider);
  final today = DateFormat('yyyy-MM-dd').format(DateTime.now());
  return ApiError.guard(() async {
    final res = await dio.get<List<dynamic>>(
      '/reports/collections',
      queryParameters: {'from': today, 'to': today, 'groupBy': 'AREA'},
    );
    return res.data!
        .map((e) => AreaProgress.fromJson(e as Map<String, dynamic>))
        .where((a) => a.target.isPositive || a.collected.isPositive)
        .toList()
      ..sort((a, b) => b.collected.compareTo(a.collected));
  });
});
