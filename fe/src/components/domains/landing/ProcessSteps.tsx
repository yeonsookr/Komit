/** 거래가 흘러가는 네 단계. 헤더·푸터의 "이용 방법"(ROUTES.howTo)이 이 섹션의 앵커를 가리킵니다. */
const STEPS = ["상담", "요청서 확정", "예치 후 착수", "완료 확인"];

/** ProcessSteps — "진행 순서". 번호와 선으로 이어진 네 단계를 한 줄에 놓습니다. */
export function ProcessSteps() {
  return (
    <section id="process" aria-labelledby="process-heading" className="scroll-mt-gnb py-12">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="process-heading" className="text-lg font-bold text-fg">
          진행 순서
        </h2>
        <p className="text-[13px] text-fg-muted">예치한 돈은 계약이 전부 끝나면 한 번에 지급됩니다</p>
      </div>

      <ol className="mt-6 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-4">
        {STEPS.map((title, index) => (
          <li key={title} className="min-w-0">
            <div className="flex items-center gap-3">
              <span className="font-mono text-[13px] text-fg-subtle tabular-nums">{index + 1}</span>
              <span aria-hidden className="h-px flex-1 bg-line" />
            </div>
            <p className="mt-3 text-base font-bold text-fg">{title}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
