/**
 * Money as integer paise. The API sends and accepts decimal strings ("1250.00"), so amounts never go
 * through floating point except when formatting for display. Mirrors mobile/lib/core/money.dart.
 */
export class Money {
  static readonly zero = new Money(0);

  private static readonly pattern = /^(-)?(\d+)(?:\.(\d{1,2}))?$/;
  private static readonly whole = new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', minimumFractionDigits: 0, maximumFractionDigits: 0,
  });
  private static readonly fraction = new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2,
  });

  constructor(readonly paise: number) {
    if (!Number.isInteger(paise)) throw new RangeError(`paise must be an integer: ${paise}`);
  }

  /** "1250", "1250.5", "1250.50", "-10.00" → Money; anything else → null. */
  static tryParse(text: string | null | undefined): Money | null {
    const m = Money.pattern.exec((text ?? '').trim());
    if (!m) return null;
    const paise = Number(m[2]) * 100 + Number((m[3] ?? '').padEnd(2, '0'));
    return new Money(m[1] ? -paise : paise);
  }

  static parse(text: string): Money {
    const m = Money.tryParse(text);
    if (!m) throw new SyntaxError(`Invalid amount: ${text}`);
    return m;
  }

  /** JSON money: a decimal string; null / undefined is zero. */
  static fromJson(value: unknown): Money {
    if (value === null || value === undefined) return Money.zero;
    if (typeof value === 'number' && Number.isInteger(value)) return new Money(value * 100);
    return Money.parse(String(value));
  }

  /** Decimal string for the API, always two places: "328.00". */
  toApi(): string {
    const abs = Math.abs(this.paise);
    const s = `${Math.trunc(abs / 100)}.${String(abs % 100).padStart(2, '0')}`;
    return this.paise < 0 ? `-${s}` : s;
  }

  /** Indian grouping, paise only when present: ₹1,23,456 or ₹1,23,456.50. */
  format(): string {
    return (this.paise % 100 === 0 ? Money.whole : Money.fraction).format(this.paise / 100);
  }

  get isZero(): boolean { return this.paise === 0; }
  get isPositive(): boolean { return this.paise > 0; }

  plus(other: Money): Money { return new Money(this.paise + other.paise); }
  minus(other: Money): Money { return new Money(this.paise - other.paise); }
  equals(other: Money): boolean { return this.paise === other.paise; }

  static sum(items: Iterable<Money>): Money {
    let total = 0;
    for (const m of items) total += m.paise;
    return new Money(total);
  }
}
