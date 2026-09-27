import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Deliverable } from "@/types/contract";
import { DeliverableFiles, DeliverableResources, hasNoMaterial } from "./DeliverableFiles";

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
      <DeliverableResources deliverable={deliverable([url, testFile, paidFile, secondUrl])} paid />,
    );

    expect(html).toContain('href="https://demo.example.com"');
    expect(html).toContain('href="https://staging.example.com"');
    expect(html).toContain("인수 시험 파일 받기");
    expect(html).toContain("해금 파일 받기");
    expect(html).toContain("/api/deliverable-materials/501/file");
    expect(html).toContain("/api/deliverable-materials/502/file");
    // URL 자료에는 받을 파일이 없어 다운로드 경로를 만들지 않는다.
    expect(html).not.toContain("/api/deliverable-materials/503/file");
  });

  it("지급 전 의뢰인에게는 해금 파일 링크 대신 받을 수 있는 때를 알리고, 시험 파일은 그대로 준다", () => {
    const html = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([testFile, paidFile])} />);

    expect(html).toContain("해금 파일은 지급 완료 후 받을 수 있습니다");
    expect(html).not.toContain("/api/deliverable-materials/502/file");
    expect(html).toContain("/api/deliverable-materials/501/file");
  });

  it("올린 개발자는 지급 전에도 해금 파일을 받는다", () => {
    const html = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([paidFile])} received />);

    expect(html).toContain("/api/deliverable-materials/502/file");
  });

  it("예전 단일 파일은 해금 파일로 다룬다", () => {
    const locked = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([], 88)} />);
    const paid = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([], 88)} paid />);

    expect(locked).toContain("기존 파일은 지급 완료 후 받을 수 있습니다");
    expect(locked).not.toContain("/api/deliverables/401/file");
    expect(paid).toContain("/api/deliverables/401/file");
  });

  it("산출물이 없어도 개발자에게는 항목을 더할 칸을 펼쳐 보이고, 의뢰인에게는 아무것도 보이지 않는다", () => {
    const developer = renderToStaticMarkup(
      <DeliverableFiles deliverables={[]} received onAddDeliverable={() => undefined} />,
    );
    const client = renderToStaticMarkup(<DeliverableFiles deliverables={[]} received={false} />);

    expect(developer).toContain("산출물 추가");
    expect(developer).toContain('aria-expanded="true"');
    expect(client).toBe("");
  });

  it("다운로드 링크는 FE 배포의 하위 경로를 붙인다", () => {
    vi.stubEnv("NEXT_PUBLIC_BASE_PATH", "/test");
    try {
      const html = renderToStaticMarkup(
        <DeliverableResources deliverable={deliverable([testFile, paidFile], 88)} paid />,
      );

      expect(html).toContain('href="/test/api/deliverable-materials/501/file"');
      expect(html).toContain('href="/test/api/deliverable-materials/502/file"');
    } finally {
      vi.unstubAllEnvs();
    }
    const legacy = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([], 88)} paid />);
    vi.stubEnv("NEXT_PUBLIC_BASE_PATH", "/test");
    try {
      expect(renderToStaticMarkup(<DeliverableResources deliverable={deliverable([], 88)} paid />))
        .toContain('href="/test/api/deliverables/401/file"');
    } finally {
      vi.unstubAllEnvs();
    }
    expect(legacy).toContain('href="/api/deliverables/401/file"');
  });

  it("URL 만 있어도 자료가 있는 산출물이다", () => {
    expect(hasNoMaterial(deliverable([url]))).toBe(false);
    expect(hasNoMaterial(deliverable([], 88))).toBe(false);
    expect(hasNoMaterial(deliverable([]))).toBe(true);
  });
});
