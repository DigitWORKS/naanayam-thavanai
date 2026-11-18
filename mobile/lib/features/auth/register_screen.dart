import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../../widgets/form_widgets.dart';
import 'auth_controller.dart';
import 'auth_models.dart';

/// Design board 9: self-registration on the 30-day free trial. On success the owner is signed in,
/// which swaps the whole app to the owner shell (this route is dropped with the old navigator).
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _form = GlobalKey<FormState>();
  final _business = TextEditingController();
  final _licence = TextEditingController();
  final _cap = TextEditingController(text: '24');
  final _owner = TextEditingController();
  final _phone = TextEditingController();
  final _pin = TextEditingController();
  final _pin2 = TextEditingController();
  bool _accepted = false;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_business, _licence, _cap, _owner, _phone, _pin, _pin2]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    if (!_accepted) {
      setState(() =>
          _error = 'Please accept the terms of service and privacy policy.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).register(RegisterInput(
            businessName: _business.text.trim(),
            licenceNo: _licence.text.trim(),
            maxInterestRate: Money.parse(_cap.text).toApi(),
            ownerName: _owner.text.trim(),
            phone: PhoneField.digits(_phone),
            pin: _pin.text,
          ));
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() => _error = switch (e.code) {
            'PHONE_IN_USE' =>
              'This mobile number is already registered. Log in instead.',
            'TENANT_EXISTS' =>
              'A finance with this number is already registered.',
            'TOO_MANY_REQUESTS' =>
              'Too many attempts. Please try again in an hour.',
            _ => e.message,
          });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String? _required(String? v) => (v ?? '').trim().isEmpty ? 'Required' : null;

  @override
  Widget build(BuildContext context) {
    final trialEnds = DateFormat('d MMM yyyy')
        .format(DateTime.now().add(const Duration(days: 30)));

    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 72,
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Register your finance', style: AppTheme.heading(20)),
          const Text('Takes 2 minutes · start with a free trial',
              style: TextStyle(
                  fontSize: 13,
                  color: AppColors.muted,
                  fontWeight: FontWeight.w500)),
        ]),
      ),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            FormSection(title: 'BUSINESS', children: [
              const FieldLabel('Business name', required: true),
              TextFormField(
                controller: _business,
                textCapitalization: TextCapitalization.words,
                maxLength: 150,
                decoration: const InputDecoration(counterText: ''),
                validator: _required,
              ),
              const SizedBox(height: 16),
              const FieldLabel('Money-lending licence no.'),
              TextFormField(
                  controller: _licence,
                  maxLength: 50,
                  decoration: const InputDecoration(counterText: '')),
              const FieldHelp('Optional now, can be added later in Settings'),
              const SizedBox(height: 16),
              const FieldLabel('Maximum interest rate you may charge',
                  required: true),
              TextFormField(
                controller: _cap,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [
                  FilteringTextInputFormatter.allow(
                      RegExp(r'^\d{0,3}\.?\d{0,2}'))
                ],
                decoration: const InputDecoration(suffixText: '% per year'),
                validator: (v) {
                  final m = Money.tryParse(v);
                  if (m == null || !m.isPositive || m.paise > 10000)
                    return 'Enter a rate between 0 and 100';
                  return null;
                },
              ),
              const FieldHelp(
                  'As allowed by your licence. The app blocks loans above this rate.'),
            ]),
            const SizedBox(height: 12),
            FormSection(title: 'YOUR LOGIN', children: [
              const FieldLabel('Your name', required: true),
              TextFormField(
                controller: _owner,
                textCapitalization: TextCapitalization.words,
                maxLength: 100,
                decoration: const InputDecoration(counterText: ''),
                validator: _required,
              ),
              const SizedBox(height: 16),
              const FieldLabel('Mobile number', required: true),
              PhoneField(controller: _phone),
              const FieldHelp('You will log in with this number'),
              const SizedBox(height: 16),
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const FieldLabel('Create PIN', required: true),
                        _pinField(
                            _pin,
                            (v) => RegExp(r'^\d{4,6}$').hasMatch(v ?? '')
                                ? null
                                : '4 to 6 digits'),
                      ]),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const FieldLabel('Confirm PIN', required: true),
                        _pinField(_pin2,
                            (v) => v == _pin.text ? null : 'PINs do not match'),
                      ]),
                ),
              ]),
              const FieldHelp('4 to 6 digits'),
            ]),
            const SizedBox(height: 12),
            FormSection(title: 'PLAN', children: [
              const _PlanOption(
                  name: 'Free trial',
                  detail: 'All features for 30 days',
                  price: '₹0',
                  selected: true),
              const SizedBox(height: 8),
              const _PlanOption(
                  name: 'Monthly', detail: 'Choose after your trial'),
              const SizedBox(height: 8),
              const _PlanOption(
                  name: 'Yearly', detail: 'Choose after your trial'),
              const SizedBox(height: 12),
              Row(children: [
                const Icon(Icons.event_available,
                    size: 16, color: AppColors.brand),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                      'Free until $trialEnds · pick Monthly or Yearly before it ends',
                      style: const TextStyle(
                          fontSize: 13, fontWeight: FontWeight.w500)),
                ),
              ]),
            ]),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(14)),
              child: CheckboxListTile(
                value: _accepted,
                onChanged: _busy
                    ? null
                    : (v) => setState(() => _accepted = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
                activeColor: AppColors.brand,
                title: const Text(
                  'I agree to the Terms of service and Privacy policy. DigitWORKS provides the software only; '
                  'I am responsible for my money-lending licence and lending practices.',
                  style: TextStyle(fontSize: 13, height: 1.5),
                ),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style: const TextStyle(
                      color: AppColors.error, fontWeight: FontWeight.w600)),
            ],
          ],
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            FilledButton(
              onPressed: _busy ? null : _submit,
              child: _busy
                  ? const SizedBox.square(
                      dimension: 22,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: Colors.white))
                  : const Text('Create account'),
            ),
            TextButton(
              onPressed: _busy ? null : () => Navigator.of(context).pop(),
              child: const Text('Already registered? Log in'),
            ),
          ]),
        ),
      ),
    );
  }

  Widget _pinField(
          TextEditingController c, FormFieldValidator<String> validator) =>
      TextFormField(
        controller: c,
        obscureText: true,
        keyboardType: TextInputType.number,
        inputFormatters: [
          FilteringTextInputFormatter.digitsOnly,
          LengthLimitingTextInputFormatter(6)
        ],
        decoration: const InputDecoration(errorMaxLines: 2),
        validator: validator,
      );
}

/// Plan row. Only the free trial is selectable during self-registration.
class _PlanOption extends StatelessWidget {
  const _PlanOption(
      {required this.name,
      required this.detail,
      this.price,
      this.selected = false});

  final String name;
  final String detail;
  final String? price;
  final bool selected;

  @override
  Widget build(BuildContext context) => Container(
        constraints: const BoxConstraints(minHeight: 64),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: selected ? AppColors.brandTint : AppColors.background,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
              color: selected ? AppColors.brand : AppColors.border,
              width: selected ? 2 : 1),
        ),
        child: Row(children: [
          Icon(selected ? Icons.radio_button_checked : Icons.radio_button_off,
              color: selected ? AppColors.brand : AppColors.border),
          const SizedBox(width: 12),
          Expanded(
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(name,
                  style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: selected ? AppColors.ink : AppColors.muted)),
              Text(detail,
                  style: const TextStyle(fontSize: 12, color: AppColors.muted)),
            ]),
          ),
          if (price != null)
            Text(price!,
                style:
                    const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
        ]),
      );
}
