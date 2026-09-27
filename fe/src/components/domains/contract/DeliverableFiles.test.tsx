import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Deliverable } from "@/types/contract";
import {
  DeliverableFiles,
  DeliverableResources,
  DeliverableRow,
  groupMaterials,
  hasNoMaterial,
  materialCount,
  numbered,
  submitInput,
} from "./DeliverableFiles";

function deliverable(
  materials: Deliverable["materials"],
  fileId: number | null = null,
  override: Partial<Deliverable> = {},
): Deliverable {
  return {
    deliverableId: 401,
    title: "완성된 웹사이트",
    fileId,
    deliveredAt: null,
    isContractual: true,
    materials,
    ...override,
  };
}

/** 개발자 화면의 산출물 한 줄. */
function row(override: Partial<Parameters<typeof DeliverableRow>[0]> = {}) {
  return renderToStaticMarkup(
    <DeliverableRow
      deliverable={deliverable([])}
      received
      paid={false}
      locked={false}
      busy={null}
      unattached={null}
      onAddUrl={async () => true}
      {...override}
    />,
  );
}

/** 잠긴 입력·선택·버튼 수. 선택 칸의 안내 항목(`<option disabled>`)은 세지 않습니다. */
function disabledCount(html: string): number {
  return html.match(/<(?:input|select|button)[^>]* disabled=""/g)?.length ?? 0;
}

/** 이름표로 찾은 태그 하나. 속성 순서와 상관없이 봅니다. */
function tag(html: string, element: string, label: string): string {
  return html.match(new RegExp(`<${element}[^>]*aria-label="${label}"[^>]*>`))?.[0] ?? "";
}

