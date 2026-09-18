"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { Callout, Modal, ModalBody, ModalFooter, ModalHeader } from "@/components/feedback";
import { requestContractTemplatePreview } from "@/lib/admin/requests";
import { ApiError } from "@/types/api";
import type { ContractTemplatePreview as Preview, ContractTemplateType } from "@/types/admin";

/**
 * 미리보기 문서에 거는 제약.
 *
 * 본문은 운영자가 적은 HTML 이고 서버가 자리표시자만 채워 돌려줍니다. 서버가
 * 무해하게 만들어 준다는 계약이 아직 없으므로(`.omx/plans/area9-console-implementation.md`
 * 3장) 화면에 그대로 꽂지 않고 격리합니다 — 두 겹입니다.
 *
 * 1. `sandbox=""` : 스크립트·폼·같은 출처 접근을 전부 끕니다.
 * 2. 이 CSP      : 바깥에서 무엇도 불러오지 못하게 합니다.
 *
 * 한 겹이 뚫려도 다른 겹이 남도록 둘 다 둡니다.
 */
const PREVIEW_CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:";

function previewDocument(html: string): string {
  return `<!doctype html>
<html lang="ko">
  <head>
    <meta charset="utf-8" />
    <meta http-equiv="Content-Security-Policy" content="${PREVIEW_CSP}" />
    <style>
      /*
        이 문서만 색을 hex 로 적습니다. sandbox iframe 은 부모와 출처가 끊겨 있어
        앱의 CSS 변수가 닿지 않습니다. 값은 tokens.css 의 --k-fg 와 같게 둡니다.
      */
      body { margin: 0; padding: 24px; color: #111019; font: 14px/1.75 system-ui, sans-serif; }
      h1, h2, h3 { margin: 1.4em 0 .6em; line-height: 1.35; }
      h1:first-child, h2:first-child, h3:first-child { margin-top: 0; }
      p { margin: .75em 0; }
    </style>
  </head>
  <body>${html}</body>
</html>`;
}

export interface ContractTemplatePreviewProps {
  /** 어떤 유형으로 채워 볼지. 유형마다 채울 수 있는 자리표시자가 다릅니다. */
  type?: ContractTemplateType;
  body: string;
  triggerLabel?: string;
}

/**
 * 계약 양식 미리보기 (`9_계약양식`·`9_양식등록`·`9_양식편집` › 미리보기).
 *
 * **채울 수 없는 자리표시자를 화면이 직접 세지 않습니다.** 어떤 이름을 채울 수
 * 있는지는 서버가 아는 사실이라 `POST /api/admin/contract-templates/preview` 가
 * 판정해 `unknownPlaceholders` 로 돌려줍니다. 프론트가 같은 판정을 한 벌 더 가지면
 * 서버가 자리표시자를 늘린 날부터 둘이 다른 답을 냅니다.
 *
 * 저장하기 전에 열어 보는 것이고 아무것도 저장하지 않습니다.
 */
export function ContractTemplatePreview({
  type,
  body,
  triggerLabel = "미리보기",
}: Readonly<ContractTemplatePreviewProps>) {
  const [open, setOpen] = React.useState(false);
  const [preview, setPreview] = React.useState<Preview | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const show = async () => {
    setOpen(true);
    setPreview(null);
    setError(null);
    setLoading(true);

    try {
      if (!type) throw new ApiError(422, "INVALID_REQUEST", "양식 유형을 먼저 선택해 주세요.");
      setPreview(await requestContractTemplatePreview({ type, body }));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "미리보기를 만들지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button variant="outline" onClick={show} disabled={!body.trim()}>
        {triggerLabel}
      </Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size="lg"
        showClose
        aria-labelledby="contract-template-preview-title"
      >
        <ModalHeader
          id="contract-template-preview-title"
          title="계약 양식 미리보기"
          description="자리표시자를 보기용 값으로 채운 모습입니다. 실제 계약 데이터가 아닙니다."
        />
        <ModalBody>
          {preview && preview.unknownPlaceholders.length > 0 && (
            <Callout
              tone="warning"
              size="sm"
              title="채울 수 없는 자리표시자가 있습니다"
              className="mb-4"
            >
              {preview.unknownPlaceholders.join(", ")} — 이대로 저장하면 계약서에 그대로 찍힙니다.
            </Callout>
          )}
          {error && (
            <Callout tone="danger" size="sm" className="mb-4">
              {error}
            </Callout>
          )}
          {/*
            문서를 iframe 안에 가둡니다. 위 `previewDocument` 주석의 두 겹이 여기 걸립니다.
            `srcDoc` 이라 바깥 요청 없이 그 자리에서 그려집니다.
          */}
          <iframe
            title="계약 양식 미리보기 내용"
            sandbox=""
            srcDoc={previewDocument(preview?.html ?? "")}
            className="h-[440px] w-full rounded-lg border border-line bg-white"
          />
          {loading && (
            <p className="mt-2 text-[13px] text-fg-subtle" role="status">
              미리보기를 만드는 중입니다.
            </p>
          )}
        </ModalBody>
        <ModalFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            닫기
          </Button>
        </ModalFooter>
      </Modal>
    </>
  );
}
