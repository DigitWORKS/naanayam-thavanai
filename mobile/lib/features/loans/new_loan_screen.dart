import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../../widgets/form_widgets.dart';
import '../auth/auth_models.dart';
import '../clients/client_models.dart' show ClientListItem, initialsOf;
import '../clients/clients_repository.dart';
import 'loan_models.dart';
import 'loans_repository.dart';

/// Design board 6 (owner only). Pops with the new loan's id.
class NewLoanScreen extends ConsumerStatefulWidget {
  const NewLoanScreen({super.key, required this.user, this.clientId});

  final AppUser user;

  /// Pre-selected client (from client detail / "Save & give loan"); null shows the picker.
  final int? clientId;

  @override
  ConsumerState<NewLoanScreen> createState() => _NewLoanScreenState();
}

class _NewLoanScreenState extends ConsumerState<NewLoanScreen> {
  final _principal = TextEditingController();
  final _rate = TextEditingController();
  final _tenure = TextEditingController(text: '100');

  int? _clientId;
  Frequency _frequency = Frequency.daily;
  InterestMethod _method = InterestMethod.flat;
  InterestCollection _collection = InterestCollection.addedToRepayment;
  DateTime _start = DateUtils.dateOnly(DateTime.now());

  LoanPreview? _preview;
  String? _previewError;
  bool _previewing = false;
  Timer? _debounce;
  int _previewGeneration = 0;

  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _clientId = widget.clientId;
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _principal.dispose();
    _rate.dispose();
    _tenure.dispose();
    super.dispose();
  }

  /// Current form as a request, or null while something is missing / invalid.
  LoanInput? _input() {
    final principal = Money.tryParse(_principal.text.replaceAll(',', ''));
    final rate = Money.tryParse(_rate.text);
    final tenure = int.tryParse(_tenure.text);
    if (_clientId == null || principal == null || !principal.isPositive || rate == null) return null;
    if (tenure == null || tenure < 1 || tenure > 1000) return null;
    return LoanInput(
      clientId: _clientId!,
      principal: principal,
      interestRate: rate,
      interestMethod: _method,
      interestCollection: _collection,
      frequency: _frequency,
      tenureCount: tenure,
      startDate: _start,
    );
  }

  String? _rateProblem(LoanRules rules) {
    final rate = Money.tryParse(_rate.text);
    if (rate == null) return null;
    return rate.compareTo(rules.maxInterestRate) > 0 ? 'Above your maximum of ${_pct(rules.maxInterestRate)}' : null;
  }

  void _changed() {
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 450), _runPreview);
  }

  Future<void> _runPreview() async {
    final input = _input();
    final generation = ++_previewGeneration;
    if (input == null) {
      setState(() {
        _preview = null;
        _previewError = null;
      });
      return;
    }
    setState(() => _previewing = true);
    try {
      final p = await ref.read(loansRepositoryProvider).preview(input);
      if (!mounted || generation != _previewGeneration) return;
      setState(() {
        _preview = p;
        _previewError = null;
      });
    } on ApiError catch (e) {
      if (!mounted || generation != _previewGeneration) return;
      setState(() {
        _preview = null;
        _previewError = e.message;
      });
    } finally {
      if (mounted && generation == _previewGeneration) setState(() => _previewing = false);
    }
  }

  Future<void> _pickClient() async {
    final picked = await showModalBottomSheet<ClientListItem>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => const _ClientPicker(),
    );
    if (picked != null) {
      setState(() => _clientId = picked.id);
      _changed();
    }
  }

  Future<void> _pickDate() async {
    final today = DateUtils.dateOnly(DateTime.now());
    final picked = await showDatePicker(
      context: context,
      initialDate: _start,
      firstDate: today.subtract(const Duration(days: 30)),
      lastDate: today.add(const Duration(days: 30)),
    );
    if (picked != null) {
      setState(() => _start = picked);
      _changed();
    }
  }

  Future<void> _create(LoanInput input) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Create loan?'),
        content: Text('Give ${_preview?.disbursedAmount.format() ?? input.principal.format()} to the client now. '
            'The schedule starts on ${DateFormat('d MMM').format(_start)}.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Create')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final id = await ref.read(loansRepositoryProvider).create(input);
      ref.invalidate(clientDetailProvider(input.clientId));
      if (mounted) Navigator.of(context).pop(id);
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  static String _pct(Money m) => '${m.toInput()}%';

  @override
  Widget build(BuildContext context) {
    final rules = ref.watch(loanRulesProvider);
    final r = switch (rules) {
      AsyncData(:final value) => value,
      _ => null,
    };
    final input = _input();
    final rateProblem = r == null ? null : _rateProblem(r);
    final canCreate = input != null && rateProblem == null && _preview != null && !_previewing && !_busy;

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.close),
          tooltip: 'Close',
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
        ),
        title: Text('New loan', style: AppTheme.heading(20)),
      ),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        _ClientCard(clientId: _clientId, rules: r, onChange: _busy ? null : _pickClient),
        const SizedBox(height: 12),
        FormSection(title: 'AMOUNT', children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              flex: 3,
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const FieldLabel('Principal', required: true),
                TextField(
                  controller: _principal,
                  enabled: !_busy,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d{0,10}\.?\d{0,2}'))],
                  style: GoogleFonts.spaceGrotesk(fontSize: 20, fontWeight: FontWeight.w700),
                  decoration: const InputDecoration(prefixText: '₹ '),
                  onChanged: (_) => _changed(),
                ),
              ]),
            ),
            const SizedBox(width: 12),
            Expanded(
              flex: 2,
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const FieldLabel('Interest % p.a.', required: true),
                TextField(
                  controller: _rate,
                  enabled: !_busy,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d{0,3}\.?\d{0,2}'))],
                  style: GoogleFonts.spaceGrotesk(fontSize: 20, fontWeight: FontWeight.w700),
                  decoration: InputDecoration(errorText: rateProblem, errorMaxLines: 2, suffixText: '%'),
                  onChanged: (_) => _changed(),
                ),
                if (r != null && rateProblem == null) FieldHelp('Max allowed ${_pct(r.maxInterestRate)}'),
              ]),
            ),
          ]),
        ]),
        const SizedBox(height: 12),
        FormSection(title: 'FREQUENCY AND TENURE', children: [
          Row(children: [
            Expanded(
              child: SegmentedButton<Frequency>(
                segments: const [
                  ButtonSegment(value: Frequency.daily, label: Text('Daily')),
                  ButtonSegment(value: Frequency.weekly, label: Text('Weekly')),
                ],
                selected: {_frequency},
                showSelectedIcon: false,
                onSelectionChanged: _busy
                    ? null
                    : (s) {
                        setState(() => _frequency = s.first);
                        _changed();
                      },
              ),
            ),
            const SizedBox(width: 12),
            SizedBox(
              width: 120,
              child: TextField(
                controller: _tenure,
                enabled: !_busy,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(4)],
                decoration: InputDecoration(suffixText: _frequency.unit),
                onChanged: (_) => _changed(),
              ),
            ),
          ]),
        ]),
        const SizedBox(height: 12),
        FormSection(title: 'INTEREST', children: [
          SegmentedButton<InterestMethod>(
            segments: [
              ButtonSegment(
                value: InterestMethod.flat,
                label: const Text('Flat'),
                enabled: r == null || r.allowedMethods.contains(InterestMethod.flat),
              ),
              ButtonSegment(
                value: InterestMethod.reducing,
                label: const Text('Reducing'),
                enabled: r == null || r.allowedMethods.contains(InterestMethod.reducing),
              ),
            ],
            selected: {_method},
            showSelectedIcon: false,
            onSelectionChanged: _busy
                ? null
                : (s) {
                    setState(() => _method = s.first);
                    _changed();
                  },
          ),
          const SizedBox(height: 10),
          SegmentedButton<InterestCollection>(
            segments: const [
              ButtonSegment(value: InterestCollection.addedToRepayment, label: Text('Added to dues')),
              ButtonSegment(value: InterestCollection.upfrontDeducted, label: Text('Deduct upfront')),
            ],
            selected: {_collection},
            showSelectedIcon: false,
            onSelectionChanged: _busy
                ? null
                : (s) {
                    setState(() => _collection = s.first);
                    _changed();
                  },
          ),
        ]),
        const SizedBox(height: 12),
        FormSection(title: 'START DATE', children: [
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
              alignment: Alignment.centerLeft,
              foregroundColor: AppColors.ink,
              side: const BorderSide(color: AppColors.border, width: 1.5),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            onPressed: _busy ? null : _pickDate,
            icon: const Icon(Icons.event_outlined),
            label: Text(DateFormat('d MMM yyyy').format(_start), style: const TextStyle(fontSize: 16)),
          ),
          const FieldHelp('The first installment is due on the next day / week'),
        ]),
        const SizedBox(height: 12),
        _PreviewCard(
          preview: _preview,
          frequency: _frequency,
          loading: _previewing,
          error: _previewError,
          ready: input != null,
        ),
        if (_error != null) ...[
          const SizedBox(height: 12),
          Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
        ],
      ]),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: FilledButton(
            onPressed: canCreate ? () => _create(input) : null,
            child: _busy
                ? const SizedBox.square(
                    dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                : Text(_preview == null ? 'Create loan' : 'Create loan · give ${_preview!.disbursedAmount.format()}'),
          ),
        ),
      ),
    );
  }
}

