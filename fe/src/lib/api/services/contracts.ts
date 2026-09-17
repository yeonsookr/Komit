import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  Contract,
  DepositRequest,
  EscrowDeposit,
  PaymentMethod,
  SignContractRequest,
  WriteDevelopmentContractRequest,
  WriteReviewRequest,
} from "@/types/contract";

/**
 * 계약서 조회.
 *
 * 컨설팅과 개발이 공통 부분(서명·전달 메시지·원문)을 함께 쓰고, 갈래별 내용만
 * `contractType` 에 따라 `consulting` 또는 `development` 한쪽에 담깁니다.
 * 나머지 한쪽은 늘 null 입니다.
 *
 * 없거나 이 계약의 당사자가 아니면 404(CONTRACT_NOT_FOUND) 입니다.
 * 존재를 드러내지 않기 위해 403 대신 404 를 줍니다.
 */
export async function getContract(contractId: number): Promise<Contract> {
  const { data } = await backendApi().get<Contract>(BACKEND.CONTRACT(contractId));
  return data;
}

/**
 * 개발 계약서 작성.
 *
 * 개발자가 요청서의 요구사항을 작업으로 나누고 금액·기간·확인 문장을 붙여 보냅니다.
 * 협의 중에 다시 보내면 덮어씁니다. 이 방의 개발자가 아니면 403 입니다.
 */
export async function writeDevelopmentContract(
  roomId: number,
  body: WriteDevelopmentContractRequest,
): Promise<Contract> {
  const { data } = await backendApi().post<Contract>(
    BACKEND.DEVELOPMENT_CONTRACTS(roomId),
    body,
  );
  return data;
}

/**
 * 계약서 서명.
 *
 * 서명판에 그린 그림을 먼저 올려 받은 식별자를 보냅니다.
 * **양쪽이 서명해야 계약이 성립하고, 서명한 계약서는 고칠 수 없습니다** (R-CON-01·R-CON-02).
 */
export async function signContract(
  contractId: number,
  body: SignContractRequest,
): Promise<Contract> {
  const { data } = await backendApi().post<Contract>(
    BACKEND.CONTRACT_SIGNATURES(contractId),
    body,
  );
  return data;
}

/**
 * 계약 금액 예치.
 *
 * 계약 금액 전액을 한 번에 맡깁니다. 돈의 원장은 Escrow 서버가 가지고 여기서는
 * 예치를 걸고 결과를 확인만 합니다. 같은 `idempotencyKey` 로 다시 보내면 결제가
 * 두 번 나가지 않습니다.
 */
export async function depositContract(
  contractId: number,
  body: DepositRequest,
): Promise<EscrowDeposit> {
  const { data } = await backendApi().post<EscrowDeposit>(
    BACKEND.CONTRACT_DEPOSIT(contractId),
    body,
  );
  return data;
}

/**
 * 예치에 쓸 결제 수단.
 *
 * 카드 결제는 쓰지 않으므로 실시간 계좌이체 수단만 옵니다. 빈 배열이면 아직
 * 등록한 것이 없다는 뜻이고, 그대로 예치를 걸면 409(ACCOUNT_REQUIRED) 입니다.
 */
export async function getPaymentMethods(): Promise<PaymentMethod[]> {
  const { data } = await backendApi().get<{ content: PaymentMethod[] }>(
    BACKEND.PAYMENT_METHODS,
  );
  return data.content;
}

/** 예치 상태. 개발자가 착수 전에 돈이 실제로 걸려 있는지 봅니다. */
export async function getDeposit(contractId: number): Promise<EscrowDeposit> {
  const { data } = await backendApi().get<EscrowDeposit>(
    BACKEND.CONTRACT_DEPOSIT(contractId),
  );
  return data;
}

/**
 * 개발 착수.
 *
 * 예치와 착수는 다른 단계입니다. 예치는 의뢰인이 돈을 맡기는 것이고, 착수는
 * 개발자가 그것을 확인하고 일을 시작한다고 알리는 것입니다.
 * 예치가 끝나지 않았으면 409(DEPOSIT_REQUIRED) 입니다.
 */
export async function startContract(contractId: number): Promise<Contract> {
  const { data } = await backendApi().post<Contract>(BACKEND.CONTRACT_START(contractId));
  return data;
}

/**
 * 리뷰 남기기.
 *
 * 유료 거래를 마친 의뢰인만 남길 수 있고 개발 실력과 설명력을 나누어 받습니다
 * (R-REV-01·R-REV-02). 본문은 비워도 됩니다.
 */
export async function writeReview(
  contractId: number,
  body: WriteReviewRequest,
): Promise<void> {
  await backendApi().post(BACKEND.CONTRACT_REVIEW(contractId), body);
}
