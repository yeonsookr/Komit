import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Deliverable } from "@/types/contract";
import { DeliverableResources, hasNoMaterial } from "./DeliverableFiles";

function deliverable(materials: Deliverable["materials"], fileId: number | null = null): Deliverable {
  return {
    deliverableId: 401,
    title: "완성된 웹사이트",
    fileId,
    deliveredAt: null,
    isContractual: true,
    materials,
  };
}

const testFile = {
  deliverableMaterialId: 501,
  fileId: 71,
  url: null,
  accessStage: "TEST" as const,
  deliveredAt: "2026-09-10T06:00:00Z",
};
const paidFile = { ...testFile, deliverableMaterialId: 502, fileId: 72, accessStage: "PAID" as const };
const url = {
  deliverableMaterialId: 503,
  fileId: null,
  url: "https://demo.example.com",
  accessStage: "TEST" as const,
  deliveredAt: "2026-09-10T06:02:00Z",
};

describe("DeliverableFiles", () => {
  it("같은 산출물의 URL 여러 개와 시험·해금 파일을 모두 표시한다", () => {
    const secondUrl = { ...url, deliverableMaterialId: 504, url: "https://staging.example.com" };

    const html = renderToStaticMarkup(
      <DeliverableResources deliverable={deliverable([url, testFile, paidFile, secondUrl])} />,
    );

    expect(html).toContain("https://demo.example.com");
    expect(html).toContain("https://staging.example.com");
    expect(html).toContain("인수 시험 파일 받기");
    expect(html).toContain("지급 완료 후 파일 받기");
    expect(html).toContain("/api/deliverable-materials/501/file");
    expect(html).toContain("/api/deliverable-materials/502/file");
    // URL 자료에는 받을 파일이 없어 다운로드 경로를 만들지 않는다.
    expect(html).not.toContain("/api/deliverable-materials/503/file");
  });

  it("예전 단일 파일만 있으면 지급 후 받기로 보여 준다", () => {
    const html = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([], 88)} />);

    expect(html).toContain("/api/deliverables/401/file");
    expect(html).toContain("지급 완료 후 기존 파일 받기");
  });

  it("URL 만 있어도 자료가 있는 산출물이다", () => {
    expect(hasNoMaterial(deliverable([url]))).toBe(false);
    expect(hasNoMaterial(deliverable([], 88))).toBe(false);
    expect(hasNoMaterial(deliverable([]))).toBe(true);
  });
});
