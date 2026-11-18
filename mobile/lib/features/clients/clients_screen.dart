import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import '../auth/auth_models.dart';
import '../collections/collections_repository.dart';
import 'client_detail_screen.dart';
import 'client_models.dart';
import 'clients_repository.dart';
import 'new_client_screen.dart';

/// Design board 7: client search with filters; shared by owners (all areas) and agents (their areas).
class ClientsScreen extends ConsumerStatefulWidget {
  const ClientsScreen({super.key, required this.user});

  final AppUser user;

  @override
  ConsumerState<ClientsScreen> createState() => _ClientsScreenState();
}

class _ClientsScreenState extends ConsumerState<ClientsScreen> {
  final _search = TextEditingController();
  Timer? _debounce;

  ClientFilter _filter = ClientFilter.all;
  int? _areaId;

  final List<ClientListItem> _items = [];
  ClientPage? _last;
  bool _loading = false;
  String? _error;

  /// Ignores responses from superseded searches (typing fast, switching chips).
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _search.dispose();
    super.dispose();
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
      final result = await ref
          .read(clientsRepositoryProvider)
          .search(q: _search.text, areaId: _areaId, filter: _filter, page: page);
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

  void _onSearchChanged(String _) {
    setState(() {}); // show / hide the clear button
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 350), _reload);
  }

  void _setFilter(ClientFilter f) {
    if (_filter == f) return;
    setState(() => _filter = f);
    _reload();
  }

  void _setArea(int? id) {
    setState(() => _areaId = _areaId == id ? null : id);
    _reload();
  }

  Future<void> _openClient(int id) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => ClientDetailScreen(clientId: id, user: widget.user),
    ));
    if (mounted) _reload();
  }

  Future<void> _newClient() async {
    final id = await Navigator.of(context).push<int>(MaterialPageRoute(
      builder: (_) => NewClientScreen(user: widget.user),
      fullscreenDialog: true,
    ));
    if (!mounted) return;
    if (id != null) {
      await _openClient(id);
    } else {
      _reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    final areas = widget.user.isOwner
        ? switch (ref.watch(activeAreasProvider)) {
            AsyncData(:final value) => value,
            _ => const <AreaRef>[],
          }
        : widget.user.areas;
    final total = _last?.totalElements;

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 64,
        titleSpacing: 16,
        title: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Text('Clients', style: AppTheme.heading(24)),
          const SizedBox(width: 10),
          if (total != null)
            Text(
              widget.user.isOwner ? '$total' : '$total in your areas',
              style: const TextStyle(fontSize: 13, color: AppColors.muted, fontWeight: FontWeight.w500),
            ),
        ]),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(112),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Column(children: [
              TextField(
                controller: _search,
                onChanged: _onSearchChanged,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: 'Search name or phone',
                  prefixIcon: const Icon(Icons.search),
                  isDense: true,
                  fillColor: AppColors.background,
                  suffixIcon: _search.text.isEmpty
                      ? null
                      : IconButton(
                          icon: const Icon(Icons.close),
                          tooltip: 'Clear',
                          onPressed: () {
                            _search.clear();
                            _reload();
                          },
                        ),
                ),
              ),
              const SizedBox(height: 10),
              SizedBox(
                height: 40,
                child: ListView(scrollDirection: Axis.horizontal, children: [
                  _chip('All', _filter == ClientFilter.all, () => _setFilter(ClientFilter.all)),
                  _chip('Behind', _filter == ClientFilter.behind, () => _setFilter(ClientFilter.behind)),
                  _chip('No active loan', _filter == ClientFilter.noActiveLoan,
                      () => _setFilter(ClientFilter.noActiveLoan)),
                  if (areas.length > 1)
                    for (final a in areas) _chip(a.name, _areaId == a.id, () => _setArea(a.id), area: true),
                ]),
              ),
            ]),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _newClient,
        backgroundColor: AppColors.brand,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.person_add_alt_1),
        label: const Text('New client', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: RefreshIndicator(onRefresh: _reload, child: _body()),
    );
  }

  Widget _body() {
    if (_items.isEmpty && _loading) return const Center(child: CircularProgressIndicator());
    if (_items.isEmpty && _error != null) {
      return ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(32), children: [
        const SizedBox(height: 60),
        Text(_error!, textAlign: TextAlign.center),
        const SizedBox(height: 16),
        Center(child: OutlinedButton(onPressed: _reload, child: const Text('Try again'))),
      ]);
    }
    if (_items.isEmpty) {
      return ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(32), children: [
        const SizedBox(height: 60),
        Text(
          _search.text.isNotEmpty || _filter != ClientFilter.all || _areaId != null
              ? 'No clients match.'
              : 'No clients yet. Tap "New client" to add one.',
          textAlign: TextAlign.center,
          style: const TextStyle(color: AppColors.muted),
        ),
      ]);
    }

    final hasMore = _last?.hasMore ?? false;
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(0, 4, 0, 96),
      itemCount: _items.length + (hasMore ? 1 : 0),
      separatorBuilder: (_, __) => const Divider(height: 1, indent: 72, color: AppColors.divider),
      itemBuilder: (context, i) {
        if (i == _items.length) {
          return Padding(
            padding: const EdgeInsets.all(16),
            child: Center(
              child: _loading
                  ? const CircularProgressIndicator()
                  : OutlinedButton(onPressed: () => _load(reset: false), child: const Text('Load more')),
            ),
          );
        }
        final c = _items[i];
        return _ClientRow(client: c, onTap: () => _openClient(c.id));
      },
    );
  }

  Widget _chip(String label, bool selected, VoidCallback onTap, {bool area = false}) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: ChoiceChip(
          label: Text(label),
          selected: selected,
          onSelected: (_) => onTap(),
          showCheckmark: false,
          avatar: area ? const Icon(Icons.place_outlined, size: 16) : null,
          selectedColor: area ? AppColors.brandTint : AppColors.ink,
          labelStyle: TextStyle(
            fontWeight: FontWeight.w600,
            color: selected && !area ? Colors.white : AppColors.ink,
          ),
          side: BorderSide(color: selected ? (area ? AppColors.brand : AppColors.ink) : AppColors.border),
        ),
      );
}

