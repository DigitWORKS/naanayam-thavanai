import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../../widgets/form_widgets.dart';
import '../dashboard/dashboard_repository.dart';
import '../loans/loan_models.dart' show ChargeType, InterestMethod;
import 'settings_models.dart';
import 'settings_repository.dart';

/// Design board 13: owner's business rules. Changes apply to new loans and to days from now on;
/// existing schedules are not recalculated.
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final _form = GlobalKey<FormState>();
  final _cap = TextEditingController();
  final _penaltyValue = TextEditingController();
  final _grace = TextEditingController();
  final _penaltyMax = TextEditingController();
  final _chargeValue = TextEditingController();

  /// Editable copy; null until the first load.
  BusinessSettings? _s;
  bool _penaltyWasOn = false;
  bool _busy = false;
  bool _dirty = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_cap, _penaltyValue, _grace, _penaltyMax, _chargeValue]) {
      c.dispose();
    }
    super.dispose();
  }

  void _adopt(BusinessSettings s) {
    _s = s;
    _penaltyWasOn = s.penaltyEnabled;
    _cap.text = s.maxInterestRate.toInput();
    _penaltyValue.text = s.penaltyValue.isZero ? '' : s.penaltyValue.toInput();
    _grace.text = '${s.penaltyGraceDays}';
    _penaltyMax.text =
        s.penaltyMaxCap == null ? '' : s.penaltyMaxCap!.toInput();
    _chargeValue.text = s.chargeValue.isZero ? '' : s.chargeValue.toInput();
    _dirty = false;
  }

  void _edit(void Function(BusinessSettings s) change) => setState(() {
        change(_s!);
        _dirty = true;
      });

  Future<void> _save() async {
    final s = _s!;
    if (!_form.currentState!.validate()) return;
    if (s.allowedMethods.isEmpty) {
      setState(() => _error = 'Allow at least one interest method.');
      return;
    }
    s.maxInterestRate = Money.parse(_cap.text);
    s.penaltyValue = Money.tryParse(_penaltyValue.text) ?? Money.zero;
    s.penaltyGraceDays = int.tryParse(_grace.text) ?? 0;
    s.penaltyMaxCap =
        _penaltyMax.text.trim().isEmpty ? null : Money.parse(_penaltyMax.text);
    s.chargeValue = Money.tryParse(_chargeValue.text) ?? Money.zero;

    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final saved = await ref.read(settingsRepositoryProvider).save(s);
      if (!mounted) return;
      setState(() => _adopt(saved));
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Settings saved')));
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  static String? _amount(String? v, {bool optional = false, int? maxPercent}) {
    final text = (v ?? '').trim();
    if (text.isEmpty) return optional ? null : 'Required';
    final m = Money.tryParse(text);
    if (m == null || !m.isPositive) return 'Enter a positive amount';
    if (maxPercent != null && m.paise > maxPercent * 100)
      return 'At most $maxPercent%';
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final loaded = ref.watch(settingsProvider);
    // First load: copy into the editable state (controllers are not attached yet, so no rebuild loop).
    if (_s == null) {
      if (loaded case AsyncData(:final value)) _adopt(value);
    }

    return PopScope(
      canPop: !_dirty || _busy,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final leave = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('Discard changes?'),
            content: const Text('Your changes to settings are not saved.'),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: const Text('Keep editing')),
              TextButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  child: const Text('Discard')),
            ],
          ),
        );
        if (leave == true && context.mounted) {
          setState(() => _dirty = false);
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(title: Text('Settings', style: AppTheme.heading(22))),
        body: _s == null
            ? loaded.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(
                        e is ApiError ? e.message : 'Could not load settings.'),
                    const SizedBox(height: 12),
                    OutlinedButton(
                        onPressed: () => ref.invalidate(settingsProvider),
                        child: const Text('Try again')),
                  ]),
                ),
                data: (_) => const SizedBox.shrink(),
              )
            : Form(key: _form, child: _body(_s!)),
        bottomNavigationBar: _s == null
            ? null
            : SafeArea(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                  child: FilledButton(
                    onPressed: _busy || !_dirty ? null : _save,
                    child: _busy
                        ? const SizedBox.square(
                            dimension: 22,
                            child: CircularProgressIndicator(
                                strokeWidth: 2.5, color: Colors.white))
                        : Text(_dirty ? 'Save settings' : 'Saved'),
                  ),
                ),
              ),
      ),
    );
  }

  Widget _body(BusinessSettings s) {
    final turningPenaltyOn = s.penaltyEnabled && !_penaltyWasOn;
    final effective = s.penaltyEffectiveFrom == null
        ? null
        : DateFormat('d MMM yyyy').format(s.penaltyEffectiveFrom!);

    return ListView(padding: const EdgeInsets.all(16), children: [
      const _PlanCard(),
      const SizedBox(height: 16),
      FormSection(title: 'INTEREST', children: [
        const FieldLabel('Maximum rate (legal cap)'),
        TextFormField(
          controller: _cap,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          inputFormatters: [
            FilteringTextInputFormatter.allow(RegExp(r'^\d{0,3}\.?\d{0,2}'))
          ],
          decoration: const InputDecoration(suffixText: '% per year'),
          onChanged: (_) => setState(() => _dirty = true),
          validator: (v) => _amount(v),
        ),
        const FieldHelp('New loans cannot go above this rate'),
        const SizedBox(height: 12),
        const FieldLabel('Allowed methods'),
        for (final m in InterestMethod.values)
          CheckboxListTile(
            value: s.allowedMethods.contains(m),
            onChanged: (v) => _edit((s) => v == true
                ? s.allowedMethods.add(m)
                : s.allowedMethods.remove(m)),
            title: Text(m == InterestMethod.flat ? 'Flat' : 'Reducing balance'),
            controlAffinity: ListTileControlAffinity.leading,
            contentPadding: EdgeInsets.zero,
            activeColor: AppColors.brand,
            dense: true,
          ),
      ]),
      const SizedBox(height: 12),
      FormSection(title: 'LOANS', children: [
        Row(children: [
          const Expanded(
              child: Text('Active loans per client',
                  style: TextStyle(fontSize: 15))),
          IconButton.outlined(
            tooltip: 'Fewer',
            onPressed: s.maxActiveLoansPerClient <= 1
                ? null
                : () => _edit((s) => s.maxActiveLoansPerClient--),
            icon: const Icon(Icons.remove),
          ),
          SizedBox(
            width: 40,
            child: Text('${s.maxActiveLoansPerClient}',
                textAlign: TextAlign.center,
                style: GoogleFonts.spaceGrotesk(
                    fontSize: 20, fontWeight: FontWeight.w700)),
          ),
          IconButton.outlined(
            tooltip: 'More',
            onPressed: s.maxActiveLoansPerClient >= 10
                ? null
                : () => _edit((s) => s.maxActiveLoansPerClient++),
            icon: const Icon(Icons.add),
          ),
        ]),
      ]),
      const SizedBox(height: 12),
      FormSection(title: 'LATE PENALTY', children: [
        SwitchListTile(
          value: s.penaltyEnabled,
          onChanged: (v) => _edit((s) => s.penaltyEnabled = v),
          title: const Text('Charge a penalty for missed days'),
          contentPadding: EdgeInsets.zero,
          activeThumbColor: AppColors.brand,
        ),
        if (s.penaltyEnabled) ...[
          const SizedBox(height: 4),
          SegmentedButton<PenaltyType>(
            segments: const [
              ButtonSegment(value: PenaltyType.fixed, label: Text('Fixed ₹')),
              ButtonSegment(
                  value: PenaltyType.percent,
                  label: Text('% of missed amount')),
            ],
            selected: {s.penaltyType},
            showSelectedIcon: false,
            onSelectionChanged: (v) => _edit((s) => s.penaltyType = v.first),
          ),
          const SizedBox(height: 12),
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    FieldLabel(s.penaltyType == PenaltyType.fixed
                        ? 'Amount'
                        : 'Percent'),
                    TextFormField(
                      controller: _penaltyValue,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: [
                        FilteringTextInputFormatter.allow(
                            RegExp(r'^\d{0,6}\.?\d{0,2}'))
                      ],
                      decoration: InputDecoration(
                        prefixText:
                            s.penaltyType == PenaltyType.fixed ? '₹ ' : null,
                        suffixText:
                            s.penaltyType == PenaltyType.percent ? '%' : null,
                        errorMaxLines: 2,
                      ),
                      onChanged: (_) => setState(() => _dirty = true),
                      validator: (v) => _amount(v,
                          maxPercent: s.penaltyType == PenaltyType.percent
                              ? 100
                              : null),
                    ),
                  ]),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const FieldLabel('Grace days'),
                    TextFormField(
                      controller: _grace,
                      keyboardType: TextInputType.number,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                        LengthLimitingTextInputFormatter(2)
                      ],
                      onChanged: (_) => setState(() => _dirty = true),
                    ),
                  ]),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const FieldLabel('Max per loan'),
                    TextFormField(
                      controller: _penaltyMax,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: [
                        FilteringTextInputFormatter.allow(
                            RegExp(r'^\d{0,8}\.?\d{0,2}'))
                      ],
                      decoration: const InputDecoration(
                          prefixText: '₹ ',
                          hintText: 'No cap',
                          errorMaxLines: 2),
                      onChanged: (_) => setState(() => _dirty = true),
                      validator: (v) => _amount(v, optional: true),
                    ),
                  ]),
            ),
          ]),
          FieldHelp(
            turningPenaltyOn
                ? 'Charged once per missed day, only if still unpaid after the grace days. '
                    'Applies to days missed from today; earlier days are never charged.'
                : 'Charged once per missed day, only if still unpaid after the grace days.'
                    '${effective == null ? '' : ' Applies to days missed from $effective.'}',
          ),
        ],
      ]),
      const SizedBox(height: 12),
      FormSection(title: 'HOW PAYMENTS ARE APPLIED', children: [
        const FieldLabel('Clear first'),
        RadioGroup<AllocationOrder>(
          groupValue: s.allocationOrder,
          onChanged: (v) => _edit((s) => s.allocationOrder = v!),
          child: const Column(children: [
            RadioListTile(
              value: AllocationOrder.penaltyFirst,
              title: Text('Penalty, then dues'),
              contentPadding: EdgeInsets.zero,
              dense: true,
            ),
            RadioListTile(
              value: AllocationOrder.installmentFirst,
              title: Text('Dues, then penalty'),
              contentPadding: EdgeInsets.zero,
              dense: true,
            ),
          ]),
        ),
        const SizedBox(height: 8),
        const FieldLabel('Extra money goes to'),
        RadioGroup<AdvanceAllocation>(
          groupValue: s.advanceAllocation,
          onChanged: (v) => _edit((s) => s.advanceAllocation = v!),
          child: const Column(children: [
            RadioListTile(
              value: AdvanceAllocation.nextInstallments,
              title: Text('Next days'),
              subtitle: Text('Client can skip the coming days'),
              contentPadding: EdgeInsets.zero,
            ),
            RadioListTile(
              value: AdvanceAllocation.lastInstallments,
              title: Text('Last days'),
              subtitle: Text(
                  'Loan finishes earlier; missed days carried forward are cleared first'),
              contentPadding: EdgeInsets.zero,
            ),
          ]),
        ),
      ]),
      const SizedBox(height: 12),
      FormSection(title: 'PRE-CLOSURE', children: [
        SwitchListTile(
          value: s.preclosureEnabled,
          onChanged: (v) => _edit((s) => s.preclosureEnabled = v),
          title: const Text('Allow closing a loan early'),
          contentPadding: EdgeInsets.zero,
          activeThumbColor: AppColors.brand,
        ),
        if (s.preclosureEnabled) ...[
          CheckboxListTile(
            value: s.rebateUnearnedInterest,
            onChanged: (v) =>
                _edit((s) => s.rebateUnearnedInterest = v ?? false),
            title: const Text("Don't charge interest for future days"),
            subtitle: const Text(
                'Client pays only the interest up to the closing day'),
            controlAffinity: ListTileControlAffinity.leading,
            contentPadding: EdgeInsets.zero,
            activeColor: AppColors.brand,
          ),
          const SizedBox(height: 8),
          const FieldLabel('Pre-closure charge'),
          SegmentedButton<ChargeType>(
            segments: const [
              ButtonSegment(value: ChargeType.none, label: Text('No charge')),
              ButtonSegment(value: ChargeType.fixed, label: Text('Fixed ₹')),
              ButtonSegment(value: ChargeType.percent, label: Text('%')),
            ],
            selected: {s.chargeType},
            showSelectedIcon: false,
            onSelectionChanged: (v) => _edit((s) => s.chargeType = v.first),
          ),
          if (s.chargeType != ChargeType.none) ...[
            const SizedBox(height: 12),
            TextFormField(
              controller: _chargeValue,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'^\d{0,8}\.?\d{0,2}'))
              ],
              decoration: InputDecoration(
                prefixText: s.chargeType == ChargeType.fixed ? '₹ ' : null,
                suffixText: s.chargeType == ChargeType.percent
                    ? '% of principal left'
                    : null,
              ),
              onChanged: (_) => setState(() => _dirty = true),
              validator: (v) => _amount(v,
                  maxPercent: s.chargeType == ChargeType.percent ? 100 : null),
            ),
          ],
        ],
      ]),
      const SizedBox(height: 12),
      FormSection(title: 'RECORDS', children: [
        Row(children: [
          const Expanded(
              child: Text('Keep records for', style: TextStyle(fontSize: 15))),
          Text('${s.recordRetentionYears} years',
              style:
                  const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
        ]),
        const FieldHelp('Set as advised by your CA for income tax'),
      ]),
      if (_error != null) ...[
        const SizedBox(height: 12),
        Text(_error!,
            style: const TextStyle(
                color: AppColors.error, fontWeight: FontWeight.w600)),
      ],
    ]);
  }
}

