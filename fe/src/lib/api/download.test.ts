import { describe, expect, it } from "vitest";
import { FALLBACK_FILE_NAME, attachmentDisposition, fileNameFrom } from "./download";

describe("fileNameFrom", () => {
  it("백엔드가 함께 실은 UTF-8 이름을 먼저 읽는다", () => {
    expect(
      fileNameFrom(
        "attachment; filename=\"=?UTF-8?Q?=EC=9D=B8=EA=B3=84.zip?=\"; filename*=UTF-8''%EC%9D%B8%EA%B3%84%20%EC%86%8C%EC%8A%A4.zip",
      ),
    ).toBe("인계 소스.zip");
  });

  it("UTF-8 이름이 없으면 일반 이름을 읽고, 둘 다 없으면 기본 이름을 쓴다", () => {
    expect(fileNameFrom('attachment; filename="komit-deliverable.zip"')).toBe("komit-deliverable.zip");
    expect(fileNameFrom("attachment")).toBe(FALLBACK_FILE_NAME);
    expect(fileNameFrom("")).toBe(FALLBACK_FILE_NAME);
  });

  it("UTF-8 이름이 잘못 인코딩돼 있으면 일반 이름으로 넘어간다", () => {
    expect(fileNameFrom("attachment; filename=\"source.zip\"; filename*=UTF-8''%E0%A4%A")).toBe("source.zip");
  });
});

describe("attachmentDisposition", () => {
  it("한글 이름을 UTF-8 칸에 싣고, 일반 칸에는 깨지지 않는 대체 이름을 둔다", () => {
    const disposition = attachmentDisposition('인계 "소스".zip');

    expect(disposition).toBe("attachment; filename=\"__ ____.zip\"; filename*=UTF-8''%EC%9D%B8%EA%B3%84%20%22%EC%86%8C%EC%8A%A4%22.zip");
    expect(fileNameFrom(disposition)).toBe('인계 "소스".zip');
  });

  it("영문 이름은 그대로 싣는다", () => {
    expect(attachmentDisposition("source(1).zip")).toBe(
      "attachment; filename=\"source(1).zip\"; filename*=UTF-8''source%281%29.zip",
    );
  });
});
