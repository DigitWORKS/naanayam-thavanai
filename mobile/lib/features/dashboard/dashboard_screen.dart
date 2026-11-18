import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import '../handover/handover_repository.dart';
import 'dashboard_repository.dart';

/// Owner home (design board 5): today's collection, portfolio, areas, handovers to confirm.
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key, required this.user});

  final AppUser user;

  Future<void> _refresh(WidgetRef ref) => Future.wait([
        ref.refresh(dashboardProvider.future),
        ref.refresh(areaProgressProvider.future),
        ref.refresh(pendingHandoversProvider.future),
      ]);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final summary = ref.watch(dashboardProvider);
    final firstName = user.name.split(' ').first;
    final today = DateFormat('EEE, d MMM').format(DateTime.now());
    final business = switch (summary) {
      AsyncData(:final value) when value.businessName.isNotEmpty => '${value.businessName} · ',
      _ => '',
    };

    return Scaffold(
      appBar: AppBar(
        backgroundColor: AppColors.background,
        toolbarHeight: 76,
        titleSpacing: 20,
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('$business$today',
              style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500)),
          Text('Vanakkam, $firstName', style: AppTheme.heading(24)),
        ]),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 16),
            child: IconButton.filled(
              tooltip: 'New loan',
              style: IconButton.styleFrom(
                backgroundColor: AppColors.brand,
                fixedSize: const Size(48, 48),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
              icon: const Icon(Icons.add, color: Colors.white),
              onPressed: () => ScaffoldMessenger.of(context)
                  .showSnackBar(const SnackBar(content: Text('New loan comes in the next update.'))),
            ),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => _refresh(ref),
        child: summary.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(32),
            children: [
              const SizedBox(height: 80),
              Text(e is ApiError ? e.message : 'Could not load the dashboard.', textAlign: TextAlign.center),
              const SizedBox(height: 16),
              Center(
                child: OutlinedButton(onPressed: () => ref.invalidate(dashboardProvider), child: const Text('Try again')),
              ),
            ],
          ),
          data: (s) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
            children: [
              _CollectedCard(s),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(
                  child: _StatTile(
                    label: 'Outstanding',
                    value: s.totalOutstanding.format(),
                    note: '${s.activeLoans} active ${s.activeLoans == 1 ? 'loan' : 'loans'}',
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _StatTile(
                    label: 'Behind schedule',
                    value: s.overdueAmount.format(),
                    valueColor: s.overdueAmount.isPositive ? AppColors.warning : AppColors.ink,
                    note: '${s.overdueLoans} ${s.overdueLoans == 1 ? 'loan' : 'loans'}',
                  ),
                ),
              ]),
              const SizedBox(height: 12),
              const _AreaSection(),
              const SizedBox(height: 12),
              const _HandoverSection(),
            ],
          ),
        ),
      ),
    );
  }
}

class _CollectedCard extends StatelessWidget {
  const _CollectedCard(this.s);

  final DashboardSummary s;

  @override
  Widget build(BuildContext context) {
    final progress = s.todayTarget.isPositive ? (s.todayCollected.paise / s.todayTarget.paise).clamp(0.0, 1.0) : 0.0;
    const soft = Color(0xFFCFE6DD);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(color: AppColors.brand, borderRadius: BorderRadius.circular(18)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Collected today', style: TextStyle(fontSize: 13, color: soft, fontWeight: FontWeight.w500)),
        const SizedBox(height: 8),
        Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Text(s.todayCollected.format(),
              style: GoogleFonts.spaceGrotesk(fontSize: 34, fontWeight: FontWeight.w700, color: Colors.white)),
          const SizedBox(width: 8),
          Text('of ${s.todayTarget.format()}', style: const TextStyle(fontSize: 14, color: soft)),
        ]),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(999),
          child: LinearProgressIndicator(
            value: progress,
            minHeight: 8,
            backgroundColor: Colors.white.withValues(alpha: 0.22),
            color: AppColors.gold,
          ),
        ),
        const SizedBox(height: 12),
        DefaultTextStyle(
          style: const TextStyle(fontSize: 13, color: AppColors.brandTint, fontWeight: FontWeight.w500),
          child: Row(children: [
            Text('Cash ${s.todayCash.format()}'),
            const SizedBox(width: 20),
            Text('UPI ${s.todayUpi.format()}'),
          ]),
        ),
      ]),
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile({required this.label, required this.value, required this.note, this.valueColor = AppColors.ink});

  final String label;
  final String value;
  final String note;
  final Color valueColor;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted, fontWeight: FontWeight.w500)),
          const SizedBox(height: 4),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(value, style: GoogleFonts.spaceGrotesk(fontSize: 20, fontWeight: FontWeight.w700, color: valueColor)),
          ),
          const SizedBox(height: 4),
          Text(note, style: const TextStyle(fontSize: 12, color: AppColors.muted, fontWeight: FontWeight.w500)),
        ]),
      );
}

class _Panel extends StatelessWidget {
  const _Panel({required this.title, this.trailing, required this.children});

  final String title;
  final Widget? trailing;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Row(children: [
            Expanded(child: Text(title, style: AppTheme.heading(16))),
            if (trailing != null) trailing!,
          ]),
          const SizedBox(height: 12),
          ...children,
        ]),
      );
}

