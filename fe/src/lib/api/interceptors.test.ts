import axios from 'axios';
import { describe, expect, it } from 'vitest';
import { attachErrorNormalizer } from './interceptors';
import { ApiError, NETWORK_ERROR_CODE, UNKNOWN_ERROR_CODE } from '@/types/api';

/** 어댑터를 갈아끼워 실제 네트워크 없이 응답을 흉내냅니다. */
function instanceReturning(reject: unknown) {
  const instance = axios.create();
  instance.defaults.adapter = () => Promise.reject(reject);
  return attachErrorNormalizer(instance);
}

function axiosErrorWith(status: number, data: unknown) {
  const error = new axios.AxiosError('failed');
  error.response = { status, data, statusText: '', headers: {}, config: error.config! };
  return error;
}

describe('attachErrorNormalizer', () => {
  it('코밋 실패 응답의 code·message 를 그대로 옮긴다', async () => {
    const api = instanceReturning(
      axiosErrorWith(404, { code: 'ROOM_NOT_FOUND', message: '방을 찾을 수 없습니다.' }),
    );

    await expect(api.get('/rooms/404')).rejects.toMatchObject({
      status: 404,
      code: 'ROOM_NOT_FOUND',
      message: '방을 찾을 수 없습니다.',
    });
  });

  it('검증 실패의 errors 목록을 함께 넘긴다', async () => {
    const api = instanceReturning(
      axiosErrorWith(422, {
        code: 'INVALID_REQUEST',
        message: '요청 값을 확인해 주세요.',
        errors: [{ field: 'size', reason: '1 이상이어야 합니다.' }],
      }),
    );

    await expect(api.get('/rooms')).rejects.toMatchObject({
      errors: [{ field: 'size', reason: '1 이상이어야 합니다.' }],
    });
  });

  it('응답이 없으면 네트워크 오류로 바꾼다', async () => {
    const api = instanceReturning(new axios.AxiosError('timeout'));

    await expect(api.get('/rooms')).rejects.toMatchObject({
      status: 0,
      code: NETWORK_ERROR_CODE,
    });
  });

  it('계약에 없는 본문은 내부 정보를 감추고 고정 문구로 덮는다', async () => {
    const api = instanceReturning(
      axiosErrorWith(500, { trace: 'java.lang.NullPointerException at Foo.java:12' }),
    );

    const error = await api.get('/rooms').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 500, code: UNKNOWN_ERROR_CODE });
    expect((error as ApiError).message).not.toContain('NullPointerException');
  });
});
