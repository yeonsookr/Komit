import { Card, Eyebrow } from "@/components/common";
import { cn } from "@/lib/cn";

/**
 * 왼쪽 카드의 예시 진단 결과. 실제 데이터가 아니라 서비스가 무엇을 보여주는지 설명하는 그림입니다.
 * 위험도 표기는 진단서 화면(2_진단서)의 높음·보통·해결됨을 그대로 씁니다.
 */
const SAMPLE_FINDINGS = [
  { severity: "높음", mark: "✕", tone: "text-danger", text: "열쇠가 인터넷에 그대로 보입니다" },
  { severity: "보통", mark: "!", tone: "text-warning", text: "사업자 정보 표기가 없습니다" },
  { severity: "해결됨", mark: "✓", tone: "text-success", text: "주소 연결 · 보안 인증서" },
] as const;

/** 오른쪽 카드의 예시 따라하기. 앞의 둘은 끝났고 셋째가 남은 모양입니다. */
const SAMPLE_STEPS = [
  { text: "설정 파일에서 열쇠 지우기", done: true },
  { text: "공개용 열쇠로 바꾸기", done: true },
  { text: "서버 자리로 옮기기", done: false },
] as const;

/** CheckupIntro — "사전 점검 서비스" 소개. 진단 결과와 따라하기 예시 카드 두 장입니다. */
export function CheckupIntro() {
  return (
    <section aria-labelledby="checkup-heading" className="py-16 text-center sm:py-20">
      <Eyebrow>사전 점검 서비스</Eyebrow>
      <h2 id="checkup-heading" className="mt-3 text-2xl font-bold tracking-tight text-fg sm:text-3xl">
        무엇부터 고쳐야 하는지 알려드립니다
      </h2>

      <div className="mt-10 grid grid-cols-1 gap-5 text-left md:grid-cols-2">
        {/* 진단 결과 */}
        <Card padding="lg" className="flex items-center">
          <Card variant="surface" padding="md" className="w-full">
            <p className="text-sm font-bold text-fg">출시까지 3개 남았습니다</p>
            <ul className="mt-3 flex flex-col gap-2.5 text-[13px]">
              {SAMPLE_FINDINGS.map((finding) => (
                <li key={finding.text} className="flex items-center gap-3">
                  <span className={cn("flex w-14 shrink-0 items-center gap-1.5 font-semibold", finding.tone)}>
                    <span aria-hidden>{finding.mark}</span>
                    {finding.severity}
                  </span>
                  <span className="text-fg-secondary">{finding.text}</span>
                </li>
              ))}
            </ul>
          </Card>
        </Card>

        {/* 따라하기 */}
        <Card padding="lg">
          <p className="flex items-center gap-2 text-sm font-bold text-fg">
            <ChecklistIcon />
            고치는 순서를 따라갑니다
          </p>
          <Card variant="surface" padding="md" className="mt-4">
            <div className="flex items-center justify-between gap-4">
              <p className="text-sm font-bold text-fg">데이터베이스 잠그기</p>
              <span className="font-mono text-[13px] font-semibold text-primary tabular-nums">15분</span>
            </div>
            <ol className="mt-3 flex flex-col gap-2.5 text-[13px]">
              {SAMPLE_STEPS.map((step, index) => (
                <li key={step.text} className="flex items-center gap-3">
                  <span
                    aria-hidden
                    className={cn(
                      "w-4 shrink-0 text-center font-mono tabular-nums",
                      step.done ? "text-success" : "text-fg-subtle",
                    )}
                  >
                    {step.done ? "✓" : index + 1}
                  </span>
                  <span className={step.done ? "text-fg-muted" : "text-fg-secondary"}>{step.text}</span>
                </li>
              ))}
            </ol>
          </Card>
        </Card>
      </div>
    </section>
  );
}

function ChecklistIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4 text-primary"
      aria-hidden
    >
      <path d="M3 6.5l1.5 1.5L7 5.5M3 13.5l1.5 1.5L7 12.5M3 20l1.5 1.5L7 19M11 7h10M11 14h10M11 21h10" />
    </svg>
  );
}