class _ClientCard extends ConsumerWidget {
  const _ClientCard({required this.clientId, required this.rules, required this.onChange});

  final int? clientId;
  final LoanRules? rules;
  final VoidCallback? onChange;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final decoration = BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14));
    if (clientId == null) {
      return Material(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          leading: const Icon(Icons.person_search_outlined, color: AppColors.brand),
          title: const Text('Choose client', style: TextStyle(fontWeight: FontWeight.w600)),
          trailing: const Icon(Icons.chevron_right),
          onTap: onChange,
        ),
      );
    }
    final detail = ref.watch(clientDetailProvider(clientId!));
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: decoration,
      child: detail.when(
        loading: () => const LinearProgressIndicator(),
        error: (e, _) => Text(e is ApiError ? e.message : 'Could not load the client.'),
        data: (c) {
          final count = c.activeLoans.length;
          final limit = rules?.maxActiveLoansPerClient;
          final full = limit != null && count >= limit;
          return Row(children: [
            CircleAvatar(
              backgroundColor: AppColors.brandTint,
              foregroundColor: AppColors.brand,
              child: Text(initialsOf(c.name), style: const TextStyle(fontWeight: FontWeight.w700)),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(c.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                Text(
                  '${c.areaName} · $count active ${count == 1 ? 'loan' : 'loans'}${limit == null ? '' : ' (limit $limit)'}',
                  style: TextStyle(fontSize: 13, color: full ? AppColors.error : AppColors.muted),
                ),
              ]),
            ),
            if (onChange != null) TextButton(onPressed: onChange, child: const Text('Change')),
          ]);
        },
      ),
    );
  }
}

