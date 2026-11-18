import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../theme/app_theme.dart';
import '../auth/auth_controller.dart';
import '../auth/auth_models.dart';
import '../sync/payment_queue.dart';

/// Signed-in user and log out. Used as the agent's "More" tab and, until business settings are
/// built, the owner's "Settings" tab.
class AccountScreen extends ConsumerWidget {
  const AccountScreen({super.key, required this.user, required this.title, this.note, this.links = const []});

  final AppUser user;
  final String title;
  final String? note;

  /// Extra entries shown under the profile (e.g. the owner's Reports and Settings).
  final List<AccountLink> links;

  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final unsent = ref.read(paymentQueueProvider).items.length;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Log out?'),
        content: Text(unsent == 0
            ? 'You will need your mobile number and PIN to log in again.'
            : '$unsent ${unsent == 1 ? 'payment is' : 'payments are'} saved on this phone and not sent yet. '
                'They stay on the phone and are sent the next time you log in here. '
                'Connect to the internet and sync first if you can.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Log out')),
        ],
      ),
    );
    if (ok == true) await ref.read(authControllerProvider.notifier).logout();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final phone = user.phone.length == 10 ? '+91 ${user.phone.substring(0, 5)} ${user.phone.substring(5)}' : user.phone;
    final role = user.isOwner ? 'Owner' : 'Agent';

    return Scaffold(
      appBar: AppBar(title: Text(title, style: AppTheme.heading(22))),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              leading: CircleAvatar(
                backgroundColor: AppColors.brandTint,
                foregroundColor: AppColors.brand,
                child: Text(user.name.isEmpty ? '?' : user.name[0].toUpperCase()),
              ),
              title: Text(user.name, style: const TextStyle(fontWeight: FontWeight.w600)),
              subtitle: Text('$role · $phone'),
            ),
          ),
          if (links.isNotEmpty) ...[
            const SizedBox(height: 12),
            Card(
              child: Column(children: [
                for (final l in links)
                  ListTile(
                    leading: Icon(l.icon),
                    title: Text(l.label),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => l.screen)),
                  ),
              ]),
            ),
          ],
          if (!user.isOwner && user.areas.isNotEmpty) ...[
            const SizedBox(height: 12),
            Card(
              child: ListTile(
                leading: const Icon(Icons.place_outlined),
                title: const Text('My areas'),
                subtitle: Text(user.areas.map((a) => a.name).join(', ')),
              ),
            ),
          ],
          if (note != null) ...[
            const SizedBox(height: 16),
            Text(note!, style: const TextStyle(color: AppColors.muted)),
          ],
          const SizedBox(height: 24),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
              foregroundColor: AppColors.error,
              side: const BorderSide(color: AppColors.border, width: 1.5),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            onPressed: () => _logout(context, ref),
            icon: const Icon(Icons.logout),
            label: const Text('Log out'),
          ),
        ],
      ),
    );
  }
}

class AccountLink {
  const AccountLink(this.label, this.icon, this.screen);

  final String label;
  final IconData icon;
  final Widget screen;
}
