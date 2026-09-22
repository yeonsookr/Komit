import { describe, expect, it } from 'vitest';
import {
  careerStatusLabel,
  formatActiveHours,
  formatCareerPeriod,
  formatFieldAndYears,
  formatPeriodDays,
  splitPriceCatalog,
} from './developerProfile';
import type { PriceCatalogItem } from '@/types/developer';

describe('formatCareerPeriod', () => {
  it('끝난 경력은 시작과 끝을 적는다', () => {
    expect(formatCareerPeriod('2021-03-01', '2024-08-31')).toBe('2021.03 – 2024.08');
  });

  it('이어지는 경력은 현재로 적는다', () => {
    expect(formatCareerPeriod('2024-09-01', null)).toBe('2024.09 – 현재');
  });

  it('날짜 문자열을 그대로 자르므로 타임존에 흔들리지 않는다', () => {
    // 자정 근처 값이 앞뒤 달로 밀리면 안 됩니다.
    expect(formatCareerPeriod('2021-01-01', '2021-12-31')).toBe('2021.01 – 2021.12');
  });
});

describe('careerStatusLabel', () => {
  it('확인된 경력과 확인 중인 경력을 가른다', () => {
    expect(careerStatusLabel(true)).toBe('확인됨');
    expect(careerStatusLabel(false)).toBe('확인 중');
  });
});

describe('formatActiveHours', () => {
  it('활동 시간대를 잇는다', () => {
    expect(formatActiveHours('10:00', '19:00')).toBe('10:00 – 19:00');
  });

  it('정하지 않은 시간대(null)는 비운다', () => {
    expect(formatActiveHours(null, '19:00')).toBeNull();
    expect(formatActiveHours('10:00', null)).toBeNull();
  });
});

describe('formatFieldAndYears', () => {
  it('분야와 정수 연차를 잇는다', () => {
    expect(formatFieldAndYears({ codeId: 42, codeName: 'DB 연결' }, 5.0)).toBe('DB 연결 5년');
  });

  it('분야가 없으면 미설정으로, 연차가 없으면 분야만 적는다', () => {
    expect(formatFieldAndYears(null, 4.5)).toBe('분야 미설정 4년');
    expect(formatFieldAndYears({ codeId: 42, codeName: 'DB 연결' }, null)).toBe('DB 연결');
  });
});

describe('splitPriceCatalog', () => {
  const catalog: PriceCatalogItem[] = [
    { priceId: 60, taskType: 'CONSULT', taskName: '30분', price: 50000, periodDays: null },
    { priceId: 62, taskType: 'DEVELOP', taskName: '인터넷에 올리기', price: 200000, periodDays: 1 },
    { priceId: 63, taskType: 'DEVELOP', taskName: '저장되게 하기', price: 350000, periodDays: 3 },
  ];

  it('작업 종류별로 나눈다', () => {
    const split = splitPriceCatalog(catalog);
    expect(split.CONSULT).toHaveLength(1);
    expect(split.DEVELOP).toHaveLength(2);
  });

  it('없는 종류는 빈 배열이다', () => {
    expect(splitPriceCatalog([]).DEVELOP).toEqual([]);
  });

  it('서버가 준 순서를 바꾸지 않는다', () => {
    expect(splitPriceCatalog(catalog).DEVELOP.map((i) => i.priceId)).toEqual([62, 63]);
  });
});

describe('formatPeriodDays', () => {
  it('날수가 있으면 적는다', () => {
    expect(formatPeriodDays(3)).toBe('3일');
  });

  it('컨설팅처럼 날수가 없으면 비운다', () => {
    expect(formatPeriodDays(null)).toBeUndefined();
  });
});
