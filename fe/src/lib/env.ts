/**
 * 서버 전용 환경 변수를 읽습니다.
 * 값이 없으면 안전하지 않은 기본값으로 진행하지 않고 즉시 실패시킵니다.
 */
export function requireServerEnv(name: string): string {
  const value = process.env[name];

  if (value === undefined || value.trim() === '') {
    throw new Error(`환경 변수 ${name}이(가) 설정되지 않았습니다.`);
  }

  return value;
}
