import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:uuid/uuid.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../theme/app_theme.dart';
import '../collections/due_item.dart';
import '../sync/payment_queue.dart';
import 'payments_repository.dart';

/// Design board 3: collect a payment (cash or UPI with UTR). Pops `true` once recorded.
class RecordPaymentScreen extends ConsumerStatefulWidget {
  const RecordPaymentScreen({super.key, required this.item});

  final DueItem item;

  @override
  ConsumerState<RecordPaymentScreen> createState() => _RecordPaymentScreenState();
}

class _RecordPaymentScreenState extends ConsumerState<RecordPaymentScreen> {
  final _form = GlobalKey<FormState>();
  final _amount = TextEditingController();
  final _utr = TextEditingController();
  final _amountFocus = FocusNode();

  /// Idempotency key: one per payment, reused on every retry of the same payment.
  final String _clientTxnId = const Uuid().v4();
  late final DateTime _paidOn;

  PaymentMode _mode = PaymentMode.cash;
  bool _busy = false;
  String? _error;

  DueItem get item => widget.item;

  @override
  void initState() {
    super.initState();
    _paidOn = DateTime.now();
    _amount.text = item.totalDue.toInput();
  }

  @override
  void dispose() {
    _amount.dispose();
    _utr.dispose();
    _amountFocus.dispose();
    super.dispose();
  }

