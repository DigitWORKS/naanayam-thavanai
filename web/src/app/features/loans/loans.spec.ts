import { formatPhone, pageWindow } from '../../shared/format';

describe('pageWindow', () => {
  it('shows every page when there are few', () => {
    expect(pageWindow(1, 3)).toEqual([1, 2, 3]);
  });

  it('keeps first, last and two either side, with gaps', () => {
    expect(pageWindow(6, 20)).toEqual([1, 0, 4, 5, 6, 7, 8, 0, 20]);
    expect(pageWindow(1, 10)).toEqual([1, 2, 3, 0, 10]);
  });
});

describe('formatPhone', () => {
  it('splits ten digits 5 + 5', () => {
    expect(formatPhone('9789044120')).toBe('97890 44120');
  });

  it('leaves anything else as is', () => {
    expect(formatPhone('+91 97890')).toBe('+91 97890');
    expect(formatPhone(null)).toBe('');
  });
});
