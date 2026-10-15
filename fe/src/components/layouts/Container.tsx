import * as React from "react";
import { cn } from "@/lib/cn";

/**
 * Container — 화면 본문 폭을 토큰으로 고정합니다.
 * 캡처 기준 폭: 일반 720 · 관리자 840 · 랜딩 960.
 * 컴포넌트마다 임의의 px 를 쓰지 않고 이 값만 사용합니다.
 */
export type ContainerWidth = "content" | "wide" | "landing" | "full";

export interface ContainerProps extends React.HTMLAttributes<HTMLDivElement> {
  width?: ContainerWidth;
  as?: React.ElementType;
}

const widths: Record<ContainerWidth, string> = {
  content: "max-w-content",
  wide: "max-w-content-wide",
  landing: "max-w-landing",
  full: "max-w-none",
};

export function Container({ width = "content", as: Comp = "div", className, ...props }: ContainerProps) {
  return <Comp className={cn("mx-auto w-full px-4 sm:px-6", widths[width], className)} {...props} />;
}
