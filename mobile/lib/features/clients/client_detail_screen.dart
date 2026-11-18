import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import '../collections/collections_repository.dart';
import '../collections/due_item.dart';
import '../loans/loan_detail_screen.dart';
import '../loans/loans_screen.dart' show LoanCard;
import '../loans/new_loan_screen.dart';
import '../payments/record_payment_screen.dart';
import 'client_models.dart';
import 'clients_repository.dart';
import 'clients_screen.dart' show InitialsAvatar;

/// Design board 10: client profile, active loans, details, collect payment.
class ClientDetailScreen extends ConsumerWidget {
  const ClientDetailScreen({super.key, required this.clientId, required this.user});

  final int clientId;
  final AppUser user;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(clientDetailProvider(clientId));
    return Scaffold(
      appBar: AppBar(title: const Text('Client', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600))),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Text(e is ApiError ? e.message : 'Could not load the client.', textAlign: TextAlign.center),
              const SizedBox(height: 16),
              OutlinedButton(
                  onPressed: () => ref.invalidate(clientDetailProvider(clientId)), child: const Text('Try again')),
            ]),
          ),
        ),
        data: (c) => RefreshIndicator(
          onRefresh: () => ref.refresh(clientDetailProvider(clientId).future),
          child: _Body(client: c, user: user),
        ),
      ),
      bottomNavigationBar: switch (detail) {
        AsyncData(:final value) when value.activeLoans.isNotEmpty => SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: FilledButton(onPressed: () => _collect(context, ref, value), child: const Text('Collect payment')),
            ),
          ),
        _ => null,
      },
    );
  }

  /// Finds the client's loans in today's due list (same data as the Today tab) and opens the
  /// payment form; asks which loan when the client has more than one.
  Future<void> _collect(BuildContext context, WidgetRef ref, ClientDetail c) async {
    List<DueItem> due;
    try {
      due = (await ref.read(dueListProvider(c.areaId).future)).where((d) => d.clientId == c.id).toList();
    } on ApiError catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      return;
    }
    if (!context.mounted) return;
    if (due.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Nothing is due on this client\'s loans today.')));
      return;
    }
    final item = due.length == 1
        ? due.first
        : await showModalBottomSheet<DueItem>(
            context: context,
            showDragHandle: true,
            builder: (ctx) => SafeArea(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                const Padding(
                  padding: EdgeInsets.only(bottom: 8),
                  child: Text('Which loan?', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                ),
                for (final d in due)
                  ListTile(
                    title: Text(d.loanNo),
                    subtitle: Text('Due now ${d.totalDue.format()}'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.pop(ctx, d),
                  ),
              ]),
            ),
          );
    if (item == null || !context.mounted) return;
    final recorded = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => RecordPaymentScreen(item: item)),
    );
    if (recorded == true) {
      ref.invalidate(clientDetailProvider(c.id));
      ref.invalidate(dueListProvider);
    }
  }
}

class _Body extends ConsumerWidget {
  const _Body({required this.client, required this.user});

  final ClientDetail client;
  final AppUser user;

  Future<void> _openLoan(BuildContext context, WidgetRef ref, int loanId) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => LoanDetailScreen(loanId: loanId, user: user),
    ));
    ref.invalidate(clientDetailProvider(client.id));
  }

  Future<void> _newLoan(BuildContext context, WidgetRef ref) async {
    final id = await Navigator.of(context).push<int>(MaterialPageRoute(
      builder: (_) => NewLoanScreen(user: user, clientId: client.id),
      fullscreenDialog: true,
    ));
    ref.invalidate(clientDetailProvider(client.id));
    if (id != null && context.mounted) await _openLoan(context, ref, id);
  }

  Future<void> _launch(BuildContext context, Uri uri) async {
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('No app found to open this.')));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final c = client;
    final since = c.clientSince == null ? '' : ' · client since ${DateFormat('MMM yyyy').format(c.clientSince!.toLocal())}';
    final docs = ref.watch(clientDocumentCountProvider(c.id));

    return ListView(physics: const AlwaysScrollableScrollPhysics(), padding: EdgeInsets.zero, children: [
      Container(
        color: AppColors.surface,
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            InitialsAvatar(name: c.name, size: 56),
            const SizedBox(width: 14),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(c.name, style: AppTheme.heading(22)),
                const SizedBox(height: 2),
                Text('${c.areaName}$since', style: const TextStyle(fontSize: 13, color: AppColors.muted)),
                if (!c.isActive)
                  const Padding(
                    padding: EdgeInsets.only(top: 4),
                    child: Text('Inactive', style: TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
                  ),
              ]),
            ),
          ]),
          const SizedBox(height: 14),
          Row(children: [
            Expanded(
              child: OutlinedButton.icon(
                style: _actionStyle,
                onPressed: () => _launch(context, Uri(scheme: 'tel', path: '+91${c.phone}')),
                icon: const Icon(Icons.call_outlined, size: 18),
                label: Text('Call ${formatPhone(c.phone)}', overflow: TextOverflow.ellipsis),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton.icon(
                style: _actionStyle,
                onPressed: () => _launch(context, Uri.parse('https://wa.me/91${c.phone}')),
                icon: const Icon(Icons.chat_outlined, size: 18),
                label: const Text('WhatsApp'),
              ),
            ),
          ]),
        ]),
      ),
      Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
            Expanded(child: Text('Active loans', style: AppTheme.heading(17))),
            if (c.activeLoans.isNotEmpty)
              Text('${c.totalOutstanding.format()} outstanding',
                  style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500)),
          ]),
          if (user.isOwner && c.isActive)
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: () => _newLoan(context, ref),
                icon: const Icon(Icons.add),
                label: const Text('New loan'),
              ),
            ),
          const SizedBox(height: 10),
          if (c.activeLoans.isEmpty)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
              child: const Text('No active loan.', style: TextStyle(color: AppColors.muted)),
            ),
          for (final l in c.activeLoans) ...[
            LoanCard(loan: l, onTap: () => _openLoan(context, ref, l.id)),
            const SizedBox(height: 8),
          ],
          const SizedBox(height: 12),
          Text('Details', style: AppTheme.heading(17)),
          const SizedBox(height: 10),
          Container(
            decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
            child: Column(children: [
              _detail('Address', c.address),
              _detail('Other number', c.altPhone == null ? null : formatPhone(c.altPhone!)),
              _detail('Aadhaar', c.aadhaarLast4 == null ? null : 'XXXX XXXX ${c.aadhaarLast4}'),
              _detail(
                'Guarantor',
                c.guarantorName == null
                    ? null
                    : [c.guarantorName!, if (c.guarantorPhone != null) formatPhone(c.guarantorPhone!)].join(' · '),
              ),
              _detail(
                'Documents',
                switch (docs) {
                  AsyncData(:final value) => value == 0 ? 'None' : '$value ${value == 1 ? 'file' : 'files'}',
                  AsyncError() => 'Could not load',
                  _ => '…',
                },
                last: true,
              ),
            ]),
          ),
        ]),
      ),
    ]);
  }

  static final _actionStyle = OutlinedButton.styleFrom(
    minimumSize: const Size.fromHeight(46),
    foregroundColor: AppColors.ink,
    side: const BorderSide(color: AppColors.border, width: 1.5),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
  );

  static Widget _detail(String label, String? value, {bool last = false}) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          border: last ? null : const Border(bottom: BorderSide(color: AppColors.divider)),
        ),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(width: 110, child: Text(label, style: const TextStyle(color: AppColors.muted))),
          Expanded(child: Text(value ?? '—', style: const TextStyle(fontWeight: FontWeight.w500))),
        ]),
      );
}
