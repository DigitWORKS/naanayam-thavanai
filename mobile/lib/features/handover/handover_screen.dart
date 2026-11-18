import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import 'handover_repository.dart';

/// Design board 11 (agent): hand today's cash to the owner. UPI is not handed over — it is already in
/// the owner's account. Once submitted, today's card shows the owner's decision instead of the form.
class HandoverScreen extends ConsumerStatefulWidget {
  const HandoverScreen({super.key, required this.user});

  final AppUser user;

  @override
  ConsumerState<HandoverScreen> createState() => _HandoverScreenState();
}

class _HandoverScreenState extends ConsumerState<HandoverScreen> {
  final _handed = TextEditingController();
  final _note = TextEditingController();

  /// Prefilled from the preview once; the agent's edits are kept across refreshes.
  bool _prefilled = false;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _handed.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _refresh() => Future.wait([
        ref.refresh(handoverPreviewProvider.future),
        ref.refresh(myRecentHandoversProvider.future),
      ]);

  Future<void> _submit(HandoverPreview p) async {
    final handed = Money.tryParse(_handed.text.replaceAll(',', ''));
    if (handed == null) {
      setState(() => _error = 'Enter the cash you are handing over.');
      return;
    }
    final diff = handed - p.cashCollected;
    if (!diff.isZero && _note.text.trim().isEmpty) {
      setState(() => _error = 'Add a note for the owner explaining the difference.');
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Hand over cash?'),
        content: Text('You are handing ${handed.format()} to the owner for '
            '${DateFormat('EEE, d MMM').format(p.date)}. You can do this once a day.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Hand over')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(handoverRepositoryProvider).submit(date: p.date, handed: handed, remarks: _note.text);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('${handed.format()} handed over. The owner will confirm.')));
      await _refresh();
    } on ApiError catch (e) {
      if (!mounted) return;
      if (e.code == 'HANDOVER_EXISTS') {
        await _refresh(); // shows the existing handover instead of the form
      } else {
        setState(() => _error = e.isNetwork
            ? "Couldn't reach the server. Pull down to refresh — if it went through, today's handover will show."
            : e.message);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final preview = ref.watch(handoverPreviewProvider);
    final firstName = widget.user.name.split(' ').first;

    if (!_prefilled) {
      if (preview case AsyncData(:final value)) {
        _handed.text = value.cashCollected.toInput();
        _prefilled = true;
      }
    }

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 72,
        titleSpacing: 16,
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('${DateFormat('EEE, d MMM').format(DateTime.now())} · $firstName',
              style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500)),
          Text("Hand over today's cash", style: AppTheme.heading(22)),
        ]),
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: preview.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(32),
            children: [
              const SizedBox(height: 60),
              Text(e is ApiError ? e.message : 'Could not load today\'s totals.', textAlign: TextAlign.center),
              const SizedBox(height: 16),
              Center(
                child: OutlinedButton(
                    onPressed: () => ref.invalidate(handoverPreviewProvider), child: const Text('Try again')),
              ),
            ],
          ),
          data: (p) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              _CollectedCard(preview: p),
              const SizedBox(height: 12),
              if (p.existing != null)
                _TodayStatus(handover: p.existing!)
              else if (p.cashCollected.isPositive || p.cashPayments > 0)
                _form(p)
              else
                const _Info('No cash collected today, so there is nothing to hand over.'),
              const SizedBox(height: 20),
              _EarlierDays(today: p.date),
            ],
          ),
        ),
      ),
      bottomNavigationBar: switch (preview) {
        AsyncData(:final value) when value.existing == null && (value.cashCollected.isPositive || value.cashPayments > 0) =>
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: FilledButton(
                onPressed: _busy ? null : () => _submit(value),
                child: _busy
                    ? const SizedBox.square(
                        dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                    : Text('Hand over ${(Money.tryParse(_handed.text) ?? Money.zero).format()}'),
              ),
            ),
          ),
        _ => null,
      },
    );
  }

  Widget _form(HandoverPreview p) {
    final handed = Money.tryParse(_handed.text.replaceAll(',', ''));
    final diff = handed == null ? null : handed - p.cashCollected;
    final (text, color, icon) = switch (diff) {
      null => ('Enter the amount', AppColors.muted, Icons.edit_outlined),
      Money(isZero: true) => ('Matches what you collected', AppColors.brand, Icons.check_circle_outline),
      Money(:final paise) when paise < 0 => ('${Money(-paise).format()} short', AppColors.warning, Icons.error_outline),
      final Money extra => ('${extra.format()} extra', AppColors.warning, Icons.error_outline),
    };

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const Text('Cash you are handing over', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        TextField(
          controller: _handed,
          enabled: !_busy,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d{0,10}\.?\d{0,2}'))],
          style: GoogleFonts.spaceGrotesk(fontSize: 28, fontWeight: FontWeight.w700),
          decoration: const InputDecoration(prefixText: '₹ '),
          onChanged: (_) => setState(() => _error = null),
        ),
        const SizedBox(height: 8),
        Row(children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(width: 6),
          Text(text, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: color)),
        ]),
        const SizedBox(height: 16),
        Text(diff == null || diff.isZero ? 'Note for the owner' : 'Note for the owner (needed)',
            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        TextField(
          controller: _note,
          enabled: !_busy,
          maxLength: 255,
          decoration: const InputDecoration(
            hintText: 'Optional, e.g. ₹200 short, client paid less',
            counterText: '',
          ),
          onChanged: (_) => setState(() => _error = null),
        ),
        if (_error != null) ...[
          const SizedBox(height: 10),
          Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
        ],
      ]),
    );
  }
}

