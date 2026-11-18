import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import '../payments/record_payment_screen.dart';
import '../sync/payment_queue.dart';
import '../sync/sync_banner.dart';
import 'collections_repository.dart';
import 'due_item.dart';

/// Agent home (design board 2): today's due list with a summary and area filter.
class TodayScreen extends ConsumerStatefulWidget {
  const TodayScreen({super.key, required this.user});

  final AppUser user;

  @override
  ConsumerState<TodayScreen> createState() => _TodayScreenState();
}

class _TodayScreenState extends ConsumerState<TodayScreen> {
  int? _areaId;

  /// Sends anything saved on the phone first, so the list reflects it.
  Future<void> _refresh() async {
    await ref.read(paymentQueueProvider.notifier).sync();
    await ref.refresh(dueListProvider(_areaId).future);
  }

  Future<void> _collect(DueItem item) async {
    final recorded = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => RecordPaymentScreen(item: item)),
    );
    if (recorded == true) ref.invalidate(dueListProvider);
  }

  @override
  Widget build(BuildContext context) {
    // Keeps the offline queue alive (and syncing) for the whole session; Today is built at login.
    ref.watch(paymentQueueProvider.select((q) => q.isEmpty));
    final due = ref.watch(dueListProvider(_areaId));
    final firstName = widget.user.name.split(' ').first;

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 72,
        titleSpacing: 16,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${DateFormat('EEE, d MMM').format(DateTime.now())} · $firstName',
                style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500)),
            Text("Today's collections", style: AppTheme.heading(22)),
          ],
        ),
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: due.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => _ErrorView(
            message: e is ApiError ? e.message : 'Could not load the list.',
            onRetry: () => ref.invalidate(dueListProvider(_areaId)),
          ),
          data: (items) => _list(items),
        ),
      ),
    );
  }

  Widget _list(List<DueItem> items) {
    final queue = ref.watch(paymentQueueProvider);
    // Loans still to collect first; ones settled today at the bottom.
    final ordered = [...items.where((i) => !i.isSettledToday), ...items.where((i) => i.isSettledToday)];
    // Owners see every area; agents only their assigned ones.
    final areas = widget.user.isOwner
        ? switch (ref.watch(activeAreasProvider)) {
            AsyncData(:final value) => value,
            _ => const <AreaRef>[],
          }
        : widget.user.areas;

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: [
        const SyncBanner(),
        _Summary(items: items),
        if (areas.length > 1) ...[
          const SizedBox(height: 12),
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: [
                _AreaChip(label: 'All areas', selected: _areaId == null, onTap: () => setState(() => _areaId = null)),
                for (final a in areas)
                  _AreaChip(label: a.name, selected: _areaId == a.id, onTap: () => setState(() => _areaId = a.id)),
              ],
            ),
          ),
        ],
        const SizedBox(height: 12),
        if (ordered.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 48),
            child: Text('Nothing due today.', textAlign: TextAlign.center, style: TextStyle(color: AppColors.muted)),
          ),
        for (final item in ordered) ...[
          _DueTile(item: item, onCollect: () => _collect(item), savedOnPhone: queue.waitingFor(item.loanId)),
          const SizedBox(height: 8),
        ],
      ],
    );
  }
}

class _Summary extends StatelessWidget {
  const _Summary({required this.items});

  final List<DueItem> items;

  @override
  Widget build(BuildContext context) {
    final left = items.map((i) => i.totalDue).sum();
    final collected = items.map((i) => i.collectedToday).sum();
    final target = left + collected;
    final loansLeft = items.where((i) => !i.isSettledToday).length;
    final progress = target.isPositive ? (collected.paise / target.paise).clamp(0.0, 1.0) : 0.0;

    Widget stat(String label, String value, {Color color = Colors.white}) => Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(fontSize: 12, color: Color(0xFFB8C7C1), fontWeight: FontWeight.w500)),
              const SizedBox(height: 2),
              Text(value, style: GoogleFonts.spaceGrotesk(fontSize: 20, fontWeight: FontWeight.w700, color: color)),
            ],
          ),
        );

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: AppColors.ink, borderRadius: BorderRadius.circular(16)),
      child: Column(
        children: [
          Row(children: [
            stat('Target', target.format()),
            stat('Collected', collected.format(), color: AppColors.gold),
            stat('Left', '$loansLeft loans'),
          ]),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 6,
              backgroundColor: const Color(0xFF2A3D35),
              color: AppColors.gold,
            ),
          ),
        ],
      ),
    );
  }
}

class _AreaChip extends StatelessWidget {
  const _AreaChip({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ChoiceChip(
          label: Text(label),
          selected: selected,
          onSelected: (_) => onTap(),
          selectedColor: AppColors.brandTint,
          side: BorderSide(color: selected ? AppColors.brand : AppColors.border),
          showCheckmark: false,
        ),
      );
}

class _DueTile extends StatelessWidget {
  const _DueTile({required this.item, required this.onCollect, required this.savedOnPhone});

  final DueItem item;
  final VoidCallback onCollect;

  /// Collected on this phone but not on the server yet (not in [item]'s figures).
  final Money savedOnPhone;

  @override
  Widget build(BuildContext context) {
    final behind = item.overdueCount > 0;
    final subtitle = item.isSettledToday
        ? '${item.areaName} · Collected ${item.collectedToday.format()}'
        : '${item.areaName} · Today ${item.todayDue.format()}';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Flexible(
                      child: Text(item.clientName,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                    ),
                    const SizedBox(width: 8),
                    Text(item.loanNo, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
                  ]),
                  const SizedBox(height: 4),
                  Text(subtitle, style: const TextStyle(fontSize: 13, color: AppColors.muted)),
                  if (savedOnPhone.isPositive) ...[
                    const SizedBox(height: 6),
                    _Tag('${savedOnPhone.format()} saved on phone'),
                  ],
                  if (!item.isSettledToday && (behind || item.penaltyDue.isPositive)) ...[
                    const SizedBox(height: 6),
                    Wrap(spacing: 6, runSpacing: 4, children: [
                      if (behind)
                        _Tag('${item.overdueCount} ${item.overdueCount == 1 ? 'day' : 'days'} behind · '
                            '${item.overdueAmount.format()}'),
                      if (item.penaltyDue.isPositive) _Tag('Penalty ${item.penaltyDue.format()}'),
                    ]),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 12),
            if (item.isSettledToday)
              const Icon(Icons.check_circle, color: AppColors.brand, size: 28, semanticLabel: 'Paid today')
            else
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(item.totalDue.format(), style: GoogleFonts.spaceGrotesk(fontSize: 18, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 6),
                  SizedBox(
                    height: 40,
                    child: FilledButton(
                      style: FilledButton.styleFrom(minimumSize: const Size(88, 40), textStyle: const TextStyle(fontSize: 14)),
                      onPressed: onCollect,
                      child: const Text('Collect'),
                    ),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}

class _Tag extends StatelessWidget {
  const _Tag(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(color: AppColors.warningTint, borderRadius: BorderRadius.circular(6)),
        child: Text(text, style: const TextStyle(fontSize: 12, color: AppColors.warning, fontWeight: FontWeight.w600)),
      );
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(32),
        children: [
          const SizedBox(height: 80),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          Center(child: OutlinedButton(onPressed: onRetry, child: const Text('Try again'))),
        ],
      );
}
