import "server-only";

import { authenticatedBackendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  CreateIssueRequest,
  Diagnosis,
  Issue,
  IssueListResponse,
  Inspection,
  SaveSurveyAnswersRequest,
  StartDiagnosisRequest,
  Survey,
  UnfinishedSurvey,
  UpdateIssueStatusRequest,
  WriteRequirementsRequest,
} from "@/types/diagnosis";

/** 목록 응답. 목이 `content` 로 감싸 내려줍니다. */
interface UnfinishedSurveyListResponse {
  content: UnfinishedSurvey[];
}

/**
 * 끝내지 않은 설문 목록.
 *
 * 설문을 끝내지 않고 나간 진단만 옵니다. 설문1 아래 「진행 중인 설문」이 이걸로
 * 그려지고, 마지막으로 저장한 시각의 내림차순입니다.
 */
export async function getUnfinishedSurveys(): Promise<UnfinishedSurvey[]> {
  const { data } = await authenticatedBackendApi().get<UnfinishedSurveyListResponse>(
    BACKEND.DIAGNOSES,
  );
  return data.content;
}

/**
 * 진단 시작.
 *
 * 이 요청으로 진단 회차가 열리고 설문지 한 벌이 만들어집니다. 프로젝트 이름을
 * 설문1에서 받으므로 화면에 들어올 때가 아니라 **다음을 누를 때** 부릅니다.
 */
export async function startDiagnosis(body: StartDiagnosisRequest): Promise<Diagnosis> {
  const { data } = await authenticatedBackendApi().post<Diagnosis>(BACKEND.DIAGNOSES, body);
  return data;
}

/**
 * 설문 조회.
 *
 * 이미 답한 보기는 `isSelected` 가 참이라 뒤로 갔다 와도 고른 상태가 보입니다.
 * 설문4 문항은 검사 결과를 올린 뒤에야 생깁니다.
 */
export async function getSurvey(diagnosisId: number): Promise<Survey> {
  const { data } = await authenticatedBackendApi().get<Survey>(BACKEND.SURVEY(diagnosisId));
  return data;
}

/**
 * 설문 답 저장.
 *
 * 보낸 문항만 바뀌고 나머지는 그대로 남습니다. 같은 단계를 다시 보내도 결과가
 * 같아 뒤로 갔다 와서 다시 눌러도 안전합니다. 저장 뒤의 설문지 전체가 옵니다.
 */
export async function saveSurveyAnswers(
  diagnosisId: number,
  body: SaveSurveyAnswersRequest,
): Promise<Survey> {
  const { data } = await authenticatedBackendApi().patch<Survey>(BACKEND.SURVEY_ANSWERS(diagnosisId), body);
  return data;
}

/**
 * 설문 완료.
 *
 * 설문4까지 마쳤다고 알립니다. **답 저장이 완료를 겸하지 않습니다** — 설문4
 * 문항은 검사 결과에서 나오므로, 답이 다 찼는지로 판정하면 설문2까지만 하고도
 * 완료로 찍힙니다. 그래서 끝났다는 것은 화면이 따로 알립니다.
 *
 * 아직 답하지 않은 문항이 남았거나 이미 끝낸 설문이면 409 입니다.
 */
export async function completeSurvey(diagnosisId: number): Promise<Survey> {
  const { data } = await authenticatedBackendApi().post<Survey>(BACKEND.SURVEY_COMPLETE(diagnosisId));
  return data;
}

/**
 * 검사 결과 파일 올리기.
 *
 * 코드가 아니라 사용자가 자기 AI 도구로 돌린 결과 파일만 받습니다.
 * 올리고 나면 이어서 설문 조회로 설문4 문항을 받습니다.
 */
export async function uploadInspection(
  diagnosisId: number,
  file: File,
): Promise<Inspection> {
  const form = new FormData();
  form.append("file", file);

  const { data } = await authenticatedBackendApi().post<Inspection>(
    BACKEND.INSPECTIONS(diagnosisId),
    form,
    // FormData 는 경계 문자열이 필요하므로 공통 JSON 헤더를 지웁니다.
    { headers: { "Content-Type": undefined } },
  );
  return data;
}

/** 내려받은 검사 파일. 파일 이름은 Content-Disposition 에 담겨 옵니다. */
export interface ScanFile {
  body: ArrayBuffer;
  fileName: string;
  contentType: string;
}

/**
 * 검사 파일 내려받기.
 *
 * 의뢰인이 자기 AI 도구에서 돌릴 파일입니다. 이 파일을 도구에 읽히면 결과 파일이
 * 만들어지고, 그 결과 파일만 다시 올립니다. 소스 코드는 올리지 않습니다.
 */
export async function downloadScanFile(diagnosisId: number): Promise<ScanFile> {
  const response = await authenticatedBackendApi().get<ArrayBuffer>(BACKEND.SCAN_FILE(diagnosisId), {
    responseType: "arraybuffer",
  });

  const disposition = String(response.headers["content-disposition"] ?? "");
  const matched = /filename="?([^";]+)"?/.exec(disposition);

  return {
    body: response.data,
    fileName: matched?.[1] ?? "komit-scan.md",
    contentType: String(response.headers["content-type"] ?? "text/markdown"),
  };
}

/** 진단 머리. 준비도 점수와 검사 시각, 프로젝트가 옵니다. */
export async function getDiagnosis(diagnosisId: number): Promise<Diagnosis> {
  const { data } = await authenticatedBackendApi().get<Diagnosis>(BACKEND.DIAGNOSIS(diagnosisId));
  return data;
}

/** 검사에서 나온 결함 목록. 진단서와 요구사항 선택이 함께 씁니다. */
export async function getIssues(diagnosisId: number): Promise<Issue[]> {
  const { data } = await authenticatedBackendApi().get<IssueListResponse>(BACKEND.ISSUES(diagnosisId), {
    // 진단서와 요청서는 일부 페이지만 보여주면 분류와 요구사항이 조용히 사라집니다.
    // 목·실서버 계약이 허용하는 최대 크기로 한 회차의 결함을 모두 받습니다.
    params: { page: 1, size: 100 },
  });
  return data.content;
}

/** 요구사항 직접 추가. 검사에서 찾지 못한 것을 의뢰인이 적습니다. */
export async function createIssue(
  diagnosisId: number,
  body: CreateIssueRequest,
): Promise<Issue> {
  const { data } = await authenticatedBackendApi().post<Issue>(BACKEND.ISSUES(diagnosisId), body);
  return data;
}

/** 요구사항 해결 처리 · 되돌리기. */
export async function updateIssueStatus(
  issueId: number,
  body: UpdateIssueStatusRequest,
): Promise<Issue> {
  const { data } = await authenticatedBackendApi().patch<Issue>(BACKEND.ISSUE(issueId), body);
  return data;
}

/**
 * 요청서 저장.
 *
 * 요청서를 통째로 받아 보낸 내용으로 맞춥니다. 보낸 목록에 없는 기존 요구사항은
 * 요청서에서 뺀 것으로 보고, 저장된 뒤의 요구사항 전부를 돌려줍니다.
 *
 * 이미 계약에 들어간 요구사항을 빼면 409 입니다.
 */
export async function writeRequirements(
  diagnosisId: number,
  body: WriteRequirementsRequest,
): Promise<Issue[]> {
  const { data } = await authenticatedBackendApi().put<IssueListResponse>(
    BACKEND.REQUIREMENTS(diagnosisId),
    body,
  );
  return data.content;
}
