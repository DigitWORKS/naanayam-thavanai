import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../theme/app_theme.dart';
import '../../widgets/form_widgets.dart';
import '../auth/auth_models.dart';
import '../collections/collections_repository.dart';
import 'client_detail_screen.dart';
import 'client_models.dart';
import 'clients_repository.dart';

/// Design board 8. Pops with a [NewClientResult]. Photos / documents are added in a follow-up.
class NewClientScreen extends ConsumerStatefulWidget {
  const NewClientScreen({super.key, required this.user});

  final AppUser user;

  @override
  ConsumerState<NewClientScreen> createState() => _NewClientScreenState();
}

class _NewClientScreenState extends ConsumerState<NewClientScreen> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _alt = TextEditingController();
  final _address = TextEditingController();
  final _aadhaar = TextEditingController();
  final _gName = TextEditingController();
  final _gPhone = TextEditingController();

  int? _areaId;
  bool _consent = false;
  bool _busy = false;
  String? _error;

  /// Set from a 409 CLIENT_EXISTS: shown under the mobile field.
  _Existing? _existing;

  @override
  void initState() {
    super.initState();
    if (!widget.user.isOwner && widget.user.areas.length == 1) _areaId = widget.user.areas.first.id;
  }

  @override
  void dispose() {
    for (final c in [_name, _phone, _alt, _address, _aadhaar, _gName, _gPhone]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save({bool giveLoan = false}) async {
    setState(() => _existing = null);
    if (!_form.currentState!.validate()) return;
    if (!_consent) {
      setState(() => _error = "The client's consent is required to save their details.");
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final id = await ref.read(clientsRepositoryProvider).create(NewClientInput(
            areaId: _areaId!,
            name: _name.text,
            phone: PhoneField.digits(_phone),
            altPhone: PhoneField.digits(_alt),
            address: _address.text,
            aadhaarLast4: _aadhaar.text,
            guarantorName: _gName.text,
            guarantorPhone: PhoneField.digits(_gPhone),
          ));
      if (mounted) Navigator.of(context).pop(NewClientResult(id, giveLoan: giveLoan));
    } on ApiError catch (e) {
      if (!mounted) return;
      setState(() {
        if (e.code == 'CLIENT_EXISTS') {
          _existing = _Existing.fromDetails(e.details);
        } else {
          _error = e.message;
        }
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _openExisting(_Existing x) => Navigator.of(context).pushReplacement(MaterialPageRoute<void>(
        builder: (_) => ClientDetailScreen(clientId: x.clientId, user: widget.user),
      ));

  static String? _optionalPhone(String? v) {
    final d = (v ?? '').replaceAll(' ', '');
    return d.isEmpty ? null : PhoneField.validate(d);
  }

  @override
  Widget build(BuildContext context) {
    final areas = widget.user.isOwner
        ? switch (ref.watch(activeAreasProvider)) {
            AsyncData(:final value) => value,
            _ => const <AreaRef>[],
          }
        : widget.user.areas;

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.close),
          tooltip: 'Close',
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
        ),
        title: Text('New client', style: AppTheme.heading(20)),
      ),
      body: Form(
        key: _form,
        child: ListView(padding: const EdgeInsets.all(16), children: [
          FormSection(title: 'CLIENT', children: [
            const FieldLabel('Full name', required: true),
            TextFormField(
              controller: _name,
              textCapitalization: TextCapitalization.words,
              maxLength: 100,
              decoration: const InputDecoration(counterText: ''),
              validator: (v) => (v ?? '').trim().isEmpty ? 'Required' : null,
            ),
            const SizedBox(height: 16),
            const FieldLabel('Mobile number', required: true),
            PhoneField(controller: _phone),
            if (_existing != null) _ExistingNotice(existing: _existing!, onOpen: () => _openExisting(_existing!)),
            const SizedBox(height: 16),
            const FieldLabel('Other number'),
            TextFormField(
              controller: _alt,
              keyboardType: TextInputType.phone,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(10)],
              decoration: const InputDecoration(hintText: 'Optional'),
              validator: _optionalPhone,
            ),
            const SizedBox(height: 16),
            const FieldLabel('Area', required: true),
            DropdownButtonFormField<int>(
              initialValue: _areaId,
              items: [for (final a in areas) DropdownMenuItem(value: a.id, child: Text(a.name))],
              onChanged: _busy ? null : (v) => setState(() => _areaId = v),
              validator: (v) => v == null ? 'Choose an area' : null,
              hint: Text(areas.isEmpty ? 'No areas yet' : 'Choose area'),
            ),
            if (!widget.user.isOwner) const FieldHelp('Only your assigned areas are listed'),
            const SizedBox(height: 16),
            const FieldLabel('Address'),
            TextFormField(
              controller: _address,
              minLines: 2,
              maxLines: 3,
              maxLength: 500,
              decoration: const InputDecoration(hintText: 'Door no, street, landmark', counterText: ''),
            ),
            const SizedBox(height: 16),
            const FieldLabel('Aadhaar · last 4 digits'),
            TextFormField(
              controller: _aadhaar,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(4)],
              decoration: const InputDecoration(prefixText: 'XXXX XXXX  '),
              validator: (v) => (v ?? '').isEmpty || v!.length == 4 ? null : 'Enter exactly 4 digits',
            ),
            const FieldHelp('Never enter the full Aadhaar number'),
          ]),
          const SizedBox(height: 12),
          FormSection(title: 'GUARANTOR (OPTIONAL)', children: [
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  const FieldLabel('Name'),
                  TextFormField(
                    controller: _gName,
                    textCapitalization: TextCapitalization.words,
                    maxLength: 100,
                    decoration: const InputDecoration(counterText: ''),
                  ),
                ]),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  const FieldLabel('Mobile'),
                  TextFormField(
                    controller: _gPhone,
                    keyboardType: TextInputType.phone,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(10)],
                    validator: _optionalPhone,
                  ),
                ]),
              ),
            ]),
          ]),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(color: AppColors.surface, borderRadius: BorderRadius.circular(14)),
            child: CheckboxListTile(
              value: _consent,
              onChanged: _busy ? null : (v) => setState(() => _consent = v ?? false),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
              activeColor: AppColors.brand,
              title: const Text.rich(TextSpan(children: [
                TextSpan(text: 'Client agrees', style: TextStyle(fontWeight: FontWeight.w600)),
                TextSpan(text: ' *', style: TextStyle(color: AppColors.error)),
              ])),
              subtitle: const Padding(
                padding: EdgeInsets.only(top: 4),
                child: Text(
                  'I allow this finance to store my details and documents for my loans and to send me payment SMS.\n'
                  'இந்தக் கடனுக்காக என் விவரங்களையும் ஆவணங்களையும் சேமிக்கவும், பணம் செலுத்திய SMS அனுப்பவும் ஒப்புக்கொள்கிறேன்.',
                  style: TextStyle(fontSize: 13, height: 1.5, color: AppColors.ink),
                ),
              ),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: const TextStyle(color: AppColors.error, fontWeight: FontWeight.w600)),
          ],
        ]),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: Row(children: [
            Expanded(
              child: widget.user.isOwner
                  ? OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        minimumSize: const Size.fromHeight(56),
                        foregroundColor: AppColors.ink,
                        side: const BorderSide(color: AppColors.ink, width: 1.5),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      onPressed: _busy ? null : _save,
                      child: const Text('Save'),
                    )
                  : FilledButton(onPressed: _busy ? null : _save, child: _saveLabel('Save')),
            ),
            // Owners usually add a client to give a loan straight away; agents cannot create loans.
            if (widget.user.isOwner) ...[
              const SizedBox(width: 10),
              Expanded(
                child: FilledButton(
                  onPressed: _busy ? null : () => _save(giveLoan: true),
                  child: _saveLabel('Save & give loan'),
                ),
              ),
            ],
          ]),
        ),
      ),
    );
  }

  Widget _saveLabel(String text) => _busy
      ? const SizedBox.square(dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
      : Text(text);
}

