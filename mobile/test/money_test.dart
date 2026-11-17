import 'package:flutter_test/flutter_test.dart';
import 'package:naanayamthavanai/core/money.dart';

void main() {
  group('Money.parse', () {
    test('accepts API decimal strings', () {
      expect(Money.parse('1250.00').paise, 125000);
      expect(Money.parse('328').paise, 32800);
      expect(Money.parse('10.5').paise, 1050);
      expect(Money.parse('-222.91').paise, -22291);
    });

    test('rejects malformed input', () {
      for (final bad in ['', '.', '1.234', '1,000', 'abc', '12.']) {
        expect(Money.tryParse(bad), isNull, reason: bad);
      }
    });

    test('fromJson treats null as zero and accepts integers', () {
      expect(Money.fromJson(null), Money.zero);
      expect(Money.fromJson(150).paise, 15000);
      expect(Money.fromJson('4270.63').paise, 427063);
    });
  });

  group('Money output', () {
    test('toApi always has two decimals', () {
      expect(const Money(32800).toApi(), '328.00');
      expect(const Money(5).toApi(), '0.05');
      expect(const Money(-22291).toApi(), '-222.91');
    });

    test('toInput drops zero paise', () {
      expect(const Money(32800).toInput(), '328');
      expect(const Money(32850).toInput(), '328.50');
    });

    test('round-trips without floating point drift', () {
      final total = ['0.10', '0.20', '0.30'].map(Money.parse).sum();
      expect(total.toApi(), '0.60');
    });
  });
}
