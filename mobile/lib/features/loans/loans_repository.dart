import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/providers.dart';
import '../../core/money.dart';
import 'loan_models.dart';

final loansRepositoryProvider = Provider<LoansRepository>((ref) => LoansRepository(ref));

final loanDetailProvider = FutureProvider.autoDispose.family<LoanDetail, int>((ref, id) {
  return ref.watch(loansRepositoryProvider).get(id);
});

final loanScheduleProvider = FutureProvider.autoDispose.family<List<Installment>, int>((ref, id) {
  return ref.watch(loansRepositoryProvider).installments(id);
});

final preclosureQuoteProvider = FutureProvider.autoDispose.family<PreclosureQuote, int>((ref, id) {
  return ref.watch(loansRepositoryProvider).preclosureQuote(id);
});

final loanLedgerProvider = FutureProvider.autoDispose.family<List<LedgerEntry>, int>((ref, id) {
  return ref.watch(loansRepositoryProvider).ledger(id);
});

/// Rate cap, allowed methods and loan limit for the New loan form.
final loanRulesProvider = FutureProvider.autoDispose<LoanRules>((ref) {
  final dio = ref.watch(apiClientProvider);
  return ApiError.guard(() async {
    final res = await dio.get<Map<String, dynamic>>('/settings');
    return LoanRules.fromJson(res.data!);
  });
});

class LoansRepository {
  LoansRepository(this._ref);

  final Ref _ref;

  Future<LoanPage> list({LoanStatus? status, int? clientId, int page = 0, int size = 30}) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).get<Map<String, dynamic>>('/loans', queryParameters: {
          if (status != null) 'status': status.name.toUpperCase(),
          if (clientId != null) 'clientId': clientId,
          'page': page,
          'size': size,
        });
        return LoanPage.fromJson(res.data!);
      });

  Future<LoanDetail> get(int id) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).get<Map<String, dynamic>>('/loans/$id');
        return LoanDetail.fromJson(res.data!);
      });

  Future<List<Installment>> installments(int id) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).get<List<dynamic>>('/loans/$id/installments');
        return res.data!.map((e) => Installment.fromJson(e as Map<String, dynamic>)).toList();
      });

  Future<List<LedgerEntry>> ledger(int id) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).get<List<dynamic>>('/loans/$id/ledger');
        return res.data!.map((e) => LedgerEntry.fromJson(e as Map<String, dynamic>)).toList();
      });

  Future<LoanPreview> preview(LoanInput input) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).post<Map<String, dynamic>>('/loans/preview', data: input.toJson());
        return LoanPreview.fromJson(res.data!);
      });

  Future<PreclosureQuote> preclosureQuote(int id) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).get<Map<String, dynamic>>('/loans/$id/preclosure-quote');
        return PreclosureQuote.fromJson(res.data!);
      });

  /// Owner only: closes the loan. [settlementAmount] must equal the server's figure at [paidOn],
  /// otherwise 409 QUOTE_OUTDATED (details.settlementAmount has the new amount).
  Future<void> preclose(
    int id, {
    required String clientTxnId,
    required Money settlementAmount,
    required bool upi,
    String? upiRefNo,
    required DateTime paidOn,
  }) =>
      ApiError.guard(() async {
        await _ref.read(apiClientProvider).post<Map<String, dynamic>>('/loans/$id/preclose', data: {
          'clientTxnId': clientTxnId,
          'settlementAmount': settlementAmount.toApi(),
          'mode': upi ? 'UPI' : 'CASH',
          if (upi) 'upiRefNo': upiRefNo,
          'paidOn': paidOn.toUtc().toIso8601String(),
        });
      });

  /// Owner only. Returns the new loan's id.
  Future<int> create(LoanInput input) => ApiError.guard(() async {
        final res = await _ref.read(apiClientProvider).post<Map<String, dynamic>>('/loans', data: input.toJson());
        return (res.data!['id'] as num).toInt();
      });
}
