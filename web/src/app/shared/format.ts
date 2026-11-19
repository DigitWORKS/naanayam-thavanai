/** Small display helpers shared by pages. */

/** "9789044120" → "97890 44120". */
export function formatPhone(p: string | null | undefined): string {
  return p && /^\d{10}$/.test(p) ? `${p.slice(0, 5)} ${p.slice(5)}` : (p ?? '');
}

/** Page numbers around the current one: 1 … 4 5 [6] 7 8 … 20 (0 = gap). */
export function pageWindow(current: number, last: number): number[] {
  const out: number[] = [];
  for (let p = 1; p <= last; p++) {
    if (p === 1 || p === last || Math.abs(p - current) <= 2) out.push(p);
    else if (out[out.length - 1] !== 0) out.push(0);
  }
  return out;
}
