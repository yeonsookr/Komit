import { describe, expect, it } from 'vitest';
import { DEFAULT_SIZE, parseSearchParams, toQueryString } from './developerSearch';

describe('parseSearchParams', () => {
  it('비어 있으면 기본값을 쓴다', () => {
    expect(parseSearchParams({})).toEqual({
      searchMode: 'problem',
      sort: 'devRating,desc',
      page: 1,
      size: DEFAULT_SIZE,
    });
  });

  it('허용된 값을 그대로 읽는다', () => {
    const parsed = parseSearchParams({
      searchMode: 'name',
      keyword: '이준',
      sort: 'price,asc',
      page: '3',
    });

    expect(parsed).toMatchObject({
      searchMode: 'name',
      keyword: '이준',
      sort: 'price,asc',
      page: 3,
    });
  });

  it('허용되지 않은 값은 기본값으로 되돌린다', () => {
    // 그대로 서버에 보내면 422 가 됩니다.
    const parsed = parseSearchParams({ searchMode: 'email', sort: 'name,asc', page: '0' });

    expect(parsed).toMatchObject({ searchMode: 'problem', sort: 'devRating,desc', page: 1 });
  });

  it('분야 코드를 여러 개 읽는다', () => {
    expect(parseSearchParams({ fieldCodeIds: ['41', '42'] }).fieldCodeIds).toEqual([41, 42]);
  });

  it('숫자가 아닌 분야 코드는 버린다', () => {
    expect(parseSearchParams({ fieldCodeIds: ['41', 'abc'] }).fieldCodeIds).toEqual([41]);
  });

  it('공백뿐인 검색어는 조건에서 뺀다', () => {
    expect(parseSearchParams({ keyword: '   ' }).keyword).toBeUndefined();
  });
});

describe('toQueryString', () => {
  it('기본값은 주소에 적지 않는다', () => {
    expect(toQueryString({ searchMode: 'problem', sort: 'devRating,desc', page: 1 })).toBe('');
  });

  it('분야 코드는 같은 이름을 거듭 적는다', () => {
    expect(toQueryString({ fieldCodeIds: [41, 42] })).toBe('fieldCodeIds=41&fieldCodeIds=42');
  });

  it('기본값이 아닌 값만 담는다', () => {
    const query = toQueryString({
      searchMode: 'name',
      keyword: '이준',
      sort: 'price,asc',
      page: 2,
    });

    expect(new URLSearchParams(query).get('searchMode')).toBe('name');
    expect(new URLSearchParams(query).get('page')).toBe('2');
  });

  it('읽은 조건을 다시 적으면 같은 결과가 나온다', () => {
    const query = 'searchMode=name&keyword=%EC%9D%B4%EC%A4%80&fieldCodeIds=41&fieldCodeIds=42';
    const parsed = parseSearchParams(Object.fromEntries(new URLSearchParams(query)));
    // URLSearchParams 로 한 번 접으면 fieldCodeIds 가 하나만 남으므로 직접 넘깁니다.
    const round = toQueryString({ ...parsed, fieldCodeIds: [41, 42] });

    expect(new URLSearchParams(round).getAll('fieldCodeIds')).toEqual(['41', '42']);
    expect(new URLSearchParams(round).get('keyword')).toBe('이준');
  });
});