class _ClientRow extends StatelessWidget {
  const _ClientRow({required this.client, required this.onTap});

  final ClientListItem client;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = client;
    final behind = c.overdueAmount.isPositive;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(children: [
          InitialsAvatar(name: c.name),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(c.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text('${formatPhone(c.phone)} · ${c.areaName}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13, color: AppColors.muted)),
            ]),
          ),
          const SizedBox(width: 8),
          if (c.activeLoans == 0)
            const Text('No active loan', style: TextStyle(fontSize: 13, color: AppColors.muted))
          else
            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Text(c.outstanding.format(), style: GoogleFonts.spaceGrotesk(fontSize: 16, fontWeight: FontWeight.w700)),
              const SizedBox(height: 2),
              Text(
                behind
                    ? '${c.overdueAmount.format()} behind'
                    : '${c.activeLoans} ${c.activeLoans == 1 ? 'loan' : 'loans'}',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: behind ? AppColors.warning : AppColors.muted,
                ),
              ),
            ]),
        ]),
      ),
    );
  }
}

/// Round avatar with the client's initials.
class InitialsAvatar extends StatelessWidget {
  const InitialsAvatar({super.key, required this.name, this.size = 44});

  final String name;
  final double size;

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: const BoxDecoration(color: AppColors.brandTint, shape: BoxShape.circle),
        child: Text(
          initialsOf(name),
          style: TextStyle(fontSize: size * 0.34, fontWeight: FontWeight.w700, color: AppColors.brand),
        ),
      );
}
