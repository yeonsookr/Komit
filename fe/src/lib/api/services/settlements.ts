import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  ContractLedger,
  DepositsResponse,
  IncomeResponse,
  SpendingResponse,
} from "@/types/settlement";

/**
 * 「7 정산」 조회. 넷 다 로그인이 필요하고 쓰기 API 는 없습니다.
 * 정산 계좌 등록·변경은 마이페이지에 있습니다.
 */

/**
 * 의뢰인이 그 달에 쓴 돈. 예치·결제·환불이 한 목록에 섞이고 환불은 음수입니다.
 *
 * `month` 는 `YYYY-MM` 이며 비우면 서버가 이번 달로 봅니다. 화면은 주소의 달을
 * 그대로 넘기고 직접 이번 달을 계산하지 않습니다 — 서버와 브라우저의 '오늘'이
 * 다를 수 있습니다.
 */
export async function getSpending(month?: string): Promise<SpendingResponse> {
  const { data } = await authenticatedBackendApi().get<SpendingResponse>(BACKEND.SETTLEMENT_SPENDING, {
    ...(month ? { params: { month } } : {}),
  });
  return data;
}

/**
 * 개발자가 받을 돈과 정산 계좌.
 *
 * 달을 받지 않습니다. 목 명세에 파라미터가 없어 언제나 현재 기준입니다.
 */
export async function getIncome(): Promise<IncomeResponse> {
  const { data } = await authenticatedBackendApi().get<IncomeResponse>(
    BACKEND.SETTLEMENT_INCOME,
  );
  return data;
}

/**
 * 맡긴 돈이 계약별로 얼마나 남아 있는지.
 *
 * 달을 받지 않습니다. 예치금은 특정 달의 집계가 아니라 지금의 잔액입니다.
 */
export async function getDeposits(): Promise<DepositsResponse> {
  const { data } = await authenticatedBackendApi().get<DepositsResponse>(
    BACKEND.SETTLEMENT_DEPOSITS,
  );
  return data;
}

/**
 * 정산 화면 세 장이 공통으로 쓰는 묶음.
 *
 * 왼쪽 목록이 소비·소득·예치금 금액을 함께 보여주므로 어느 화면이든 셋을 다 읽습니다.
 * 달은 소비에만 걸립니다 — 나머지 둘은 목 명세에 달 파라미터가 없습니다.
 */
export async function getSettlementOverview(month?: string): Promise<{
  spending: SpendingResponse;
  income: IncomeResponse;
  deposits: DepositsResponse;
}> {
  const [spending, income, deposits] = await Promise.all([
    getSpending(month),
    getIncome(),
    getDeposits(),
  ]);
  return { spending, income, deposits };
}

/** 계약 하나의 예치금 원장. 없거나 남의 계약이면 404. */
export async function getContractLedger(contractId: number): Promise<ContractLedger> {
  const { data } = await authenticatedBackendApi().get<ContractLedger>(
    BACKEND.CONTRACT_LEDGER(contractId),
  );
  return data;
}