/// Plan and expiry from the dashboard response; upgrading is handled by DigitWORKS until billing exists.
class _PlanCard extends ConsumerWidget {
  const _PlanCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summary = switch (ref.watch(dashboardProvider)) {
      AsyncData(:final value) => value,
      _ => null,
    };
    final plan = switch (summary?.plan) {
      'TRIAL' => 'Free trial',
      'MONTHLY' => 'Monthly plan',
      'YEARLY' => 'Yearly plan',
      _ => 'Plan',
    };
    final expiry = summary?.planExpiry;
    final daysLeft =
        expiry?.difference(DateUtils.dateOnly(DateTime.now())).inDays;
    final ends = expiry == null
        ? ''
        : daysLeft! < 0
            ? 'Ended ${DateFormat('d MMM yyyy').format(expiry)}'
            : 'Ends ${DateFormat('d MMM yyyy').format(expiry)} · $daysLeft ${daysLeft == 1 ? 'day' : 'days'} left';

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
          color: AppColors.ink, borderRadius: BorderRadius.circular(16)),
      child: Row(children: [
        Expanded(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${summary?.businessName ?? ''} · Plan',
                style: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFFB8C7C1),
                    fontWeight: FontWeight.w500)),
            const SizedBox(height: 4),
            Text(plan,
                style: GoogleFonts.spaceGrotesk(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: Colors.white)),
            if (ends.isNotEmpty)
              Text(ends,
                  style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: (daysLeft ?? 99) <= 7
                          ? const Color(0xFFFF8A80)
                          : AppColors.gold)),
          ]),
        ),
        FilledButton(
          style: FilledButton.styleFrom(
            backgroundColor: AppColors.gold,
            foregroundColor: AppColors.ink,
            minimumSize: const Size(0, 44),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          onPressed: () => showDialog<void>(
            context: context,
            builder: (ctx) => AlertDialog(
              title: const Text('Upgrade plan'),
              content: const Text(
                  'Online payment for plans is coming soon. To move to Monthly or Yearly now, '
                  'contact DigitWORKS and we will update your plan.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(ctx),
                    child: const Text('OK'))
              ],
            ),
          ),
          child: const Text('Upgrade',
              style: TextStyle(fontWeight: FontWeight.w700)),
        ),
      ]),
    );
  }
}
