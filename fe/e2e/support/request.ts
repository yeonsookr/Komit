import type { Page } from '@playwright/test';

const EXAMPLE_REQUIREMENTS = [
  {
    issueId: 311,
    title: '결제 간헐 실패 수정',
    detail: '결제 응답을 기다리지 않고 화면을 넘깁니다.',
    categoryCodeId: 73,
    severity: 'HIGH',
    qaCriteria: [
      { qaCriterionId: 203, testCase: '결제를 50번 연속으로 넣어도 모두 성공한다' },
    ],
  },
  {
    issueId: 312,
    title: '관리자 API에 인증 추가',
    detail: '로그인한 사람이면 남의 주문 내역까지 열람 가능합니다',
    categoryCodeId: 71,
    severity: 'HIGH',
    qaCriteria: [
      { qaCriterionId: 201, testCase: '관리자 주소로 들어가면 로그인 화면이 뜬다' },
      { qaCriterionId: 202, testCase: '로그인 없이 열면 막힌다' },
    ],
  },
  {
    issueId: 313,
    title: '개인정보처리방침 · 이용약관 페이지 추가',
    detail: '실제 사용자를 받는 순간부터 적용됩니다',
    categoryCodeId: 72,
    severity: 'MEDIUM',
    qaCriteria: [],
  },
] as const;

async function write(page: Page, diagnosisId: number, requirements: readonly object[]) {
  const response = await page.request.put(`/api/diagnoses/${diagnosisId}/requirements`, {
    data: { requirements },
  });
  if (!response.ok()) {
    throw new Error(`요청서 테스트 준비 실패: ${response.status()}`);
  }
}

export async function seedExampleRequest(page: Page, diagnosisId: number): Promise<void> {
  await write(page, diagnosisId, EXAMPLE_REQUIREMENTS);
}

export async function clearExampleRequest(page: Page, diagnosisId: number): Promise<void> {
  await write(page, diagnosisId, []);
}
