import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import 'auth_controller.dart';

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
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).login(_phone.text, _pin.text);
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() => _error = e.status == 401 ? 'Wrong mobile number or PIN.' : e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const _Brand(),
                  const SizedBox(height: 40),
                  const _Label('Mobile number'),
                  TextFormField(
                    controller: _phone,
                    keyboardType: TextInputType.phone,
                    textInputAction: TextInputAction.next,
                    autofillHints: const [AutofillHints.telephoneNumber],
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(10)],
                    style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w600, letterSpacing: 1),
                    decoration: const InputDecoration(
                      prefixText: '+91  ',
                      hintText: '10-digit number',
                    ),
                    validator: (v) =>
                        RegExp(r'^[6-9]\d{9}$').hasMatch(v ?? '') ? null : 'Enter a valid 10-digit mobile number',
                  ),
                  const SizedBox(height: 20),
                  const _Label('PIN'),
                  TextFormField(
                    controller: _pin,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    textInputAction: TextInputAction.done,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
                    style: GoogleFonts.inter(fontSize: 22, fontWeight: FontWeight.w700, letterSpacing: 8),
                    decoration: const InputDecoration(hintText: '4–6 digits'),
                    validator: (v) => RegExp(r'^\d{4,6}$').hasMatch(v ?? '') ? null : 'PIN is 4 to 6 digits',
                    onFieldSubmitted: (_) => _submit(),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
                  ],
                  const SizedBox(height: 28),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: _busy
                        ? const SizedBox.square(
                            dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                        : const Text('Log in'),
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    'Forgot your PIN? Ask your financier to reset it.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.muted),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Brand extends StatelessWidget {
  const _Brand();

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 72,
          height: 72,
          alignment: Alignment.center,
          decoration: const BoxDecoration(color: AppColors.gold, shape: BoxShape.circle),
          child: Text('நா', style: GoogleFonts.notoSansTamil(fontSize: 28, fontWeight: FontWeight.w700, color: AppColors.ink)),
        ),
        const SizedBox(height: 16),
        Text('நாணயம் தவணை', style: GoogleFonts.notoSansTamil(fontSize: 24, fontWeight: FontWeight.w700)),
        const SizedBox(height: 4),
        Text('NAANAYAM THAVANAI',
            style: GoogleFonts.spaceGrotesk(fontSize: 13, fontWeight: FontWeight.w600, letterSpacing: 2, color: AppColors.muted)),
      ],
    );
  }
}

class _Label extends StatelessWidget {
  const _Label(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(text, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
      );
}
