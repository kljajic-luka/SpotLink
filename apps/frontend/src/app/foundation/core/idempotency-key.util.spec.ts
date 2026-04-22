import { createIdempotencyKey } from './idempotency-key.util';

describe('createIdempotencyKey', () => {
  it('vraca kljuc sa podrazumevanim prefiksom', () => {
    const key = createIdempotencyKey();

    expect(key.startsWith('sl_')).toBeTrue();
    expect(key.length).toBeGreaterThan(6);
  });

  it('podrzava prilagodjeni prefiks', () => {
    const key = createIdempotencyKey('rez');

    expect(key.startsWith('rez_')).toBeTrue();
  });

  it('generise razlicite vrednosti po pozivu', () => {
    const first = createIdempotencyKey();
    const second = createIdempotencyKey();

    expect(first).not.toEqual(second);
  });
});
