import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import '../../widgets/form_widgets.dart';
import 'auth_controller.dart';

/// Change own PIN. Other phones signed in as this user are signed out; this one stays signed in.
class ChangePinScreen extends ConsumerStatefulWidget {
  const ChangePinScreen({super.key});

  @override
  ConsumerState<ChangePinScreen> createState() => _ChangePinScreenState();
}

class _ChangePinScreenState extends ConsumerState<ChangePinScreen> {
  static final _pinRule = RegExp(r'^\d{4,6}$');

  final _current = TextEditingController();
  final _next = TextEditingController();
  final _confirm = TextEditingController();
  bool _busy = false;
  String? _error;

  /// Which box the error belongs to: 'current', 'next' or 'confirm'.
  String? _errorAt;

  @override
  void dispose() {
    _current.dispose();
    _next.dispose();
    _confirm.dispose();
    super.dispose();
  }

  void _fail(String at, String message) => setState(() {
        _errorAt = at;
        _error = message;
      });

  Future<void> _submit() async {
    if (!_pinRule.hasMatch(_current.text)) return _fail('current', 'Enter your current PIN (4 to 6 digits).');
    if (!_pinRule.hasMatch(_next.text)) return _fail('next', 'New PIN must be 4 to 6 digits.');
    if (_next.text == _current.text) return _fail('next', 'Choose a PIN different from the current one.');
    if (_confirm.text != _next.text) return _fail('confirm', 'The new PINs do not match.');

    setState(() {
      _busy = true;
      _error = null;
      _errorAt = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).changePin(_current.text, _next.text);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('PIN changed. Other phones using this login are signed out.')),
      );
      Navigator.of(context).pop();
    } on ApiError catch (e) {
      if (!mounted) return;
      switch (e.code) {
        case 'WRONG_PIN':
          _current.clear();
          _fail('current', 'Current PIN is incorrect.');
        case 'SAME_PIN':
          _fail('next', 'Choose a PIN different from the current one.');
        case 'TOO_MANY_ATTEMPTS':
          _fail('current', 'Too many attempts. Try again in 15 minutes.');
        default:
          _fail('', e.message);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _box(String label, String key, TextEditingController c, {VoidCallback? onDone}) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          FieldLabel(label),
          PinBoxes(controller: c, hasError: _errorAt == key, onSubmitted: (_) => onDone?.call()),
          if (_errorAt == key && _error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
          ],
          const SizedBox(height: 20),
        ],
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Change PIN', style: AppTheme.heading(22))),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
          children: [
            _box('Current PIN', 'current', _current),
            _box('New PIN', 'next', _next),
            _box('New PIN again', 'confirm', _confirm, onDone: _submit),
            const FieldHelp('4 to 6 digits. Other phones using this login will be signed out.'),
            if (_errorAt == '' && _error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
            ],
            const SizedBox(height: 28),
            FilledButton(
              onPressed: _busy ? null : _submit,
              child: _busy
                  ? const SizedBox.square(
                      dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                  : const Text('Change PIN'),
            ),
          ],
        ),
      ),
    );
  }
}
