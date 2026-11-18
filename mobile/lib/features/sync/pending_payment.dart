import '../../core/money.dart';
import '../payments/payments_repository.dart';

enum PendingState { pending, failed }

/// A payment collected without a connection, kept on the phone until /payments/sync accepts it.
/// [clientTxnId] is the same id the online attempt used, so the server records it at most once.
class PendingPayment {
  const PendingPayment({
    required this.clientTxnId,
    required this.userId,
    required this.loanId,
    required this.loanNo,
    required this.clientName,
    required this.amount,
    required this.mode,
    this.upiRefNo,
    required this.paidOn,
    required this.savedAt,
    this.state = PendingState.pending,
    this.errorCode,
    this.errorMessage,
    this.attempts = 0,
  });

  final String clientTxnId;

  /// Agent / owner who collected it; only that user's session sends it.
  final int userId;
  final int loanId;
  final String loanNo;
  final String clientName;
  final Money amount;
  final PaymentMode mode;
  final String? upiRefNo;
  final DateTime paidOn;
  final DateTime savedAt;
  final PendingState state;
  final String? errorCode;
  final String? errorMessage;
  final int attempts;

  bool get needsAttention => state == PendingState.failed;

  factory PendingPayment.fromDraft(PaymentDraft d, {required int userId, required String loanNo, required String clientName}) =>
      PendingPayment(
        clientTxnId: d.clientTxnId,
        userId: userId,
        loanId: d.loanId,
        loanNo: loanNo,
        clientName: clientName,
        amount: d.amount,
        mode: d.mode,
        upiRefNo: d.upiRefNo,
        paidOn: d.paidOn,
        savedAt: DateTime.now(),
      );

  PaymentDraft toDraft() => PaymentDraft(
        clientTxnId: clientTxnId,
        loanId: loanId,
        amount: amount,
        mode: mode,
        paidOn: paidOn,
        upiRefNo: upiRefNo,
      );

  PendingPayment copyWith({PendingState? state, String? errorCode, String? errorMessage, int? attempts}) =>
      PendingPayment(
        clientTxnId: clientTxnId,
        userId: userId,
        loanId: loanId,
        loanNo: loanNo,
        clientName: clientName,
        amount: amount,
        mode: mode,
        upiRefNo: upiRefNo,
        paidOn: paidOn,
        savedAt: savedAt,
        state: state ?? this.state,
        errorCode: state == PendingState.pending ? null : (errorCode ?? this.errorCode),
        errorMessage: state == PendingState.pending ? null : (errorMessage ?? this.errorMessage),
        attempts: attempts ?? this.attempts,
      );

  Map<String, Object?> toRow() => {
        'client_txn_id': clientTxnId,
        'user_id': userId,
        'loan_id': loanId,
        'loan_no': loanNo,
        'client_name': clientName,
        'amount': amount.toApi(),
        'mode': mode.name,
        'upi_ref_no': upiRefNo,
        'paid_on': paidOn.toUtc().toIso8601String(),
        'saved_at': savedAt.toUtc().toIso8601String(),
        'state': state.name,
        'error_code': errorCode,
        'error_message': errorMessage,
        'attempts': attempts,
      };

  factory PendingPayment.fromRow(Map<String, Object?> r) => PendingPayment(
        clientTxnId: r['client_txn_id'] as String,
        userId: r['user_id'] as int,
        loanId: r['loan_id'] as int,
        loanNo: r['loan_no'] as String,
        clientName: r['client_name'] as String,
        amount: Money.parse(r['amount'] as String),
        mode: PaymentMode.values.byName(r['mode'] as String),
        upiRefNo: r['upi_ref_no'] as String?,
        paidOn: DateTime.parse(r['paid_on'] as String).toLocal(),
        savedAt: DateTime.parse(r['saved_at'] as String).toLocal(),
        state: PendingState.values.byName(r['state'] as String),
        errorCode: r['error_code'] as String?,
        errorMessage: r['error_message'] as String?,
        attempts: (r['attempts'] as int?) ?? 0,
      );
}
