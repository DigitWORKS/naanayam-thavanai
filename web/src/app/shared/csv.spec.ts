import { cell, toCsv } from './csv';

describe('csv', () => {
  it('quotes and doubles quotes', () => {
    expect(cell('Meena "Raj"')).toBe('"Meena ""Raj"""');
  });

  it('neutralises formulas but keeps negative numbers', () => {
    expect(cell('=HYPERLINK(1)')).toBe(`"'=HYPERLINK(1)"`);
    expect(cell('@SUM(A1)')).toBe(`"'@SUM(A1)"`);
    expect(cell('-180.00')).toBe('"-180.00"');
    expect(cell(-5)).toBe('"-5"');
  });

  it('starts with a BOM and uses CRLF', () => {
    expect(toCsv([['a', 1], ['b', null]])).toBe('﻿"a","1"\r\n"b",""\r\n');
  });
});
