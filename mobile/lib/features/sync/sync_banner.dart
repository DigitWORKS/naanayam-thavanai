import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../theme/app_theme.dart';
import 'payment_queue.dart';
import 'pending_payments_screen.dart';

/// Shown on the Today list while payments are saved on the phone or need attention.
class SyncBanner extends ConsumerWidget {
  const SyncBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final q = ref.watch(paymentQueueProvider);
    if (q.isEmpty) return const SizedBox.shrink();

    void open() => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const PendingPaymentsScreen()));

    final waiting = q.waiting.length;
    final failed = q.failed.length;
    final attention = failed > 0;
    final title = [
      if (waiting > 0) '$waiting ${waiting == 1 ? 'payment' : 'payments'} saved on phone',
      if (attention) '$failed need${failed == 1 ? 's' : ''} attention',
    ].join(' · ');
    final subtitle = q.syncing
        ? 'Sending…'
        : attention
            ? 'Tap to review'
            : q.offline
                ? 'No internet. They will be sent automatically.'
                : 'Will be sent automatically.';

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: attention ? const Color(0xFFFDECEA) : AppColors.warningTint,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: open,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 10, 8, 10),
            child: Row(children: [
              Icon(attention ? Icons.error_outline : Icons.cloud_upload_outlined,
                  color: attention ? AppColors.error : AppColors.warning),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(title,
                      style: TextStyle(
                          fontWeight: FontWeight.w600, color: attention ? AppColors.error : AppColors.warning)),
                  Text(subtitle, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
                ]),
              ),
              if (q.syncing)
                const Padding(
                  padding: EdgeInsets.all(12),
                  child: SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2)),
                )
              else if (waiting > 0)
                TextButton(
                  onPressed: () => ref.read(paymentQueueProvider.notifier).sync(),
                  child: const Text('Sync now'),
                ),
            ]),
          ),
        ),
      ),
    );
  }
}
