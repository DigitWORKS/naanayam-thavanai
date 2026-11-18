/// What to do with a queued payment after one /payments/sync round. Pure, so it is unit tested.
enum SyncAction {
  /// Recorded on the server (CREATED, or DUPLICATE = an earlier attempt already got through).
  remove,

  /// Not settled yet; send again in the next round.
  retry,

  /// Rejected by a business rule; a person must look at it (Needs attention).
  fail,
}

class SyncDecision {
  const SyncDecision(this.action, {this.code, this.message});

  final SyncAction action;
  final String? code;
  final String? message;
}

/// Server failure codes that clear up on their own when the same payment is sent again.
const _retryableCodes = {'DUPLICATE'};

/// Decides per clientTxnId. Items the server did not answer for are retried.
Map<String, SyncDecision> decideSyncResults(Iterable<String> sent, List<dynamic> results) {
  final byId = <String, SyncDecision>{};
  for (final r in results.cast<Map<String, dynamic>>()) {
    final id = r['clientTxnId'] as String?;
    if (id == null) continue;
    byId[id] = switch (r['status']) {
      'CREATED' || 'DUPLICATE' => const SyncDecision(SyncAction.remove),
      _ => _failure(r['error']),
    };
  }
  return {for (final id in sent) id: byId[id] ?? const SyncDecision(SyncAction.retry)};
}

SyncDecision _failure(Object? error) {
  final e = error is Map ? error : const {};
  final code = e['code'] as String?;
  if (code != null && _retryableCodes.contains(code)) return SyncDecision(SyncAction.retry, code: code);
  return SyncDecision(
    SyncAction.fail,
    code: code ?? 'FAILED',
    message: _friendly(code) ?? (e['message'] as String?) ?? 'The server did not accept this payment.',
  );
}

String? _friendly(String? code) => switch (code) {
      'LOAN_NOT_ACTIVE' => 'The loan was closed before this payment was sent.',
      'DUPLICATE_UPI_REF' => 'This UTR is already used for another payment. Check the number with the client.',
      'INVALID_UPI_REF' => 'The UTR is not 12 digits.',
      'CLIENT_TXN_REUSED' => 'A different payment was already recorded with this id.',
      'PAID_ON_IN_FUTURE' => "The phone's clock was ahead when this was collected. Fix the clock and retry.",
      'NOT_FOUND' => 'The loan is no longer available to you.',
      _ => null,
    };
