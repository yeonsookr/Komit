import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/types/api";
import { GET } from "./route";

const { downloadDeliverableMaterial } = vi.hoisted(() => ({ downloadDeliverableMaterial: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/services", () => ({ downloadDeliverableMaterial }));

beforeEach(() => {
  downloadDeliverableMaterial.mockReset();
});

/** 백엔드 본문처럼 조각으로 나뉘어 오는 스트림. 다 읽혔는지도 봅니다. */
function chunkedStream(chunks: number[][]) {
  const state = { pulled: 0 };
  const body = new ReadableStream<Uint8Array>({
    pull(controller) {
      const next = chunks[state.pulled];
      state.pulled += 1;
      if (next === undefined) controller.close();
      else controller.enqueue(new Uint8Array(next));
    },
  });
  return { body, state };
}

const request = () => new Request("http://localhost/api/deliverable-materials/501/file");
const params = (deliverableMaterialId: string) => ({ params: Promise.resolve({ deliverableMaterialId }) });

describe("GET /api/deliverable-materials/[deliverableMaterialId]/file", () => {
  it("파일을 스트림 그대로 넘기고 한글 이름을 브라우저가 읽을 수 있게 싣는다", async () => {
    const { body, state } = chunkedStream([[1, 2], [3]]);
    downloadDeliverableMaterial.mockResolvedValueOnce({
      body,
      fileName: "인계 소스.zip",
      contentType: "application/zip",
      contentLength: "3",
    });

    const response = await GET(request(), params("501"));

    expect(response.status).toBe(200);
    // 응답을 만든 시점에는 본문을 읽지 않습니다. 브라우저가 읽는 만큼 백엔드에서 당겨 옵니다.
    expect(state.pulled).toBeLessThan(3);
    expect(response.headers.get("Content-Type")).toBe("application/zip");
    expect(response.headers.get("Content-Length")).toBe("3");
    expect(response.headers.get("Content-Disposition")).toContain(
      "filename*=UTF-8''%EC%9D%B8%EA%B3%84%20%EC%86%8C%EC%8A%A4.zip",
    );
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
    expect(downloadDeliverableMaterial).toHaveBeenCalledWith(501);
  });

  it("백엔드가 크기를 주지 않으면 Content-Length 를 싣지 않는다", async () => {
    downloadDeliverableMaterial.mockResolvedValueOnce({
      body: chunkedStream([[9]]).body,
      fileName: "a.zip",
      contentType: "application/octet-stream",
      contentLength: null,
    });

    const response = await GET(request(), params("501"));

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Length")).toBeNull();
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(new Uint8Array([9]));
  });

  it("지급 전 PAID 파일이면 백엔드의 409 사유를 그대로 전한다", async () => {
    downloadDeliverableMaterial.mockRejectedValueOnce(
      new ApiError(409, "DELIVERABLE_ACCESS_LOCKED", "대금 지급이 완료된 뒤에 내려받을 수 있습니다."),
    );

    const response = await GET(request(), params("502"));

    expect(response.status).toBe(409);
    expect(await response.json()).toMatchObject({
      code: "DELIVERABLE_ACCESS_LOCKED",
      message: "대금 지급이 완료된 뒤에 내려받을 수 있습니다.",
    });
  });

  it("백엔드에 닿지 못하면 공통 오류 모양으로 답한다", async () => {
    downloadDeliverableMaterial.mockRejectedValueOnce(
      new ApiError(0, "NETWORK_ERROR", "서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    );

    const response = await GET(request(), params("503"));

    expect(response.status).toBe(502);
    expect(await response.json()).toMatchObject({ code: "NETWORK_ERROR" });
  });

  it("숫자가 아닌 식별자는 백엔드에 묻지 않고 없다고 답한다", async () => {
    const response = await GET(request(), params("abc"));

    expect(response.status).toBe(404);
    expect(downloadDeliverableMaterial).not.toHaveBeenCalled();
  });
});
