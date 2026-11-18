import 'package:intl/intl.dart';

import '../../core/money.dart';

enum Frequency { daily, weekly }

enum InterestMethod { flat, reducing }

enum InterestCollection { addedToRepayment, upfrontDeducted }

enum LoanStatus { active, closed, preclosed, defaulted }

enum InstallmentStatus { pending, partial, paid, missed, settled }

String _api(Enum e) => e.name.replaceAllMapped(RegExp(r'[A-Z]'), (m) => '_${m[0]}').toUpperCase();

T _fromApi<T extends Enum>(List<T> values, String api) => values.firstWhere((v) => _api(v) == api);

final _apiDate = DateFormat('yyyy-MM-dd');

DateTime _date(Object? v) => DateTime.parse(v as String);

extension FrequencyText on Frequency {
  String get label => this == Frequency.daily ? 'Daily' : 'Weekly';
  String get unit => this == Frequency.daily ? 'days' : 'weeks';
}

extension LoanStatusText on LoanStatus {
  String get label => switch (this) {
        LoanStatus.active => 'Active',
        LoanStatus.closed => 'Closed',
        LoanStatus.preclosed => 'Pre-closed',
        LoanStatus.defaulted => 'Defaulted',
      };
}

/// Loan row (API: LoanSummary) for the Loans tab and the client detail.
class LoanSummary {
  const LoanSummary({
    required this.id,
    required this.loanNo,
    required this.principal,
    required this.frequency,
    required this.installmentAmount,
    required this.outstanding,
    required this.overdueAmount,
    required this.status,
    required this.clientId,
    required this.clientName,
    required this.areaName,
    required this.totalPayable,
    this.endDate,
  });

  final int id;
  final String loanNo;
  final Money principal;
  final Frequency frequency;
  final Money installmentAmount;
  final Money outstanding;
  final Money overdueAmount;
  final LoanStatus status;
  final int clientId;
  final String clientName;
  final String areaName;
  final Money totalPayable;
  final DateTime? endDate;

  /// Share of the total payable already paid (0–1).
  double get paidShare =>
      totalPayable.isPositive ? (1 - outstanding.paise / totalPayable.paise).clamp(0.0, 1.0) : 0.0;

  factory LoanSummary.fromJson(Map<String, dynamic> j) => LoanSummary(
        id: (j['id'] as num).toInt(),
        loanNo: j['loanNo'] as String,
        principal: Money.fromJson(j['principal']),
        frequency: _fromApi(Frequency.values, j['frequency'] as String),
        installmentAmount: Money.fromJson(j['installmentAmount']),
        outstanding: Money.fromJson(j['outstanding']),
        overdueAmount: Money.fromJson(j['overdueAmount']),
        status: _fromApi(LoanStatus.values, j['status'] as String),
        clientId: ((j['clientId'] as num?) ?? 0).toInt(),
        clientName: (j['clientName'] as String?) ?? '',
        areaName: (j['areaName'] as String?) ?? '',
        totalPayable: Money.fromJson(j['totalPayable']),
        endDate: j['endDate'] == null ? null : _date(j['endDate']),
      );
}

class LoanPage {
  const LoanPage({required this.items, required this.page, required this.totalPages, required this.totalElements});

  final List<LoanSummary> items;
  final int page;
  final int totalPages;
  final int totalElements;

  bool get hasMore => page + 1 < totalPages;

  factory LoanPage.fromJson(Map<String, dynamic> j) {
    final meta = j['meta'] as Map<String, dynamic>;
    return LoanPage(
      items: (j['content'] as List).map((e) => LoanSummary.fromJson(e as Map<String, dynamic>)).toList(),
      page: (meta['page'] as num).toInt(),
      totalPages: (meta['totalPages'] as num).toInt(),
      totalElements: (meta['totalElements'] as num).toInt(),
    );
  }
}