/// Result of the New client screen.
class NewClientResult {
  const NewClientResult(this.clientId, {this.giveLoan = false});

  final int clientId;
  final bool giveLoan;
}

class _Existing {
  const _Existing({required this.clientId, required this.name, required this.areaName, required this.accessible});

  final int clientId;
  final String name;
  final String areaName;
  final bool accessible;

  factory _Existing.fromDetails(Map<String, dynamic> d) => _Existing(
        clientId: ((d['clientId'] as num?) ?? 0).toInt(),
        name: (d['name'] as String?) ?? 'another client',
        areaName: (d['areaName'] as String?) ?? '',
        accessible: (d['accessible'] as bool?) ?? false,
      );
}

class _ExistingNotice extends StatelessWidget {
  const _ExistingNotice({required this.existing, required this.onOpen});

  final _Existing existing;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    final where = existing.areaName.isEmpty ? '' : ' (${existing.areaName})';
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Wrap(crossAxisAlignment: WrapCrossAlignment.center, children: [
        Text('This number already belongs to ${existing.name}$where. ',
            style: const TextStyle(fontSize: 13, color: AppColors.error, fontWeight: FontWeight.w500)),
        if (existing.accessible)
          GestureDetector(
            onTap: onOpen,
            child: const Text('Open client',
                style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: AppColors.brand,
                    decoration: TextDecoration.underline)),
          )
        else
          const Text('Ask the owner to assign you that area.', style: TextStyle(fontSize: 13, color: AppColors.muted)),
      ]),
    );
  }
}
