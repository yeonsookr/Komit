import { Container } from "@/components/layouts";
import { Eyebrow } from "@/components/common";
import { TOTAL_STEPS } from "@/lib/survey";

export interface SurveyStepProps {
  /** 1부터 셉니다. */
  step: number;
  title: string;
  description?: string;
  children: React.ReactNode;
  /** 하단 버튼 줄. */
  footer?: React.ReactNode;
}

/**
 * SurveyStep — 설문 한 단계의 껍데기.
 * `STEP 1 / 4` · 제목 · 설명 · 본문 · 하단 버튼을 같은 간격으로 맞춥니다.
 */
export function SurveyStep({ step, title, description, children, footer }: Readonly<SurveyStepProps>) {
  return (
    <Container className="py-10 sm:py-14">
      <Eyebrow mono>
        STEP {step} / {TOTAL_STEPS}
      </Eyebrow>
      <h1 className="mt-2 text-[28px] leading-tight font-bold text-fg">{title}</h1>
      {description && <p className="mt-1.5 text-sm text-fg-muted">{description}</p>}

      <div className="mt-8 flex flex-col gap-6">{children}</div>

      {footer && <div className="mt-10 flex justify-end gap-3">{footer}</div>}
    </Container>
  );
}
