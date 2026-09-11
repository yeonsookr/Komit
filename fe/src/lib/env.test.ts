import { afterEach, describe, expect, it } from 'vitest';
import { requireServerEnv } from './env';

const KEY = 'KOMIT_TEST_ENV';

afterEach(() => {
  delete process.env[KEY];
});

describe('requireServerEnv', () => {
  it('값이 있으면 그대로 반환한다', () => {
    process.env[KEY] = 'https://example.com';
    expect(requireServerEnv(KEY)).toBe('https://example.com');
  });

  it('값이 없으면 실패시킨다', () => {
    expect(() => requireServerEnv(KEY)).toThrowError(/설정되지 않았습니다/);
  });

  it('공백만 있는 값도 없는 것으로 본다', () => {
    process.env[KEY] = '   ';
    expect(() => requireServerEnv(KEY)).toThrowError(/설정되지 않았습니다/);
  });
});
