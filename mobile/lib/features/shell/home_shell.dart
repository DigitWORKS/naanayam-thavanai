import 'package:flutter/material.dart';

import '../auth/auth_models.dart';
import '../clients/clients_screen.dart';
import '../collections/today_screen.dart';
import '../dashboard/dashboard_screen.dart';
import '../loans/loans_screen.dart';
import 'account_screen.dart';
import 'coming_soon_screen.dart';

/// Signed-in home with the bottom navigation from the designs:
/// owner — Home / Today / Clients / Loans / More; agent — Today / Clients / Handover / More.
/// Tabs are kept alive in an [IndexedStack] so switching back keeps scroll position and data.
class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.user});

  final AppUser user;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _Tab {
  const _Tab(this.label, this.icon, this.selectedIcon, this.screen);

  final String label;
  final IconData icon;
  final IconData selectedIcon;
  final Widget screen;
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;
  late final List<_Tab> _tabs = widget.user.isOwner ? _ownerTabs(widget.user) : _agentTabs(widget.user);

  /// Owners also collect and manage clients themselves, so they get Today and Clients too.
  /// Five tabs is the bottom-bar maximum: Reports and Settings live under More.
  static List<_Tab> _ownerTabs(AppUser u) => [
        _Tab('Home', Icons.home_outlined, Icons.home, DashboardScreen(user: u)),
        _Tab('Today', Icons.today_outlined, Icons.today, TodayScreen(user: u)),
        _Tab('Clients', Icons.people_alt_outlined, Icons.people_alt, ClientsScreen(user: u)),
        _Tab('Loans', Icons.receipt_long_outlined, Icons.receipt_long, LoansScreen(user: u)),
        _Tab(
          'More',
          Icons.more_horiz,
          Icons.more_horiz,
          AccountScreen(user: u, title: 'More', links: const [
            AccountLink('Reports', Icons.bar_chart_outlined,
                ComingSoonScreen(title: 'Reports', note: 'Collections by day / agent / area and interest income.')),
            AccountLink('Settings', Icons.settings_outlined,
                ComingSoonScreen(title: 'Settings', note: 'Interest, penalty, payment order and pre-closure settings.')),
          ]),
        ),
      ];

  static List<_Tab> _agentTabs(AppUser u) => [
        _Tab('Today', Icons.today_outlined, Icons.today, TodayScreen(user: u)),
        _Tab('Clients', Icons.people_alt_outlined, Icons.people_alt, ClientsScreen(user: u)),
        const _Tab('Handover', Icons.account_balance_wallet_outlined, Icons.account_balance_wallet,
            ComingSoonScreen(title: 'Cash handover', note: "Hand over today's cash to the owner.")),
        _Tab('More', Icons.more_horiz, Icons.more_horiz, AccountScreen(user: u, title: 'More')),
      ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _index, children: [for (final t in _tabs) t.screen]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: [
          for (final t in _tabs)
            NavigationDestination(icon: Icon(t.icon), selectedIcon: Icon(t.selectedIcon), label: t.label),
        ],
      ),
    );
  }
}
