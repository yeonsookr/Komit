import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getMyPage, updateMyName, updateNotificationSetting } from './mypage';

/**
 * 서버 전용 모듈이라 `server-only` 와 axios 인스턴스, 토큰 헤더를 가짜로 바꿔 둡니다.
 * 여기서 보는 것은 어느 주소를 어떤 방법으로 부르고 무엇을 돌려주는지입니다.
 */
const { get, patch } = vi.hoisted(() => ({ get: vi.fn(), patch: vi.fn() }));
vi.mock('server-only', () => ({}));
vi.mock('../server', () => ({ backendApi: () => ({ get, patch }) }));
vi.mock('@/lib/auth/session', () => ({
  bearerHeaders: async () => ({ headers: { Authorization: 'Bearer t' } }),
}));

const AUTH = { headers: { Authorization: 'Bearer t' } };

beforeEach(() => {
  get.mockReset();
  patch.mockReset();
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
  it('바뀐 내 정보를 돌려준다', async () => {
    patch.mockResolvedValueOnce({ data: { userId: 12, name: '김미미' } });

    await expect(updateMyName({ name: '김미미' })).resolves.toMatchObject({ name: '김미미' });
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
