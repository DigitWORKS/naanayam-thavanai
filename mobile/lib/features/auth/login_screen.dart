import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import '../../widgets/brand_mark.dart';
import '../../widgets/form_widgets.dart';
import 'auth_controller.dart';
import 'register_screen.dart';

/// Design board 1. The Tamil / English switch comes with the Tamil strings.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key, this.notice});

  final String? notice;

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _phone = TextEditingController();
  final _pin = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _error = widget.notice;
  }

  @override
  void dispose() {
    _phone.dispose();
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    if (!RegExp(r'^\d{4,6}$').hasMatch(_pin.text)) {
      setState(() => _error = 'PIN is 4 to 6 digits.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).login(PhoneField.digits(_phone), _pin.text);
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.status == 401 ? 'Wrong mobile number or PIN.' : e.message;
        _pin.clear();
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, box) => SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
            child: ConstrainedBox(
              // Full screen height so the footer sits at the bottom, scrolling when the keyboard is up.
              constraints: BoxConstraints(minHeight: box.maxHeight - 56),
              child: IntrinsicHeight(
                child: Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const SizedBox(height: 40),
                      const BrandMark(),
                      const SizedBox(height: 48),
                      const FieldLabel('Mobile number'),
                      PhoneField(controller: _phone, textInputAction: TextInputAction.next),
                      const SizedBox(height: 20),
                      const FieldLabel('PIN'),
                      PinBoxes(controller: _pin, hasError: _error != null, onSubmitted: (_) => _submit()),
                      if (_error != null) ...[
                        const SizedBox(height: 12),
                        Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
                      ],
                      const SizedBox(height: 28),
                      FilledButton(
                        onPressed: _busy ? null : _submit,
                        child: _busy
                            ? const SizedBox.square(
                                dimension: 22,
                                child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                            : const Text('Log in'),
                      ),
                      const Spacer(),
                      const SizedBox(height: 32),
                      const Text(
                        'Forgot your PIN? Ask your financier to reset it.',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 14, color: AppColors.muted, height: 1.5),
                      ),
                      const SizedBox(height: 12),
                      OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          minimumSize: const Size.fromHeight(52),
                          backgroundColor: AppColors.surface,
                          side: const BorderSide(color: AppColors.border, width: 1.5),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                        onPressed: _busy
                            ? null
                            : () => Navigator.of(context)
                                .push(MaterialPageRoute<void>(builder: (_) => const RegisterScreen())),
                        child: const Text.rich(TextSpan(
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500, color: AppColors.ink),
                          children: [
                            TextSpan(text: 'Run a finance business?  '),
                            TextSpan(text: 'Register', style: TextStyle(fontWeight: FontWeight.w700, color: AppColors.brand)),
                          ],
                        )),
                      ),
                      const SizedBox(height: 12),
                      const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.lock_outline, size: 14, color: AppColors.muted),
                          SizedBox(width: 8),
                          Text('DigitWorks · Secure login',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.muted)),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