class _PreviewCard extends StatelessWidget {
  const _PreviewCard({
    required this.preview,
    required this.frequency,
    required this.loading,
    required this.error,
    required this.ready,
  });

  final LoanPreview? preview;
  final Frequency frequency;
  final bool loading;
  final String? error;
  final bool ready;

  @override
  Widget build(BuildContext context) {
    final p = preview;
    const soft = Color(0xFFB8C7C1);
    Widget line(String label, String value) => Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Row(children: [
            Expanded(child: Text(label, style: const TextStyle(color: soft))),
            Text(value, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
          ]),
        );

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AppColors.ink, borderRadius: BorderRadius.circular(16)),
      child: !ready
          ? const Text('Choose a client and enter the amount, rate and tenure to see the schedule.',
              style: TextStyle(color: soft))
          : error != null
              ? Text(error!, style: const TextStyle(color: AppColors.gold, fontWeight: FontWeight.w600))
              : p == null
                  ? const Center(child: CircularProgressIndicator(color: AppColors.gold))
                  : AnimatedOpacity(
                      opacity: loading ? 0.5 : 1,
                      duration: const Duration(milliseconds: 150),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                        Row(children: [
                          Expanded(
                            child: Text('Client pays ${frequency.label.toLowerCase()}',
                                style: const TextStyle(color: soft)),
                          ),
                          Text(p.installmentAmount.format(),
                              style: GoogleFonts.spaceGrotesk(
                                  fontSize: 26, fontWeight: FontWeight.w700, color: AppColors.gold)),
                        ]),
                        line('Interest', p.interestAmount.format()),
                        line('Give client now', p.disbursedAmount.format()),
                        line('Total payable', p.totalPayable.format()),
                        const Divider(color: Color(0xFF2A3D35), height: 24),
                        Row(children: [
                          Expanded(
                            child: Text(
                              '${p.firstDueDate == null ? '' : DateFormat('d MMM').format(p.firstDueDate!)} → '
                              '${DateFormat('d MMM yyyy').format(p.endDate)}',
                              style: const TextStyle(color: soft, fontSize: 13),
                            ),
                          ),
                          if (p.lastInstallment != p.installmentAmount)
                            Text('last ${frequency == Frequency.daily ? 'day' : 'week'} ${p.lastInstallment.format()}',
                                style: const TextStyle(color: soft, fontSize: 13)),
                        ]),
                      ]),
                    ),
    );
  }
}