class _AreaSection extends ConsumerWidget {
  const _AreaSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final areas = ref.watch(areaProgressProvider);
    return _Panel(
      title: 'By area',
      trailing: const Text('collected / target', style: TextStyle(fontSize: 12, color: AppColors.muted)),
      children: areas.when(
        loading: () => [const LinearProgressIndicator()],
        error: (e, _) => [Text(e is ApiError ? e.message : 'Could not load areas.')],
        data: (rows) => rows.isEmpty
            ? [const Text('No collections due today.', style: TextStyle(color: AppColors.muted))]
            : [
                for (final a in rows) ...[
                  Row(children: [
                    Expanded(child: Text(a.area, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500))),
                    Text(a.percent == null ? a.collected.format() : '${a.collected.format()} · ${a.percent}%',
                        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                  ]),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(999),
                    child: LinearProgressIndicator(
                      value: ((a.percent ?? 100) / 100).clamp(0.0, 1.0),
                      minHeight: 8,
                      backgroundColor: const Color(0xFFE3EAE7),
                      color: AppColors.brand,
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
              ],
      ),
    );
  }
}

class _HandoverSection extends ConsumerWidget {
  const _HandoverSection();

  Future<void> _decide(BuildContext context, WidgetRef ref, Handover h, HandoverStatus decision, String? remarks) async {
    try {
      await ref.read(handoverRepositoryProvider).decide(h.id, decision, remarks: remarks);
      ref.invalidate(pendingHandoversProvider);
      ref.invalidate(dashboardProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(decision == HandoverStatus.confirmed
              ? '${h.handedAmount.format()} from ${h.agentName} confirmed'
              : 'Handover from ${h.agentName} marked as disputed'),
        ));
      }
    } on ApiError catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _confirm(BuildContext context, WidgetRef ref, Handover h) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Confirm handover'),
        content: Text('Received ${h.handedAmount.format()} cash from ${h.agentName}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size(100, 44)),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    if (ok == true && context.mounted) await _decide(context, ref, h, HandoverStatus.confirmed, null);
  }

  Future<void> _review(BuildContext context, WidgetRef ref, Handover h) async {
    var remarks = '';
    final decision = await showModalBottomSheet<HandoverStatus>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(20, 0, 20, 20 + MediaQuery.viewInsetsOf(ctx).bottom),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text('Review handover · ${h.agentName}', style: AppTheme.heading(18)),
          const SizedBox(height: 12),
          _line('Collected in cash', h.expectedAmount),
          _line('Handed over', h.handedAmount),
          _line(h.difference.paise < 0 ? 'Short' : 'Extra', Money(h.difference.paise.abs()), color: AppColors.warning),
          const SizedBox(height: 12),
          TextField(
            onChanged: (v) => remarks = v,
            decoration: const InputDecoration(hintText: 'Remarks (optional)'),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, HandoverStatus.confirmed),
            child: Text('Accept ${h.handedAmount.format()}'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            onPressed: () => Navigator.pop(ctx, HandoverStatus.disputed),
            child: const Text('Dispute'),
          ),
        ]),
      ),
    );
    if (decision != null && context.mounted) await _decide(context, ref, h, decision, remarks);
  }

  static Widget _line(String label, Money value, {Color color = AppColors.ink}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(children: [
          Expanded(child: Text(label, style: const TextStyle(color: AppColors.muted))),
          Text(value.format(), style: TextStyle(fontWeight: FontWeight.w600, color: color)),
        ]),
      );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pending = ref.watch(pendingHandoversProvider);
    final count = switch (pending) {
      AsyncData(:final value) => value.length,
      _ => 0,
    };
    final today = DateUtils.dateOnly(DateTime.now());

    return _Panel(
      title: 'Cash handovers',
      trailing: count == 0
          ? null
          : Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
              decoration: BoxDecoration(color: AppColors.warningTint, borderRadius: BorderRadius.circular(999)),
              child: Text('$count to confirm',
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.warning)),
            ),
      children: pending.when(
        loading: () => [const LinearProgressIndicator()],
        error: (e, _) => [Text(e is ApiError ? e.message : 'Could not load handovers.')],
        data: (list) => list.isEmpty
            ? [const Text('Nothing to confirm.', style: TextStyle(color: AppColors.muted))]
            : [
                for (final h in list)
                  Container(
                    padding: const EdgeInsets.only(top: 10, bottom: 4),
                    decoration: const BoxDecoration(border: Border(top: BorderSide(color: Color(0xFFE3EAE7)))),
                    child: Row(children: [
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(h.agentName, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
                          const SizedBox(height: 2),
                          Text(
                            [
                              if (!DateUtils.isSameDay(h.handoverDate, today)) DateFormat('d MMM').format(h.handoverDate),
                              'Handed ${h.handedAmount.format()}',
                              if (h.matches)
                                'matches'
                              else if (h.difference.paise < 0)
                                '${Money(-h.difference.paise).format()} short'
                              else
                                '${h.difference.format()} extra',
                            ].join(' · '),
                            style: TextStyle(fontSize: 12, color: h.matches ? AppColors.muted : AppColors.warning),
                          ),
                        ]),
                      ),
                      const SizedBox(width: 10),
                      h.matches
                          ? FilledButton(
                              style: FilledButton.styleFrom(
                                minimumSize: const Size(0, 40),
                                padding: const EdgeInsets.symmetric(horizontal: 14),
                                textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                              ),
                              onPressed: () => _confirm(context, ref, h),
                              child: const Text('Confirm'),
                            )
                          : OutlinedButton(
                              style: OutlinedButton.styleFrom(
                                minimumSize: const Size(0, 40),
                                padding: const EdgeInsets.symmetric(horizontal: 14),
                                side: const BorderSide(color: AppColors.ink, width: 1.5),
                                foregroundColor: AppColors.ink,
                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                              ),
                              onPressed: () => _review(context, ref, h),
                              child: const Text('Review'),
                            ),
                    ]),
                  ),
              ],
      ),
    );
  }
}
