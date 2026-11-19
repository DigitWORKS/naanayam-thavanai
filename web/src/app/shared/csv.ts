/**
 * CSV for Excel: UTF-8 with BOM (Tamil names), CRLF, every cell quoted, and text starting with = + - @ (or tab /
 * CR) prefixed with ' so a spreadsheet does not run it as a formula — same rules as the server's loan export.
 * Numbers are written plain.
 */
export function toCsv(rows: (string | number | null | undefined)[][]): string {
  return '﻿' + rows.map((r) => r.map(cell).join(',')).join('\r\n') + '\r\n';
}

export function cell(v: string | number | null | undefined): string {
  if (v === null || v === undefined) return '""';
  let s = String(v);
  if (typeof v !== 'number' && !/^-?\d+(\.\d+)?$/.test(s) && /^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return `"${s.replace(/"/g, '""')}"`;
}

/** Saves the rows as a .csv download. */
export function downloadCsv(filename: string, rows: (string | number | null | undefined)[][]): void {
  const url = URL.createObjectURL(new Blob([toCsv(rows)], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
