import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import { fileNameFrom } from "../download";
import type {
  ConfirmMilestoneRequest,
  Deliverable,
  DeliverableAccessStage,
  Milestone,
  SaveQaResultsRequest,
} from "@/types/contract";

/**
 * 작업 확인 요청.
 *
 * 개발자가 작업을 끝내고 의뢰인에게 확인을 부탁합니다. 보낸 시각부터 자동 통과
 * 기한을 셉니다 (R-QA-02). 다시 보내면 기한도 다시 셉니다. 바디는 없습니다.
 * 인계 작업은 일반 작업을 모두 통과하고 인계 자료를 갖춘 뒤에 요청합니다 (R-QA-06).
 */
export async function submitMilestone(milestoneId: number): Promise<Milestone> {
  const { data } = await authenticatedBackendApi().post<Milestone>(
    BACKEND.MILESTONE_SUBMISSION(milestoneId),
  );
  return data;
}

/**
 * 확인 결과 저장.
 *
 * 의뢰인이 확인 문장을 직접 해보고 결과를 남깁니다. **다 체크하지 않아도 보낼 수
 * 있습니다** — 3줄 중 2줄만 됐다면 그대로 보내고 개발자가 무엇이 남았는지 봅니다.
 *
 * 확인 요청 중인 작업을 일부만 체크해 보내면 작업이 개발자에게 돌아가고 자동 통과가 멈춥니다
 * (R-QA-02). 완료 처리는 통과시키기가 맡습니다 (R-QA-01).
 * 체크는 되돌릴 수 없으므로 이번에 체크한 것만 보내면 서버가 더합니다.
 */
export async function saveQaResults(
  milestoneId: number,
  body: SaveQaResultsRequest,
): Promise<Milestone> {
  const { data } = await authenticatedBackendApi().put<Milestone>(
    BACKEND.MILESTONE_QA_RESULTS(milestoneId),
    body,
  );
  return data;
}

/**
 * 작업 통과시키기.
 *
 * 확인 문장을 모두 체크한 작업만 완료로 확정합니다 (R-QA-01).
 * **통과 확정은 되돌릴 수 없습니다** (R-QA-03). 하나라도 남아 있으면
 * 400(QA_NOT_ALL_CHECKED) 입니다.
 *
 * 대금은 여기서 나가지 않습니다. 모든 작업이 통과된 뒤 한 번에 지급됩니다 (R-PAY-01).
 */
export async function confirmMilestone(
  milestoneId: number,
  body: ConfirmMilestoneRequest,
): Promise<Milestone> {
  const { data } = await authenticatedBackendApi().post<Milestone>(
    BACKEND.MILESTONE_CONFIRMATION(milestoneId),
    body,
  );
  return data;
}

/**
 * 인계물 붙이기.
 *
 * **파일은 이 요청으로 보내지 않습니다.** 다른 파일과 같이 업로드 3콜을 먼저
 * 마치고 거기서 받은 `fileId` 만 보냅니다. 소스 파일 묶음은 크기가 커서 코밋
 * 서버를 지나가지 않게 합니다.
 *
 * 붙일 때마다 자료가 하나 늘고, 같은 산출물의 다른 URL·파일은 그대로 남습니다. 같은 파일을 같은
 * 용도로 다시 보내면 새로 만들지 않습니다. 시험용(TEST)은 의뢰인이 바로 받고, 해금 파일(PAID)은
 * 지급이 끝난 뒤에 받습니다. 일반 작업을 모두 통과한 뒤부터 인계 작업의 확인을 요청하기 전까지만
 * 붙입니다 (R-QA-06).
 */
export async function attachDeliverable(
  deliverableId: number,
  fileId: number,
  accessStage: DeliverableAccessStage,
): Promise<Deliverable> {
  const { data } = await authenticatedBackendApi().post<Deliverable>(
    BACKEND.DELIVERABLE_FILE(deliverableId),
    { fileId, accessStage },
  );
  return data;
}

/**
 * 인수 시험 URL 하나 더하기. URL 은 시험 중에 여는 자료라 열람 단계가 TEST 입니다.
 * 같은 산출물의 다른 URL 과 파일은 그대로 남습니다 (R-QA-06).
 */
export async function addDeliverableUrl(deliverableId: number, url: string): Promise<Deliverable> {
  const { data } = await authenticatedBackendApi().post<Deliverable>(
    BACKEND.DELIVERABLE_URLS(deliverableId),
    { url },
  );
  return data;
}

/**
 * 계약이 성립한 뒤 산출물 항목 더하기. 서명한 계약서의 범위·금액·확인 문장은 바뀌지
 * 않고, 응답의 `isContractual` 이 false 입니다 (R-QA-06).
 */
export async function addDeliverable(contractId: number, title: string): Promise<Deliverable> {
  const { data } = await authenticatedBackendApi().post<Deliverable>(
    BACKEND.CONTRACT_DELIVERABLES(contractId),
    { title },
  );
  return data;
}

/** 내려받은 인계물. 파일 이름은 Content-Disposition 에 담겨 옵니다. */
export interface DeliverableFile {
  body: ArrayBuffer;
  fileName: string;
  contentType: string;
}

/**
 * 인계물 내려받기.
 *
 * 의뢰인이 넘겨받은 것을 확인할 때 씁니다. 이 계약의 당사자만 받을 수 있습니다.
 */
export async function downloadDeliverable(deliverableId: number): Promise<DeliverableFile> {
  const response = await authenticatedBackendApi().get<ArrayBuffer>(
    BACKEND.DELIVERABLE_FILE(deliverableId),
    { responseType: "arraybuffer" },
  );

  return {
    body: response.data,
    fileName: fileNameFrom(String(response.headers["content-disposition"] ?? "")),
    contentType: String(response.headers["content-type"] ?? "application/octet-stream"),
  };
}

/** 인계 자료 식별자로 고른 파일 하나를 내려받습니다. 서버가 자료별 권한을 검사합니다. */
export async function downloadDeliverableMaterial(deliverableMaterialId: number): Promise<DeliverableFile> {
  const response = await authenticatedBackendApi().get<ArrayBuffer>(
    BACKEND.DELIVERABLE_MATERIAL_FILE(deliverableMaterialId),
    { responseType: "arraybuffer" },
  );
  return {
    body: response.data,
    fileName: fileNameFrom(String(response.headers["content-disposition"] ?? "")),
    contentType: String(response.headers["content-type"] ?? "application/octet-stream"),
  };
}