class _CollectedCard extends StatelessWidget {
  const _CollectedCard({required this.preview});

  final HandoverPreview preview;

  @override
  Widget build(BuildContext context) {
    final p = preview;
    const soft = Color(0xFFB8C7C1);
    final last = p.lastCashAt == null ? '' : ' · last at ${DateFormat('h:mm a').format(p.lastCashAt!)}';
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AppColors.ink, borderRadius: BorderRadius.circular(16)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Cash you collected', style: TextStyle(fontSize: 13, color: soft, fontWeight: FontWeight.w500)),
        const SizedBox(height: 4),
        Text(p.cashCollected.format(),
            style: GoogleFonts.spaceGrotesk(fontSize: 34, fontWeight: FontWeight.w700, color: AppColors.gold)),
        const SizedBox(height: 4),
        Text('${p.cashPayments} cash ${p.cashPayments == 1 ? 'payment' : 'payments'}$last',
            style: const TextStyle(fontSize: 13, color: soft)),
        if (p.upiCollected.isPositive) ...[
          const Divider(height: 24, color: Color(0xFF2A3D35)),
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.info_outline, size: 16, color: soft),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                "UPI ${p.upiCollected.format()} went straight to the owner's account and is not handed over",
                style: const TextStyle(fontSize: 13, color: soft, height: 1.4),
              ),
            ),
          ]),
        ],
      ]),
    );
  }
}

class _TodayStatus extends StatelessWidget {
  const _TodayStatus({required this.handover});

  final Handover handover;

  @override
  Widget build(BuildContext context) {
    final h = handover;
    final (label, color, bg) = _statusStyle(h);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Expanded(child: Text('Handed over today', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600))),
          _Pill(label, color, bg),
        ]),
        const SizedBox(height: 8),
        Text(h.handedAmount.format(), style: GoogleFonts.spaceGrotesk(fontSize: 26, fontWeight: FontWeight.w700)),
        if (!h.matches)
          Text('Collected ${h.expectedAmount.format()}', style: const TextStyle(color: AppColors.muted)),
        if (h.remarks != null && h.remarks!.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text('Note: ${h.remarks}', style: const TextStyle(fontSize: 13, color: AppColors.muted)),
        ],
        if (h.status == HandoverStatus.pending) ...[
          const SizedBox(height: 8),
          const Text('Waiting for the owner to confirm.', style: TextStyle(fontSize: 13, color: AppColors.muted)),
        ],
      ]),
    );
  }
}

class _EarlierDays extends ConsumerWidget {
  const _EarlierDays({required this.today});

  final DateTime today;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final list = ref.watch(myRecentHandoversProvider);
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text('Earlier days', style: AppTheme.heading(17)),
      const SizedBox(height: 10),
      list.when(
        loading: () => const LinearProgressIndicator(),
        error: (e, _) => Text(e is ApiError ? e.message : 'Could not load earlier handovers.'),
        data: (rows) {
          final earlier = rows.where((h) => !DateUtils.isSameDay(h.handoverDate, today)).toList();
          if (earlier.isEmpty) {
            return const Text('No earlier handovers in the last two weeks.', style: TextStyle(color: AppColors.muted));
          }
          return Container(
            decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
            child: Column(children: [
              for (final h in earlier)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: AppColors.divider))),
                  child: Row(children: [
                    Expanded(
                      child: Text(
                        '${DateFormat('EEE, d MMM').format(h.handoverDate)} · '
                        '${h.matches ? h.handedAmount.format() : '${h.handedAmount.format()} of ${h.expectedAmount.format()}'}',
                        style: const TextStyle(fontSize: 14),
                      ),
                    ),
                    Builder(builder: (_) {
                      final (label, color, bg) = _statusStyle(h);
                      return _Pill(label, color, bg);
                    }),
                  ]),
                ),
            ]),
          );
        },
      ),
    ]);
  }
}

(String, Color, Color) _statusStyle(Handover h) => switch (h.status) {
      HandoverStatus.confirmed => ('Confirmed', AppColors.brand, AppColors.brandTint),
      HandoverStatus.disputed => (
          h.matches ? 'Disputed' : 'Disputed ${Money(h.difference.paise.abs()).format()}',
          AppColors.error,
          const Color(0xFFFDECEA)
        ),
      HandoverStatus.pending => ('Waiting', AppColors.warning, AppColors.warningTint),
    };

class _Pill extends StatelessWidget {
  const _Pill(this.text, this.color, this.bg);

  final String text;
  final Color color;
  final Color bg;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(999)),
        child: Text(text, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: color)),
      );
}

class _Info extends StatelessWidget {
  const _Info(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
        child: Text(text, style: const TextStyle(color: AppColors.muted)),
      );
}
