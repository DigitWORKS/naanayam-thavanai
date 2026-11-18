import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import '../payments/payments_repository.dart' show isValidUtr, normalizeUtr;
import 'loan_models.dart';
import 'loans_repository.dart';

/// Design board 12: today's settlement quote; the owner receives the amount and closes the loan.
/// Agents can see the quote but cannot close. Pops `true` once the loan is closed.
class PreclosureScreen extends ConsumerStatefulWidget {
  const PreclosureScreen({super.key, required this.loan, required this.user});

  final LoanDetail loan;
  final AppUser user;

  @override
  ConsumerState<PreclosureScreen> createState() => _PreclosureScreenState();
}

class _PreclosureScreenState extends ConsumerState<PreclosureScreen> {
  final _utr = TextEditingController();

  /// One id for this closing; reused on retry so the server records it at most once.
  final String _clientTxnId = const Uuid().v4();
  bool _upi = false;
  bool _busy = false;
  String? _error;
  bool _outcomeUnknown = false;
  DateTime? _paidOn;

  int get loanId => widget.loan.id;

  @override
  void dispose() {
    _utr.dispose();
    super.dispose();
  }

  Future<void> _close(PreclosureQuote q) async {
    if (_upi && !isValidUtr(normalizeUtr(_utr.text))) {
      setState(() => _error = 'UTR must be 12 digits.');
      return;
    }
    if (!_outcomeUnknown) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Close this loan?'),
          content: Text('Receive ${q.settlementAmount.format()} ${_upi ? 'by UPI' : 'in cash'} from '
              '${widget.loan.clientName} and close ${widget.loan.loanNo}. This cannot be undone.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Close loan')),
          ],
        ),
      );
      if (ok != true) return;
      _paidOn = DateTime.now();
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(loansRepositoryProvider).preclose(
            loanId,
            clientTxnId: _clientTxnId,
            settlementAmount: q.settlementAmount,
            upi: _upi,
            upiRefNo: _upi ? normalizeUtr(_utr.text) : null,
            paidOn: _paidOn!,
          );
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.check_circle, color: AppColors.brand, size: 40),
          title: Text('${widget.loan.loanNo} closed'),
          content: Text('${q.settlementAmount.format()} received from ${widget.loan.clientName}.',
              textAlign: TextAlign.center),
          actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Done'))],
        ),
      );
      if (mounted) Navigator.of(context).pop(true);
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        _outcomeUnknown = e.isNetwork;
        _error = switch (e.code) {
          _ when e.isNetwork =>
            "Couldn't confirm. Check the connection and tap Try again — the loan will not be closed twice.",
          'QUOTE_OUTDATED' => 'The amount changed to ₹${e.details['settlementAmount'] ?? ''} because of a recent '
              'payment. Check the new quote below.',
          'DUPLICATE_UPI_REF' => 'This UTR is already used for another payment.',
          _ => e.message,
        };
      });
      if (e.code == 'QUOTE_OUTDATED') ref.invalidate(preclosureQuoteProvider(loanId));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final quote = ref.watch(preclosureQuoteProvider(loanId));
    final rules = ref.watch(loanRulesProvider);
    final chargeLabel = switch (rules) {
      AsyncData(:final value) => value.preclosureChargeLabel,
      _ => null,
    };
    final locked = _busy || _outcomeUnknown;
    final owner = widget.user.isOwner;

    return PopScope(
      canPop: !_busy,
      child: Scaffold(
        appBar: AppBar(
          toolbarHeight: 64,
          title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Pre-close loan', style: AppTheme.heading(20)),
            Text('${widget.loan.loanNo} · ${widget.loan.clientName}',
                style: const TextStyle(fontSize: 13, color: AppColors.muted)),
          ]),
        ),
        body: quote.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Text(e is ApiError ? e.message : 'Could not get the quote.', textAlign: TextAlign.center),
                const SizedBox(height: 16),
                OutlinedButton(
                    onPressed: () => ref.invalidate(preclosureQuoteProvider(loanId)), child: const Text('Try again')),
              ]),
            ),
          ),
          data: (q) => ListView(padding: const EdgeInsets.all(16), children: [
            _QuoteCard(quote: q, chargeLabel: chargeLabel),
            const SizedBox(height: 16),
            if (owner) ...[
              const Text('Paid by', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              SegmentedButton<bool>(
                segments: const [
                  ButtonSegment(value: false, label: Text('Cash'), icon: Icon(Icons.payments_outlined)),
                  ButtonSegment(value: true, label: Text('UPI'), icon: Icon(Icons.qr_code_2)),
                ],
                selected: {_upi},
                onSelectionChanged: locked ? null : (s) => setState(() => _upi = s.first),
              ),
              if (_upi) ...[
                const SizedBox(height: 16),
                const Text('UPI transaction ID (UTR)', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                TextField(
                  controller: _utr,
                  enabled: !locked,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(12)],
                  style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w600, letterSpacing: 1.5),
                  decoration: const InputDecoration(hintText: '12 digits'),
                ),
              ],
              const SizedBox(height: 16),
            ],
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: AppColors.warningTint, borderRadius: BorderRadius.circular(12)),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Icon(Icons.info_outline, color: AppColors.warning, size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    owner
                        ? 'This closes the loan for good and cannot be undone. If another payment is recorded first, '
                            'the amount changes and you will see a new quote.'
                        : 'Only the owner can close a loan early. Share this amount with the client.',
                    style: const TextStyle(fontSize: 13, color: AppColors.warning, height: 1.4),
                  ),
                ),
              ]),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
            ],
          ]),
        ),
        bottomNavigationBar: switch (quote) {
          AsyncData(:final value) when owner => SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: FilledButton(
                  onPressed: _busy ? null : () => _close(value),
                  child: _busy
                      ? const SizedBox.square(
                          dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                      : Text(_outcomeUnknown ? 'Try again' : 'Receive ${value.settlementAmount.format()} and close'),
                ),
              ),
            ),
          _ => null,
        },
      ),
    );
  }
}

