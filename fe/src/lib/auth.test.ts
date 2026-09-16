import { describe, expect, it } from 'vitest';
import { NEXT_PARAM, safeNext } from './auth';

/**
 * safeNext 는 열린 리다이렉트를 막는 검사입니다. 느슨해지면 로그인을 거쳐 낯선
 * 사이트로 사용자를 보낼 수 있으므로, 막아야 할 모양을 하나씩 못박아 둡니다.
 */
describe('safeNext', () => {
  it('우리 화면의 경로는 그대로 통과시킨다', () => {
    expect(safeNext('/checkup')).toBe('/checkup');
    expect(safeNext('/developers?keyword=%EC%9D%B4%EC%A4%80')).toBe(
      '/developers?keyword=%EC%9D%B4%EC%A4%80',
    );
    expect(safeNext('/checkup/31?step=2')).toBe('/checkup/31?step=2');
  });

  it('외부 주소는 막는다', () => {
    expect(safeNext('https://evil.com')).toBeUndefined();
    expect(safeNext('http://evil.com')).toBeUndefined();
  });

  it('프로토콜을 생략한 외부 주소도 막는다', () => {
    // 슬래시로 시작하지만 브라우저는 https://evil.com 으로 읽습니다.
    expect(safeNext('//evil.com')).toBeUndefined();
    expect(safeNext('///evil.com')).toBeUndefined();
  });

  it('슬래시로 시작하지 않는 값은 막는다', () => {
    expect(safeNext('checkup')).toBeUndefined();
    expect(safeNext('javascript:alert(1)')).toBeUndefined();
    expect(safeNext('')).toBeUndefined();
  });

  it('값이 없으면 undefined 를 돌려준다', () => {
    expect(safeNext(undefined)).toBeUndefined();
  });

  it('같은 이름이 여러 번 오면 첫 값만 본다', () => {
    expect(safeNext(['/checkup', '//evil.com'])).toBe('/checkup');
    expect(safeNext(['//evil.com', '/checkup'])).toBeUndefined();
  });

  it('주소에 싣는 이름은 next 다', () => {
    expect(NEXT_PARAM).toBe('next');
  });
});
