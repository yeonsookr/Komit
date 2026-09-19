import { describe, expect, it } from 'vitest';
import {
  formatDateLabel,
  formatDateWithWeekday,
  formatRelativeTime,
  formatSessionSchedule,
  formatFullDate,
  formatShortDate,
  formatShortDateTime,
  formatTime,
  toDayKey,
} from './format';

// 목 서버가 내려주는 값은 +09:00 오프셋이 붙은 문자열입니다.
const SENT_AT = '2026-08-20T10:14:00+09:00';

describe('날짜·시각 포맷터', () => {
  it('말풍선 시각을 24시간 표기로 찍는다', () => {
    expect(formatTime(SENT_AT)).toBe('10:14');
  });

  it('날짜 구분 라벨을 만든다', () => {
    expect(formatDateLabel(SENT_AT)).toBe('8월 20일');
  });

  it('예약 날짜는 요일을 함께 표시한다', () => {
    expect(formatDateWithWeekday('2026-08-22T15:00:00+09:00')).toBe('8월 22일 토');
  });

  it('컨설팅 세션 일시는 요일을 괄호로 묶고 시각까지 적는다', () => {
    // 와이어프레임 `4_의뢰인_계약서상세` 의 「8월 29일 (토) 14:00」 표기입니다.
    expect(formatSessionSchedule('2026-08-29T14:00:00+09:00')).toBe('8월 29일 (토) 14:00');
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
    expect(formatShortDate('2026-08-17T09:12:00+09:00')).toBe('8/17');
  });

  it('약관 개정일은 연도까지 찍는다', () => {
    expect(formatFullDate('2026-03-01')).toBe('2026년 3월 1일');
    expect(formatFullDate('2026-06-15')).toBe('2026년 6월 15일');
  });

  it('날짜만 찍을 때도 한국 시각으로 넘어간 날을 쓴다', () => {
    // 2026-08-17T23:00Z 는 한국에서 다음 날 오전입니다.
    expect(formatShortDate('2026-08-17T23:00:00Z')).toBe('8/18');
  });
});

describe('formatRelativeTime', () => {
  const now = new Date('2026-08-28T14:00:00+09:00');

  it('1분 안쪽은 지금이다', () => {
    expect(formatRelativeTime('2026-08-28T13:59:30+09:00', now)).toBe('지금');
  });

  it('한 시간 안쪽은 분으로 센다', () => {
    expect(formatRelativeTime('2026-08-28T13:48:00+09:00', now)).toBe('12분 전');
  });

  it('같은 날이면 시간으로 센다', () => {
    expect(formatRelativeTime('2026-08-28T11:00:00+09:00', now)).toBe('3시간 전');
  });

  it('하루 전은 어제다', () => {
    expect(formatRelativeTime('2026-08-27T23:50:00+09:00', now)).toBe('어제');
  });

  it('그보다 오래됐으면 날짜로 찍는다', () => {
    expect(formatRelativeTime('2026-08-20T09:00:00+09:00', now)).toBe('8/20');
  });

  it('시계가 어긋나 미래로 보여도 지금으로 본다', () => {
    expect(formatRelativeTime('2026-08-28T14:00:30+09:00', now)).toBe('지금');
  });
});
