import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import '../../widgets/form_widgets.dart';
import 'auth_controller.dart';

/// Owner deletes the business account (Google Play account-deletion requirement). The business closes at once;
/// its data is erased 30 days later unless DigitWORKS support restores it before then.
class DeleteAccountScreen extends ConsumerStatefulWidget {
  const DeleteAccountScreen({super.key});

  @override
  ConsumerState<DeleteAccountScreen> createState() => _DeleteAccountScreenState();
}

class _DeleteAccountScreenState extends ConsumerState<DeleteAccountScreen> {
  final _pin = TextEditingController();
  bool _understood = false;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!RegExp(r'^\d{4,6}$').hasMatch(_pin.text)) {
      setState(() => _error = 'Enter your PIN (4 to 6 digits).');
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete business account?'),
        content: const Text('Everyone in your business is signed out now. '
            'All data is erased after 30 days and cannot be recovered after that.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;

    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      // On success the app returns to the login screen with the erase date.
      await ref.read(authControllerProvider.notifier).deleteAccount(_pin.text);
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = switch (e.code) {
          'WRONG_PIN' => 'PIN is incorrect.',
          'TOO_MANY_ATTEMPTS' => 'Too many attempts. Try again in 15 minutes.',
          _ => e.message,
        };
        if (e.code == 'WRONG_PIN') _pin.clear();
      });
    }
  }

  static const _deleted = [
    'Your login and every agent login',
    'Areas, clients and their documents',
    'Loans, schedules, payments and handovers',
    'Business settings and plan',
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Delete account', style: AppTheme.heading(22))),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFFFDECEA),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('This deletes your whole business account',
                    style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.error, fontSize: 16)),
                const SizedBox(height: 10),
                for (final d in _deleted)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const Text('•  ', style: TextStyle(color: AppColors.ink)),
                      Expanded(child: Text(d)),
                    ]),
                  ),
              ]),
            ),
            const SizedBox(height: 16),
            const Text(
              'Everyone is signed out straight away and nobody can log in. All data is erased after 30 days. '
              'Until then DigitWORKS support can restore the account if you ask. '
              'Keep any records you need for tax or legal purposes before you delete.',
              style: TextStyle(color: AppColors.muted, height: 1.45),
            ),
            const SizedBox(height: 20),
            CheckboxListTile(
              value: _understood,
              onChanged: _busy ? null : (v) => setState(() => _understood = v ?? false),
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
              title: const Text('I understand that all business data will be erased'),
            ),
            const SizedBox(height: 12),
            const FieldLabel('Your PIN'),
            PinBoxes(controller: _pin, hasError: _error != null),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
            ],
            const SizedBox(height: 28),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: AppColors.error),
              onPressed: _busy || !_understood ? null : _submit,
              child: _busy
                  ? const SizedBox.square(
                      dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                  : const Text('Delete business account'),
            ),
          ],
        ),
      ),
    );
  }
}