/// GET /loans/{id}.
class LoanDetail {
  const LoanDetail({
    required this.id,
    required this.loanNo,
    required this.status,
    required this.clientId,
    required this.clientName,
    required this.principal,
    required this.interestRate,
    required this.interestMethod,
    required this.interestCollection,
    required this.interestAmount,
    required this.disbursedAmount,
    required this.totalPayable,
    required this.totalPaid,
    required this.outstanding,
    required this.overdueAmount,
    required this.pendingPenalty,
    required this.frequency,
    required this.installmentAmount,
    required this.tenureCount,
    required this.startDate,
    required this.originalEndDate,
    required this.currentEndDate,
    this.legalNote,
  });

  final int id;
  final String loanNo;
  final LoanStatus status;
  final int clientId;
  final String clientName;
  final Money principal;

  /// Decimal string, e.g. "20.00".
  final String interestRate;
  final InterestMethod interestMethod;
  final InterestCollection interestCollection;
  final Money interestAmount;
  final Money disbursedAmount;
  final Money totalPayable;
  final Money totalPaid;
  final Money outstanding;
  final Money overdueAmount;
  final Money pendingPenalty;
  final Frequency frequency;
  final Money installmentAmount;
  final int tenureCount;
  final DateTime startDate;
  final DateTime originalEndDate;
  final DateTime currentEndDate;
  final String? legalNote;

  bool get isActive => status == LoanStatus.active;

  factory LoanDetail.fromJson(Map<String, dynamic> j) => LoanDetail(
        id: (j['id'] as num).toInt(),
        loanNo: j['loanNo'] as String,
        status: _fromApi(LoanStatus.values, j['status'] as String),
        clientId: (j['clientId'] as num).toInt(),
        clientName: (j['clientName'] as String?) ?? '',
        principal: Money.fromJson(j['principal']),
        interestRate: '${j['interestRate']}',
        interestMethod: _fromApi(InterestMethod.values, j['interestMethod'] as String),
        interestCollection: _fromApi(InterestCollection.values, j['interestCollection'] as String),
        interestAmount: Money.fromJson(j['interestAmount']),
        disbursedAmount: Money.fromJson(j['disbursedAmount']),
        totalPayable: Money.fromJson(j['totalPayable']),
        totalPaid: Money.fromJson(j['totalPaid']),
        outstanding: Money.fromJson(j['outstanding']),
        overdueAmount: Money.fromJson(j['overdueAmount']),
        pendingPenalty: Money.fromJson(j['pendingPenalty']),
        frequency: _fromApi(Frequency.values, j['frequency'] as String),
        installmentAmount: Money.fromJson(j['installmentAmount']),
        tenureCount: (j['tenureCount'] as num).toInt(),
        startDate: _date(j['startDate']),
        originalEndDate: _date(j['originalEndDate']),
        currentEndDate: _date(j['currentEndDate']),
        legalNote: j['legalNote'] as String?,
      );
}

/// One row of the schedule (API: Installment).
class Installment {
  const Installment({
    required this.seqNo,
    required this.dueDate,
    required this.dueAmount,
    required this.paidAmount,
    required this.penaltyAmount,
    required this.penaltyPaid,
    required this.status,
    required this.isExtension,
  });

  final int seqNo;
  final DateTime dueDate;
  final Money dueAmount;
  final Money paidAmount;
  final Money penaltyAmount;
  final Money penaltyPaid;
  final InstallmentStatus status;

  /// Added at the end to carry a missed day's amount.
  final bool isExtension;

  factory Installment.fromJson(Map<String, dynamic> j) => Installment(
        seqNo: (j['seqNo'] as num).toInt(),
        dueDate: _date(j['dueDate']),
        dueAmount: Money.fromJson(j['dueAmount']),
        paidAmount: Money.fromJson(j['paidAmount']),
        penaltyAmount: Money.fromJson(j['penaltyAmount']),
        penaltyPaid: Money.fromJson(j['penaltyPaid']),
        status: _fromApi(InstallmentStatus.values, j['status'] as String),
        isExtension: (j['isExtension'] as bool?) ?? false,
      );
}

