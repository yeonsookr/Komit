import { describe, expect, it } from 'vitest';
import { formatDateLabel, formatShortDateTime, formatTime, toDayKey } from './format';

// 목 서버가 내려주는 값은 +09:00 오프셋이 붙은 문자열입니다.
const SENT_AT = '2026-08-20T10:14:00+09:00';

describe('날짜·시각 포맷터', () => {
  it('말풍선 시각을 24시간 표기로 찍는다', () => {
    expect(formatTime(SENT_AT)).toBe('10:14');
  });

  it('날짜 구분 라벨을 만든다', () => {
    expect(formatDateLabel(SENT_AT)).toBe('8월 20일');
  });

  it('같은 날 메시지는 같은 키를 갖는다', () => {
    expect(toDayKey('2026-08-20T00:30:00+09:00')).toBe(toDayKey('2026-08-20T23:30:00+09:00'));
  });

  it('자정을 넘기면 다른 키가 된다', () => {
    expect(toDayKey('2026-08-20T23:59:00+09:00')).not.toBe(toDayKey('2026-08-21T00:01:00+09:00'));
  });

  it('오프셋이 다른 같은 시점은 한국 시각 기준으로 묶인다', () => {
    // 2026-08-20T01:14Z 는 한국에서 같은 날 10:14 입니다.
    expect(formatTime('2026-08-20T01:14:00Z')).toBe('10:14');
    expect(toDayKey('2026-08-20T01:14:00Z')).toBe('2026-08-20');
  });

  it('좁은 자리 표기는 앞의 0을 떼고 찍는다', () => {
    expect(formatShortDateTime('2026-08-18T16:40:00+09:00')).toBe('8/18 16:40');
  });
});
