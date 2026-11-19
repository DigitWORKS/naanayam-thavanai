import { ApplicationRef, Injector, resource } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { safeValue } from './resource';

describe('safeValue', () => {
  const settle = async () => {
    await new Promise((r) => setTimeout(r));
    await TestBed.inject(ApplicationRef).whenStable();
  };

  it('returns undefined in the error state, where value() throws', async () => {
    const r = resource({ loader: () => Promise.reject(new Error('404')), injector: TestBed.inject(Injector) });
    await settle();
    expect(r.status()).toBe('error');
    expect(() => r.value()).toThrow();
    expect(safeValue(r)).toBeUndefined();
  });

  it('returns the value once loaded', async () => {
    const r = resource({ loader: () => Promise.resolve(42), injector: TestBed.inject(Injector) });
    await settle();
    expect(safeValue(r)).toBe(42);
  });
});