/// One payment or reversal on the loan (API: LedgerEntry).
class LedgerEntry {
  const LedgerEntry({
    required this.id,
    required this.amount,
    required this.isReversal,
    required this.isPreclosure,
    required this.isUpi,
    this.upiRefNo,
    required this.paidOn,
    required this.collectedByName,
    required this.runningBalance,
  });

  final int id;
  final Money amount;
  final bool isReversal;
  final bool isPreclosure;
  final bool isUpi;
  final String? upiRefNo;
  final DateTime paidOn;
  final String collectedByName;
  final Money runningBalance;

  factory LedgerEntry.fromJson(Map<String, dynamic> j) => LedgerEntry(
        id: (j['id'] as num).toInt(),
        amount: Money.fromJson(j['amount']),
        isReversal: j['entryType'] == 'REVERSAL',
        isPreclosure: j['purpose'] == 'PRECLOSURE',
        isUpi: j['mode'] == 'UPI',
        upiRefNo: j['upiRefNo'] as String?,
        paidOn: DateTime.parse(j['paidOn'] as String).toLocal(),
        collectedByName: (j['collectedByName'] as String?) ?? '',
        runningBalance: Money.fromJson(j['runningBalance']),
      );
}

/// GET /settings — only what loan entry needs.
class LoanRules {
  const LoanRules({required this.maxInterestRate, required this.allowedMethods, required this.maxActiveLoansPerClient});

  final Money maxInterestRate;
  final Set<InterestMethod> allowedMethods;
  final int maxActiveLoansPerClient;

  factory LoanRules.fromJson(Map<String, dynamic> j) => LoanRules(
        maxInterestRate: Money.fromJson(j['maxInterestRate']),
        allowedMethods: ((j['allowedInterestMethods'] as List?) ?? const ['FLAT'])
            .map((m) => _fromApi(InterestMethod.values, m as String))
            .toSet(),
        maxActiveLoansPerClient: ((j['maxActiveLoansPerClient'] as num?) ?? 2).toInt(),
      );
}

/// POST /loans and /loans/preview body.
class LoanInput {
  const LoanInput({
    required this.clientId,
    required this.principal,
    required this.interestRate,
    required this.interestMethod,
    required this.interestCollection,
    required this.frequency,
    required this.tenureCount,
    required this.startDate,
  });

  final int clientId;
  final Money principal;

  /// Percent per annum, as Money for exact two-decimal handling ("20.00").
  final Money interestRate;
  final InterestMethod interestMethod;
  final InterestCollection interestCollection;
  final Frequency frequency;
  final int tenureCount;
  final DateTime startDate;

  Map<String, dynamic> toJson() => {
        'clientId': clientId,
        'principal': principal.toApi(),
        'interestRate': interestRate.toApi(),
        'interestMethod': _api(interestMethod),
        'interestCollection': _api(interestCollection),
        'frequency': _api(frequency),
        'tenureCount': tenureCount,
        'startDate': _apiDate.format(startDate),
      };
}

/// POST /loans/preview result.
class LoanPreview {
  const LoanPreview({
    required this.interestAmount,
    required this.disbursedAmount,
    required this.totalPayable,
    required this.installmentAmount,
    required this.firstDueDate,
    required this.endDate,
    required this.lastInstallment,
  });

  final Money interestAmount;
  final Money disbursedAmount;
  final Money totalPayable;
  final Money installmentAmount;
  final DateTime? firstDueDate;
  final DateTime endDate;
  final Money lastInstallment;

  factory LoanPreview.fromJson(Map<String, dynamic> j) {
    final rows = (j['installments'] as List?) ?? const [];
    return LoanPreview(
      interestAmount: Money.fromJson(j['interestAmount']),
      disbursedAmount: Money.fromJson(j['disbursedAmount']),
      totalPayable: Money.fromJson(j['totalPayable']),
      installmentAmount: Money.fromJson(j['installmentAmount']),
      firstDueDate: rows.isEmpty ? null : _date((rows.first as Map)['dueDate']),
      endDate: _date(j['endDate']),
      lastInstallment: rows.isEmpty ? Money.zero : Money.fromJson((rows.last as Map)['dueAmount']),
    );
  }
}
