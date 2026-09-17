import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  ConfirmMilestoneRequest,
  Deliverable,
  Milestone,
  SaveQaResultsRequest,
  SubmitMilestoneRequest,
} from "@/types/contract";

/**
 * 작업 확인 요청.
 *
 * 개발자가 작업을 끝내고 의뢰인에게 확인을 부탁합니다. 보낸 시각부터 자동 통과
 * 기한을 셉니다 (R-QA-02). 다시 보내면 기한도 다시 셉니다.
 */
export async function submitMilestone(
  milestoneId: number,
  body: SubmitMilestoneRequest = {},
): Promise<Milestone> {
  const { data } = await backendApi().post<Milestone>(
    BACKEND.MILESTONE_SUBMISSION(milestoneId),
    body,
  );
  return data;
}

/**
 * 확인 결과 저장.
 *
 * 의뢰인이 확인 문장을 직접 해보고 결과를 남깁니다. **다 체크하지 않아도 보낼 수
 * 있습니다** — 3줄 중 2줄만 됐다면 그대로 보내고 개발자가 무엇이 남았는지 봅니다.
 *
 * 작업 상태는 바뀌지 않습니다. 완료 처리는 통과시키기가 맡습니다 (R-QA-01).
 * 체크는 되돌릴 수 없으므로 이번에 체크한 것만 보내면 서버가 더합니다.
 */
export async function saveQaResults(
  milestoneId: number,
  body: SaveQaResultsRequest,
): Promise<Milestone> {
  const { data } = await backendApi().put<Milestone>(
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
  const { data } = await backendApi().post<Milestone>(
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
 * 붙인 시각이 인계 시각으로 남고 의뢰인 화면에서 내려받을 수 있게 됩니다.
 * 다시 붙이면 새 파일로 바뀝니다.
 */
export async function attachDeliverable(
  deliverableId: number,
  fileId: number,
): Promise<Deliverable> {
  const { data } = await backendApi().post<Deliverable>(
    BACKEND.DELIVERABLE_FILE(deliverableId),
    { fileId },
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
  const response = await backendApi().get<ArrayBuffer>(
    BACKEND.DELIVERABLE_FILE(deliverableId),
    { responseType: "arraybuffer" },
  );

  const disposition = String(response.headers["content-disposition"] ?? "");
  const matched = /filename="?([^";]+)"?/.exec(disposition);

  return {
    body: response.data,
    fileName: matched?.[1] ?? "deliverable",
    contentType: String(response.headers["content-type"] ?? "application/octet-stream"),
  };
}
