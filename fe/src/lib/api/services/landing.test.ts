import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FEATURED_DEVELOPER_COUNT, getFeaturedDevelopers, getLandingSummary } from './landing';

/**
 * 서버 전용 모듈이라 `server-only` 와 axios 인스턴스를 가짜로 바꿔 둡니다.
 * 여기서 보는 것은 요청 파라미터 구성과 실패 처리뿐입니다.
 *
 * vi.mock 은 import 보다 먼저 실행되므로 가짜 함수는 vi.hoisted 로 만듭니다.
 * 응답은 테스트마다 한 번짜리(…Once)로 정합니다 — 다음 테스트로 새지 않습니다.
 */
const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('server-only', () => ({}));
// backendApi 는 첫 호출에서 인스턴스를 만드는 함수입니다(lib/api/server.ts).
vi.mock('../server', () => ({ backendApi: () => ({ get }) }));

beforeEach(() => get.mockReset());

describe('getLandingSummary', () => {
  it('지표를 그대로 돌려준다', async () => {
    const summary = { weeklyInquiryRoomCount: 412, weekStartAt: '2026-09-07T00:00:00+09:00' };
    get.mockResolvedValueOnce({ data: summary });

    await expect(getLandingSummary()).resolves.toEqual(summary);
  });

  it('읽지 못하면 던지지 않고 null 을 돌려준다', async () => {
    get.mockRejectedValueOnce(new Error('500'));
    vi.spyOn(console, 'error').mockImplementation(() => {});

    await expect(getLandingSummary()).resolves.toBeNull();
  });
});

describe('getFeaturedDevelopers', () => {
  it('기본으로 3명을 요청하고 content 만 돌려준다', async () => {
    get.mockResolvedValueOnce({ data: { content: [{ developerId: 3 }] } });

    await expect(getFeaturedDevelopers()).resolves.toEqual([{ developerId: 3 }]);
    expect(get).toHaveBeenCalledWith('/api/landing/featured-developers', {
      params: { size: FEATURED_DEVELOPER_COUNT },
    });
    expect(FEATURED_DEVELOPER_COUNT).toBe(3);
  });

  it('목록을 읽지 못하면 그대로 던진다', async () => {
    get.mockRejectedValueOnce(new Error('500'));

    await expect(getFeaturedDevelopers()).rejects.toThrow('500');
  });
});
