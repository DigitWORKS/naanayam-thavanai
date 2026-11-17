import 'package:intl/intl.dart';

/// Money held as integer paise. The API sends and accepts decimal strings ("1250.00"),
/// so amounts never pass through floating point except for display formatting.
class Money implements Comparable<Money> {
  const Money(this.paise);

  final int paise;

  static const zero = Money(0);

  static final _pattern = RegExp(r'^(-)?(\d+)(?:\.(\d{1,2}))?$');
  static final _whole = NumberFormat.currency(locale: 'en_IN', symbol: '₹', decimalDigits: 0);
  static final _fraction = NumberFormat.currency(locale: 'en_IN', symbol: '₹', decimalDigits: 2);

  /// Parses "1250", "1250.5", "1250.50", "-10.00". Returns null for anything else.
  static Money? tryParse(String? text) {
    if (text == null) return null;
    final m = _pattern.firstMatch(text.trim());
    if (m == null) return null;
    final rupees = int.parse(m.group(2)!);
    final paise = rupees * 100 + int.parse((m.group(3) ?? '').padRight(2, '0'));
    return Money(m.group(1) == null ? paise : -paise);
  }

  static Money parse(String text) =>
      tryParse(text) ?? (throw FormatException('Invalid amount: $text'));

  /// JSON money is a decimal string; null is treated as zero.
  static Money fromJson(Object? value) {
    if (value == null) return zero;
    if (value is int) return Money(value * 100);
    return parse(value.toString());
  }

  /// Decimal string for the API, always two places: "328.00".
  String toApi() {
    final abs = paise.abs();
    final s = '${abs ~/ 100}.${(abs % 100).toString().padLeft(2, '0')}';
    return paise < 0 ? '-$s' : s;
  }

  /// Text for an input field: "328" or "328.50".
  String toInput() => paise % 100 == 0 ? '${paise ~/ 100}' : toApi();

  /// Indian grouping: ₹1,23,456 (paise shown only when present).
  String format() => (paise % 100 == 0 ? _whole : _fraction).format(paise / 100);

  bool get isZero => paise == 0;
  bool get isPositive => paise > 0;

  Money operator +(Money other) => Money(paise + other.paise);
  Money operator -(Money other) => Money(paise - other.paise);

  @override
  int compareTo(Money other) => paise.compareTo(other.paise);

  @override
  bool operator ==(Object other) => other is Money && other.paise == paise;

  @override
  int get hashCode => paise.hashCode;

  @override
  String toString() => toApi();
}

extension MoneySum on Iterable<Money> {
  Money sum() => fold(Money.zero, (a, b) => a + b);
}
