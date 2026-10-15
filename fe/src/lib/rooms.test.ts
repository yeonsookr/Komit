import { describe, expect, it } from 'vitest';
import { filterBySide, groupByProject, isReceived } from './rooms';
import type { RoomSummary } from '@/types/room';
import type { Me } from '@/types/user';

function room(overrides: Partial<RoomSummary> = {}): RoomSummary {
  return {
    roomId: 91,
    recommendationItemId: 501,
    type: 'INQUIRY',
    status: 'OPEN',
    projectId: 8,
    projectName: '수제 비누 가게',
    developerId: 3,
    counterpartName: '이준',
    chatRefId: 'room-91-chat',
    openedAt: '2026-08-20T09:00:00+09:00',
    ...overrides,
  };
}

function me(overrides: Partial<Me> = {}): Me {
  return {
    userId: 12,
    name: '김미미',
    profileImageFileId: null,
    profileImageUrl: null,
    hasDevProfile: false,
    developerId: null,
    unreadNotificationCount: 0,
    ...overrides,
  };
}

describe('isReceived', () => {
  it('내 developerId 와 방의 developerId 가 같으면 맡은 의뢰다', () => {
    expect(isReceived(room({ developerId: 3 }), me({ developerId: 3 }))).toBe(true);
  });

  it('다르면 맡긴 의뢰다', () => {
    expect(isReceived(room({ developerId: 3 }), me({ developerId: 9 }))).toBe(false);
  });

  it('개발자로 전환하지 않은 사용자는 늘 맡긴 의뢰다', () => {
    expect(isReceived(room({ developerId: 3 }), me({ developerId: null }))).toBe(false);
  });
});

describe('filterBySide', () => {
  const rooms = [
    room({ roomId: 91, developerId: 3 }),
    room({ roomId: 95, developerId: 7 }),
  ];

  it('양쪽인 사용자는 방이 탭으로 갈린다', () => {
    const user = me({ developerId: 7 });
    expect(filterBySide(rooms, user, 'RECEIVED').map((r) => r.roomId)).toEqual([95]);
    expect(filterBySide(rooms, user, 'SENT').map((r) => r.roomId)).toEqual([91]);
  });

  it('개발자가 아니면 맡은 의뢰가 비어 있다', () => {
    expect(filterBySide(rooms, me(), 'RECEIVED')).toEqual([]);
  });
});

describe('groupByProject', () => {
  it('같은 프로젝트의 방을 한 묶음으로 만든다', () => {
    const groups = groupByProject([
      room({ roomId: 93, projectId: 8 }),
      room({ roomId: 91, projectId: 8 }),
      room({ roomId: 99, projectId: 12, projectName: '미미헤어샵 예약' }),
    ]);

    expect(groups).toHaveLength(2);
    expect(groups[0].rooms.map((r) => r.roomId)).toEqual([93, 91]);
    expect(groups[1].projectName).toBe('미미헤어샵 예약');
  });

  it('서버가 준 순서를 바꾸지 않는다', () => {
    const groups = groupByProject([
      room({ roomId: 99, projectId: 12 }),
      room({ roomId: 93, projectId: 8 }),
    ]);

    expect(groups.map((g) => g.projectId)).toEqual([12, 8]);
  });

  it('빈 목록은 빈 배열이 된다', () => {
    expect(groupByProject([])).toEqual([]);
  });
});
