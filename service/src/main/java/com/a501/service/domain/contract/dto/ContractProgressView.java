package com.a501.service.domain.contract.dto;

/**
 * 계약 하나가 어디까지 왔는지. 탈퇴 전 확인처럼 「아직 끝나지 않은 계약이 있나」를 묻는 쪽이 씁니다. (R-ACC-05)
 *
 * @param contractId 계약 식별자
 * @param roomId     계약이 달린 방 식별자
 * @param concluded  체결({@code SIGNED})했는지. 체결 전에는 예치가 있을 수 없습니다
 * @param finished   상담 합의나 개발 계약이 완료({@code COMPLETED})·파기({@code TERMINATED})됐는지
 */
public record ContractProgressView(Integer contractId, Integer roomId, boolean concluded, boolean finished) {
}
