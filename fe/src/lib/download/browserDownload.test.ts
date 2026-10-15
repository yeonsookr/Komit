import { describe, expect, it, vi } from "vitest";
import { FALLBACK_FILE_NAME } from "@/lib/api/download";
import {
  DOWNLOAD_FAILED_MESSAGE,
  downloadFromBrowser,
  failureMessage,
  oneAtATime,
  readDownload,
  requestDownload,
} from "./browserDownload";

function fileResponse(disposition?: string): Response {
  return new Response("zip-bytes", {
    status: 200,
    headers: disposition ? { "Content-Disposition": disposition } : {},
  });
}

function errorResponse(status: number, body: unknown): Response {
  return new Response(typeof body === "string" ? body : JSON.stringify(body), {
    status,
    headers: { "Content-Type": typeof body === "string" ? "text/html" : "application/json" },
  });
}

describe("browserDownload", () => {
  it("성공하면 파일 본문과 Content-Disposition 의 한글 이름을 돌려준다", async () => {
    const result = await readDownload(
      fileResponse("attachment; filename=\"_.zip\"; filename*=UTF-8''%EC%86%8C%EC%8A%A4.zip"),
    );

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.fileName).toBe("소스.zip");
    expect(await result.blob.text()).toBe("zip-bytes");
  });

  it("이름이 없으면 기본 이름으로 저장한다", async () => {
    const result = await readDownload(fileResponse());

    expect(result).toMatchObject({ ok: true, fileName: FALLBACK_FILE_NAME });
  });

  it("실패하면 서버가 준 사유를 그대로 돌려준다", async () => {
    const result = await readDownload(
      errorResponse(409, { code: "DELIVERABLE_LOCKED", message: "지급 완료 후 받을 수 있습니다." }),
    );

    expect(result).toEqual({ ok: false, message: "지급 완료 후 받을 수 있습니다." });
  });

  it("사유가 없거나 JSON 이 아닌 실패 본문이면 기본 문구를 쓴다", async () => {
    expect(await readDownload(errorResponse(504, "<html>Gateway Timeout</html>"))).toEqual({
      ok: false,
      message: DOWNLOAD_FAILED_MESSAGE,
    });
    expect(await readDownload(errorResponse(500, { code: "X" }))).toEqual({
      ok: false,
      message: DOWNLOAD_FAILED_MESSAGE,
    });
    expect(failureMessage({ message: "  " })).toBe(DOWNLOAD_FAILED_MESSAGE);
    expect(failureMessage({ message: 3 })).toBe(DOWNLOAD_FAILED_MESSAGE);
    expect(failureMessage(null)).toBe(DOWNLOAD_FAILED_MESSAGE);
  });

  it("본문을 받다 끊기면 예외 대신 기본 문구로 실패한다", async () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      const response = new Response("partial", { status: 200 });
      vi.spyOn(response, "blob").mockRejectedValue(new TypeError("network error"));

      expect(await readDownload(response)).toEqual({ ok: false, message: DOWNLOAD_FAILED_MESSAGE });
    } finally {
      error.mockRestore();
    }
  });

  it("요청이 닿지 않으면 예외 대신 기본 문구로 실패한다", async () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      const fetcher = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));

      expect(await requestDownload("/api/deliverable-materials/501/file", fetcher)).toEqual({
        ok: false,
        message: DOWNLOAD_FAILED_MESSAGE,
      });
    } finally {
      error.mockRestore();
    }
  });

  it("받은 주소를 그대로 요청한다. 하위 경로는 부르는 쪽이 붙인다", async () => {
    const fetcher = vi.fn().mockResolvedValue(fileResponse());

    await requestDownload("/test/api/deliverable-materials/501/file", fetcher);

    expect(fetcher).toHaveBeenCalledWith("/test/api/deliverable-materials/501/file");
  });

  it("성공하면 저장만 하고, 실패하면 저장하지 않고 사유를 알린다", async () => {
    const save = vi.fn();
    const notify = vi.fn();

    const ok = await downloadFromBrowser("/api/deliverables/401/file", notify, {
      fetcher: vi.fn().mockResolvedValue(fileResponse('attachment; filename="manual.pdf"')),
      save,
    });
    expect(ok).toBe(true);
    expect(save).toHaveBeenCalledWith(expect.any(Blob), "manual.pdf");
    expect(notify).not.toHaveBeenCalled();

    save.mockClear();
    const failed = await downloadFromBrowser("/api/deliverables/401/file", notify, {
      fetcher: vi.fn().mockResolvedValue(errorResponse(403, { message: "계약 당사자만 받을 수 있습니다." })),
      save,
    });
    expect(failed).toBe(false);
    expect(save).not.toHaveBeenCalled();
    expect(notify).toHaveBeenCalledWith("계약 당사자만 받을 수 있습니다.");
  });

  it("받는 중에 다시 누르면 요청하지 않고, 끝나면(실패해도) 다시 받을 수 있다", async () => {
    let finish: () => void = () => {};
    const task = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    const run = oneAtATime(task);

    const first = run();
    await run();
    expect(task).toHaveBeenCalledTimes(1);

    finish();
    await first;
    task.mockRejectedValueOnce(new Error("실패"));
    await expect(run()).rejects.toThrow("실패");
    const third = run();
    expect(task).toHaveBeenCalledTimes(3);
    finish();
    await third;
  });
});
