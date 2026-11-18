import 'package:flutter_test/flutter_test.dart';
import 'package:naanayamthavanai/features/sync/sync_outcome.dart';

void main() {
  Map<String, dynamic> result(String id, String status, [String? code]) => {
        'clientTxnId': id,
        'status': status,
        if (code != null) 'error': {'code': code, 'message': 'server says $code'},
      };

  test('created and duplicate are removed from the phone', () {
    final d = decideSyncResults(['a', 'b'], [result('a', 'CREATED'), result('b', 'DUPLICATE')]);
    expect(d['a']!.action, SyncAction.remove);
    expect(d['b']!.action, SyncAction.remove);
  });

  test('concurrent-write duplicate failure is retried, not shown as an error', () {
    final d = decideSyncResults(['a'], [result('a', 'FAILED', 'DUPLICATE')]);
    expect(d['a']!.action, SyncAction.retry);
  });

  test('business-rule failures need attention with a readable message', () {
    final d = decideSyncResults(['a', 'b'], [
      result('a', 'FAILED', 'LOAN_NOT_ACTIVE'),
      result('b', 'FAILED', 'SOMETHING_NEW'),
    ]);
    expect(d['a']!.action, SyncAction.fail);
    expect(d['a']!.message, contains('closed'));
    expect(d['b']!.action, SyncAction.fail);
    expect(d['b']!.message, 'server says SOMETHING_NEW');
  });

  test('items the server did not answer for are retried', () {
    final d = decideSyncResults(['a', 'b'], [result('a', 'CREATED')]);
    expect(d['b']!.action, SyncAction.retry);
  });
}
