import { renewedExpiry } from './tenant.models';

describe('renewedExpiry (same rule as Plan.renewedExpiry)', () => {
  const today = '2026-11-19';

  it('extends a running plan from its current expiry', () => {
    expect(renewedExpiry('MONTHLY', '2026-12-16', today)).toBe('2027-01-16');
    expect(renewedExpiry('YEARLY', '2026-12-16', today)).toBe('2027-12-16');
  });

  it('starts from today when the plan has ended or is missing', () => {
    expect(renewedExpiry('MONTHLY', '2026-10-03', today)).toBe('2026-12-19');
    expect(renewedExpiry('TRIAL', null, today)).toBe('2026-12-19');
  });

  it('clamps to the end of the month like Java plusMonths', () => {
    expect(renewedExpiry('MONTHLY', '2027-01-31', '2027-01-20')).toBe('2027-02-28');
    expect(renewedExpiry('YEARLY', '2028-02-29', '2028-02-01')).toBe('2029-02-28');
  });
});
