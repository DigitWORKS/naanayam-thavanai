import '../../core/money.dart';

/// One loan on the due list (API schema DueItem).
class DueItem {
  const DueItem({
    required this.loanId,
    required this.loanNo,
    required this.clientId,
    required this.clientName,
    required this.clientPhone,
    required this.areaName,
    required this.todayDue,
    required this.overdueAmount,
    required this.overdueCount,
    required this.penaltyDue,
    required this.totalDue,
    required this.collectedToday,
  });

  final int loanId;
  final String loanNo;
  final int clientId;
  final String clientName;
  final String clientPhone;
  final String areaName;
  final Money todayDue;
  final Money overdueAmount;
  final int overdueCount;
  final Money penaltyDue;

  /// Still to collect now: overdue + today + penalty, less what was already paid.
  final Money totalDue;
  final Money collectedToday;

  bool get isSettledToday => !totalDue.isPositive;

  factory DueItem.fromJson(Map<String, dynamic> j) => DueItem(
        loanId: (j['loanId'] as num).toInt(),
        loanNo: j['loanNo'] as String,
        clientId: (j['clientId'] as num).toInt(),
        clientName: j['clientName'] as String,
        clientPhone: (j['clientPhone'] as String?) ?? '',
        areaName: (j['areaName'] as String?) ?? '',
        todayDue: Money.fromJson(j['todayDue']),
        overdueAmount: Money.fromJson(j['overdueAmount']),
        overdueCount: ((j['overdueCount'] as num?) ?? 0).toInt(),
        penaltyDue: Money.fromJson(j['penaltyDue']),
        totalDue: Money.fromJson(j['totalDue']),
        collectedToday: Money.fromJson(j['collectedToday']),
      );
}
