package com.a501.service.domain.contract.dto;

/**
 * 계약 하나가 어디까지 왔는지. 탈퇴 전 확인처럼 「아직 끝나지 않은 계약이 있나」를 묻는 쪽이 씁니다. (R-ACC-05)
 *
 * @param contractId 계약 식별자
 * @param roomId     계약이 달린 방 식별자
 * @param depositable 예치가 있을 수 있는지. 의뢰인이 서명하면 개발자 서명 전에도 예치할 수 있어(R-CON-20)
 *                    의뢰인이 서명했거나 계약이 성립했으면 참입니다. 거짓이면 Escrow 에 묻지 않아도 됩니다
 * @param finished   상담 합의나 개발 계약이 완료({@code COMPLETED})·파기({@code TERMINATED})됐거나,
 *                   의뢰인이 계약을 취소({@code CANCELLED})했는지 (R-CON-21)
 */
public record ContractProgressView(Integer contractId, Integer roomId, boolean depositable, boolean finished) {
}
