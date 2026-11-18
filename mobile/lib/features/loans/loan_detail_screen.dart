import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import '../clients/clients_repository.dart';
import '../collections/collections_repository.dart';
import '../payments/record_payment_screen.dart';
import 'loan_models.dart';
import 'loans_repository.dart';

/// Design board 4: balances, schedule and payments for one loan.
class LoanDetailScreen extends ConsumerStatefulWidget {
  const LoanDetailScreen({super.key, required this.loanId, required this.user});

  final int loanId;
  final AppUser user;

  @override
  ConsumerState<LoanDetailScreen> createState() => _LoanDetailScreenState();
}

class _LoanDetailScreenState extends ConsumerState<LoanDetailScreen> {
  bool _showPayments = false;
  bool _showAllPaid = false;

  int get id => widget.loanId;

  Future<void> _refresh() => Future.wait([
        ref.refresh(loanDetailProvider(id).future),
        ref.refresh(loanScheduleProvider(id).future),
        ref.refresh(loanLedgerProvider(id).future),
      ]);

  Future<void> _collect(LoanDetail loan) async {
    try {
      final due = (await ref.read(dueListProvider(null).future)).where((d) => d.loanId == loan.id).toList();
      if (!mounted) return;
      if (due.isEmpty) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Nothing is due on this loan today.')));
        return;
      }
      final recorded = await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (_) => RecordPaymentScreen(item: due.first)),
      );
      if (recorded == true) {
        ref.invalidate(dueListProvider);
        ref.invalidate(clientDetailProvider(loan.clientId));
        await _refresh();
      }
    } on ApiError catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final loan = ref.watch(loanDetailProvider(id));
    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 64,
        title: switch (loan) {
          AsyncData(:final value) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(value.loanNo, style: AppTheme.heading(20)),
              Text(value.clientName, style: const TextStyle(fontSize: 13, color: AppColors.muted)),
            ]),
          _ => Text('Loan', style: AppTheme.heading(20)),
        },
        actions: [
          if (loan case AsyncData(:final value))
            Padding(
              padding: const EdgeInsets.only(right: 16),
              child: _StatusBadge(value.status),
            ),
        ],
      ),
      body: loan.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text(e is ApiError ? e.message : 'Could not load the loan.'),
            const SizedBox(height: 12),
            OutlinedButton(onPressed: () => ref.invalidate(loanDetailProvider(id)), child: const Text('Try again')),
          ]),
        ),
        data: (l) => RefreshIndicator(
          onRefresh: _refresh,
          child: ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(16), children: [
            _SummaryCard(loan: l, schedule: ref.watch(loanScheduleProvider(id)).asList),
            const SizedBox(height: 16),
            SegmentedButton<bool>(
              segments: const [
                ButtonSegment(value: false, label: Text('Schedule')),
                ButtonSegment(value: true, label: Text('Payments')),
              ],
              selected: {_showPayments},
              showSelectedIcon: false,
              onSelectionChanged: (s) => setState(() => _showPayments = s.first),
            ),
            const SizedBox(height: 12),
            if (_showPayments) _payments() else _schedule(),
          ]),
        ),
      ),
      bottomNavigationBar: switch (loan) {
        AsyncData(:final value) when value.isActive => SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: Row(children: [
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(52),
                      foregroundColor: AppColors.ink,
                      side: const BorderSide(color: AppColors.ink, width: 1.5),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Pre-close quote comes in the next update.'))),
                    child: const Text('Pre-close quote'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                    onPressed: () => _collect(value),
                    child: const Text('Collect'),
                  ),
                ),
              ]),
            ),
          ),
        _ => null,
      },
    );
  }

  Widget _schedule() {
    final rows = ref.watch(loanScheduleProvider(id));
    return rows.when(
      loading: () => const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
      error: (e, _) => Text(e is ApiError ? e.message : 'Could not load the schedule.'),
      data: (list) {
        // Hide older paid rows by default: keep the last two paid ones for context.
        final firstOpen = list.indexWhere((i) => i.status != InstallmentStatus.paid && i.status != InstallmentStatus.settled);
        final cut = firstOpen < 0 ? list.length : firstOpen;
        final hidden = _showAllPaid ? 0 : (cut - 2).clamp(0, list.length);
        final today = DateUtils.dateOnly(DateTime.now());
        return Container(
          decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
          child: Column(children: [
            if (hidden > 0)
              TextButton(
                onPressed: () => setState(() => _showAllPaid = true),
                child: Text('Show $hidden earlier paid'),
              ),
            for (final i in list.skip(hidden)) _InstallmentRow(i, today: today),
          ]),
        );
      },
    );
  }

  Widget _payments() {
    final ledger = ref.watch(loanLedgerProvider(id));
    return ledger.when(
      loading: () => const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
      error: (e, _) => Text(e is ApiError ? e.message : 'Could not load payments.'),
      data: (entries) {
        if (entries.isEmpty) {
          return const Padding(
            padding: EdgeInsets.all(24),
            child: Text('No payments yet.', textAlign: TextAlign.center, style: TextStyle(color: AppColors.muted)),
          );
        }
        final newestFirst = [...entries]..sort((a, b) => b.paidOn.compareTo(a.paidOn));
        return Container(
          decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
          child: Column(children: [for (final e in newestFirst) _LedgerRow(e)]),
        );
      },
    );
  }
}

