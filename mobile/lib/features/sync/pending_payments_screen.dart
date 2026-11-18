import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../theme/app_theme.dart';
import '../payments/payments_repository.dart';
import 'payment_queue.dart';
import 'pending_payment.dart';

/// Payments saved on this phone: waiting to be sent, or rejected by the server and needing a decision.
class PendingPaymentsScreen extends ConsumerWidget {
  const PendingPaymentsScreen({super.key});

  Future<void> _discard(BuildContext context, WidgetRef ref, PendingPayment p) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Discard this payment?'),
        content: Text('${p.amount.format()} from ${p.clientName} was never recorded on the server. '
            'Discard it only if the client did not actually pay, or you will record it again by hand.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Keep')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Discard'),
          ),
        ],
      ),
    );
    if (ok == true) await ref.read(paymentQueueProvider.notifier).discard(p);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final q = ref.watch(paymentQueueProvider);
    final queue = ref.read(paymentQueueProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: Text('Saved on phone', style: AppTheme.heading(20)),
        actions: [
          if (q.waiting.isNotEmpty)
            TextButton(onPressed: q.syncing ? null : queue.sync, child: Text(q.syncing ? 'Sending…' : 'Sync now')),
        ],
      ),
      body: q.isEmpty
          ? const Center(
              child: Text('Everything is sent.', style: TextStyle(color: AppColors.muted)),
            )
          : ListView(padding: const EdgeInsets.all(16), children: [
              if (q.failed.isNotEmpty) ...[
                const _Heading('Needs attention'),
                for (final p in q.failed)
                  _Row(
                    item: p,
                    trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                      TextButton(onPressed: () => queue.retry(p), child: const Text('Retry')),
                      TextButton(
                        onPressed: () => _discard(context, ref, p),
                        style: TextButton.styleFrom(foregroundColor: AppColors.error),
                        child: const Text('Discard'),
                      ),
                    ]),
                  ),
                const SizedBox(height: 16),
              ],
              if (q.waiting.isNotEmpty) ...[
                _Heading(q.offline ? 'Waiting for internet' : 'Waiting to send'),
                for (final p in q.waiting) _Row(item: p),
              ],
            ]),
    );
  }
}

class _Heading extends StatelessWidget {
  const _Heading(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(text, style: AppTheme.heading(16)),
      );
}

class _Row extends StatelessWidget {
  const _Row({required this.item, this.trailing});

  final PendingPayment item;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final p = item;
    final mode = p.mode == PaymentMode.upi ? 'UPI ${p.upiRefNo ?? ''}' : 'Cash';
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(
            child: Text('${p.clientName} · ${p.loanNo}', style: const TextStyle(fontWeight: FontWeight.w600)),
          ),
          Text(p.amount.format(), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        ]),
        const SizedBox(height: 2),
        Text('$mode · collected ${DateFormat('d MMM, h:mm a').format(p.paidOn)}',
            style: const TextStyle(fontSize: 12, color: AppColors.muted)),
        if (p.errorMessage != null) ...[
          const SizedBox(height: 6),
          Text(p.errorMessage!, style: const TextStyle(fontSize: 13, color: AppColors.error)),
        ],
        if (trailing != null) Align(alignment: Alignment.centerRight, child: trailing),
      ]),
    );
  }
}