class _QuoteCard extends StatelessWidget {
  const _QuoteCard({required this.quote, required this.chargeLabel});

  final PreclosureQuote quote;
  final String? chargeLabel;

  @override
  Widget build(BuildContext context) {
    final q = quote;
    Widget line(String label, String value, {Color color = AppColors.ink}) => Padding(
          padding: const EdgeInsets.only(top: 10),
          child: Row(children: [
            Expanded(child: Text(label, style: const TextStyle(color: AppColors.muted))),
            Text(value, style: TextStyle(fontWeight: FontWeight.w600, color: color)),
          ]),
        );

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(16)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          const Expanded(child: Text('To close today', style: TextStyle(fontSize: 13, color: AppColors.muted))),
          Text('Quote for ${DateFormat('d MMM').format(q.asOf)}',
              style: const TextStyle(fontSize: 12, color: AppColors.muted)),
        ]),
        const SizedBox(height: 4),
        Text(q.settlementAmount.format(), style: GoogleFonts.spaceGrotesk(fontSize: 34, fontWeight: FontWeight.w700)),
        const Divider(height: 24, color: AppColors.divider),
        line('Principal left', q.outstandingPrincipal.format()),
        if (q.outstandingInterest.isPositive) line('Interest left', q.outstandingInterest.format()),
        if (q.pendingPenalty.isPositive) line('Unpaid penalty', q.pendingPenalty.format()),
        if (q.interestRebate.isPositive)
          line('Interest not charged (future days)', '− ${q.interestRebate.format()}', color: AppColors.brand),
        if (q.preclosureCharge.isPositive)
          line('Pre-closure charge${chargeLabel == null ? '' : ' $chargeLabel'}', '+ ${q.preclosureCharge.format()}'),
      ]),
    );
  }
}