  Money? get _parsedAmount => Money.tryParse(_amount.text);

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final digits = normalizeUtr(data?.text ?? '').replaceAll(RegExp(r'\D'), '');
    if (digits.isNotEmpty) _utr.text = digits.length > 12 ? digits.substring(0, 12) : digits;
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    final draft = PaymentDraft(
      clientTxnId: _clientTxnId,
      loanId: item.loanId,
      amount: _parsedAmount!,
      mode: _mode,
      paidOn: _paidOn,
      upiRefNo: _mode == PaymentMode.upi ? normalizeUtr(_utr.text) : null,
    );
    try {
      final recorded = await ref.read(paymentsRepositoryProvider).record(draft);
      if (!mounted) return;
      await _showDone(recorded);
      if (mounted) Navigator.of(context).pop(true);
    } on ApiError catch (e) {
      if (!mounted) return;
      if (e.isNetwork) {
        // No connection (or no answer): keep it on the phone under the same clientTxnId. If the first
        // attempt did reach the server, the sync is answered DUPLICATE and nothing is recorded twice.
        await _saveOffline(draft);
        return;
      }
      setState(() => _error = _message(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _saveOffline(PaymentDraft draft) async {
    try {
      await ref
          .read(paymentQueueProvider.notifier)
          .enqueue(draft, loanNo: item.loanNo, clientName: item.clientName);
    } on ApiError catch (e) {
      if (mounted) setState(() => _error = e.message);
      return;
    } catch (_) {
      if (mounted) setState(() => _error = 'Could not save the payment on this phone. Try again.');
      return;
    }
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.cloud_upload_outlined, color: AppColors.warning, size: 40),
        title: Text('${draft.amount.format()} saved on phone'),
        content: Text(
          'No internet right now. The payment from ${item.clientName} is saved and will be sent automatically '
          'when the connection is back.',
          textAlign: TextAlign.center,
        ),
        actions: [TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('OK'))],
      ),
    );
    if (mounted) Navigator.of(context).pop(true);
  }

  String _message(ApiError e) => switch (e.code) {
        'DUPLICATE_UPI_REF' => 'This UTR is already used for another payment. Check the number with the client.',
        'INVALID_UPI_REF' => 'UTR must be 12 digits.',
        'UPI_REF_REQUIRED' => 'Enter the UPI transaction ID (UTR).',
        _ => e.message,
      };

  Future<void> _showDone(RecordedPayment p) => showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.check_circle, color: AppColors.brand, size: 40),
          title: Text('${p.amount.format()} collected'),
          content: Text('${item.clientName} · ${item.loanNo}\nBalance on loan: ${p.balanceAfter.format()}',
              textAlign: TextAlign.center),
          actions: [TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Done'))],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final locked = _busy;
    final behind = item.overdueCount > 0;

    return PopScope(
      canPop: !_busy,
      child: Scaffold(
        appBar: AppBar(title: Text('Collect payment', style: AppTheme.heading(20))),
        body: Form(
          key: _form,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Expanded(
                          child: Text(item.clientName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                        ),
                        Text(item.loanNo, style: const TextStyle(color: AppColors.brand, fontWeight: FontWeight.w600)),
                      ]),
                      const SizedBox(height: 12),
                      _line('Today', item.todayDue),
                      if (behind)
                        _line('Behind (${item.overdueCount} ${item.overdueCount == 1 ? 'day' : 'days'})',
                            item.overdueAmount),
                      if (item.penaltyDue.isPositive) _line('Penalty', item.penaltyDue),
                      if (item.collectedToday.isPositive) _line('Already paid today', item.collectedToday),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const _Label('Amount received'),
              TextFormField(
                controller: _amount,
                focusNode: _amountFocus,
                enabled: !locked,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d{0,2}'))],
                style: GoogleFonts.spaceGrotesk(fontSize: 28, fontWeight: FontWeight.w700),
                decoration: const InputDecoration(prefixText: '₹ '),
                onChanged: (_) => setState(() {}),
                validator: (v) {
                  final m = Money.tryParse(v);
                  if (m == null || !m.isPositive) return 'Enter the amount received';
                  return null;
                },
              ),
              const SizedBox(height: 8),
              Wrap(spacing: 8, children: [
                if (item.todayDue.isPositive && item.todayDue != item.totalDue)
                  _quick('Today ${item.todayDue.format()}', item.todayDue, locked),
                _quick('All due ${item.totalDue.format()}', item.totalDue, locked),
                ActionChip(
                  label: const Text('Other'),
                  onPressed: locked
                      ? null
                      : () {
                          _amount.clear();
                          _amountFocus.requestFocus();
                          setState(() {});
                        },
                ),
              ]),
              const SizedBox(height: 20),
              const _Label('Paid by'),
              SegmentedButton<PaymentMode>(
                segments: const [
                  ButtonSegment(value: PaymentMode.cash, label: Text('Cash'), icon: Icon(Icons.payments_outlined)),
                  ButtonSegment(value: PaymentMode.upi, label: Text('UPI'), icon: Icon(Icons.qr_code_2)),
                ],
                selected: {_mode},
                onSelectionChanged: locked ? null : (s) => setState(() => _mode = s.first),
              ),
              if (_mode == PaymentMode.upi) ...[
                const SizedBox(height: 20),
                const _Label('UPI transaction ID (UTR)'),
                TextFormField(
                  controller: _utr,
                  enabled: !locked,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(12)],
                  style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w600, letterSpacing: 1.5),
                  decoration: InputDecoration(
                    hintText: '12 digits, e.g. 431912345678',
                    suffixIcon: TextButton.icon(
                      onPressed: locked ? null : _paste,
                      icon: const Icon(Icons.content_paste, size: 18),
                      label: const Text('Paste'),
                    ),
                  ),
                  validator: (v) => isValidUtr(normalizeUtr(v ?? '')) ? null : 'UTR must be 12 digits',
                ),
                const SizedBox(height: 6),
                const Text(
                  'Ask the client to open the payment in their UPI app (GPay, PhonePe, Paytm) '
                  'and read out the UPI transaction ID / UTR.',
                  style: TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
              if (_error != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: AppColors.warningTint, borderRadius: BorderRadius.circular(10)),
                  child: Text(_error!, style: const TextStyle(color: AppColors.warning, fontWeight: FontWeight.w600)),
                ),
              ],
            ],
          ),
        ),
        bottomNavigationBar: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: FilledButton(
              onPressed: _busy ? null : _submit,
              child: _busy
                  ? const SizedBox.square(
                      dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                  : Text(_submitLabel()),
            ),
          ),
        ),
      ),
    );
  }

  String _submitLabel() {
    final amount = _parsedAmount;
    final verb = _mode == PaymentMode.upi ? 'Record UPI' : 'Record cash';
    return amount != null && amount.isPositive ? '$verb ${amount.format()}' : verb;
  }

  Widget _quick(String label, Money value, bool locked) => ActionChip(
        label: Text(label),
        onPressed: locked
            ? null
            : () {
                _amount.text = value.toInput();
                setState(() {});
              },
      );

  Widget _line(String label, Money value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(child: Text(label, style: const TextStyle(color: AppColors.muted))),
          Text(value.format(), style: const TextStyle(fontWeight: FontWeight.w600)),
        ]),
      );
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