const FILE_BUTTON = "완성된 웹사이트 파일 추가";
const TITLE_INPUT = "산출물 항목 이름";

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

    expect(html).toContain("해금 파일 · 지급 완료 후 받을 수 있습니다");
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
      <DeliverableFiles deliverables={[]} received onAddDeliverable={async () => true} />,
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

  it("자료를 URL·인수 시험 파일·해금 파일 순서로 묶고, 같은 종류가 여럿이면 번호를 붙인다", () => {
    const secondPaid = { ...paidFile, deliverableMaterialId: 505, fileId: 73 };
    const materials = [paidFile, testFile, url, secondPaid];

    const groups = groupMaterials(materials);
    expect(groups.urls.map((item) => item.deliverableMaterialId)).toEqual([503]);
    expect(groups.testFiles.map((item) => item.deliverableMaterialId)).toEqual([501]);
    expect(groups.paidFiles.map((item) => item.deliverableMaterialId)).toEqual([502, 505]);

    const html = renderToStaticMarkup(<DeliverableResources deliverable={deliverable(materials)} paid />);
    expect(html.indexOf("인수 시험 URL")).toBeLessThan(html.indexOf("인수 시험 파일 받기"));
    expect(html.indexOf("인수 시험 파일 받기")).toBeLessThan(html.indexOf("해금 파일 1 받기"));
    expect(html).toContain("해금 파일 2 받기");
    // 등록 순서가 번호 순서다.
    expect(html.indexOf("/api/deliverable-materials/502/file")).toBeLessThan(
      html.indexOf("/api/deliverable-materials/505/file"),
    );
  });

  it("지급 전 의뢰인에게 해금 파일이 여럿이면 번호로 등록 사실만 알린다", () => {
    const secondPaid = { ...paidFile, deliverableMaterialId: 505, fileId: 73 };
    const html = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([paidFile, secondPaid])} />);

    expect(html).toContain("해금 파일 1 · 지급 완료 후 받을 수 있습니다");
    expect(html).toContain("해금 파일 2 · 지급 완료 후 받을 수 있습니다");
    expect(html).not.toContain("/api/deliverable-materials/");
  });

  it("개발자 화면의 해금 파일은 지급 전이면 의뢰인이 아직 못 받는다고 적는다", () => {
    const before = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([paidFile])} received />);
    const after = renderToStaticMarkup(<DeliverableResources deliverable={deliverable([paidFile])} received paid />);

    expect(before).toContain("의뢰인은 지급 완료 후 받습니다");
    expect(after).not.toContain("의뢰인은 지급 완료 후 받습니다");
    expect(after).toContain("/api/deliverable-materials/502/file");
  });

  it("번호는 같은 종류가 여럿일 때만 붙인다", () => {
    expect(numbered("해금 파일", 0, 1)).toBe("해금 파일");
    expect(numbered("해금 파일", 1, 3)).toBe("해금 파일 2");
  });

  it("접힘 줄은 산출물 수와 자료 수를 따로 센다", () => {
    const list = [
      deliverable([url, testFile, paidFile]),
      deliverable([], 88, { deliverableId: 402, title: "운영 설명서" }),
      deliverable([], null, { deliverableId: 403, title: "빈 항목" }),
    ];

    expect(materialCount(list)).toBe(4);
    const html = renderToStaticMarkup(<DeliverableFiles deliverables={list} received={false} />);
    expect(html).toMatch(/산출물 <span[^>]*>3<\/span>개 · 자료 <span[^>]*>4<\/span>개/);
  });

  it("체결 뒤 더한 산출물에는 표시를 붙인다", () => {
    expect(row({ deliverable: deliverable([], null, { isContractual: false }) })).toContain("계약 뒤 추가");
    expect(row()).not.toContain("계약 뒤 추가");
  });

  it("용도를 고르기 전에는 파일을 고를 수 없고 무엇을 고를지 알린다", () => {
    const before = row();
    expect(before).toContain("용도를 먼저 고르면 파일을 고를 수 있습니다.");
    expect(tag(before, "button", FILE_BUTTON)).toContain(' disabled=""');

    const test = row({ stage: "TEST" });
    expect(test).toContain("인수 시험용은 지급 전에도 의뢰인이 받아 시험합니다.");
    expect(tag(test, "button", FILE_BUTTON)).not.toContain(' disabled=""');

    expect(row({ stage: "PAID" })).toContain("지급 후 해금 파일은 대금 지급이 끝나야 의뢰인에게 열립니다.");
  });

  it("잠기면 용도·파일·URL 입력을 모두 잠근다", () => {
    const open = row({ stage: "TEST" });
    const locked = row({ stage: "TEST", locked: true });

    // 열려 있을 때는 빈 URL 칸의 보내기 버튼만 잠겨 있다.
    expect(disabledCount(open)).toBe(1);
    // 용도 선택, 숨은 파일 칸, 파일 추가, URL 칸, URL 보내기.
    expect(disabledCount(locked)).toBe(5);
  });

  it("로딩은 진행 중인 요청의 열쇠가 같은 버튼에만 붙는다", () => {
    const uploading = row({ stage: "TEST", locked: true, busy: "file:401" });
    const otherKind = row({ stage: "TEST", locked: true, busy: "milestone:401" });

    expect(uploading.match(/aria-busy="true"/g)).toHaveLength(1);
    expect(tag(uploading, "button", FILE_BUTTON)).toContain('aria-busy="true"');
    // 작업 식별자가 산출물 식별자와 같은 숫자여도 산출물 버튼에 로딩이 붙지 않는다.
    expect(otherKind).not.toContain('aria-busy="true"');
  });

  it("URL 과 산출물 이름은 R-QA-06 의 길이까지만 받는다", () => {
    expect(tag(row(), "input", "완성된 웹사이트 인수 시험 URL")).toContain('maxLength="2000"');
    const html = renderToStaticMarkup(<DeliverableFiles deliverables={[]} received onAddDeliverable={async () => true} />);
    expect(tag(html, "input", TITLE_INPUT)).toContain('maxLength="100"');
  });

  it("붙이지 못한 파일이 있으면 그 산출물에만 다시 붙이기를 보인다", () => {
    const html = row({ unattached: { deliverableId: 401, fileName: "source.zip" } });

    expect(html).toContain("‘source.zip’ 을 올렸지만 붙이지 못했습니다.");
    expect(html).toContain("다시 붙이기");
    expect(row()).not.toContain("다시 붙이기");
  });

  it("등록이 닫혔으면 접혀 있어도 까닭을 보이고 산출물 추가를 잠근다", () => {
    const reasons = {
      HANDOVER_NOT_OPEN: "일반 작업을 모두 통과한 뒤에 인계 자료를 등록할 수 있습니다.",
      HANDOVER_UNDER_REVIEW: "최종 인수 시험 중이라 인계 자료를 바꿀 수 없습니다.",
      FINAL_ACCEPTANCE_PASSED: "최종 인수 시험을 통과해 인계 자료를 더 바꿀 수 없습니다.",
    } as const;

    for (const [registration, reason] of Object.entries(reasons)) {
      const collapsed = renderToStaticMarkup(
        <DeliverableFiles
          deliverables={[deliverable([testFile])]}
          received
          registration={registration as keyof typeof reasons}
          onAddDeliverable={async () => true}
        />,
      );
      expect(collapsed).toContain('aria-expanded="false"');
      expect(collapsed).toContain(reason);

      const empty = renderToStaticMarkup(
        <DeliverableFiles
          deliverables={[]}
          received
          registration={registration as keyof typeof reasons}
          onAddDeliverable={async () => true}
        />,
      );
      expect(tag(empty, "input", TITLE_INPUT)).toContain(' disabled=""');
    }

    const open = renderToStaticMarkup(<DeliverableFiles deliverables={[]} received onAddDeliverable={async () => true} />);
    expect(tag(open, "input", TITLE_INPUT)).not.toBe("");
    expect(tag(open, "input", TITLE_INPUT)).not.toContain(' disabled=""');
    for (const reason of Object.values(reasons)) expect(open).not.toContain(reason);
  });

  it("의뢰인 화면에는 닫힌 까닭을 보이지 않는다", () => {
    const html = renderToStaticMarkup(
      <DeliverableFiles deliverables={[deliverable([testFile])]} received={false} registration="HANDOVER_UNDER_REVIEW" />,
    );

    expect(html).not.toContain("인계 자료를 바꿀 수 없습니다");
  });

  it("입력은 성공한 뒤에만 비우고, 실패하면 적은 값을 남긴다", async () => {
    expect(await submitInput(" https://demo.example.com ", async () => true)).toEqual({ value: "", error: null });
    expect(await submitInput("https://demo.example.com", async () => false)).toEqual({
      value: "https://demo.example.com",
      error: null,
    });
  });

  it("형식이 틀리면 보내지 않고 사유를 돌려준다. 빈 값은 보내지 않는다", async () => {
    const send = vi.fn(async () => true);

    expect(await submitInput("demo.example.com", send, () => "주소 형식")).toEqual({
      value: "demo.example.com",
      error: "주소 형식",
    });
    expect(await submitInput("   ", send)).toEqual({ value: "   ", error: null });
    expect(send).not.toHaveBeenCalled();
  });
});
