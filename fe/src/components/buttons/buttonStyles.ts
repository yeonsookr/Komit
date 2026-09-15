import { cn } from "@/lib/cn";

/**
 * 버튼 모양. Button 컴포넌트와 링크(`<Link>`, `<a>`)가 같은 클래스를 씁니다.
 *
 * 이 파일에는 "use client" 가 없습니다. 서버 컴포넌트가 buttonClass() 를 호출해야 하는데,
 * 클라이언트 모듈의 함수는 서버에서 부를 수 없기 때문입니다(컴포넌트로 렌더하거나 props 로만 넘길 수 있음).
 *
 * 화면에서 관찰된 5가지 변형:
 *  - primary : 보라 solid  (문제 진단하기, 상담 시작하기, 다음 →, 입장, 리뷰 남기기)
 *  - dark    : 먹색 solid  (보내기, 저장하기, 서명하고 계약하기, 예치하기, 신고하기)
 *  - outline : 흰 배경 + 회색 테두리 (취소, 수정 제안하기, 사진 바꾸기, 파일 고르기)
 *  - ghost   : 텍스트만, 보라 (+ 항목 추가, 보기 ›, 모두 읽음으로)
 *  - danger  : 연분홍 배경 + 빨간 테두리/텍스트 (문제 신고, 세션 종료, 탈퇴하기)
 */
export type ButtonVariant = "primary" | "dark" | "outline" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg" | "xl";

export interface ButtonStyleOptions {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** 가로 전체 채움 (스티키 CTA, 카드 하단 버튼) */
  fullWidth?: boolean;
  className?: string;
}

const base =
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap font-semibold " +
  "rounded-md transition-colors duration-150 select-none " +
  "disabled:cursor-not-allowed disabled:bg-surface disabled:text-fg-disabled disabled:border-transparent " +
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";

const variants: Record<ButtonVariant, string> = {
  primary: "bg-primary text-on-primary hover:bg-primary-hover active:bg-primary-active",
  dark: "bg-ink text-on-ink hover:bg-ink-hover",
  outline:
    "bg-bg text-fg border border-line hover:bg-surface-2 active:bg-surface " +
    "disabled:bg-bg disabled:border-line",
  ghost:
    "bg-transparent text-primary hover:bg-primary-soft-hover active:bg-primary-soft px-2 " +
    "disabled:bg-transparent",
  danger:
    "bg-danger-bg-strong text-danger border border-danger-border hover:bg-danger-bg " +
    "disabled:bg-bg",
};

const sizes: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-[13px]",
  md: "h-10 px-4 text-sm",
  lg: "h-12 px-5 text-[15px]",
  xl: "h-14 px-6 text-base",
};

/**
 * 버튼 모양 클래스. 링크를 버튼처럼 보이게 할 때 씁니다 —
 * `<Link><Button/></Link>` 처럼 인터랙티브 요소를 겹치지 않기 위한 출구입니다.
 *
 *   <Link href={ROUTES.developer(id)} className={buttonClass({ fullWidth: true })}>상담 시작하기</Link>
 */
export function buttonClass({
  variant = "primary",
  size = "md",
  fullWidth,
  className,
}: ButtonStyleOptions = {}): string {
  return cn(base, variants[variant], sizes[size], fullWidth && "w-full", className);
}
