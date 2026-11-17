import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../core/providers.dart';

enum PaymentMode { cash, upi }

/// A payment as entered on the device. [clientTxnId] is fixed when the form opens and
/// reused on every retry, so the server records it at most once (idempotency key).
class PaymentDraft {
  const PaymentDraft({
    required this.clientTxnId,
    required this.loanId,
    required this.amount,
    required this.mode,
    required this.paidOn,
    this.upiRefNo,
  });

  final String clientTxnId;
  final int loanId;
  final Money amount;
  final PaymentMode mode;
  final DateTime paidOn;
  final String? upiRefNo;

  Map<String, dynamic> toJson() => {
        'clientTxnId': clientTxnId,
        'loanId': loanId,
        'amount': amount.toApi(),
        'mode': mode == PaymentMode.upi ? 'UPI' : 'CASH',
        if (mode == PaymentMode.upi) 'upiRefNo': upiRefNo,
        // UTC with "Z": the server converts to business time (Asia/Kolkata).
        'paidOn': paidOn.toUtc().toIso8601String(),
      };
}

class RecordedPayment {
  const RecordedPayment({required this.id, required this.amount, required this.balanceAfter});

  final int id;
  final Money amount;

  /// Loan balance after this payment.
  final Money balanceAfter;

  factory RecordedPayment.fromJson(Map<String, dynamic> j) => RecordedPayment(
        id: (j['id'] as num).toInt(),
        amount: Money.fromJson(j['amount']),
        balanceAfter: Money.fromJson(j['balanceAfter']),
      );
}

final paymentsRepositoryProvider = Provider<PaymentsRepository>((ref) => PaymentsRepository(ref));

class PaymentsRepository {
  PaymentsRepository(this._ref);

  final Ref _ref;

  /// 201 (new) and 200 (same clientTxnId already recorded) both mean success.
  Future<RecordedPayment> record(PaymentDraft draft) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).post<Map<String, dynamic>>('/payments', data: draft.toJson());
        return RecordedPayment.fromJson(res.data!);
      });
}

/// UTR as typed or pasted: spaces removed; valid when exactly 12 digits.
String normalizeUtr(String input) => input.replaceAll(RegExp(r'\s'), '');

bool isValidUtr(String utr) => RegExp(r'^\d{12}$').hasMatch(utr);
