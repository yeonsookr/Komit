import type { CodeItem } from "@/types/developer";

/**
 * 계좌 등록 화면의 은행. 데모 계좌는 에스크로가 미리 등록한 은행 한 곳으로만 열리므로
 * (`R-ACC-10`), 화면은 그 은행만 보여주고 다른 은행은 고를 수 없습니다.
 */

/** 에스크로가 계좌를 여는 은행코드. (`R-ACC-10`) */
export const DEMO_BANK_CODE = "001";

/**
 * 에스크로 은행코드 ↔ 서버 `BANK_CODE` 목록의 은행 이름.
 *
 * 에스크로는 은행을 `001` 같은 금융망 코드로 다루고, 서버 코드 목록은 은행마다
 * 내부 식별자를 따로 붙입니다. 두 값을 잇는 자리를 여기 한 곳으로 둡니다.
 */
const BANK_NAMES: Record<string, string> = {
  [DEMO_BANK_CODE]: "싸피은행",
};

/**
 * 은행코드에 해당하는 서버 코드 항목. 계좌 등록 화면은 이 은행 하나만 늘어놓습니다.
 *
 * 요청에 싣는 `bankCodeId`는 서버가 준 목록의 값이라 화면에서 지어내지 않습니다.
 * 매핑에 없는 코드이거나 서버 목록에 그 은행이 없으면 null입니다.
 */
export function findBank(banks: CodeItem[], bankCode: string): CodeItem | null {
  const name = BANK_NAMES[bankCode];
  if (name === undefined) return null;
  return banks.find((bank) => bank.codeName === name) ?? null;
}
