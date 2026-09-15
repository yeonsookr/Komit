import { describe, expect, it } from 'vitest';
import { countByCategory, hasScore, splitByInput, versionLabel } from './diagnosisReport';
import type { Issue } from '@/types/diagnosis';

function issue(overrides: Partial<Issue> = {}): Issue {
  return {
    issueId: 311,
    title: '결제 간헐 실패 수정',
    detail: null,
    location: null,
    isInput: false,
    category: { codeId: 73, codeName: '결제' },
    status: 'UNRESOLVED',
    severity: 'HIGH',
    fixedVersion: null,
    originIssueId: null,
    qaCriteria: [],
    ...overrides,
  };
}

describe('splitByInput', () => {
  it('의뢰 내용에서 나온 결함을 따로 가른다', () => {
    const issues = [
      issue({ issueId: 311, isInput: true }),
      issue({ issueId: 312, isInput: false }),
    ];

    const { input, others } = splitByInput(issues);
    expect(input.map((i) => i.issueId)).toEqual([311]);
    expect(others.map((i) => i.issueId)).toEqual([312]);
  });

  it('관련 여부를 모르는 결함은 그 밖으로 둔다', () => {
    // isInput 이 null 이면 참이 아니므로 위로 올리지 않습니다.
    const { input, others } = splitByInput([issue({ isInput: null })]);
    expect(input).toEqual([]);
    expect(others).toHaveLength(1);
  });

  it('빈 목록은 양쪽 다 비어 있다', () => {
    expect(splitByInput([])).toEqual({ input: [], others: [] });
  });
});

describe('countByCategory', () => {
  it('분류별로 센다', () => {
    const counts = countByCategory([
      issue({ category: { codeId: 71, codeName: '보안' } }),
      issue({ category: { codeId: 71, codeName: '보안' } }),
      issue({ category: { codeId: 72, codeName: '위법' } }),
    ]);

    expect(counts.get(71)).toBe(2);
    expect(counts.get(72)).toBe(1);
  });

  it('없는 분류는 undefined 다', () => {
    expect(countByCategory([]).get(71)).toBeUndefined();
  });
});

describe('hasScore', () => {
  it('검사 전에는 점수가 없다', () => {
    expect(hasScore(null)).toBe(false);
  });

  it('0점도 점수로 본다', () => {
    expect(hasScore(0)).toBe(true);
  });
});

describe('versionLabel', () => {
  it('회차를 사람 말로 적는다', () => {
    expect(versionLabel(2)).toBe('2차 진단');
  });
});
