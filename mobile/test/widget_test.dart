// Replaces the counter-app test that `flutter create` would generate.
import 'package:flutter_test/flutter_test.dart';
import 'package:naanayamthavanai/features/payments/payments_repository.dart';

void main() {
  test('UTR: spaces ignored, exactly 12 digits', () {
    expect(isValidUtr(normalizeUtr('4319 1234 5678')), isTrue);
    expect(isValidUtr(normalizeUtr('43191234567')), isFalse);
    expect(isValidUtr(normalizeUtr('43191234567a')), isFalse);
  });
}