extension on AsyncValue<List<Installment>> {
  List<Installment>? get asList => switch (this) {
        AsyncData(:final value) => value,
        _ => null,
      };
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge(this.status);

  final LoanStatus status;

  @override
  Widget build(BuildContext context) {
    final (fg, bg) = switch (status) {
      LoanStatus.active => (AppColors.brand, AppColors.brandTint),
      LoanStatus.defaulted => (AppColors.error, const Color(0xFFFDECEA)),
      _ => (AppColors.muted, AppColors.background),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(999)),
      child: Text(status.label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: fg)),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.loan, required this.schedule});

  final LoanDetail loan;
  final List<Installment>? schedule;

  @override
  Widget build(BuildContext context) {
    final l = loan;
    final rows = schedule;
    final paidCount = rows?.where((i) => i.status == InstallmentStatus.paid || i.status == InstallmentStatus.settled).length;
    final extensions = rows?.where((i) => i.isExtension).length ?? 0;
    final payable = l.totalPayable;
    final progress = payable.isPositive ? (l.totalPaid.paise / payable.paise).clamp(0.0, 1.0) : 0.0;
    final rate = '${Money.tryParse(l.interestRate)?.toInput() ?? l.interestRate}% p.a. '
        '${l.interestMethod == InterestMethod.flat ? 'flat' : 'reducing'}';
    final ends = DateFormat('d MMM yyyy').format(l.currentEndDate) + (extensions > 0 ? ' (+$extensions)' : '');

    Widget cell(String label, String value) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
          const SizedBox(height: 2),
          Text(value, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
        ]);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(16)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Outstanding', style: TextStyle(fontSize: 13, color: AppColors.muted)),
              Text(l.outstanding.format(), style: GoogleFonts.spaceGrotesk(fontSize: 30, fontWeight: FontWeight.w700)),
            ]),
          ),
          if (paidCount != null && rows != null)
            Text('$paidCount of ${rows.length} paid',
                style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500)),
        ]),
        const SizedBox(height: 10),
        ClipRRect(
          borderRadius: BorderRadius.circular(999),
          child: LinearProgressIndicator(
            value: progress,
            minHeight: 8,
            backgroundColor: const Color(0xFFE3EAE7),
            color: AppColors.brand,
          ),
        ),
        if (l.overdueAmount.isPositive || l.pendingPenalty.isPositive) ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: AppColors.warningTint, borderRadius: BorderRadius.circular(10)),
            child: Text(
              [
                if (l.overdueAmount.isPositive) '${l.overdueAmount.format()} behind schedule',
                if (l.pendingPenalty.isPositive) 'penalty ${l.pendingPenalty.format()}',
              ].join(' · '),
              style: const TextStyle(color: AppColors.warning, fontWeight: FontWeight.w600),
            ),
          ),
        ],
        const SizedBox(height: 14),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          childAspectRatio: 3.2,
          mainAxisSpacing: 8,
          crossAxisSpacing: 12,
          children: [
            cell('Principal', l.principal.format()),
            cell('Interest', rate),
            cell('Total payable', l.totalPayable.format()),
            cell('Paid', l.totalPaid.format()),
            cell(l.frequency.label, l.installmentAmount.format()),
            cell('Ends', ends),
          ],
        ),
        if (l.interestCollection == InterestCollection.upfrontDeducted)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text('Interest ${l.interestAmount.format()} deducted upfront · given ${l.disbursedAmount.format()}',
                style: const TextStyle(fontSize: 12, color: AppColors.muted)),
          ),
        if (l.legalNote != null && l.legalNote!.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text('Legal note: ${l.legalNote}', style: const TextStyle(fontSize: 13, color: AppColors.error)),
          ),
      ]),
    );
  }
}

