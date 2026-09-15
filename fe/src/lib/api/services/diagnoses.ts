import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import type {
  Diagnosis,
  Inspection,
  SaveSurveyAnswersRequest,
  StartDiagnosisRequest,
  Survey,
} from "@/types/diagnosis";

/**
 * 진단 시작.
 *
 * 이 요청으로 진단 회차가 열리고 설문지 한 벌이 만들어집니다. 프로젝트 이름을
 * 설문1에서 받으므로 화면에 들어올 때가 아니라 **다음을 누를 때** 부릅니다.
 */
export async function startDiagnosis(body: StartDiagnosisRequest): Promise<Diagnosis> {
  const { data } = await backendApi.post<Diagnosis>(BACKEND.DIAGNOSES, body);
  return data;
}

/**
 * 설문 조회.
 *
 * 이미 답한 보기는 `isSelected` 가 참이라 뒤로 갔다 와도 고른 상태가 보입니다.
 * 설문4 문항은 검사 결과를 올린 뒤에야 생깁니다.
 */
export async function getSurvey(diagnosisId: number): Promise<Survey> {
  const { data } = await backendApi.get<Survey>(BACKEND.SURVEY(diagnosisId));
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
  const { data } = await backendApi.patch<Survey>(BACKEND.SURVEY_ANSWERS(diagnosisId), body);
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

  const { data } = await backendApi.post<Inspection>(
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
  const response = await backendApi.get<ArrayBuffer>(BACKEND.SCAN_FILE(diagnosisId), {
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
