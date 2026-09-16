import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  checkWithdrawal,
  getMyPage,
  updateMyName,
  updateNotificationSetting,
  withdraw,
} from './mypage';

/**
 * 서버 전용 모듈이라 `server-only` 와 axios 인스턴스, 토큰 헤더를 가짜로 바꿔 둡니다.
 * 여기서 보는 것은 어느 주소를 어떤 방법으로 부르고 무엇을 돌려주는지입니다.
 */
const { get, patch, post } = vi.hoisted(() => ({ get: vi.fn(), patch: vi.fn(), post: vi.fn() }));
vi.mock('server-only', () => ({}));
vi.mock('../server', () => ({ backendApi: () => ({ get, patch, post }) }));
vi.mock('@/lib/auth/session', () => ({
  bearerHeaders: async () => ({ headers: { Authorization: 'Bearer t' } }),
}));

const AUTH = { headers: { Authorization: 'Bearer t' } };

beforeEach(() => {
  get.mockReset();
  patch.mockReset();
  post.mockReset();
});

describe('getMyPage', () => {
  it('마이페이지를 그대로 돌려주고 토큰을 싣는다', async () => {
    const page = { account: { name: '김미미' }, projects: [] };
    get.mockResolvedValueOnce({ data: page });

    await expect(getMyPage()).resolves.toEqual(page);
    expect(get).toHaveBeenCalledWith('/api/mypage', AUTH);
  });
});

describe('updateMyName', () => {
  it('바뀐 계정 칸을 돌려준다', async () => {
    // 목이 돌려주는 것은 계정 칸이지 헤더가 쓰는 내 정보가 아닙니다.
    const account = { name: '김미미', email: 'mimi@kakao.com', profileImageUrl: null };
    patch.mockResolvedValueOnce({ data: account });

    await expect(updateMyName({ name: '김미미' })).resolves.toEqual(account);
    expect(patch).toHaveBeenCalledWith('/api/users/me', { name: '김미미' }, AUTH);
  });
});

describe('updateNotificationSetting', () => {
  it('바뀐 설정을 돌려준다', async () => {
    patch.mockResolvedValueOnce({ data: { notificationEnabled: false } });

    await expect(updateNotificationSetting({ notificationEnabled: false })).resolves.toEqual({
      notificationEnabled: false,
    });
    expect(patch).toHaveBeenCalledWith(
      '/api/settings/notification',
      { notificationEnabled: false },
      AUTH,
    );
  });
});

describe('checkWithdrawal', () => {
  it('막고 있는 것을 그대로 돌려준다', async () => {
    const check = {
      canWithdraw: false,
      blockers: [{ type: 'CONTRACT', title: '수제 비누 가게 · 진행 중인 의뢰', amount: null, targetId: 130 }],
    };
    get.mockResolvedValueOnce({ data: check });

    await expect(checkWithdrawal()).resolves.toEqual(check);
    expect(get).toHaveBeenCalledWith('/api/users/me/withdrawal', AUTH);
  });
});

describe('withdraw', () => {
  it('본문 없이 POST 로 부른다', async () => {
    post.mockResolvedValueOnce({ data: { isDeleted: true, deletedAt: '2026-09-14T10:05:00+09:00' } });

    await expect(withdraw()).resolves.toMatchObject({ isDeleted: true });
    expect(post).toHaveBeenCalledWith('/api/users/me/withdrawal', undefined, AUTH);
  });
});
