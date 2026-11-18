import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';

import '../theme/app_theme.dart';

/// Field label above an input, with an optional required star.
class FieldLabel extends StatelessWidget {
  const FieldLabel(this.text, {super.key, this.required = false});

  final String text;
  final bool required;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text.rich(TextSpan(
          text: text,
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.ink),
          children: [if (required) const TextSpan(text: ' *', style: TextStyle(color: AppColors.error))],
        )),
      );
}

/// Small grey help text under a field.
class FieldHelp extends StatelessWidget {
  const FieldHelp(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 6),
        child: Text(text, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
      );
}

/// White rounded section with an uppercase heading (register / settings forms).
class FormSection extends StatelessWidget {
  const FormSection({super.key, required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title,
                style: GoogleFonts.spaceGrotesk(
                    fontSize: 13, fontWeight: FontWeight.w700, letterSpacing: 1, color: AppColors.muted)),
            const SizedBox(height: 14),
            ...children,
          ],
        ),
      );
}

/// Indian mobile number: "+91 | 97896 11197". Read the value with [PhoneField.digits].
class PhoneField extends StatelessWidget {
  const PhoneField({super.key, required this.controller, this.textInputAction, this.enabled = true});

  final TextEditingController controller;
  final TextInputAction? textInputAction;
  final bool enabled;

  static String digits(TextEditingController c) => c.text.replaceAll(RegExp(r'\D'), '');

  static String? validate(String? v) =>
      RegExp(r'^[6-9]\d{9}$').hasMatch((v ?? '').replaceAll(' ', '')) ? null : 'Enter a valid 10-digit mobile number';

  @override
  Widget build(BuildContext context) => TextFormField(
        controller: controller,
        enabled: enabled,
        keyboardType: TextInputType.phone,
        textInputAction: textInputAction,
        autofillHints: const [AutofillHints.telephoneNumberNational],
        inputFormatters: [_IndianMobileFormatter()],
        style: GoogleFonts.inter(fontSize: 17, fontWeight: FontWeight.w500, letterSpacing: 0.7),
        decoration: InputDecoration(
          hintText: '98765 43210',
          prefixIcon: Padding(
            padding: const EdgeInsets.only(left: 16, right: 10),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Text('+91', style: GoogleFonts.inter(fontSize: 17, fontWeight: FontWeight.w600, color: AppColors.muted)),
              const SizedBox(width: 10),
              Container(width: 1, height: 24, color: AppColors.divider),
            ]),
          ),
          prefixIconConstraints: const BoxConstraints(minHeight: 56),
        ),
        validator: validate,
      );
}

/// Keeps up to 10 digits and shows them as "98765 43210".
class _IndianMobileFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    var d = newValue.text.replaceAll(RegExp(r'\D'), '');
    if (d.length > 10) d = d.substring(0, 10);
    final text = d.length > 5 ? '${d.substring(0, 5)} ${d.substring(5)}' : d;
    return TextEditingValue(text: text, selection: TextSelection.collapsed(offset: text.length));
  }
}

/// PIN entry drawn as [length] boxes with dots (login design). A transparent text field sits on
/// top of the boxes, so taps, the numeric keyboard and backspace all work natively.
class PinBoxes extends StatefulWidget {
  const PinBoxes({super.key, required this.controller, this.length = 6, this.onSubmitted, this.hasError = false});

  final TextEditingController controller;
  final int length;
  final ValueChanged<String>? onSubmitted;
  final bool hasError;

  @override
  State<PinBoxes> createState() => _PinBoxesState();
}

class _PinBoxesState extends State<PinBoxes> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  void _changed() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final filled = widget.controller.text.length;
    return SizedBox(
      height: 56,
      child: Stack(children: [
        Row(children: [
          for (var i = 0; i < widget.length; i++) ...[
            if (i > 0) const SizedBox(width: 8),
            Expanded(
              child: Container(
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    width: 1.5,
                    color: widget.hasError
                        ? AppColors.error
                        : i < filled
                            ? AppColors.brand
                            : AppColors.border,
                  ),
                ),
                child: i < filled
                    ? Container(
                        width: 12,
                        height: 12,
                        decoration: const BoxDecoration(color: AppColors.ink, shape: BoxShape.circle))
                    : null,
              ),
            ),
          ],
        ]),
        Positioned.fill(
          child: TextField(
            controller: widget.controller,
            keyboardType: TextInputType.number,
            obscureText: true,
            showCursor: false,
            enableInteractiveSelection: false,
            autocorrect: false,
            enableSuggestions: false,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(widget.length)],
            style: const TextStyle(color: Colors.transparent, fontSize: 1),
            decoration: const InputDecoration(
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              filled: false,
              counterText: '',
              contentPadding: EdgeInsets.zero,
            ),
            onSubmitted: widget.onSubmitted,
          ),
        ),
      ]),
    );
  }
}