class _InstallmentRow extends StatelessWidget {
  const _InstallmentRow(this.i, {required this.today});

  final Installment i;
  final DateTime today;

  @override
  Widget build(BuildContext context) {
    final isToday = DateUtils.isSameDay(i.dueDate, today);
    final past = i.dueDate.isBefore(today);
    final (label, color) = switch (i.status) {
      InstallmentStatus.paid => ('Paid', AppColors.brand),
      InstallmentStatus.settled => ('Settled', AppColors.muted),
      InstallmentStatus.missed => ('Missed', AppColors.warning),
      InstallmentStatus.partial => ('Part ${i.paidAmount.format()}', AppColors.warning),
      InstallmentStatus.pending when isToday => ('Due', AppColors.ink),
      InstallmentStatus.pending when past => ('Overdue', AppColors.warning),
      InstallmentStatus.pending when i.isExtension => ('Extension', AppColors.muted),
      InstallmentStatus.pending => ('Pending', AppColors.muted),
    };
    final date = DateFormat('d MMM').format(i.dueDate);

    return Container(
      color: isToday ? AppColors.brandTint : null,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
      child: Row(children: [
        SizedBox(width: 44, child: Text('#${i.seqNo}', style: const TextStyle(fontSize: 13, color: AppColors.muted))),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(isToday ? 'Today · $date' : date,
                style: TextStyle(fontSize: 14, fontWeight: isToday ? FontWeight.w700 : FontWeight.w500)),
            if (i.penaltyAmount.isPositive)
              Text('Penalty ${i.penaltyAmount.format()}${i.penaltyPaid.isPositive ? ' · paid ${i.penaltyPaid.format()}' : ''}',
                  style: const TextStyle(fontSize: 12, color: AppColors.warning)),
          ]),
        ),
        Text(i.dueAmount.format(), style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
        const SizedBox(width: 12),
        SizedBox(
          width: 84,
          child: Text(label,
              textAlign: TextAlign.right,
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: color)),
        ),
      ]),
    );
  }
}

class _LedgerRow extends StatelessWidget {
  const _LedgerRow(this.e);

  final LedgerEntry e;

  @override
  Widget build(BuildContext context) {
    final mode = e.isUpi ? 'UPI${e.upiRefNo == null ? '' : ' · ${e.upiRefNo}'}' : 'Cash';
    final what = e.isReversal ? 'Reversed' : (e.isPreclosure ? 'Pre-closure' : mode);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: AppColors.divider))),
      child: Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(DateFormat('d MMM yyyy, h:mm a').format(e.paidOn),
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
            Text([what, if (e.collectedByName.isNotEmpty) e.collectedByName].join(' · '),
                style: TextStyle(fontSize: 12, color: e.isReversal ? AppColors.error : AppColors.muted)),
          ]),
        ),
        Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Text(
            e.amount.format(),
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: e.isReversal ? AppColors.error : AppColors.ink,
              decoration: e.isReversal ? TextDecoration.lineThrough : null,
            ),
          ),
          Text('Balance ${e.runningBalance.format()}', style: const TextStyle(fontSize: 12, color: AppColors.muted)),
        ]),
      ]),
    );
  }
}
