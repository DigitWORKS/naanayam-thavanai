import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import '../clients/clients_repository.dart';
import '../collections/collections_repository.dart';
import '../handover/handover_repository.dart';
import '../payments/payments_repository.dart';
import 'payment_queue_store.dart';
import 'pending_payment.dart';
import 'sync_outcome.dart';

class QueueState {
  const QueueState({this.items = const [], this.syncing = false, this.offline = false, this.lastSyncedAt});

  /// The signed-in user's queued payments, oldest first.
  final List<PendingPayment> items;
  final bool syncing;

  /// The last attempt could not reach the server.
  final bool offline;
  final DateTime? lastSyncedAt;

  List<PendingPayment> get waiting => items.where((i) => !i.needsAttention).toList();
  List<PendingPayment> get failed => items.where((i) => i.needsAttention).toList();
  bool get isEmpty => items.isEmpty;

  /// Amount waiting on the phone for one loan (shown on the Today list).
  Money waitingFor(int loanId) => items.where((i) => i.loanId == loanId).map((i) => i.amount).sum();

  QueueState copyWith({List<PendingPayment>? items, bool? syncing, bool? offline, DateTime? lastSyncedAt}) => QueueState(
        items: items ?? this.items,
        syncing: syncing ?? this.syncing,
        offline: offline ?? this.offline,
        lastSyncedAt: lastSyncedAt ?? this.lastSyncedAt,
      );
}

final paymentQueueStoreProvider = Provider<PaymentQueueStore>((ref) => PaymentQueueStore());

/// Offline payment queue for the signed-in user. Sends on start, on resume, every minute while
/// anything is waiting, after a payment is queued, and on demand (pull to refresh / Sync now).
final paymentQueueProvider = NotifierProvider<PaymentQueue, QueueState>(PaymentQueue.new);

class PaymentQueue extends Notifier<QueueState> {
  static const _interval = Duration(minutes: 1);
  static const _maxBatch = 500;

  int? _userId;
  Timer? _timer;
  AppLifecycleListener? _lifecycle;
  Future<void>? _running;

  PaymentQueueStore get _store => ref.read(paymentQueueStoreProvider);

  @override
  QueueState build() {
    final auth = ref.watch(authControllerProvider);
    _userId = auth is SignedIn ? auth.user.id : null;
    ref.onDispose(() {
      _timer?.cancel();
      _lifecycle?.dispose();
    });
    if (_userId != null) {
      _lifecycle = AppLifecycleListener(onResume: () => unawaited(sync()));
      _timer = Timer.periodic(_interval, (_) {
        if (state.waiting.isNotEmpty) unawaited(sync());
      });
      Future.microtask(() async {
        await _reload();
        await sync();
      });
    }
    return const QueueState();
  }

  Future<void> _reload() async {
    final uid = _userId;
    if (uid == null) return;
    final items = await _store.forUser(uid);
    if (uid == _userId) state = state.copyWith(items: items);
  }

  /// Saves a payment that could not be sent, then tries to send it right away.
  Future<void> enqueue(PaymentDraft draft, {required String loanNo, required String clientName}) async {
    final uid = _userId;
    if (uid == null) throw const ApiError(code: 'SIGNED_OUT', message: 'Log in again to save this payment.');
    await _store.insert(PendingPayment.fromDraft(draft, userId: uid, loanNo: loanNo, clientName: clientName));
    await _reload();
    unawaited(sync());
  }

  /// Sends everything waiting. Concurrent calls share one round.
  Future<void> sync() => _running ??= _syncRound().whenComplete(() => _running = null);

  Future<void> _syncRound() async {
    final uid = _userId;
    final batch = state.waiting.take(_maxBatch).toList();
    if (uid == null || batch.isEmpty) return;
    state = state.copyWith(syncing: true);
    try {
      final decisions = await _send(batch);
      final remove = <String>[];
      final update = <PendingPayment>[];
      for (final item in batch) {
        final d = decisions[item.clientTxnId]!;
        switch (d.action) {
          case SyncAction.remove:
            remove.add(item.clientTxnId);
          case SyncAction.retry:
            update.add(item.copyWith(attempts: item.attempts + 1));
          case SyncAction.fail:
            update.add(item.copyWith(
                state: PendingState.failed, errorCode: d.code, errorMessage: d.message, attempts: item.attempts + 1));
        }
      }
      await _store.apply(remove: remove, update: update);
      state = state.copyWith(offline: false, lastSyncedAt: DateTime.now());
      if (remove.isNotEmpty) _refreshServerViews();
    } on ApiError catch (e) {
      // Network or session problems: keep everything and try again later.
      state = state.copyWith(offline: e.isNetwork);
    } finally {
      await _reload();
      state = state.copyWith(syncing: false);
    }
  }

  /// One batch call; if the server rejects the batch as a whole (400: one malformed item), sends the
  /// items one by one so a single bad payment cannot hold up the rest.
  Future<Map<String, SyncDecision>> _send(List<PendingPayment> batch) async {
    try {
      return await _post(batch);
    } on ApiError catch (e) {
      if (e.status != 400) rethrow;
      if (batch.length == 1) {
        return {batch.first.clientTxnId: SyncDecision(SyncAction.fail, code: e.code, message: e.message)};
      }
      final out = <String, SyncDecision>{};
      for (final item in batch) {
        out.addAll(await _send([item]));
      }
      return out;
    }
  }

  Future<Map<String, SyncDecision>> _post(List<PendingPayment> batch) => ApiError.guard(() async {
        final res = await ref.read(apiClientProvider).post<List<dynamic>>(
          '/payments/sync',
          data: {'payments': [for (final i in batch) i.toDraft().toJson()]},
        );
        return decideSyncResults(batch.map((i) => i.clientTxnId), res.data!);
      });

  /// Payments now on the server change the due list, handover totals and client balances.
  void _refreshServerViews() {
    ref.invalidate(dueListProvider);
    ref.invalidate(handoverPreviewProvider);
    ref.invalidate(clientDetailProvider);
  }

  /// Puts a failed payment back in the queue (e.g. after fixing the phone clock) and sends it.
  Future<void> retry(PendingPayment item) async {
    await _store.update(item.copyWith(state: PendingState.pending));
    await _reload();
    await sync();
  }

  /// Removes a payment from the phone for good (it was never recorded on the server).
  Future<void> discard(PendingPayment item) async {
    await _store.delete(item.clientTxnId);
    await _reload();
  }
}
