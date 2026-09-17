"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { cn } from "@/lib/cn";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError } from "@/types/api";
import type { Inspection } from "@/types/diagnosis";

/** ② 에서 AI 도구에 그대로 붙여 넣는 말. */
const RUN_PROMPT = "komit-scan.md를 읽고 이 프로젝트를 검사해줘";
/** ① 에 적히는 설치 명령. */
const INSTALL_COMMAND = "/plugin install komit-scan@komit";

export interface ScanFileStepProps {
  diagnosisId: number;
  /** 이미 올린 결과 파일 이름. 없으면 안내 문구를 보여줍니다. */
  uploadedFileName: string | null;
  onUploaded: (fileName: string) => void;
}

/**
 * ScanFileStep — 설문3. 검사 파일을 받아 직접 실행하고 결과만 올립니다.
 *
 * 소스 코드는 올라가지 않습니다. 이 단계에는 설문 문항이 없어 답을 저장하지
 * 않습니다(목 명세의 답 저장 화면은 설문1·2·4입니다).
 */
export function ScanFileStep({
  diagnosisId,
  uploadedFileName,
  onUploaded,
}: Readonly<ScanFileStepProps>) {
  const showToast = useToast();
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = React.useState(false);

  /** ① 검사 파일을 내려받습니다. 파일 이름은 응답 헤더가 정합니다. */
  const download = () => {
    // 브라우저가 내려받기를 처리하도록 자기 도메인 경로를 그대로 엽니다.
    window.location.href = INTERNAL.SCAN_FILE(diagnosisId);
  };

  /** ② 실행할 말을 복사합니다. */
  const copyPrompt = async () => {
    try {
      await navigator.clipboard.writeText(RUN_PROMPT);
      showToast({ tone: "success", title: "복사했습니다." });
    } catch {
      showToast({ tone: "warning", title: "복사하지 못했습니다. 직접 선택해 복사해 주세요." });
    }
  };

  /** ③ 만들어진 결과 파일을 올립니다. */
  const upload = async (file: File) => {
    setUploading(true);

    const form = new FormData();
    form.append("file", file);

    try {
      await clientApi.post<Inspection>(INTERNAL.INSPECTIONS(diagnosisId), form);
      onUploaded(file.name);
    } catch (error) {
      // 받는 형식이 아니면 400 SCAN_FILE_INVALID 가 옵니다.
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "파일을 올리지 못했습니다.",
        description: "내려받은 결과 파일을 그대로 올려주세요.",
      });
    } finally {
      setUploading(false);
      // 같은 파일을 다시 골라도 change 가 일어나도록 비웁니다.
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <NumberedRow index="①" label="검사 파일 받기">
        <CommandBox
          text={INSTALL_COMMAND}
          action={
            <Button variant="outline" size="sm" onClick={download}>
              내려받기
            </Button>
          }
        />
      </NumberedRow>

      <NumberedRow index="②" label="쓰시는 AI 도구에서 실행">
        <CommandBox
          text={RUN_PROMPT}
          action={
            <Button variant="outline" size="sm" onClick={copyPrompt} leftIcon={<CopyIcon />}>
              복사
            </Button>
          }
        />
      </NumberedRow>

      <NumberedRow index="③" label="만들어진 결과 파일 올리기">
        <CommandBox
          text={uploadedFileName ?? "komit-scan-XXXXX.md"}
          muted={uploadedFileName === null}
          action={
            <>
              <input
                ref={inputRef}
                type="file"
                className="sr-only"
                aria-label="검사 결과 파일"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void upload(file);
                }}
              />
              <Button
                variant="outline"
                size="sm"
                loading={uploading}
                onClick={() => inputRef.current?.click()}
              >
                파일 선택
              </Button>
            </>
          }
        />
      </NumberedRow>

      <div className="flex flex-col gap-1">
        <HelpDisclosure summary="Claude Code · Cursor · Codex 실행 방법 보기">
          내려받은 <Mono>komit-scan.md</Mono> 를 프로젝트 폴더에 두고, 쓰시는 도구의 대화창에
          ② 의 말을 그대로 붙여 넣으세요. 도구가 검사를 마치면 결과 파일이 만들어집니다.
        </HelpDisclosure>
        <HelpDisclosure summary="AI 도구를 처음 쓰시나요? 5분 안내">
          AI 도구가 없어도 괜찮습니다. 무료 상담에서 개발자가 대신 확인해 드릴 수 있어요.
        </HelpDisclosure>
      </div>
    </div>
  );
}

/** ② 버튼 앞의 복사 아이콘. */
function CopyIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-3.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <rect x="9" y="9" width="11" height="11" rx="2" />
      <path d="M5 15V5a2 2 0 0 1 2-2h10" />
    </svg>
  );
}

function Mono({ children }: Readonly<{ children: React.ReactNode }>) {
  return <span className="font-mono text-[13px] text-fg">{children}</span>;
}

/** ① ② ③ 번호와 라벨이 붙은 한 줄. */
function NumberedRow({
  index,
  label,
  children,
}: Readonly<{ index: string; label: string; children: React.ReactNode }>) {
  return (
    <section>
      <h2 className="text-sm font-semibold text-fg">
        <span className="mr-1 text-fg-muted">{index}</span>
        {label}
      </h2>
      <div className="mt-2">{children}</div>
    </section>
  );
}

/** 회색 면에 담긴 명령·파일 이름과 오른쪽 버튼. */
function CommandBox({
  text,
  action,
  muted,
}: Readonly<{ text: string; action: React.ReactNode; muted?: boolean }>) {
  return (
    <div className="flex items-center gap-4 rounded-lg bg-surface px-4 py-3.5">
      <code
        className={cn(
          "min-w-0 flex-1 truncate font-mono text-sm",
          muted ? "text-fg-subtle" : "text-fg",
        )}
      >
        {text}
      </code>
      <div className="shrink-0">{action}</div>
    </div>
  );
}

/** 접었다 펴는 도움말. */
function HelpDisclosure({
  summary,
  children,
}: Readonly<{ summary: string; children: React.ReactNode }>) {
  return (
    <details className="group">
      <summary className="flex cursor-pointer list-none items-center gap-2 rounded-sm py-1.5 text-sm text-fg-muted hover:text-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
        <span aria-hidden className="text-fg-subtle transition-transform group-open:rotate-90">
          ›
        </span>
        {summary}
      </summary>
      <p className="pl-5 text-[13px] leading-6 text-fg-muted">{children}</p>
    </details>
  );
}
