"use client";

import { fileNameFrom } from "@/lib/api/download";

/**
 * 브라우저에서 인계 파일을 받습니다.
 *
 * 링크로 열면 권한이 없을 때 오류 본문만 담긴 창이 열립니다. 요청으로 받아 성공하면 파일로 저장하고,
 * 실패하면 서버가 준 사유를 돌려줘 화면이 알리게 합니다.
 */

/** 서버가 사유를 주지 않았거나 요청이 닿지 않았을 때의 문구. */
export const DOWNLOAD_FAILED_MESSAGE = "파일을 받지 못했습니다. 잠시 후 다시 시도해 주십시오.";

/**
 * 저장을 시작한 뒤 임시 주소를 해제하기까지 기다리는 시간. 바로 해제하면 일부 브라우저에서
 * 저장이 시작되기 전에 끊깁니다. 규칙 값이 아닌 브라우저 동작 여유입니다.
 */
const REVOKE_DELAY_MS = 1_000;

export type DownloadResult = { ok: true; blob: Blob; fileName: string } | { ok: false; message: string };

/** 실패 응답 본문에서 알릴 문구를 고릅니다. 서버의 `message` 가 비어 있으면 기본 문구를 씁니다. */
export function failureMessage(body: unknown): string {
  if (typeof body === "object" && body !== null && "message" in body) {
    const { message } = body as { message: unknown };
    if (typeof message === "string" && message.trim() !== "") return message;
  }
  return DOWNLOAD_FAILED_MESSAGE;
}

/** 응답을 저장할 파일이나 알릴 문구로 풉니다. 파일 이름은 Content-Disposition 에서 읽습니다. */
export async function readDownload(response: Response): Promise<DownloadResult> {
  if (response.ok) {
    // 머리를 받은 뒤 본문을 받다 끊기면 blob() 이 거절됩니다. 예외로 새지 않게 실패로 돌려줍니다.
    try {
      return {
        ok: true,
        blob: await response.blob(),
        fileName: fileNameFrom(response.headers.get("content-disposition") ?? ""),
      };
    } catch (cause) {
      console.error("[download] 본문을 받다 끊김", cause);
      return { ok: false, message: DOWNLOAD_FAILED_MESSAGE };
    }
  }
  // 게이트웨이 오류처럼 JSON 이 아닌 본문도 옵니다. 읽지 못하면 기본 문구를 씁니다.
  const body: unknown = await response.json().catch(() => null);
  return { ok: false, message: failureMessage(body) };
}

/** 파일을 요청합니다. 요청이 닿지 않아도 예외를 던지지 않고 실패로 돌려줍니다. */
export async function requestDownload(url: string, fetcher: typeof fetch = fetch): Promise<DownloadResult> {
  let response: Response;
  try {
    response = await fetcher(url);
  } catch (cause) {
    console.error("[download] 요청이 닿지 않음", cause);
    return { ok: false, message: DOWNLOAD_FAILED_MESSAGE };
  }
  return readDownload(response);
}

/** 받은 파일을 브라우저 저장으로 넘깁니다. */
export function saveBlob(blob: Blob, fileName: string): void {
  const href = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = href;
  anchor.download = fileName;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(href), REVOKE_DELAY_MS);
}

/**
 * 받기 한 번. 성공하면 저장하고 true, 실패하면 사유를 `notify` 로 넘기고 false 를 돌려줍니다.
 *
 * @param url 하위 경로(`appPath`)를 붙인 주소
 */
export async function downloadFromBrowser(
  url: string,
  notify: (message: string) => void,
  { fetcher = fetch, save = saveBlob }: { fetcher?: typeof fetch; save?: typeof saveBlob } = {},
): Promise<boolean> {
  const result = await requestDownload(url, fetcher);
  if (!result.ok) {
    notify(result.message);
    return false;
  }
  save(result.blob, result.fileName);
  return true;
}

/**
 * 진행 중이면 다시 부르지 않는 함수로 감쌉니다. 버튼이 잠기기 전의 두 번째 클릭도 막습니다.
 * 앞의 요청이 끝나면(실패해도) 다시 부를 수 있습니다.
 */
export function oneAtATime(task: () => Promise<unknown>): () => Promise<void> {
  let running = false;
  return async () => {
    if (running) return;
    running = true;
    try {
      await task();
    } finally {
      running = false;
    }
  };
}
