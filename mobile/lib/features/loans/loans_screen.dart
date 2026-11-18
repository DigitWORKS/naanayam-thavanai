import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import 'loan_detail_screen.dart';
import 'loan_models.dart';
import 'loans_repository.dart';
import 'new_loan_screen.dart';

/// Owner's Loans tab: loans by status, newest first, with New loan.
class LoansScreen extends ConsumerStatefulWidget {
  const LoansScreen({super.key, required this.user});

  final AppUser user;

  @override
  ConsumerState<LoansScreen> createState() => _LoansScreenState();
}

class _LoansScreenState extends ConsumerState<LoansScreen> {
  LoanStatus _status = LoanStatus.active;
  final List<LoanSummary> _items = [];
  LoanPage? _last;
  bool _loading = false;
  String? _error;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() => _load(reset: true);

  Future<void> _load({required bool reset}) async {
    final generation = reset ? ++_generation : _generation;
    final page = reset ? 0 : (_last?.page ?? -1) + 1;
    setState(() {
      _loading = true;
      _error = null;
      if (reset) {
        _items.clear();
        _last = null;
      }
    });
    try {
      final result = await ref.read(loansRepositoryProvider).list(status: _status, page: page);
      if (!mounted || generation != _generation) return;
      setState(() {
        _items.addAll(result.items);
        _last = result;
      });
    } on ApiError catch (e) {
      if (mounted && generation == _generation) setState(() => _error = e.message);
    } finally {
      if (mounted && generation == _generation) setState(() => _loading = false);
    }
  }

  Future<void> _open(int loanId) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => LoanDetailScreen(loanId: loanId, user: widget.user),
    ));
    if (mounted) _reload();
  }

  Future<void> _newLoan() async {
    final id = await Navigator.of(context).push<int>(MaterialPageRoute(
      builder: (_) => NewLoanScreen(user: widget.user),
      fullscreenDialog: true,
    ));
    if (!mounted) return;
    if (id != null) {
      await _open(id);
    } else {
      _reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    final total = _last?.totalElements;
    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 64,
        titleSpacing: 16,
        title: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Text('Loans', style: AppTheme.heading(24)),
          const SizedBox(width: 10),
          if (total != null)
            Text('$total ${_status.label.toLowerCase()}',
                style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500)),
        ]),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(52),
          child: SizedBox(
            height: 52,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              scrollDirection: Axis.horizontal,
              children: [
                for (final s in LoanStatus.values)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(s.label),
                      selected: _status == s,
                      showCheckmark: false,
                      selectedColor: AppColors.ink,
                      labelStyle: TextStyle(
                          fontWeight: FontWeight.w600, color: _status == s ? Colors.white : AppColors.ink),
                      side: BorderSide(color: _status == s ? AppColors.ink : AppColors.border),
                      onSelected: (_) {
                        if (_status == s) return;
                        setState(() => _status = s);
                        _reload();
                      },
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
      floatingActionButton: widget.user.isOwner
          ? FloatingActionButton.extended(
              onPressed: _newLoan,
              backgroundColor: AppColors.brand,
              foregroundColor: Colors.white,
              icon: const Icon(Icons.add),
              label: const Text('New loan', style: TextStyle(fontWeight: FontWeight.w600)),
            )
          : null,
      body: RefreshIndicator(onRefresh: _reload, child: _body()),
    );
  }

  Widget _body() {
    if (_items.isEmpty && _loading) return const Center(child: CircularProgressIndicator());
    if (_items.isEmpty) {
      return ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(32), children: [
        const SizedBox(height: 60),
        Text(_error ?? 'No ${_status.label.toLowerCase()} loans.',
            textAlign: TextAlign.center, style: const TextStyle(color: AppColors.muted)),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Center(child: OutlinedButton(onPressed: _reload, child: const Text('Try again'))),
        ],
      ]);
    }
    final hasMore = _last?.hasMore ?? false;
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      itemCount: _items.length + (hasMore ? 1 : 0),
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (context, i) {
        if (i == _items.length) {
          return Center(
            child: _loading
                ? const Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator())
                : OutlinedButton(onPressed: () => _load(reset: false), child: const Text('Load more')),
          );
        }
        final l = _items[i];
        return LoanCard(loan: l, showClient: true, onTap: () => _open(l.id));
      },
    );
  }
}

/// Loan card used by the Loans tab and the client detail.
class LoanCard extends StatelessWidget {
  const LoanCard({super.key, required this.loan, required this.onTap, this.showClient = false});

  final LoanSummary loan;
  final VoidCallback onTap;
  final bool showClient;

  @override
  Widget build(BuildContext context) {
    final l = loan;
    final active = l.status == LoanStatus.active;
    final behind = active && l.overdueAmount.isPositive;
    final ends = l.endDate == null ? '' : ' · ends ${DateFormat('d MMM yyyy').format(l.endDate!)}';

    final (tagText, tagColor, tagBg) = switch (l.status) {
      LoanStatus.active when behind => ('${l.overdueAmount.format()} behind', AppColors.warning, AppColors.warningTint),
      LoanStatus.active => ('On track', AppColors.brand, AppColors.brandTint),
      LoanStatus.defaulted => ('Defaulted', AppColors.error, const Color(0xFFFDECEA)),
      _ => (l.status.label, AppColors.muted, AppColors.background),
    };

    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (showClient) ...[
              Text(l.clientName, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              Text(l.areaName, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
              const SizedBox(height: 8),
            ],
            Row(children: [
              Expanded(
                child: Text('${l.loanNo} · ${l.principal.format()}',
                    style: GoogleFonts.spaceGrotesk(fontSize: 15, fontWeight: FontWeight.w700)),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(color: tagBg, borderRadius: BorderRadius.circular(999)),
                child: Text(tagText, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: tagColor)),
              ),
            ]),
            if (active) ...[
              const SizedBox(height: 10),
              ClipRRect(
                borderRadius: BorderRadius.circular(999),
                child: LinearProgressIndicator(
                  value: l.paidShare,
                  minHeight: 6,
                  backgroundColor: const Color(0xFFE3EAE7),
                  color: AppColors.brand,
                ),
              ),
              const SizedBox(height: 8),
              Text('${l.frequency.label} ${l.installmentAmount.format()} · ${l.outstanding.format()} left$ends',
                  style: const TextStyle(fontSize: 13, color: AppColors.muted)),
            ],
          ]),
        ),
      ),
    );
  }
}