/// Bottom sheet: search clients and pick one.
class _ClientPicker extends ConsumerStatefulWidget {
  const _ClientPicker();

  @override
  ConsumerState<_ClientPicker> createState() => _ClientPickerState();
}

class _ClientPickerState extends ConsumerState<_ClientPicker> {
  final _q = TextEditingController();
  Timer? _debounce;
  List<ClientListItem> _items = const [];
  bool _loading = true;
  String? _error;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _search();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _q.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    final generation = ++_generation;
    setState(() => _loading = true);
    try {
      final page = await ref.read(clientsRepositoryProvider).search(q: _q.text, size: 30);
      if (!mounted || generation != _generation) return;
      setState(() {
        _items = page.items;
        _error = null;
      });
    } on ApiError catch (e) {
      if (mounted && generation == _generation) setState(() => _error = e.message);
    } finally {
      if (mounted && generation == _generation) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.75,
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              controller: _q,
              autofocus: true,
              decoration: const InputDecoration(
                hintText: 'Search name or phone',
                prefixIcon: Icon(Icons.search),
                isDense: true,
              ),
              onChanged: (_) {
                _debounce?.cancel();
                _debounce = Timer(const Duration(milliseconds: 350), _search);
              },
            ),
          ),
          if (_loading) const LinearProgressIndicator(minHeight: 2),
          Expanded(
            child: _error != null
                ? Center(child: Text(_error!))
                : _items.isEmpty && !_loading
                    ? const Center(child: Text('No clients found.', style: TextStyle(color: AppColors.muted)))
                    : ListView.builder(
                        itemCount: _items.length,
                        itemBuilder: (_, i) {
                          final c = _items[i];
                          return ListTile(
                            leading: CircleAvatar(
                              backgroundColor: AppColors.brandTint,
                              foregroundColor: AppColors.brand,
                              child: Text(initialsOf(c.name), style: const TextStyle(fontWeight: FontWeight.w700)),
                            ),
                            title: Text(c.name),
                            subtitle: Text('${c.areaName} · ${c.activeLoans} active'),
                            onTap: () => Navigator.pop(context, c),
                          );
                        },
                      ),
          ),
        ]),
      ),
    );
  }
}
