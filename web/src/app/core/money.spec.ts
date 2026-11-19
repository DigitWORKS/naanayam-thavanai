import { Money } from './money';

describe('Money', () => {
  it('parses API decimal strings', () => {
    expect(Money.parse('1250.00').paise).toBe(125000);
    expect(Money.parse('328').paise).toBe(32800);
    expect(Money.parse('10.5').paise).toBe(1050);
    expect(Money.parse('-222.91').paise).toBe(-22291);
  });

  it('rejects malformed input', () => {
    for (const bad of ['', '.', '1.234', '1,000', 'abc', '12.']) {
      expect(Money.tryParse(bad), bad).toBeNull();
    }
  });

  it('treats null as zero and accepts integers in JSON', () => {
    expect(Money.fromJson(null).isZero).toBe(true);
    expect(Money.fromJson(150).paise).toBe(15000);
    expect(Money.fromJson('4270.63').paise).toBe(427063);
  });

  it('writes two decimals for the API', () => {
    expect(new Money(32800).toApi()).toBe('328.00');
    expect(new Money(5).toApi()).toBe('0.05');
    expect(new Money(-22291).toApi()).toBe('-222.91');
  });

  it('adds without floating point drift', () => {
    expect(Money.sum(['0.10', '0.20', '0.30'].map(Money.parse)).toApi()).toBe('0.60');
  });

  it('formats with Indian grouping', () => {
    expect(new Money(12345600).format()).toBe('₹1,23,456');
    expect(new Money(12345650).format()).toBe('₹1,23,456.50');
  });
});
