import { describe, expect, it } from 'vitest';
import {
  canSave,
  newCriterion,
  newRequirement,
  toDraft,
  toRequest,
  type DraftRequirement,
} from './requirementDraft';
import type { Issue } from '@/types/diagnosis';

function issue(overrides: Partial<Issue> = {}): Issue {
  return {
    issueId: 312,
    title: '관리자 API에 인증 추가',
    detail: null,
    location: null,
    isInput: false,
    category: { codeId: 71, codeName: '보안' },
    status: 'UNRESOLVED',
    severity: 'HIGH',
    fixedVersion: null,
    originIssueId: null,
    qaCriteria: [
      {
        qaCriterionId: 204,
        testCase: '관리자 주소로 들어가면 로그인 화면이 뜬다',
        condition: null,
        status: 'UNRESOLVED',
        issueId: 312,
      },
    ],
    ...overrides,
  };
}

describe('toDraft', () => {
  it('서버가 준 요구사항을 편집 상태로 옮긴다', () => {
    const [draft] = toDraft([issue()]);

    expect(draft.issueId).toBe(312);
    expect(draft.categoryCodeId).toBe(71);
    expect(draft.criteria).toEqual([
      expect.objectContaining({ qaCriterionId: 204, testCase: '관리자 주소로 들어가면 로그인 화면이 뜬다' }),
    ]);
  });

  it('줄마다 화면용 키를 붙인다', () => {
    const [draft] = toDraft([issue()]);
    expect(draft.key).toBe('issue-312');
    expect(draft.criteria[0].key).toBe('qa-204');
  });
});

describe('newRequirement / newCriterion', () => {
  it('새 줄은 식별자가 null 이다', () => {
    // 서버가 저장하면서 발급합니다.
    expect(newRequirement('주문 취소 화면 추가', 74, 'LOW').issueId).toBeNull();
    expect(newCriterion().qaCriterionId).toBeNull();
  });

  it('새 줄끼리 키가 겹치지 않는다', () => {
    expect(newCriterion().key).not.toBe(newCriterion().key);
  });
});

describe('toRequest', () => {
  it('편집 상태를 저장할 모양으로 옮긴다', () => {
    const [sent] = toRequest(toDraft([issue()]));

    expect(sent).toEqual({
      issueId: 312,
      title: '관리자 API에 인증 추가',
      detail: null,
      categoryCodeId: 71,
      severity: 'HIGH',
      qaCriteria: [{ qaCriterionId: 204, testCase: '관리자 주소로 들어가면 로그인 화면이 뜬다' }],
    });
  });

  it('적다 만 확인 문장은 보내지 않는다', () => {
    const draft = toDraft([issue()]);
    draft[0].criteria.push(newCriterion());

    expect(toRequest(draft)[0].qaCriteria).toHaveLength(1);
  });

  it('앞뒤 공백을 떼고 보낸다', () => {
    const draft = toDraft([issue()]);
    draft[0].criteria[0].testCase = '  로그인 없이 열면 막힌다  ';

    expect(toRequest(draft)[0].qaCriteria[0].testCase).toBe('로그인 없이 열면 막힌다');
  });

  it('요구사항을 지우면 보낸 목록에서 빠진다', () => {
    // 보낸 목록에 없는 기존 요구사항은 요청서에서 뺀 것으로 봅니다.
    expect(toRequest([])).toEqual([]);
  });
});

describe('canSave', () => {
  const draft: DraftRequirement[] = toDraft([issue()]);

  it('요구사항이 하나도 없으면 저장하지 않는다', () => {
    expect(canSave([])).toBe(false);
  });

  it('제목이 빈 줄이 있으면 저장하지 않는다', () => {
    expect(canSave([{ ...draft[0], title: '   ' }])).toBe(false);
  });

  it('제목이 모두 차 있으면 저장할 수 있다', () => {
    expect(canSave(draft)).toBe(true);
  });
});
