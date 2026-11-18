import 'package:intl/intl.dart';

import '../../core/money.dart';
import '../loans/loan_models.dart' show ChargeType, InterestMethod;

enum PenaltyType { fixed, percent }

enum AllocationOrder { penaltyFirst, installmentFirst }

enum AdvanceAllocation { nextInstallments, lastInstallments }

String _api(Enum e) => e.name.replaceAllMapped(RegExp(r'[A-Z]'), (m) => '_${m[0]}').toUpperCase();

T? _fromApi<T extends Enum>(List<T> values, Object? api) =>
    api == null ? null : values.firstWhere((v) => _api(v) == api);

/// GET / PUT /settings (owner edits; the whole object is sent back on save).
class BusinessSettings {
  BusinessSettings({
    required this.maxInterestRate,
    required this.allowedMethods,
    required this.maxActiveLoansPerClient,
    required this.penaltyEnabled,
    required this.penaltyType,
    required this.penaltyValue,
    required this.penaltyGraceDays,
    required this.penaltyMaxCap,
    required this.penaltyEffectiveFrom,
    required this.recordRetentionYears,
    required this.allocationOrder,
    required this.advanceAllocation,
    required this.preclosureEnabled,
    required this.rebateUnearnedInterest,
    required this.chargeType,
    required this.chargeValue,
  });

  Money maxInterestRate;
  Set<InterestMethod> allowedMethods;
  int maxActiveLoansPerClient;
  bool penaltyEnabled;
  PenaltyType penaltyType;
  Money penaltyValue;
  int penaltyGraceDays;

  /// Null means no cap per loan.
  Money? penaltyMaxCap;

  /// Set by the server when the penalty is turned on; read only.
  final DateTime? penaltyEffectiveFrom;

  /// Read only in the app (set on the CA's advice).
  final int recordRetentionYears;
  AllocationOrder allocationOrder;
  AdvanceAllocation advanceAllocation;
  bool preclosureEnabled;
  bool rebateUnearnedInterest;
  ChargeType chargeType;
  Money chargeValue;

  factory BusinessSettings.fromJson(Map<String, dynamic> j) => BusinessSettings(
        maxInterestRate: Money.fromJson(j['maxInterestRate']),
        allowedMethods: ((j['allowedInterestMethods'] as List?) ?? const ['FLAT'])
            .map((m) => _fromApi(InterestMethod.values, m)!)
            .toSet(),
        maxActiveLoansPerClient: ((j['maxActiveLoansPerClient'] as num?) ?? 2).toInt(),
        penaltyEnabled: (j['penaltyEnabled'] as bool?) ?? false,
        penaltyType: _fromApi(PenaltyType.values, j['penaltyType']) ?? PenaltyType.fixed,
        penaltyValue: Money.fromJson(j['penaltyValue']),
        penaltyGraceDays: ((j['penaltyGraceDays'] as num?) ?? 0).toInt(),
        penaltyMaxCap: j['penaltyMaxCap'] == null ? null : Money.fromJson(j['penaltyMaxCap']),
        penaltyEffectiveFrom:
            j['penaltyEffectiveFrom'] == null ? null : DateTime.parse(j['penaltyEffectiveFrom'] as String),
        recordRetentionYears: ((j['recordRetentionYears'] as num?) ?? 8).toInt(),
        allocationOrder: _fromApi(AllocationOrder.values, j['allocationOrder']) ?? AllocationOrder.penaltyFirst,
        advanceAllocation:
            _fromApi(AdvanceAllocation.values, j['advanceAllocation']) ?? AdvanceAllocation.nextInstallments,
        preclosureEnabled: (j['preclosureEnabled'] as bool?) ?? false,
        rebateUnearnedInterest: j['preclosureRebate'] == 'UNEARNED_INTEREST',
        chargeType: _fromApi(ChargeType.values, j['preclosureChargeType']) ?? ChargeType.none,
        chargeValue: Money.fromJson(j['preclosureChargeValue']),
      );

  Map<String, dynamic> toJson() => {
        'maxInterestRate': maxInterestRate.toApi(),
        'allowedInterestMethods': allowedMethods.map(_api).toList(),
        'maxActiveLoansPerClient': maxActiveLoansPerClient,
        'penaltyEnabled': penaltyEnabled,
        'penaltyType': _api(penaltyType),
        'penaltyValue': penaltyValue.toApi(),
        'penaltyGraceDays': penaltyGraceDays,
        'penaltyMaxCap': penaltyMaxCap?.toApi(),
        if (penaltyEffectiveFrom != null) 'penaltyEffectiveFrom': DateFormat('yyyy-MM-dd').format(penaltyEffectiveFrom!),
        'recordRetentionYears': recordRetentionYears,
        'allocationOrder': _api(allocationOrder),
        'advanceAllocation': _api(advanceAllocation),
        'preclosureEnabled': preclosureEnabled,
        'preclosureRebate': rebateUnearnedInterest ? 'UNEARNED_INTEREST' : 'NONE',
        'preclosureChargeType': _api(chargeType),
        'preclosureChargeValue': chargeType == ChargeType.none ? '0.00' : chargeValue.toApi(),
      };
}
