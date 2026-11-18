import '../../core/money.dart';
import '../loans/loan_models.dart';

export '../loans/loan_models.dart' show Frequency, FrequencyText, LoanSummary;

enum ClientFilter {
  all('ALL'),
  behind('BEHIND'),
  noActiveLoan('NO_ACTIVE_LOAN');

  const ClientFilter(this.api);

  final String api;
}

/// Row of GET /clients: identity plus a roll-up of the client's active loans.
class ClientListItem {
  const ClientListItem({
    required this.id,
    required this.areaName,
    required this.name,
    required this.phone,
    required this.activeLoans,
    required this.outstanding,
    required this.overdueAmount,
  });

  final int id;
  final String areaName;
  final String name;
  final String phone;
  final int activeLoans;
  final Money outstanding;
  final Money overdueAmount;

  factory ClientListItem.fromJson(Map<String, dynamic> j) => ClientListItem(
        id: (j['id'] as num).toInt(),
        areaName: (j['areaName'] as String?) ?? '',
        name: j['name'] as String,
        phone: j['phone'] as String,
        activeLoans: ((j['activeLoans'] as num?) ?? 0).toInt(),
        outstanding: Money.fromJson(j['outstanding']),
        overdueAmount: Money.fromJson(j['overdueAmount']),
      );
}

class ClientPage {
  const ClientPage({required this.items, required this.page, required this.totalPages, required this.totalElements});

  final List<ClientListItem> items;
  final int page;
  final int totalPages;
  final int totalElements;

  bool get hasMore => page + 1 < totalPages;

  factory ClientPage.fromJson(Map<String, dynamic> j) {
    final meta = j['meta'] as Map<String, dynamic>;
    return ClientPage(
      items: (j['content'] as List).map((e) => ClientListItem.fromJson(e as Map<String, dynamic>)).toList(),
      page: (meta['page'] as num).toInt(),
      totalPages: (meta['totalPages'] as num).toInt(),
      totalElements: (meta['totalElements'] as num).toInt(),
    );
  }
}

/// GET /clients/{id}.
class ClientDetail {
  const ClientDetail({
    required this.id,
    required this.areaId,
    required this.areaName,
    required this.name,
    required this.phone,
    this.altPhone,
    this.address,
    this.aadhaarLast4,
    this.guarantorName,
    this.guarantorPhone,
    this.clientSince,
    required this.isActive,
    required this.activeLoans,
    required this.totalOutstanding,
  });

  final int id;
  final int areaId;
  final String areaName;
  final String name;
  final String phone;
  final String? altPhone;
  final String? address;
  final String? aadhaarLast4;
  final String? guarantorName;
  final String? guarantorPhone;

  /// Consent is recorded when the client is created, so it doubles as "client since".
  final DateTime? clientSince;
  final bool isActive;
  final List<LoanSummary> activeLoans;
  final Money totalOutstanding;

  factory ClientDetail.fromJson(Map<String, dynamic> j) => ClientDetail(
        id: (j['id'] as num).toInt(),
        areaId: (j['areaId'] as num).toInt(),
        areaName: (j['areaName'] as String?) ?? '',
        name: j['name'] as String,
        phone: j['phone'] as String,
        altPhone: j['altPhone'] as String?,
        address: j['address'] as String?,
        aadhaarLast4: j['aadhaarLast4'] as String?,
        guarantorName: j['guarantorName'] as String?,
        guarantorPhone: j['guarantorPhone'] as String?,
        clientSince: j['consentGivenAt'] == null ? null : DateTime.tryParse(j['consentGivenAt'] as String),
        isActive: (j['isActive'] as bool?) ?? true,
        activeLoans: ((j['activeLoans'] as List?) ?? const [])
            .map((e) => LoanSummary.fromJson(e as Map<String, dynamic>))
            .toList(),
        totalOutstanding: Money.fromJson(j['totalOutstanding']),
      );
}

/// POST /clients body. Consent is mandatory; [consentVersion] names the notice text shown.
class NewClientInput {
  const NewClientInput({
    required this.areaId,
    required this.name,
    required this.phone,
    this.altPhone,
    this.address,
    this.aadhaarLast4,
    this.guarantorName,
    this.guarantorPhone,
  });

  static const consentVersion = 'v1';

  final int areaId;
  final String name;
  final String phone;
  final String? altPhone;
  final String? address;
  final String? aadhaarLast4;
  final String? guarantorName;
  final String? guarantorPhone;

  Map<String, dynamic> toJson() {
    String? opt(String? v) => v == null || v.trim().isEmpty ? null : v.trim();
    return {
      'areaId': areaId,
      'name': name.trim(),
      'phone': phone,
      if (opt(altPhone) != null) 'altPhone': opt(altPhone),
      if (opt(address) != null) 'address': opt(address),
      if (opt(aadhaarLast4) != null) 'aadhaarLast4': opt(aadhaarLast4),
      if (opt(guarantorName) != null) 'guarantorName': opt(guarantorName),
      if (opt(guarantorPhone) != null) 'guarantorPhone': opt(guarantorPhone),
      'consentGiven': true,
      'consentVersion': consentVersion,
    };
  }
}

/// "Meena Raj" → "MR". Tamil names keep vowel signs with their letter ("மீனா" → "மீ").
String initialsOf(String name) {
  final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (parts.isEmpty) return '?';
  final first = _firstLetter(parts.first);
  return parts.length == 1 ? first.toUpperCase() : (first + _firstLetter(parts.last)).toUpperCase();
}

/// "9789044120" → "97890 44120".
String formatPhone(String phone) =>
    phone.length == 10 ? '${phone.substring(0, 5)} ${phone.substring(5)}' : phone;

/// First letter including any following Tamil vowel signs / virama (U+0BBE–U+0BCD, U+0BD7).
String _firstLetter(String word) {
  final runes = word.runes.toList();
  var end = 1;
  while (end < runes.length && ((runes[end] >= 0x0BBE && runes[end] <= 0x0BCD) || runes[end] == 0x0BD7)) {
    end++;
  }
  return String.fromCharCodes(runes.sublist(0, end));
}
